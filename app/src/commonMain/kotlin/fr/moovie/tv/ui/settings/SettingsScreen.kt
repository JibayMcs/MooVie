package fr.moovie.tv.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuOpen
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ClosedCaption
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.remote.remoteTypable
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.cast_scan_action
import fr.moovie.tv.resources.cast_scan_label
import fr.moovie.tv.resources.cast_scan_never
import fr.moovie.tv.resources.cast_scan_refused
import fr.moovie.tv.resources.cast_scan_running
import fr.moovie.tv.resources.cast_scan_silent
import fr.moovie.tv.resources.cast_scan_unreachable
import fr.moovie.tv.resources.cast_scan_unsupported
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_disabled
import fr.moovie.tv.resources.common_enabled
import fr.moovie.tv.resources.screensaver_after_hours
import fr.moovie.tv.resources.screensaver_after_minutes
import fr.moovie.tv.resources.screensaver_never
import fr.moovie.tv.resources.settings_autoplay
import fr.moovie.tv.resources.settings_key_hide
import fr.moovie.tv.resources.settings_key_show
import fr.moovie.tv.resources.settings_autoplay_help
import androidx.compose.material.icons.filled.Person
import fr.moovie.tv.data.profile.Profile
import fr.moovie.tv.data.profile.ProfileRepository
import fr.moovie.tv.resources.profile_default
import fr.moovie.tv.resources.profile_switch
import fr.moovie.tv.resources.profile_switch_help
import fr.moovie.tv.resources.settings_cat_profiles
import fr.moovie.tv.ui.profile.LocalSwitchProfile
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.ui.download.DownloadsSection
import fr.moovie.tv.ui.sync.SyncSection
import fr.moovie.tv.resources.settings_cat_api
import fr.moovie.tv.resources.settings_cat_backup
import fr.moovie.tv.resources.settings_cat_dns
import fr.moovie.tv.resources.settings_cat_home
import fr.moovie.tv.resources.settings_cat_intro
import fr.moovie.tv.resources.settings_cat_playback
import fr.moovie.tv.resources.settings_cat_screensaver
import fr.moovie.tv.resources.settings_cat_sources
import fr.moovie.tv.resources.settings_cat_downloads
import fr.moovie.tv.resources.settings_cat_sync
import fr.moovie.tv.resources.settings_cat_subtitles
import fr.moovie.tv.resources.settings_cat_update
import fr.moovie.tv.resources.settings_disable
import fr.moovie.tv.resources.settings_dns_help
import fr.moovie.tv.resources.settings_doh_off
import fr.moovie.tv.resources.settings_doh_on
import fr.moovie.tv.resources.settings_doh_resolver
import fr.moovie.tv.resources.settings_cat_history
import fr.moovie.tv.resources.settings_enable
import fr.moovie.tv.resources.settings_history_help
import fr.moovie.tv.resources.settings_introdb_help
import fr.moovie.tv.resources.settings_introdb_hint
import fr.moovie.tv.resources.settings_introdb_key
import fr.moovie.tv.resources.settings_intro_help
import fr.moovie.tv.resources.settings_language
import fr.moovie.tv.resources.settings_splash
import fr.moovie.tv.resources.settings_splash_help
import fr.moovie.tv.resources.common_show
import fr.moovie.tv.resources.common_hide
import fr.moovie.tv.resources.settings_history_widgets
import fr.moovie.tv.resources.settings_move_down
import fr.moovie.tv.resources.settings_move_up
import fr.moovie.tv.resources.settings_player_clock
import fr.moovie.tv.resources.settings_trailer_autoplay
import fr.moovie.tv.resources.settings_trailer_sound
import fr.moovie.tv.resources.settings_trailer_sound_help
import fr.moovie.tv.resources.settings_trailer_autoplay_help
import fr.moovie.tv.resources.settings_player_clock_help
import fr.moovie.tv.resources.settings_screensaver_delay
import fr.moovie.tv.resources.settings_screensaver_help
import fr.moovie.tv.resources.settings_skip_intro
import fr.moovie.tv.resources.settings_sources_help
import fr.moovie.tv.resources.settings_sources_cache
import fr.moovie.tv.resources.settings_sources_cache_action
import fr.moovie.tv.resources.settings_sources_cache_done
import fr.moovie.tv.resources.settings_sources_cache_help
import fr.moovie.tv.data.sources.SourceCacheRepository
import fr.moovie.tv.data.sources.StreamMeasureRepository
import fr.moovie.tv.resources.settings_stream_lang
import fr.moovie.tv.resources.settings_title
import fr.moovie.tv.resources.settings_tmdb_help
import fr.moovie.tv.resources.settings_tmdb_hint
import fr.moovie.tv.resources.settings_tmdb_key
import fr.moovie.tv.resources.settings_update_help
import fr.moovie.tv.resources.settings_current_version
import fr.moovie.tv.resources.settings_update_interval
import fr.moovie.tv.resources.settings_update_prereleases
import fr.moovie.tv.resources.settings_update_prereleases_help
import fr.moovie.tv.resources.settings_check_now
import fr.moovie.tv.resources.settings_check_now_help
import fr.moovie.tv.resources.settings_check_running
import fr.moovie.tv.resources.settings_check_uptodate
import fr.moovie.tv.resources.settings_check_failed
import fr.moovie.tv.ui.update.UpdateCheck
import fr.moovie.tv.resources.update_every_hours
import fr.moovie.tv.resources.update_every_minutes
import fr.moovie.tv.resources.update_never
import fr.moovie.tv.ui.backup.BackupSection
import fr.moovie.tv.ui.home.HomeLayoutSection
import fr.moovie.tv.ui.adaptive.LocalUiFlavor
import fr.moovie.tv.resources.pairing_title
import fr.moovie.tv.resources.pairing_scan
import androidx.compose.material.icons.filled.SettingsRemote
import androidx.compose.runtime.rememberCoroutineScope
import fr.moovie.tv.data.cast.CastScan
import fr.moovie.tv.data.cast.CastScanVerdict
import fr.moovie.tv.data.remote.RemoteTargetRepository
import fr.moovie.tv.resources.remote_none
import fr.moovie.tv.resources.remote_link_invalid
import fr.moovie.tv.resources.remote_link_action
import fr.moovie.tv.resources.remote_link_hint
import fr.moovie.tv.resources.remote_link_help
import fr.moovie.tv.resources.remote_link_title
import fr.moovie.tv.resources.remote_none_help
import fr.moovie.tv.resources.remote_paired_help
import fr.moovie.tv.resources.remote_reconnect
import fr.moovie.tv.resources.remote_reconnect_action
import fr.moovie.tv.resources.remote_reconnect_failed
import fr.moovie.tv.resources.remote_reconnect_help
import fr.moovie.tv.resources.remote_reconnect_ok
import fr.moovie.tv.resources.remote_reconnect_running
import fr.moovie.tv.resources.remote_forget_target
import fr.moovie.tv.resources.settings_cat_remote
import fr.moovie.tv.resources.remote_forget
import fr.moovie.tv.resources.remote_forget_action
import fr.moovie.tv.resources.remote_forget_done
import fr.moovie.tv.resources.remote_forget_help
import kotlinx.coroutines.launch
import fr.moovie.tv.resources.pairing_action
import fr.moovie.tv.ui.adaptive.UiFlavor
import fr.moovie.tv.ui.adaptive.useBottomNav
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.subtitles.SubtitlesSection
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieSelect
import org.jetbrains.compose.resources.stringResource
import fr.moovie.tv.shared.appVersionName
import fr.moovie.tv.ui.theme.MOOVIE_ERROR
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_DIM
import fr.moovie.tv.ui.theme.MOOVIE_TEXT_FAINT
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import fr.moovie.tv.ui.theme.MOOVIE_SURFACE_HIGH
import fr.moovie.tv.ui.theme.MOOVIE_TEXT
import fr.moovie.tv.ui.theme.ESPACE
import fr.moovie.tv.ui.theme.ESPACE_SERRE

