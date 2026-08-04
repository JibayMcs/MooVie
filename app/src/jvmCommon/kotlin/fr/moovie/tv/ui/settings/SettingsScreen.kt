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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import fr.moovie.tv.data.net.DohProvider
import fr.moovie.tv.data.settings.ScreensaverDelay
import fr.moovie.tv.data.settings.StreamLanguage
import fr.moovie.tv.data.settings.UpdateInterval
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.common_back
import fr.moovie.tv.resources.common_disabled
import fr.moovie.tv.resources.common_enabled
import fr.moovie.tv.resources.screensaver_after_hours
import fr.moovie.tv.resources.screensaver_after_minutes
import fr.moovie.tv.resources.screensaver_never
import fr.moovie.tv.resources.settings_autoplay
import fr.moovie.tv.resources.settings_autoplay_help
import fr.moovie.tv.resources.settings_cat_api
import fr.moovie.tv.resources.settings_cat_backup
import fr.moovie.tv.resources.settings_cat_dns
import fr.moovie.tv.resources.settings_cat_intro
import fr.moovie.tv.resources.settings_cat_playback
import fr.moovie.tv.resources.settings_cat_screensaver
import fr.moovie.tv.resources.settings_cat_sources
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
import fr.moovie.tv.resources.settings_player_clock_help
import fr.moovie.tv.resources.settings_screensaver_delay
import fr.moovie.tv.resources.settings_screensaver_help
import fr.moovie.tv.resources.settings_skip_intro
import fr.moovie.tv.resources.settings_sources_help
import fr.moovie.tv.resources.settings_stream_lang
import fr.moovie.tv.resources.settings_title
import fr.moovie.tv.resources.settings_tmdb_help
import fr.moovie.tv.resources.settings_tmdb_hint
import fr.moovie.tv.resources.settings_tmdb_key
import fr.moovie.tv.resources.settings_update_help
import fr.moovie.tv.resources.settings_update_interval
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
import fr.moovie.tv.ui.components.MoovieButton
import fr.moovie.tv.ui.subtitles.SubtitlesSection
import fr.moovie.tv.ui.components.MoovieIconButton
import fr.moovie.tv.ui.components.MoovieSelect
import org.jetbrains.compose.resources.stringResource

/**
 * Largeur du volet de navigation. Volontairement contenue : en 1080p l'écran ne
 * fait que 960 dp de large, un volet trop large étrangle la colonne des libellés.
 */
private val NAV_WIDTH = 260.dp

/** Sections de l'écran, dans l'ordre d'affichage du volet gauche. */
private enum class SettingsSection {
    API, PLAYBACK, INTRO, SUBTITLES, HISTORY, SCREENSAVER, UPDATE, DNS, SOURCES, BACKUP,
}

