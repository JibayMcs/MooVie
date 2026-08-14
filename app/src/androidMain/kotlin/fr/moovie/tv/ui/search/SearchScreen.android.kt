package fr.moovie.tv.ui.search

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Locale

/**
 * Wrapper Android : branche le [SearchViewModel] (repos DataStore androidMain)
 * sur l'écran partagé [SearchScreenContent] de jvmCommon, et y ajoute la dictée
 * vocale — la seule partie de cet écran vraiment propre à Android.
 */
@Composable
fun SearchScreen(
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    onBack: () -> Unit,
    /** Entrée vers la découverte, seulement là où la barre basse est pleine. */
    onOpenDiscovery: (() -> Unit)? = null,
    viewModel: SearchViewModel = viewModel(),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val watchlistKeys by viewModel.watchlistKeys.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()

    val context = LocalContext.current

    val recognizer = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
            ?.takeIf { it.isNotBlank() }
            // La recherche est déjà réactive : renseigner le champ suffit à
            // lancer la requête, il n'y a rien à valider derrière.
            ?.let(viewModel::setQuery)
    }

    // Toutes les box Android TV n'embarquent pas de moteur de reconnaissance.
    // Sans ce contrôle le bouton s'afficherait et ne ferait rien : on préfère
    // qu'il n'existe pas.
    val voiceAvailable = remember {
        context.packageManager.queryIntentActivities(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH),
            PackageManager.MATCH_DEFAULT_ONLY,
        ).isNotEmpty()
    }

    SearchScreenContent(
        onOpenDiscovery = onOpenDiscovery,
        query = query,
        results = results,
        history = history,
        onQueryChange = viewModel::setQuery,
        onOpen = { item ->
            viewModel.remember()
            onOpenTitle(item.id, item.isTv)
        },
        watchlistKeys = watchlistKeys,
        onAddToWatchlist = viewModel::addToWatchlist,
        onRemoveFromWatchlist = viewModel::removeFromWatchlist,
        onRemoveHistory = viewModel::removeHistory,
        onClearHistory = viewModel::clearHistory,
        filters = filters,
        onFiltersChange = viewModel::setFilters,
        onVoiceSearch = if (!voiceAvailable) {
            null
        } else {
            {
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(
                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
                    )
                    // Dicter dans la langue de l'app : `Locale.getDefault()` est
                    // déjà celle choisie dans les réglages, LocaleManager
                    // l'appliquant avant toute UI.
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault().toLanguageTag())
                }
                // Un moteur peut disparaître entre le contrôle et le clic
                // (désinstallation, profil restreint) : l'app ne doit pas
                // tomber pour une recherche vocale.
                runCatching { recognizer.launch(intent) }
            }
        },
    )
}
