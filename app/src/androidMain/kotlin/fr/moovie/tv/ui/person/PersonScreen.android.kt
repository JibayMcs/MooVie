package fr.moovie.tv.ui.person

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.person_credits_empty
import fr.moovie.tv.resources.person_credits_error
import org.jetbrains.compose.resources.stringResource

/**
 * Wrapper Android : branche le [PersonViewModel] sur l'écran partagé
 * [PersonScreenContent] de jvmCommon.
 */
@Composable
fun PersonScreen(
    personId: Int,
    name: String,
    onOpenTitle: (tmdbId: Int, isTv: Boolean) -> Unit,
    viewModel: PersonViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val watched by viewModel.watched.collectAsStateWithLifecycle()
    val watchlistKeys by viewModel.watchlistKeys.collectAsStateWithLifecycle()

    // Les messages sont résolus ici : le ViewModel n'a pas de contexte de
    // composition, et une chaîne de ressources s'y lirait en suspendu.
    val empty = stringResource(Res.string.person_credits_empty)
    val error = stringResource(Res.string.person_credits_error)
    LaunchedEffect(personId) { viewModel.load(personId, empty, error) }

    PersonScreenContent(
        name = name,
        state = state,
        watched = watched,
        watchlistKeys = watchlistKeys,
        onOpenTitle = onOpenTitle,
    )
}