/**
 * Largeur du volet de navigation. Volontairement contenue : en 1080p l'écran ne
 * fait que 960 dp de large, un volet trop large étrangle la colonne des libellés.
 */
private val NAV_WIDTH = 260.dp

/** Sections de l'écran, dans l'ordre d'affichage du volet gauche. */

private enum class SettingsSection {
    // REMOTE juste après PROFILES : les deux répondent à « qui, et avec quoi »,
    // avant tout ce qui touche au contenu. L'appairage était dans API & Clés
    // parce qu'il n'y servait qu'à saisir des clés ; depuis qu'il porte aussi la
    // télécommande, il n'a plus rien à faire dans une section de secrets.
    PROFILES, REMOTE, API, HOME, PLAYBACK, INTRO, SUBTITLES, HISTORY, SCREENSAVER, UPDATE, DNS,
    SOURCES, BACKUP, SYNC, DOWNLOADS,
}

/**
 * Largeur de la barre repliée : une icône de 20 dp dans une cible de 48 dp,
 * marges comprises. C'est ce qu'elle coûte au contenu sur un téléphone — 68 dp
 * sur 448, contre 260 pour le volet déplié.
 */
private val RAIL_WIDTH = 68.dp

/**
 * Icône de chaque section, pour la barre repliée.
 *
 * Une barre d'icônes ne vaut que si chaque icône se devine sans son libellé :
 * une clé pour les clés d'API, un triangle de lecture pour la lecture, un
 * sous-titre pour les sous-titres. Là où aucun symbole ne s'impose (les
 * sources), on prend celui du domaine plutôt qu'une abstraction — et le libellé
 * reste à un appui, en dépliant.
 */
private fun sectionIcon(section: SettingsSection): ImageVector = when (section) {
    SettingsSection.PROFILES -> Icons.Default.Person
    SettingsSection.REMOTE -> Icons.Default.SettingsRemote
    SettingsSection.API -> Icons.Default.Key
    SettingsSection.HOME -> Icons.Default.ViewList
    SettingsSection.PLAYBACK -> Icons.Default.PlayArrow
    SettingsSection.INTRO -> Icons.Default.SkipNext
    SettingsSection.SUBTITLES -> Icons.Default.ClosedCaption
    SettingsSection.HISTORY -> Icons.Default.History
    SettingsSection.SCREENSAVER -> Icons.Default.Bedtime
    SettingsSection.UPDATE -> Icons.Default.SystemUpdate
    SettingsSection.DNS -> Icons.Default.Dns
    SettingsSection.SOURCES -> Icons.Default.Layers
    SettingsSection.BACKUP -> Icons.Default.Save
    SettingsSection.SYNC -> Icons.Default.CloudSync
    SettingsSection.DOWNLOADS -> Icons.Default.Download
}

@Composable
private fun sectionLabel(section: SettingsSection): String = stringResource(
    when (section) {
        SettingsSection.PROFILES -> Res.string.settings_cat_profiles
        SettingsSection.REMOTE -> Res.string.settings_cat_remote
        SettingsSection.API -> Res.string.settings_cat_api
        SettingsSection.HOME -> Res.string.settings_cat_home
        SettingsSection.PLAYBACK -> Res.string.settings_cat_playback
        SettingsSection.INTRO -> Res.string.settings_cat_intro
        SettingsSection.HISTORY -> Res.string.settings_cat_history
        SettingsSection.SCREENSAVER -> Res.string.settings_cat_screensaver
        SettingsSection.UPDATE -> Res.string.settings_cat_update
        SettingsSection.DNS -> Res.string.settings_cat_dns
        SettingsSection.SOURCES -> Res.string.settings_cat_sources
        SettingsSection.SUBTITLES -> Res.string.settings_cat_subtitles
        SettingsSection.BACKUP -> Res.string.settings_cat_backup
        SettingsSection.SYNC -> Res.string.settings_cat_sync
        SettingsSection.DOWNLOADS -> Res.string.settings_cat_downloads
    },
)