@Composable
private fun sectionLabel(section: SettingsSection): String = stringResource(
    when (section) {
        SettingsSection.API -> Res.string.settings_cat_api
        SettingsSection.PLAYBACK -> Res.string.settings_cat_playback
        SettingsSection.INTRO -> Res.string.settings_cat_intro
        SettingsSection.HISTORY -> Res.string.settings_cat_history
        SettingsSection.SCREENSAVER -> Res.string.settings_cat_screensaver
        SettingsSection.UPDATE -> Res.string.settings_cat_update
        SettingsSection.DNS -> Res.string.settings_cat_dns
        SettingsSection.SOURCES -> Res.string.settings_cat_sources
        SettingsSection.SUBTITLES -> Res.string.settings_cat_subtitles
        SettingsSection.BACKUP -> Res.string.settings_cat_backup
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
    onCheckUpdates: () -> Unit,
    onSetScreensaverDelay: (ScreensaverDelay) -> Unit,
    onSetDohEnabled: (Boolean) -> Unit,
    onSetDohProvider: (DohProvider) -> Unit,
    onToggleProvider: (name: String, enabled: Boolean) -> Unit,
    onMoveProviderUp: (String) -> Unit,
    onMoveProviderDown: (String) -> Unit,
    onBack: () -> Unit,
    languageSelector: @Composable () -> Unit,
) {
    var section by remember { mutableStateOf(SettingsSection.API) }
    // La section ne suit le focus que si celui-ci vient d'un appui haut/bas dans
    // le volet. Compose replie le focus sur le premier élément focalisable quand
    // celui qui le portait disparaît : sans ce garde-fou, un contrôle du volet
    // droit qui s'efface (une étape de sauvegarde qui passe à la suivante)
    // ramenait le focus ici et faisait sauter la section affichée.
    var navKeyDriven by remember { mutableStateOf(true) }
    // Focus initial sur la 1re section, pas sur un contrôle : le champ clé TMDB
    // s'auto-focaliserait et ouvrirait le clavier à l'entrée dans l'écran.
    val firstSectionFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { firstSectionFocus.requestFocus() } }

    Row(modifier = Modifier.fillMaxSize()) {
        // ── Volet gauche : navigation ────────────────────────────────────────
        Column(
            modifier = Modifier
                .width(NAV_WIDTH)
                .fillMaxHeight()
                .background(Color(0xFF141414))
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
                .padding(vertical = 40.dp, horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                stringResource(Res.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(start = 8.dp, bottom = 20.dp),
            )
            SettingsSection.entries.forEachIndexed { index, entry ->
                MoovieButton(
                    onClick = { section = entry },
                    selected = entry == section,
                    modifier = Modifier
                        .fillMaxWidth()
                        // La section suit le focus : parcourir le volet gauche au
                        // D-pad montre directement les réglages visés. Valider
                        // pour changer de section obligeait à un aller-retour par
                        // catégorie juste pour savoir ce qu'elle contient.
                        .onFocusChanged { if (it.isFocused && navKeyDriven) section = entry }
                        .then(if (index == 0) Modifier.focusRequester(firstSectionFocus) else Modifier),
                ) {
                    // weight : le libellé occupe la ligne, donc reste aligné à gauche.
                    Text(sectionLabel(entry), modifier = Modifier.weight(1f))
                }
            }
            // Écart fixe, plus un poids : dans une colonne défilante, `weight`
            // ne repousse plus rien vers le bas et le bouton disparaissait.
            Spacer(Modifier.height(20.dp))
            MoovieButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.common_back), modifier = Modifier.weight(1f))
            }
        }

        // ── Volet droit : contenu de la section ──────────────────────────────
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 40.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                sectionLabel(section),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )

            when (section) {
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
                                    Text(stringResource(Res.string.settings_check_running), color = Color(0xFF9A9A9A))
                                UpdateCheck.UP_TO_DATE ->
                                    Text(stringResource(Res.string.settings_check_uptodate), color = Color(0xFF9A9A9A))
                                UpdateCheck.FAILED ->
                                    Text(stringResource(Res.string.settings_check_failed), color = Color(0xFFE0A0A0))
                                UpdateCheck.IDLE -> Unit
                            }
                            // Toujours `enabled` : le passer à false pendant la
                            // vérification faisait perdre le focus au bouton —
                            // Compose le retire d'un nœud désactivé et ne le
                            // rend pas — et la télécommande se retrouvait
                            // renvoyée dans le volet des sections. Les appuis
                            // répétés sont absorbés par checkNow() lui-même.
                            MoovieButton(onClick = onCheckUpdates) {
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
                        color = Color(0xFF9A9A9A),
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
                }

                // Comme la sauvegarde : un parcours (se connecter), pas un
                // réglage. D'où son état porté par elle-même.
                SettingsSection.SUBTITLES -> SubtitlesSection()

                // Seule autre section à porter son propre état : c'est un parcours en
                // plusieurs étapes, pas un réglage. Voir [BackupSection].
                SettingsSection.BACKUP -> BackupSection()
            }
        }
    }
}

/**
 * Ligne de réglage : libellé (et aide) à gauche, contrôle à droite. Le contrôle
 * est borné en largeur pour que tous les réglages s'alignent verticalement.
 */
@Composable
private fun SettingRow(
    label: String,
    help: String? = null,
    control: @Composable () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).padding(end = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(label, style = MaterialTheme.typography.titleMedium)
            if (help != null) {
                Text(
                    help,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF9A9A9A),
                )
            }
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
private fun OnOff(
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
            .background(if (index % 2 == 0) Color(0xFF161616) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "${index + 1}. ${provider.name}",
            style = MaterialTheme.typography.titleMedium,
            color = if (provider.enabled) Color.White else Color(0xFF777777),
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

@Composable
private fun ApiKeyField(value: String, hint: String, onValueChange: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color(0xFF555555), MoovieShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        if (value.isEmpty()) {
            Text(hint, color = Color(0xFF888888))
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = Color.White),
            cursorBrush = SolidColor(Color.White),
            visualTransformation = MaskedKey,
            modifier = Modifier
                .fillMaxWidth()
                // Le champ texte avale les flèches par défaut : sans ça le D-pad
                // ne peut plus en sortir (pas de touche Tab sur une télécommande).
                // Gauche compte désormais aussi : c'est la sortie vers le volet
                // de navigation.
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
}


/**
 * Masque la clé TMDB en n'en laissant lire que la fin.
 *
 * Une clé d'API n'est pas un mot de passe qu'on ressaisit : on la colle une fois
 * puis on veut seulement pouvoir vérifier que c'est la bonne. La masquer
 * entièrement rendrait ce contrôle impossible ; l'afficher en clair la met sur
 * toutes les captures d'écran, et l'écran des réglages est justement ce qu'on
 * montre dans une documentation. Les quatre derniers caractères suffisent à
 * l'identifier sans la livrer.
 */
private object MaskedKey : VisualTransformation {
    private const val VISIBLE_TAIL = 4

    override fun filter(text: AnnotatedString): TransformedText {
        if (text.length <= VISIBLE_TAIL) return TransformedText(text, OffsetMapping.Identity)
        val masked = "•".repeat(text.length - VISIBLE_TAIL) + text.text.takeLast(VISIBLE_TAIL)
        // Le masque garde la longueur d'origine : les positions du curseur et de
        // la sélection restent donc valables telles quelles.
        return TransformedText(AnnotatedString(masked), OffsetMapping.Identity)
    }
}
