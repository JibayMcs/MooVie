package fr.moovie.tv.ui.settings

import fr.moovie.tv.ui.update.UpdateViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import fr.moovie.tv.data.download.Download
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import fr.moovie.tv.ui.theme.MoovieShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.R
import fr.moovie.tv.data.settings.AppLanguage
import fr.moovie.tv.data.settings.LocaleManager
import fr.moovie.tv.ui.components.MoovieButton

/**
 * Wrapper Android : branche le [SettingsViewModel] (repos DataStore androidMain)
 * sur l'écran partagé [SettingsScreenContent] de jvmCommon. Le sélecteur de
 * langue reste ici : il dépend de LocaleManager + redémarrage de l'activité.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    /** Lecture d'un titre téléchargé, sans passer par sa fiche ni par TMDB. */
    onPlayDownload: (Download) -> Unit = {},
    viewModel: SettingsViewModel = viewModel(),
) {
    val apiKey by viewModel.tmdbApiKey.collectAsStateWithLifecycle()
    val introDbKey by viewModel.introDbApiKey.collectAsStateWithLifecycle()
    val streamLang by viewModel.streamLanguage.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val dohEnabled by viewModel.dohEnabled.collectAsStateWithLifecycle()
    val dohProvider by viewModel.dohProvider.collectAsStateWithLifecycle()
    val skipIntroOutro by viewModel.skipIntroOutro.collectAsStateWithLifecycle()
    val autoPlayNext by viewModel.autoPlayNext.collectAsStateWithLifecycle()
    val playerClock by viewModel.playerClock.collectAsStateWithLifecycle()
    val trailerAutoplay by viewModel.trailerAutoplay.collectAsStateWithLifecycle()
    val trailerSound by viewModel.trailerSound.collectAsStateWithLifecycle()
    val hideHistoryWidgets by viewModel.hideHistoryWidgets.collectAsStateWithLifecycle()
    val splashAnimation by viewModel.splashAnimation.collectAsStateWithLifecycle()
    // Même instance que MainActivity (scope Activity) : une version trouvée ici
    // remonte donc directement dans la bannière.
    val updateViewModel: UpdateViewModel = viewModel()
    val updateCheck by updateViewModel.checkStatus.collectAsStateWithLifecycle()
    val updateInterval by viewModel.updateInterval.collectAsStateWithLifecycle()
    val screensaverDelay by viewModel.screensaverDelay.collectAsStateWithLifecycle()

    SettingsScreenContent(
        apiKey = apiKey,
        introDbKey = introDbKey,
        streamLang = streamLang,
        skipIntroOutro = skipIntroOutro,
        autoPlayNext = autoPlayNext,
        playerClock = playerClock,
        trailerAutoplay = trailerAutoplay,
        trailerSound = trailerSound,
        hideHistoryWidgets = hideHistoryWidgets,
        splashAnimation = splashAnimation,
        updateCheck = updateCheck,
        updateInterval = updateInterval,
        screensaverDelay = screensaverDelay,
        dohEnabled = dohEnabled,
        dohProvider = dohProvider,
        providers = providers,
        onSetApiKey = viewModel::setTmdbApiKey,
        onSetIntroDbKey = viewModel::setIntroDbApiKey,
        onSetStreamLanguage = viewModel::setStreamLanguage,
        onSetSkipIntroOutro = viewModel::setSkipIntroOutro,
        onSetAutoPlayNext = viewModel::setAutoPlayNext,
        onSetPlayerClock = viewModel::setPlayerClock,
        onSetTrailerAutoplay = viewModel::setTrailerAutoplay,
        onSetTrailerSound = viewModel::setTrailerSound,
        onSetHideHistoryWidgets = viewModel::setHideHistoryWidgets,
        onSetSplashAnimation = viewModel::setSplashAnimation,
        onCheckUpdates = updateViewModel::checkNow,
        onSetUpdateInterval = viewModel::setUpdateInterval,
        onSetScreensaverDelay = viewModel::setScreensaverDelay,
        onSetDohEnabled = viewModel::setDohEnabled,
        onSetDohProvider = viewModel::setDohProvider,
        onToggleProvider = viewModel::toggleProvider,
        onMoveProviderUp = viewModel::moveProviderUp,
        onMoveProviderDown = viewModel::moveProviderDown,
        onBack = onBack,
        onPlayDownload = onPlayDownload,
        languageSelector = { LanguageSelector() },
    )
}

/** Libellé traduit d'une langue de l'app. */
@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.SYSTEM -> R.string.language_system
        AppLanguage.FRENCH -> R.string.language_fr
        AppLanguage.ENGLISH -> R.string.language_en
        AppLanguage.SPANISH -> R.string.language_es
    },
)

/**
 * Sélecteur de langue « select » : un bouton affichant la langue courante ouvre
 * une liste modale des options. Choisir applique la locale et recrée l'activité.
 */
@Composable
private fun LanguageSelector() {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val current = remember { LocaleManager.current(context) }

    MoovieButton(onClick = { open = true }) {
        Text(languageLabel(current))
        Spacer(Modifier.width(8.dp))
        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(20.dp))
    }

    if (open) {
        val firstFocus = remember { FocusRequester() }
        Dialog(onDismissRequest = { open = false }) {
            Column(
                modifier = Modifier
                    .clip(MoovieShape)
                    .background(Color(0xF5161616))
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.settings_language), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                AppLanguage.entries.forEachIndexed { index, language ->
                    val selected = language == current
                    MoovieButton(
                        onClick = {
                            open = false
                            // Redémarrage à froid : recharge l'UI + les données
                            // TMDB dans la nouvelle langue (recreate ne suffit pas,
                            // les ViewModels survivent avec leurs données en cache).
                            LocaleManager.applyAndRestart(context, language)
                        },
                        selected = selected,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (index == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    ) {
                        if (selected) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(languageLabel(language))
                    }
                }
            }
        }
        LaunchedEffect(Unit) { runCatching { firstFocus.requestFocus() } }
    }
}