/**
 * Écran de réglages partagé TV + desktop, en deux volets : la liste des
 * sections à gauche, le contenu de la section choisie à droite.
 *
 * Le défilement vertical unique d'avant obligeait à traverser au D-pad tous les
 * contrôles des sections précédentes pour atteindre la dernière. Ici deux appuis
 * suffisent, et chaque ligne aligne son libellé à gauche et son contrôle à
 * droite plutôt que de les empiler.
 *
 * État hoisté ; [languageSelector] est un slot plateforme (changer la langue de
 * l'app passe par LocaleManager + redémarrage côté Android).
 */
@Composable
fun SettingsScreenContent(
    apiKey: String,
    introDbKey: String,
    streamLang: StreamLanguage,
    skipIntroOutro: Boolean,
    autoPlayNext: Boolean,
    playerClock: Boolean,
    trailerAutoplay: Boolean,
    onSetTrailerAutoplay: (Boolean) -> Unit,
    trailerSound: Boolean,
    onSetTrailerSound: (Boolean) -> Unit,
    updatePrereleases: Boolean,
    onSetUpdatePrereleases: (Boolean) -> Unit,
    hideHistoryWidgets: Boolean,
    splashAnimation: Boolean,
    updateInterval: UpdateInterval,
    updateCheck: UpdateCheck,
    screensaverDelay: ScreensaverDelay,
    dohEnabled: Boolean,
    dohProvider: DohProvider,
    providers: List<ProviderSetting>,
    onSetApiKey: (String) -> Unit,
    onSetIntroDbKey: (String) -> Unit,
    onSetStreamLanguage: (StreamLanguage) -> Unit,
    onSetSkipIntroOutro: (Boolean) -> Unit,
    onSetAutoPlayNext: (Boolean) -> Unit,
    onSetPlayerClock: (Boolean) -> Unit,
    onSetHideHistoryWidgets: (Boolean) -> Unit,
    onSetSplashAnimation: (Boolean) -> Unit,
    onSetUpdateInterval: (UpdateInterval) -> Unit,
    /**
     * Lance une vérification de version, ou null là où l'application ne peut
     * pas se mettre à jour elle-même.
     *
     * Null sur iOS, et c'est une contrainte du système, pas un manque : une app
     * iOS ne peut pas s'installer une nouvelle version — la mise à jour y passe
     * par la source SideStore, en dehors de l'application. La section entière
     * disparaît alors, plutôt que d'offrir un bouton qui ne pourrait rien faire
     * de ce qu'il annonce.
     */
    onCheckUpdates: (() -> Unit)?,
    onSetScreensaverDelay: (ScreensaverDelay) -> Unit,
    onSetDohEnabled: (Boolean) -> Unit,
    onSetDohProvider: (DohProvider) -> Unit,
    onToggleProvider: (name: String, enabled: Boolean) -> Unit,
    onMoveProviderUp: (String) -> Unit,
    onMoveProviderDown: (String) -> Unit,
    onBack: () -> Unit,
    /** Lecture d'un titre téléchargé, sans passer par sa fiche. */
    onPlayDownload: (Download) -> Unit = {},
    languageSelector: @Composable () -> Unit,
    /**
     * Section « Télécommande », ou null là où elle n'a rien à dire.
     *
     * Un **emplacement** et non un appel direct, pour la même raison que
     * [languageSelector] juste au-dessus : ce qu'elle affiche s'adosse aux
     * sockets de `data.cast` et `data.remote`, qui n'existent que sur les cibles
     * JVM. Android et desktop passent `::RemoteSection` ; iOS passe null, et la
     * section disparaît de la barre au lieu de s'y afficher vide — c'est ce que
     * faisait déjà `remoteOffered()`, désormais lu à la présence de ce
     * paramètre.
     */
    remoteSection: (@Composable (onPair: () -> Unit) -> Unit)? = null,
    /**
     * Boîte d'appairage d'un téléphone, ou null là où l'appairage n'est pas
     * proposé. Même raison : elle porte un serveur HTTP local.
     */
    pairingDialog: (@Composable (onDismiss: () -> Unit) -> Unit)? = null,
) {
    var section by remember { mutableStateOf(SettingsSection.API) }
    // Sur téléphone, le volet de navigation se replie en barre d'icônes : 260 dp
    // sur 448 ne laissaient que 190 dp au contenu, qui cassait les libellés mot
    // par mot. Replié il n'en coûte que 68, et le contenu reste visible à côté —
    // ce qu'un écran de liste séparé ne permettait pas.
    val compact = useBottomNav
    // Déplié en permanence hors tactile : la télécommande n'a pas de quoi
    // déplier commodément, et la place ne manque pas sur un 1080p.
    var railExpanded by remember { mutableStateOf(false) }
    val expanded = !compact || railExpanded
    // La section ne suit le focus que si celui-ci vient d'un appui haut/bas dans
    // le volet. Compose replie le focus sur le premier élément focalisable quand
    // celui qui le portait disparaît : sans ce garde-fou, un contrôle du volet
    // droit qui s'efface (une étape de sauvegarde qui passe à la suivante)
    // ramenait le focus ici et faisait sauter la section affichée.
    var navKeyDriven by remember { mutableStateOf(true) }
    // Focus initial sur une section et non sur un contrôle : le champ clé TMDB
    // s'auto-focaliserait et ouvrirait le clavier à l'entrée dans l'écran.
    //
    // **Sur la section affichée, et non sur la première du volet.** Il allait
    // à l'index 0, ce qui déclenchait le `onFocusChanged` ci-dessous et
    // remplaçait aussitôt la section de départ par celle-là : l'écran ouvrait
    // donc sur « Profils », en contradiction avec le `SettingsSection.API`
    // déclaré juste au-dessus. Deux lignes du même fichier disaient le
    // contraire l'une de l'autre, et c'est le focus qui gagnait.
    //
    // Mémorisée : `section` bouge avec le focus, et sans cela le point d'entrée
    // se déplacerait avec elle.
    val sectionInitiale = remember { section }
    val firstSectionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstSectionFocus.requestFocus() } }

    // Superposition plutôt que côte à côte : dépliée, la barre se pose **par
    // dessus** le contenu au lieu de le comprimer. Le pousser à 190 dp le
    // recassait mot par mot — le défaut même qu'on est en train de corriger.
    // Le contenu ne réserve donc que la largeur repliée, et ne bouge plus.
    Box(modifier = Modifier.fillMaxSize()) {
        // ── Volet gauche : navigation ────────────────────────────────────────
        Column(
            modifier = Modifier
                .zIndex(1f)
                .width(if (expanded) NAV_WIDTH else RAIL_WIDTH)
                .fillMaxHeight()
                .background(MOOVIE_SURFACE)
                // Défilant : à neuf sections, la liste dépasse la hauteur d'un
                // écran 1080p et poussait le bouton Retour hors champ.
                .verticalScroll(rememberScrollState())
                // Piloté par les touches et non par le focus perdu : Compose
                // notifie la perte avant le gain, et un drapeau remis à zéro sur
                // la perte annulait le changement de section qu'il devait servir.
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionUp, Key.DirectionDown -> navKeyDriven = true
                            // Droite : on quitte le volet. Un retour du focus ici
                            // sans nouvel appui sera un repli, pas un choix.
                            Key.DirectionRight -> navKeyDriven = false
                            else -> Unit
                        }
                    }
                    false
                }
                .padding(
                    vertical = if (compact) 24.dp else 40.dp,
                    horizontal = if (expanded) 20.dp else 10.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (compact) {
                // Déplier / replier. Le titre « Réglages » ne tient pas dans une
                // barre de 68 dp, ce bouton en tient lieu — et l'onglet du bas
                // dit déjà où l'on est.
                MoovieIconButton(
                    onClick = { railExpanded = !railExpanded },
                    icon = if (expanded) Icons.AutoMirrored.Filled.MenuOpen else Icons.Default.Menu,
                    contentDescription = stringResource(
                        if (expanded) Res.string.common_hide else Res.string.common_show,
                    ),
                )
                Spacer(Modifier.height(8.dp))
            } else {
                Text(
                    stringResource(Res.string.settings_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(start = 8.dp, bottom = 20.dp),
                )
            }
            // L'écran de veille n'a pas de sens sur un téléphone : le système y
            // éteint déjà l'écran tout seul, et personne ne laisse un film en
            // pause sur un appareil qu'il tient en main. La section disparaît
            // plutôt que de rester là, inutile.
            // La télécommande, elle, n'a de sens qu'aux deux bouts du salon :
            // le téléviseur qu'on pilote et le téléphone qui pilote. Sur un
            // ordinateur, la section n'aurait aucune ligne à afficher.
            // La section n'existe que si la plateforme a fourni de quoi la
            // dessiner. Voir le paramètre `remoteSection`.
            val remote = remoteSection != null
            val sections = SettingsSection.entries.filterNot {
                (compact && it == SettingsSection.SCREENSAVER) ||
                    (!remote && it == SettingsSection.REMOTE) ||
                    (onCheckUpdates == null && it == SettingsSection.UPDATE)
            }
            sections.forEach { entry ->
                MoovieButton(
                    onClick = {
                        section = entry
                        // Replié après le choix : la barre dépliée mange la
                        // moitié d'un écran de téléphone, et on l'a ouverte pour
                        // choisir, pas pour rester dedans.
                        if (compact) railExpanded = false
                    },
                    selected = entry == section,
                    modifier = Modifier
                        .fillMaxWidth()
                        // La section suit le focus : parcourir le volet gauche au
                        // D-pad montre directement les réglages visés. Valider
                        // pour changer de section obligeait à un aller-retour par
                        // catégorie juste pour savoir ce qu'elle contient.
                        .onFocusChanged { if (it.isFocused && navKeyDriven) section = entry }
                        .then(
                            if (entry == sectionInitiale) {
                                Modifier.focusRequester(firstSectionFocus)
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    Icon(
                        imageVector = sectionIcon(entry),
                        // Le libellé le dit déjà quand il est là ; replié, c'est
                        // l'icône qui doit parler aux lecteurs d'écran.
                        contentDescription = if (expanded) null else sectionLabel(entry),
                        modifier = Modifier.size(20.dp),
                    )
                    if (expanded) {
                        Spacer(Modifier.width(12.dp))
                        // weight : le libellé occupe la ligne, donc reste aligné à gauche.
                        Text(sectionLabel(entry), modifier = Modifier.weight(1f))
                    }
                }
            }
            // Écart fixe, plus un poids : dans une colonne défilante, `weight`
            // ne repousse plus rien vers le bas et le bouton disparaissait.
            Spacer(Modifier.height(20.dp))
            MoovieButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = if (expanded) null else stringResource(Res.string.common_back),
                    modifier = Modifier.size(20.dp),
                )
                if (expanded) {
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(Res.string.common_back), modifier = Modifier.weight(1f))
                }
            }
        }

        // ── Volet droit : contenu de la section ──────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Largeur repliée seulement : déplier ne doit pas redistribuer
                // la place, sinon le texte se recompose à chaque ouverture.
                .padding(start = if (compact) RAIL_WIDTH else NAV_WIDTH)
                .verticalScroll(rememberScrollState())
                .padding(
                    horizontal = if (compact) 20.dp else 40.dp,
                    vertical = if (compact) 24.dp else 40.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                sectionLabel(section),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            // Appairage d'un téléphone pour la saisie. L'état vit hors du `when`
            // pour que sa mémoire ne dépende pas de la branche affichée.
            var pairing by remember { mutableStateOf(false) }
            if (pairing) pairingDialog?.invoke({ pairing = false })

            when (section) {
                SettingsSection.REMOTE -> remoteSection?.invoke { pairing = true }

                SettingsSection.API -> {
                    SettingRow(
                        label = stringResource(Res.string.settings_tmdb_key),
                        help = stringResource(Res.string.settings_tmdb_help),
                    ) {
                        ApiKeyField(
                            value = apiKey,
                            hint = stringResource(Res.string.settings_tmdb_hint),
                            onValueChange = onSetApiKey,
                        )
                    }
                    // Deuxième clé, et deuxième modèle : celle de TheIntroDB
                    // identifie un contributeur, pas l'application. D'où sa
                    // place ici, à côté de celle de TMDB.
                    SettingRow(
                        label = stringResource(Res.string.settings_introdb_key),
                        help = stringResource(Res.string.settings_introdb_help),
                    ) {
                        ApiKeyField(
                            value = introDbKey,
                            hint = stringResource(Res.string.settings_introdb_hint),
                            onValueChange = onSetIntroDbKey,
                        )
                    }
                }

                SettingsSection.PLAYBACK -> {
                    SettingRow(label = stringResource(Res.string.settings_stream_lang)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StreamLanguage.entries.forEach { lang ->
                                MoovieButton(
                                    onClick = { onSetStreamLanguage(lang) },
                                    selected = lang == streamLang,
                                ) { Text(lang.name) }
                            }
                        }
                    }
                    SettingRow(label = stringResource(Res.string.settings_language)) {
                        languageSelector()
                    }
                    SettingRow(
                        label = stringResource(Res.string.settings_splash),
                        help = stringResource(Res.string.settings_splash_help),
                    ) {
                        OnOff(value = splashAnimation, onChange = onSetSplashAnimation)
                    }
                    SettingRow(
                        label = stringResource(Res.string.settings_player_clock),
                        help = stringResource(Res.string.settings_player_clock_help),
                    ) {
                        OnOff(value = playerClock, onChange = onSetPlayerClock)
                    }
                    SettingRow(
                        label = stringResource(Res.string.settings_trailer_autoplay),
                        help = stringResource(Res.string.settings_trailer_autoplay_help),
                    ) {
                        OnOff(value = trailerAutoplay, onChange = onSetTrailerAutoplay)
                    }
                    // Sous l'aperçu, et non ailleurs : c'est son son. Le
                    // laisser actif quand l'aperçu est coupé n'a pas de sens,
                    // mais on n'en grise pas la ligne — un réglage grisé se
                    // lit mal, et le texte d'aide dit déjà ce qu'il commande.
                    SettingRow(
                        label = stringResource(Res.string.settings_trailer_sound),
                        help = stringResource(Res.string.settings_trailer_sound_help),
                    ) {
                        OnOff(value = trailerSound, onChange = onSetTrailerSound)
                    }
                }

                SettingsSection.INTRO -> {
                    SettingRow(
                        label = stringResource(Res.string.settings_skip_intro),
                        help = stringResource(Res.string.settings_intro_help),
                    ) {
                        OnOff(value = skipIntroOutro, onChange = onSetSkipIntroOutro)
                    }
                    SettingRow(
                        label = stringResource(Res.string.settings_autoplay),
                        help = stringResource(Res.string.settings_autoplay_help),
                    ) {
                        OnOff(value = autoPlayNext, onChange = onSetAutoPlayNext)
                    }
                }

                SettingsSection.HISTORY -> SettingRow(
                    label = stringResource(Res.string.settings_history_widgets),
                    help = stringResource(Res.string.settings_history_help),
                ) {
                    // Le réglage stocké dit « masquer » ; les boutons disent ce
                    // qu'ils font. D'où l'inversion, faite ici plutôt que dans
                    // le dépôt : les sauvegardes déjà écrites gardent leur sens.
                    OnOff(
                        value = !hideHistoryWidgets,
                        onChange = { onSetHideHistoryWidgets(!it) },
                        onLabel = stringResource(Res.string.common_show),
                        offLabel = stringResource(Res.string.common_hide),
                    )
                }

                SettingsSection.SCREENSAVER -> SettingRow(
                    label = stringResource(Res.string.settings_screensaver_delay),
                    help = stringResource(Res.string.settings_screensaver_help),
                ) {
                    MoovieSelect(
                        title = stringResource(Res.string.settings_screensaver_delay),
                        options = ScreensaverDelay.entries.toList(),
                        selected = screensaverDelay,
                        label = { screensaverDelayLabel(it) },
                        onSelect = onSetScreensaverDelay,
                    )
                }

                SettingsSection.UPDATE -> {
                    // La version installée, en tête de section.
                    //
                    // Elle manquait, et son absence a coûté cher : « À jour » ne
                    // dit pas *depuis quoi*, si bien qu'un appareil portant une
                    // version plus haute que tout ce qui est publié — un build
                    // local, une rc dépassée — se lit exactement comme un canal
                    // en panne. C'est la première chose à regarder quand une
                    // mise à jour n'arrive pas, elle a donc sa place ici et non
                    // dans un écran « À propos ».
                    SettingRow(label = stringResource(Res.string.settings_current_version)) {
                        Text(appVersionName, style = MaterialTheme.typography.titleMedium)
                    }
                    SettingRow(
                        label = stringResource(Res.string.settings_update_interval),
                        help = stringResource(Res.string.settings_update_help),
                    ) {
                        MoovieSelect(
                            title = stringResource(Res.string.settings_update_interval),
                            options = UpdateInterval.entries.toList(),
                            selected = updateInterval,
                            label = { updateIntervalLabel(it) },
                            onSelect = onSetUpdateInterval,
                        )
                    }
                    // Juste sous la fréquence : les deux disent *quand* et *quoi*
                    // on ira chercher, et se lisent ensemble.
                    SettingRow(
                        label = stringResource(Res.string.settings_update_prereleases),
                        help = stringResource(Res.string.settings_update_prereleases_help),
                    ) {
                        OnOff(value = updatePrereleases, onChange = onSetUpdatePrereleases)
                    }
                    // Vérification immédiate : sans elle, installer une version
                    // tout juste publiée imposait d'attendre le prochain tour de
                    // la minuterie — jusqu'à deux heures — ou de relancer l'app.
                    SettingRow(
                        label = stringResource(Res.string.settings_check_now),
                        help = stringResource(Res.string.settings_check_now_help),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            // Le cas « rien de neuf » est le plus fréquent, et
                            // c'est le seul que la bannière ne sait pas dire :
                            // sans ce retour le bouton paraîtrait inerte.
                            when (updateCheck) {
                                UpdateCheck.CHECKING ->
                                    Text(stringResource(Res.string.settings_check_running), color = MOOVIE_TEXT_DIM)
                                UpdateCheck.UP_TO_DATE ->
                                    Text(stringResource(Res.string.settings_check_uptodate), color = MOOVIE_TEXT_DIM)
                                UpdateCheck.FAILED ->
                                    Text(stringResource(Res.string.settings_check_failed), color = MOOVIE_ERROR)
                                UpdateCheck.IDLE -> Unit
                            }
                            // Toujours `enabled` : le passer à false pendant la
                            // vérification faisait perdre le focus au bouton —
                            // Compose le retire d'un nœud désactivé et ne le
                            // rend pas — et la télécommande se retrouvait
                            // renvoyée dans le volet des sections. Les appuis
                            // répétés sont absorbés par checkNow() lui-même.
                            MoovieButton(onClick = { onCheckUpdates?.invoke() }) {
                                Text(stringResource(Res.string.settings_check_now))
                            }
                        }
                    }
                }

                SettingsSection.DNS -> {
                    SettingRow(
                        label = stringResource(Res.string.settings_doh_on),
                        help = stringResource(Res.string.settings_dns_help),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MoovieButton(
                                onClick = { onSetDohEnabled(true) },
                                selected = dohEnabled,
                            ) { Text(stringResource(Res.string.settings_doh_on)) }
                            MoovieButton(
                                onClick = { onSetDohEnabled(false) },
                                selected = !dohEnabled,
                            ) { Text(stringResource(Res.string.settings_doh_off)) }
                        }
                    }
                    // Le résolveur n'a de sens que si le DoH est actif.
                    if (dohEnabled) {
                        SettingRow(label = stringResource(Res.string.settings_doh_resolver)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                DohProvider.entries.forEach { provider ->
                                    MoovieButton(
                                        onClick = { onSetDohProvider(provider) },
                                        selected = provider == dohProvider,
                                    ) { Text(provider.label) }
                                }
                            }
                        }
                    }
                }

                SettingsSection.SOURCES -> {
                    Text(
                        stringResource(Res.string.settings_sources_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MOOVIE_TEXT_DIM,
                    )
                    providers.forEachIndexed { index, provider ->
                        ProviderRow(
                            index = index,
                            provider = provider,
                            isLast = index == providers.lastIndex,
                            onMoveUp = { onMoveProviderUp(provider.name) },
                            onMoveDown = { onMoveProviderDown(provider.name) },
                            onToggle = { onToggleProvider(provider.name, !provider.enabled) },
                        )
                    }

                    // Vider le cache des sources, à la main.
                    //
                    // Les liens trouvés sont gardés six heures pour que revenir
                    // sur une fiche soit instantané. Le revers est qu'un site
                    // qui change de format — ou un correctif qu'on vient
                    // d'installer — reste invisible pendant tout ce temps sur
                    // les titres déjà ouverts, sans aucun moyen d'insister.
                    //
                    // La version de l'application invalide déjà les entrées à
                    // chaque mise à jour ; ce bouton couvre l'autre cas, celui
                    // où c'est le **site** qui a bougé et pas nous.
                    val cacheScope = rememberCoroutineScope()
                    var cleared by remember { mutableStateOf(false) }
                    SettingRow(
                        label = stringResource(Res.string.settings_sources_cache),
                        help = stringResource(
                            if (cleared) Res.string.settings_sources_cache_done
                            else Res.string.settings_sources_cache_help,
                        ),
                    ) {
                        MoovieButton(
                            enabled = !cleared,
                            onClick = {
                                cacheScope.launch {
                                    SourceCacheRepository().clear()
                                    // Les mesures de qualité aussi : les deux
                                    // magasins répondent à la même question —
                                    // « l'application me ressert du vieux » —
                                    // et n'en vider qu'un laisserait la moitié
                                    // du symptôme, les sources étant alors
                                    // reclassées sur des hauteurs d'avant.
                                    StreamMeasureRepository().clear()
                                    cleared = true
                                }
                            },
                        ) { Text(stringResource(Res.string.settings_sources_cache_action)) }
                    }
                }

                // Comme la sauvegarde : un parcours (se connecter), pas un
                // réglage. D'où son état porté par elle-même.
                SettingsSection.SUBTITLES -> SubtitlesSection()

                // Seule autre section à porter son propre état : c'est un parcours en
                // plusieurs étapes, pas un réglage. Voir [BackupSection].
                // Comme la sauvegarde : son état est le magasin lui-même,
                // pas un réglage hissé jusqu'ici. Voir [HomeLayoutSection].
                SettingsSection.HOME -> HomeLayoutSection()

                SettingsSection.PROFILES -> ProfilesSection()

                SettingsSection.BACKUP -> BackupSection()

                // Comme la sauvegarde : un parcours qui porte son propre état.
                SettingsSection.SYNC -> SyncSection()

                SettingsSection.DOWNLOADS -> DownloadsSection(onPlay = onPlayDownload)
            }
        }
    }
}

