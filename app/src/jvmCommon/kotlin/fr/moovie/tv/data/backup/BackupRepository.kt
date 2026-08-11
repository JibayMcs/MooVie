package fr.moovie.tv.data.backup

import fr.moovie.tv.data.home.HomeLayoutRepository
import fr.moovie.tv.data.home.mergeHomeLayouts
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.profile.Profile
import fr.moovie.tv.data.profile.ProfileRepository
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.SettingsRepository
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.data.watch.WatchProgressRepository
import fr.moovie.tv.shared.appVersionName
import fr.moovie.tv.shared.platformName
import kotlinx.coroutines.flow.first

/**
 * Fabrique et applique les sauvegardes.
 *
 * Le calcul de la fusion vit à part, dans [mergeBackup] : c'est une fonction
 * pure et testée, la seule partie du dispositif qui puisse détruire des
 * données. Ce dépôt ne fait que la brancher aux magasins.
 */
class BackupRepository(
    private val watchRepo: WatchProgressRepository,
    private val settingsRepo: SettingsRepository,
    private val layoutRepo: HomeLayoutRepository = HomeLayoutRepository(),
    private val profileRepo: ProfileRepository = ProfileRepository(),
) {

    /**
     * Assemble l'état de l'appareil.
     *
     * @param includeApiKey la clé TMDB est écrite **en clair** dans le fichier.
     *   Le dépôt étant public, tout chiffrement embarqué serait réversible par
     *   quiconque lit le code : plutôt qu'une fausse sécurité, l'utilisateur
     *   décide en connaissance de cause, prévenu par l'écran d'export.
     */
    suspend fun export(includeApiKey: Boolean, now: Long): MoovieBackup = MoovieBackup(
        exportedAt = now,
        appVersion = appVersionName,
        platform = platformName,
        // Tous les profils, pas seulement celui qu'on regarde. Sauvegarder
        // l'appareil et n'en emporter qu'un tiers serait la pire des demi-mesures :
        // l'utilisateur croit ses données à l'abri, et découvre le manque le jour
        // où il restaure.
        profiles = allProfiles(),
        profilesRemovedAt = profileRepo.deletedAt.first(),
        tmdbApiKey = settingsRepo.tmdbApiKey.first().takeIf { includeApiKey && it.isNotBlank() },
        introDbApiKey = settingsRepo.introDbApiKey.first().takeIf { includeApiKey && it.isNotBlank() },
        settings = currentSettings(),
    )

    private suspend fun allProfiles(): List<BackupProfile> =
        profileRepo.profiles.first().map { profile ->
            // Dépôts visant explicitement ce profil : basculer dessus pour le
            // lire aurait fait clignoter l'écran de l'utilisateur à chaque export.
            val watch = WatchProgressRepository(profile.id)
            val layout = HomeLayoutRepository(profile.id)
            BackupProfile(
                id = profile.id,
                createdAt = profile.createdAt,
                name = profile.name,
                colorIndex = profile.colorIndex,
                resume = watch.continueWatching.first(),
                watchlist = watch.watchlist.first(),
                watched = watch.watched.first().toList(),
                watchedAt = watch.watchedAt(),
                resumeRemovedAt = watch.resumeRemovedAt(),
                watchlistRemovedAt = watch.watchlistRemovedAt(),
                history = watch.history.first(),
                audioTracks = watch.audioTracks(),
                titles = watch.titles(),
                homeLayout = layout.layout.first(),
            )
        }

    /**
     * Écrit la sauvegarde et rend le chemin obtenu, ou null si le support a
     * refusé l'écriture.
     */
    fun write(target: BackupTarget, backup: MoovieBackup, fileName: String): String? =
        writeBackup(target.id, fileName, BackupJson.encode(backup))

    /** Relit une sauvegarde depuis un support. Null si illisible ou étrangère. */
    fun read(path: String): MoovieBackup? = readBackup(path)?.let { BackupJson.decode(it) }

    /**
     * Applique une sauvegarde et rend le bilan à afficher.
     *
     * Deux formats à servir : un fichier d'avant les profils n'en désigne aucun
     * et part donc dans le profil actif — c'est la seule lecture possible, il ne
     * dit pas de qui il vient. Un fichier v2 s'applique profil par profil.
     */
    suspend fun import(backup: MoovieBackup, mode: ImportMode): ImportReport {
        val report =
            if (backup.profiles.isEmpty()) importLegacy(backup, mode) else importProfiles(backup, mode)
        backup.settings?.let { applySettings(it) }
        backup.tmdbApiKey?.takeIf { it.isNotBlank() }?.let { settingsRepo.setTmdbApiKey(it) }
        backup.introDbApiKey?.takeIf { it.isNotBlank() }?.let { settingsRepo.setIntroDbApiKey(it) }
        return report
    }

    /**
     * Un profil par entrée du fichier, recréé au besoin **à son identifiant
     * d'origine** pour qu'un second import retombe dessus au lieu d'en faire un
     * jumeau.
     *
     * Le bilan est la somme des profils : l'écran d'après annonce ce qui a bougé
     * sur l'appareil, pas un détail par personne que personne n'a demandé.
     */
    private suspend fun importProfiles(backup: MoovieBackup, mode: ImportMode): ImportReport {
        var total = ImportReport(0, 0, 0, 0, 0, 0)

        // Les retraits **avant** les profils. Ils décident lesquels ont encore
        // le droit d'exister : le fichier distant contient toujours les profils
        // supprimés depuis, et les recréer sans poser la question est ce qui les
        // faisait ressusciter au lancement suivant.
        profileRepo.mergeDeletions(backup.profilesRemovedAt)
        val tombstones = profileRepo.deletedAt.first()

        for (entry in backup.profiles) {
            val profile = Profile(
                id = entry.id,
                name = entry.name,
                colorIndex = entry.colorIndex,
                createdAt = entry.createdAt,
            )
            // Retrait postérieur à la création : la suppression est la décision
            // la plus récente, on ne réécrit rien — ni le profil, ni ses données.
            if (profileRepo.isDeleted(profile, tombstones)) continue
            profileRepo.upsert(profile)
            val watch = WatchProgressRepository(entry.id)
            val layout = HomeLayoutRepository(entry.id)
            val current = WatchState(
                resume = watch.continueWatching.first(),
                watchlist = watch.watchlist.first(),
                watched = watch.watched.first(),
                watchedAt = watch.watchedAt(),
                resumeRemovedAt = watch.resumeRemovedAt(),
                watchlistRemovedAt = watch.watchlistRemovedAt(),
                history = watch.history.first(),
                audioTracks = watch.audioTracks(),
            )
            val incoming = WatchState(
                resume = entry.resume,
                watchlist = entry.watchlist,
                watched = entry.watched.toSet(),
                watchedAt = entry.watchedAt,
                resumeRemovedAt = entry.resumeRemovedAt,
                watchlistRemovedAt = entry.watchlistRemovedAt,
                history = entry.history,
                audioTracks = entry.audioTracks,
            )
            val (merged, report) = mergeWatchState(current, incoming, mode)
            watch.replaceAll(
                resume = merged.resume,
                watchlist = merged.watchlist,
                watched = merged.watched,
                history = merged.history,
                audioTracks = merged.audioTracks,
                titles = entry.titles,
                watchedAt = merged.watchedAt,
                resumeRemovedAt = merged.resumeRemovedAt,
                watchlistRemovedAt = merged.watchlistRemovedAt,
            )
            if (entry.homeLayout.isNotEmpty()) {
                layout.replaceAll(
                    when (mode) {
                        ImportMode.REPLACE -> entry.homeLayout
                        ImportMode.MERGE -> mergeHomeLayouts(layout.layout.first(), entry.homeLayout)
                    },
                )
            }
            total = total.plus(report)
        }
        return total
    }

    /** Chemin des sauvegardes d'avant les profils : tout va dans le profil actif. */
    private suspend fun importLegacy(backup: MoovieBackup, mode: ImportMode): ImportReport {
        val current = WatchState(
            resume = watchRepo.continueWatching.first(),
            watchlist = watchRepo.watchlist.first(),
            watched = watchRepo.watched.first(),
            watchedAt = watchRepo.watchedAt(),
            resumeRemovedAt = watchRepo.resumeRemovedAt(),
            watchlistRemovedAt = watchRepo.watchlistRemovedAt(),
            history = watchRepo.history.first(),
            audioTracks = watchRepo.audioTracks(),
        )
        val (merged, report) = mergeBackup(current, backup, mode)

        watchRepo.replaceAll(
            resume = merged.resume,
            watchlist = merged.watchlist,
            watched = merged.watched,
            history = merged.history,
            audioTracks = merged.audioTracks,
            // Les fiches de l'appareil ne sont pas effacées par `replaceAll` :
            // celles du fichier viennent les compléter.
            titles = backup.titles,
            watchedAt = merged.watchedAt,
            resumeRemovedAt = merged.resumeRemovedAt,
            watchlistRemovedAt = merged.watchlistRemovedAt,
        )
        // L'ordre du fichier l'emporte ; en fusion, les genres épinglés propres à
        // cet appareil sont conservés (voir [mergeHomeLayouts]).
        if (backup.homeLayout.isNotEmpty()) {
            layoutRepo.replaceAll(
                when (mode) {
                    ImportMode.REPLACE -> backup.homeLayout
                    ImportMode.MERGE -> mergeHomeLayouts(layoutRepo.layout.first(), backup.homeLayout)
                },
            )
        }
        return report
    }

    private suspend fun currentSettings() = BackupSettings(
        streamLanguage = settingsRepo.streamLanguage.first().name,
        disabledProviders = settingsRepo.disabledProviders.first().toList(),
        providerOrder = settingsRepo.providerOrder.first(),
        dohEnabled = settingsRepo.dohEnabled.first(),
        dohProvider = settingsRepo.dohProvider.first().name,
        skipIntroOutro = settingsRepo.skipIntroOutro.first(),
        autoPlayNext = settingsRepo.autoPlayNext.first(),
        playerClock = settingsRepo.playerClock.first(),
        hideHistoryWidgets = settingsRepo.hideHistoryWidgets.first(),
        updateInterval = settingsRepo.updateInterval.first().name,
        screensaverDelay = settingsRepo.screensaverDelay.first().name,
        splashAnimation = settingsRepo.splashAnimation.first(),
        subtitleLanguages = settingsRepo.subtitleLanguages.first(),
        osUsername = settingsRepo.osUsername.first().takeIf { it.isNotBlank() },
        osRemember = settingsRepo.osRemember.first(),
    )

    /**
     * Les réglages ne se fusionnent pas : ils sont repris tels quels, champ par
     * champ, et un champ absent laisse celui de l'appareil intact. Les valeurs
     * d'énumération passent par `runCatching` — un export d'une version plus
     * ancienne peut nommer un résolveur DoH qui n'existe plus.
     */
    private suspend fun applySettings(s: BackupSettings) {
        s.streamLanguage?.let { name ->
            runCatching { StreamLanguage.valueOf(name) }.getOrNull()
                ?.let { settingsRepo.setStreamLanguage(it) }
        }
        s.dohProvider?.let { name ->
            runCatching { DohProvider.valueOf(name) }.getOrNull()
                ?.let { settingsRepo.setDohProvider(it) }
        }
        s.updateInterval?.let { name ->
            runCatching { UpdateInterval.valueOf(name) }.getOrNull()
                ?.let { settingsRepo.setUpdateInterval(it) }
        }
        s.screensaverDelay?.let { name ->
            runCatching { ScreensaverDelay.valueOf(name) }.getOrNull()
                ?.let { settingsRepo.setScreensaverDelay(it) }
        }
        s.dohEnabled?.let { settingsRepo.setDohEnabled(it) }
        s.skipIntroOutro?.let { settingsRepo.setSkipIntroOutro(it) }
        s.autoPlayNext?.let { settingsRepo.setAutoPlayNext(it) }
        s.playerClock?.let { settingsRepo.setPlayerClock(it) }
        s.hideHistoryWidgets?.let { settingsRepo.setHideHistoryWidgets(it) }
        s.splashAnimation?.let { settingsRepo.setSplashAnimation(it) }
        // Vide = le fichier n'en parle pas. Écraser les langues de l'appareil par
        // une liste vide le laisserait sans aucun sous-titre à chercher.
        if (s.subtitleLanguages.isNotEmpty()) settingsRepo.setSubtitleLanguages(s.subtitleLanguages)
        s.osUsername?.takeIf { it.isNotBlank() }?.let {
            settingsRepo.restoreOsAccount(it, remember = s.osRemember ?: false)
        }
        if (s.providerOrder.isNotEmpty()) settingsRepo.setProviderOrder(s.providerOrder)
        s.disabledProviders.forEach { settingsRepo.setProviderEnabled(it, enabled = false) }
    }
}
