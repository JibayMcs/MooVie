package fr.moovie.tv.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.material3.Text
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.data.download.Download
import fr.moovie.tv.ui.update.UpdateCheck
import platform.Foundation.NSLocale
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForLanguageCode
import platform.Foundation.preferredLanguages

/**
 * Emballage iOS : branche [SettingsViewModel] sur l'écran partagé
 * [SettingsScreenContent].
 *
 * ## Le même écran, pas un écran ressemblant
 *
 * `SettingsScreenContent` est celui d'Android et du desktop — mêmes sections,
 * mêmes libellés, mêmes contrôles, au pixel près, parce que c'est le même code.
 * Ce fichier ne dessine rien : il lit les flux du ViewModel et les passe. Écrire
 * une page de réglages propre à iOS aurait donné deux écrans à faire diverger.
 *
 * ## Les deux choses qu'iOS ne passe pas
 *
 * `remoteSection` et `pairingDialog` restent nuls : le portage a écarté le rôle
 * de télécommande et de cible Cast, et leur couche de données s'adosse de toute
 * façon à des sockets d'écoute propres aux cibles JVM. La section disparaît.
 *
 * `onCheckUpdates` est nul aussi, et pour une raison qui ne changera pas : une
 * application iOS **ne peut pas** s'installer une nouvelle version d'elle-même.
 * La mise à jour passe par la source SideStore, hors de l'application. Un bouton
 * « Vérifier » n'aurait rien pu faire de ce qu'il annonce.
 *
 * Le sélecteur de langue affiche la langue du système sans la rendre modifiable
 * — voir [LanguageSelector] plus bas.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onPlayDownload: (Download) -> Unit = {},
    viewModel: SettingsViewModel = viewModel { SettingsViewModel() },
) {
    val apiKey by viewModel.tmdbApiKey.collectAsState()
    val introDbKey by viewModel.introDbApiKey.collectAsState()
    val streamLang by viewModel.streamLanguage.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val dohEnabled by viewModel.dohEnabled.collectAsState()
    val dohProvider by viewModel.dohProvider.collectAsState()
    val skipIntroOutro by viewModel.skipIntroOutro.collectAsState()
    val autoPlayNext by viewModel.autoPlayNext.collectAsState()
    val playerClock by viewModel.playerClock.collectAsState()
    val trailerAutoplay by viewModel.trailerAutoplay.collectAsState()
    val trailerSound by viewModel.trailerSound.collectAsState()
    val updatePrereleases by viewModel.updatePrereleases.collectAsState()
    val hideHistoryWidgets by viewModel.hideHistoryWidgets.collectAsState()
    val splashAnimation by viewModel.splashAnimation.collectAsState()
    val updateInterval by viewModel.updateInterval.collectAsState()
    val screensaverDelay by viewModel.screensaverDelay.collectAsState()

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
        // La section « Mise à jour » est retirée par `onCheckUpdates = null` :
        // cette valeur n'est donc jamais lue. Elle reste exigée par la signature
        // commune, d'où l'état neutre.
        updateCheck = UpdateCheck.IDLE,
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
        updatePrereleases = updatePrereleases,
        onSetUpdatePrereleases = viewModel::setUpdatePrereleases,
        onSetHideHistoryWidgets = viewModel::setHideHistoryWidgets,
        onSetSplashAnimation = viewModel::setSplashAnimation,
        onCheckUpdates = null,
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
        remoteSection = null,
        pairingDialog = null,
    )
}

/**
 * Langue de l'interface : celle du système, affichée sans être modifiable.
 *
 * Android propose d'en changer parce qu'il sait relancer son activité pour
 * appliquer le choix, et son `AppLanguage` est d'ailleurs propre à cette
 * plateforme. iOS, lui, expose ce réglage **hors de l'application** — Réglages >
 * Moo-vie > Langue préférée. Le doubler ici donnerait deux endroits pour
 * répondre à la même question, dont un seul ferait autorité ; le champ dit donc
 * ce qui s'applique, et laisse le système décider.
 *
 * `NSLocale.preferredLanguages` et non `currentLocale` : la première rend le
 * choix de l'utilisateur pour cette application, la seconde le format régional,
 * qui en est indépendant — on peut lire en anglais avec des dates françaises.
 */
@Composable
private fun LanguageSelector() {
    val langue = remember {
        val tag = NSLocale.preferredLanguages.firstOrNull() as? String
        tag?.let { NSLocale.currentLocale.localizedStringForLanguageCode(it.substringBefore('-')) }
            ?: tag
            ?: ""
    }
    Text(langue.replaceFirstChar { it.uppercase() })
}