/**
 * Section « Profils ».
 *
 * Volontairement maigre : tout se fait sur la porte d'entrée, qui est déjà
 * l'écran de gestion. Cette section n'existe que pour **l'atteindre**, sans quoi
 * une installation à profil unique n'aurait aucun moyen d'en créer un second —
 * la porte ne s'ouvre pas quand il n'y a rien à choisir.
 */
@Composable
private fun ProfilesSection() {
    val repo = remember { ProfileRepository() }
    val active by repo.active.collectAsState(initial = Profile.Default)
    val switch = LocalSwitchProfile.current
    SettingRow(
        label = active.name.ifBlank { stringResource(Res.string.profile_default) },
        help = stringResource(Res.string.profile_switch_help),
    ) {
        MoovieButton(onClick = switch) { Text(stringResource(Res.string.profile_switch)) }
    }
}

/**
 * Ligne de réglage : libellé (et aide) à gauche, contrôle à droite.
 *
 * **Sur téléphone, tout passe en une seule colonne** : libellé, aide, puis le
 * contrôle en dessous. Se partager la largeur à deux suppose une largeur à
 * partager — sur les 380 dp restants d'un portrait, chaque moitié tombait sous
 * 190 dp et libellés comme aides se cassaient mot par mot. Empilé, le texte
 * reprend toute la ligne et le contrôle aussi.
 */
