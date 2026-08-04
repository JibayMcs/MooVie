package fr.moovie.tv.data.backup

import fr.moovie.tv.data.home.HomeLayoutRepository
import fr.moovie.tv.data.home.mergeHomeLayouts
import fr.moovie.tv.data.net.DohProvider
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
        resume = watchRepo.continueWatching.first(),
        watchlist = watchRepo.watchlist.first(),
        watched = watchRepo.watched.first().toList(),
        history = watchRepo.history.first(),
        audioTracks = watchRepo.audioTracks(),
        titles = watchRepo.titles(),
        homeLayout = layoutRepo.layout.first(),
        tmdbApiKey = settingsRepo.tmdbApiKey.first().takeIf { includeApiKey && it.isNotBlank() },
        introDbApiKey = settingsRepo.introDbApiKey.first().takeIf { includeApiKey && it.isNotBlank() },
        settings = currentSettings(),
    )

    /**
     * Écrit la sauvegarde et rend le chemin obtenu, ou null si le support a
     * refusé l'écriture.
     */
    fun write(target: BackupTarget, backup: MoovieBackup, fileName: String): String? =
        writeBackup(target.id, fileName, BackupJson.encode(backup))

    /** Relit une sauvegarde depuis un support. Null si illisible ou étrangère. */
    fun read(path: String): MoovieBackup? = readBackup(path)?.let { BackupJson.decode(it) }

    /** Applique une sauvegarde et rend le bilan à afficher. */
    suspend fun import(backup: MoovieBackup, mode: ImportMode): ImportReport {
        val current = WatchState(
            resume = watchRepo.continueWatching.first(),
            watchlist = watchRepo.watchlist.first(),
            watched = watchRepo.watched.first(),
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
        backup.settings?.let { applySettings(it) }
        backup.tmdbApiKey?.takeIf { it.isNotBlank() }?.let { settingsRepo.setTmdbApiKey(it) }
        backup.introDbApiKey?.takeIf { it.isNotBlank() }?.let { settingsRepo.setIntroDbApiKey(it) }
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