@Composable
internal fun SettingRow(
    label: String,
    help: String? = null,
    control: @Composable () -> Unit,
) {
    val texts = @Composable {
        Text(label, style = MaterialTheme.typography.titleMedium, color = MOOVIE_TEXT)
        if (help != null) {
            Text(
                help,
                style = MaterialTheme.typography.bodySmall,
                color = MOOVIE_TEXT_DIM,
            )
        }
    }

    // **Un filet sous chaque réglage.**
    //
    // La page en aligne une cinquantaine, sans rien entre eux : un libellé, son
    // aide en gris, un contrôle à droite, et on recommence. Rien ne dit où
    // finit un réglage et où commence le suivant, si bien qu'une aide se lit
    // comme le début du réglage d'en dessous. Un filet à peine visible suffit à
    // les redécouper — c'est le rôle d'un séparateur, et il n'en faut pas plus.
    //
    // Il est **au-dessus** et non en dessous : le dernier réglage d'une section
    // n'a alors pas de trait flottant sous lui, et le premier se détache du
    // titre de sa section.
    val filet = Modifier.drawBehind {
        drawRect(
            color = MOOVIE_SURFACE_HIGH,
            size = Size(size.width, 1.dp.toPx()),
        )
    }

    if (useBottomNav) {
        Column(
            modifier = Modifier.fillMaxWidth().then(filet).padding(vertical = ESPACE),
            verticalArrangement = Arrangement.spacedBy(ESPACE_SERRE),
        ) {
            texts()
            // Aligné à gauche, dans le fil du libellé qu'il complète : renvoyé
            // à droite il flotterait seul au bout d'une ligne vide.
            Box(modifier = Modifier.fillMaxWidth()) { control() }
        }
        return
    }

    Row(
        // Quatre points de marge verticale tassaient les réglages les uns sur
        // les autres ; le contrôle d'un réglage touchait presque l'aide du
        // précédent. Le rythme de la page dit douze.
        modifier = Modifier.fillMaxWidth().then(filet).padding(vertical = ESPACE),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            texts()
        }
        // Moitié/moitié plutôt qu'une largeur fixe : l'écran est étroit en dp et
        // un contrôle figé écrasait la colonne des libellés, qui se cassait
        // alors mot par mot.
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd,
        ) {
            control()
        }
    }
}

/** Couple de boutons Activé / Désactivé. */
@Composable
internal fun OnOff(
    value: Boolean,
    onChange: (Boolean) -> Unit,
    /**
     * Libellés des deux boutons. « Activé / Désactivé » convient à un réglage
     * dont l'intitulé est une **capacité** ; il devient illisible dès que
     * l'intitulé décrit une *action*, où l'on obtient un « Masquer : Désactivé »
     * qu'il faut relire deux fois. Dans ce cas on nomme l'objet dans l'intitulé
     * et les deux actions ici.
     */
    onLabel: String = stringResource(Res.string.common_enabled),
    offLabel: String = stringResource(Res.string.common_disabled),
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        MoovieButton(onClick = { onChange(true) }, selected = value) { Text(onLabel) }
        MoovieButton(onClick = { onChange(false) }, selected = !value) { Text(offLabel) }
    }
}

/** Ligne d'un provider : rang et nom à gauche, actions à droite. */
@Composable
private fun ProviderRow(
    index: Int,
    provider: ProviderSetting,
    isLast: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MoovieShape)
            .background(if (index % 2 == 0) MOOVIE_SURFACE else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${index + 1}. ${provider.name}",
            style = MaterialTheme.typography.titleMedium,
            color = if (provider.enabled) Color.White else MOOVIE_TEXT_FAINT,
            modifier = Modifier.weight(1f),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (index > 0) {
                MoovieIconButton(
                    onClick = onMoveUp,
                    icon = Icons.Default.KeyboardArrowUp,
                    contentDescription = stringResource(Res.string.settings_move_up, provider.name),
                )
            }
            if (!isLast) {
                MoovieIconButton(
                    onClick = onMoveDown,
                    icon = Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(Res.string.settings_move_down, provider.name),
                )
            }
            MoovieIconButton(
                onClick = onToggle,
                icon = Icons.Default.PowerSettingsNew,
                contentDescription = if (provider.enabled) {
                    stringResource(Res.string.settings_disable, provider.name)
                } else {
                    stringResource(Res.string.settings_enable, provider.name)
                },
                selected = provider.enabled,
            )
        }
    }
}

/** Libellé lisible d'un délai de mise en veille. */
@Composable
private fun screensaverDelayLabel(delay: ScreensaverDelay): String = when {
    delay == ScreensaverDelay.NEVER -> stringResource(Res.string.screensaver_never)
    delay.minutes < 60 -> stringResource(Res.string.screensaver_after_minutes, delay.minutes)
    else -> stringResource(Res.string.screensaver_after_hours, delay.minutes / 60)
}

/** Libellé lisible d'une fréquence de vérification. */
@Composable
private fun updateIntervalLabel(interval: UpdateInterval): String = when {
    interval == UpdateInterval.NEVER -> stringResource(Res.string.update_never)
    interval.minutes < 60 -> stringResource(Res.string.update_every_minutes, interval.minutes)
    else -> stringResource(Res.string.update_every_hours, interval.minutes / 60)
}

/**
 * Champ de saisie d'une clé d'API, avec bouton œil pour la masquer.
 *
 * **La clé est lisible par défaut**, et c'est délibéré. Le champ masquait
 * auparavant tous les caractères sauf les quatre derniers, en permanence. Deux
 * raisons de l'avoir retiré :
 *
 * - une clé d'API n'est pas un mot de passe. On ne la ressaisit pas de mémoire,
 *   on la colle une fois puis on veut vérifier qu'elle est juste — ce que le
 *   masquage empêchait précisément ;
 * - la transformation se rejouait à chaque frappe, ce qui décalait le curseur.
 *
 * Le masquage reste disponible, mais devient un geste : le bouton sert à cacher
 * la clé avant une capture d'écran ou une démonstration, pas à gêner la saisie.
 *
 * **Le champ tient sa propre copie du texte** ([draft]), et c'est ce qui répare
 * réellement la saisie rapide. Il était auparavant piloté directement par la
 * valeur persistée : chaque frappe déclenchait une écriture DataStore, dont la
 * valeur ne revenait qu'après un aller-retour asynchrone et écrasait au passage
 * les caractères tapés entre-temps. Une clé de 32 caractères injectée par
 * `adb shell input text` en perdait les deux tiers, sans que rien ne le
 * signale — et le masquage, longtemps soupçonné, n'y était pour rien.
 */
@Composable
internal fun ApiKeyField(value: String, hint: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    // Au D-pad, l'œil se place **avant** le champ. Entrer dans un champ texte
    // ouvre le clavier virtuel d'Android TV, qui capte alors toute la
    // navigation : un bouton posé après le champ devient inatteignable à la
    // télécommande, et la flèche censée l'atteindre tape une touche du clavier.
    // Au doigt et à la souris, l'œil reste à droite, là où on l'attend.
    val eyeFirst = LocalUiFlavor.current.isDpad
    var hidden by rememberSaveable { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf(value) }
    var focused by remember { mutableStateOf(false) }
    // Se réaligne sur la valeur persistée uniquement hors saisie : une
    // restauration de sauvegarde ou une injection par intent doit se voir, mais
    // jamais au prix de ce que l'utilisateur est en train de taper.
    LaunchedEffect(value, focused) {
        if (!focused && value != draft) draft = value
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val eyeButton = @Composable {
            MoovieIconButton(
                onClick = { hidden = !hidden },
                icon = if (hidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = stringResource(
                    if (hidden) Res.string.settings_key_show else Res.string.settings_key_hide,
                ),
                selected = hidden,
            )
        }
        if (eyeFirst) eyeButton()
        Box(
            modifier = Modifier
                .weight(1f)
                .border(1.dp, MOOVIE_TEXT_FAINT, MoovieShape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            if (draft.isEmpty()) {
                Text(hint, color = MOOVIE_TEXT_DIM)
            }
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it
                    onValueChange(it)
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White),
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (hidden) {
                    PasswordVisualTransformation('•')
                } else {
                    VisualTransformation.None
                },
                modifier = Modifier
                    .fillMaxWidth()
                    // Le cas d'usage d'origine de l'appairage : une clé de 31
                    // caractères à taper à la télécommande. `secret` annonce le
                    // champ sans annoncer son contenu — la clé déjà saisie n'a
                    // aucune raison de traverser le réseau local.
                    .remoteTypable(
                        label = hint,
                        value = draft,
                        onValueChange = {
                            draft = it
                            onValueChange(it)
                        },
                        secret = true,
                    )
                    .onFocusChanged { focused = it.isFocused }
                    // Le champ texte avale les flèches par défaut : sans ça le D-pad
                    // ne peut plus en sortir (pas de touche Tab sur une télécommande).
                    // Gauche compte désormais aussi : c'est la sortie vers le volet
                    // de navigation — au D-pad, elle passe par le bouton œil, qui
                    // est placé juste avant le champ.
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        val direction = when (event.key) {
                            Key.DirectionDown -> FocusDirection.Down
                            Key.DirectionUp -> FocusDirection.Up
                            Key.DirectionLeft -> FocusDirection.Left
                            else -> return@onPreviewKeyEvent false
                        }
                        focusManager.moveFocus(direction)
                        true
                    },
            )
        }
        if (!eyeFirst) eyeButton()
    }
}
