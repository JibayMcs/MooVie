package fr.moovie.tv.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.lifecycle.HasDefaultViewModelProviderFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import fr.moovie.tv.data.profile.ProfileRepository
import fr.moovie.tv.shared.fabriqueParDefaut
import kotlinx.coroutines.flow.first

/**
 * Rouvre la porte des profils. Fourni par [ProfileHost], appelé depuis les
 * réglages — rien d'autre n'a besoin de savoir comment on change de profil.
 */
val LocalSwitchProfile = staticCompositionLocalOf<() -> Unit> { {} }

/**
 * Enveloppe de l'app : décide quel profil est servi avant que quoi que ce soit
 * ne lise des données.
 *
 * Trois raisons d'exister, dans cet ordre :
 *
 * 1. **Rien ne se compose avant la réponse.** Les dépôts lisent le profil actif
 *    à leur construction ; en laisser un se construire trop tôt lui ferait
 *    ouvrir le fichier du profil précédent et le servir jusqu'à la prochaine
 *    recomposition. Même raisonnement que l'écran d'installation, qui ne montre
 *    pas d'accueil vide le temps d'une lecture DataStore.
 * 2. **La porte s'affiche seulement s'il y a un choix.** Un seul profil, c'est
 *    l'immense majorité des installations : leur imposer une formalité à chaque
 *    lancement pour une feature qu'elles n'utilisent pas serait un mauvais
 *    échange.
 * 3. **`key(session)` remonte tout l'arbre au changement de profil.** C'est ce
 *    qui rend l'état global [fr.moovie.tv.data.store.ActiveProfile] correct :
 *    dépôts reconstruits, flux resouscrits, ViewModels recréés — y compris ceux
 *    qui survivent à la navigation, comme le DetailsViewModel partagé.
 */
@Composable
fun ProfileHost(content: @Composable (profileId: String) -> Unit) {
    val repo = remember { ProfileRepository() }

    // null tant que la question n'est pas tranchée : on ne compose rien.
    var resolved by remember { mutableStateOf(false) }
    // null = la porte est ouverte, personne n'a encore choisi.
    var session by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val active = repo.restoreActive()
        session = if (repo.profiles.first().size > 1) null else active
        resolved = true
    }

    if (!resolved) return

    val current = session
    if (current == null) {
        ProfileGate(onPicked = { session = it })
    } else {
        key(current) {
            // Un magasin de ViewModels par profil.
            //
            // `key` ne suffisait pas : `viewModel()` résout sur le magasin de
            // l'Activity, pas sur la composition. Les écrans se reconstruisaient
            // bien, mais recevaient le *même* HomeViewModel — construit quand
            // l'autre profil était actif, et dont le dépôt collectait déjà
            // l'ancien fichier. On changeait de profil et « Reprendre la
            // lecture » proposait toujours l'épisode du précédent.
            //
            // Le fournir ici corrige les douze appels à `viewModel()` d'un
            // seul endroit. C'est sûr parce que l'Activity déclare
            // `configChanges` sur l'orientation : une rotation ne remonte pas
            // la composition, et n'emporte donc pas ces ViewModels.
            // Le propriétaire parent (l'Activity) est conservé pour sa
            // fabrique : sans elle, `viewModel()` retombe sur la fabrique JVM
            // par défaut, qui ne sait construire qu'un ViewModel sans argument.
            // UpdateViewModel est un AndroidViewModel — il lui faut
            // l'Application, portée par les CreationExtras du parent.
            val parent = LocalViewModelStoreOwner.current
            val owner = remember(current, parent) { ProfileViewModelStoreOwner(parent) }
            DisposableEffect(owner) { onDispose { owner.viewModelStore.clear() } }
            CompositionLocalProvider(
                LocalViewModelStoreOwner provides owner,
                LocalSwitchProfile provides { session = null },
            ) {
                content(current)
            }
        }
    }
}

/**
 * Magasin de ViewModels dont la durée de vie est celle d'un profil.
 *
 * Le magasin est neuf — c'est tout l'intérêt — mais la **fabrique** est celle du
 * propriétaire parent : construire un ViewModel et décider où le ranger sont
 * deux questions distinctes, et seule la seconde dépend du profil.
 */
private class ProfileViewModelStoreOwner(
    private val parent: ViewModelStoreOwner?,
) : ViewModelStoreOwner, HasDefaultViewModelProviderFactory {

    override val viewModelStore = ViewModelStore()

    private val parentFactories = parent as? HasDefaultViewModelProviderFactory

    override val defaultViewModelProviderFactory: ViewModelProvider.Factory
        get() = parentFactories?.defaultViewModelProviderFactory ?: fabriqueParDefaut()

    override val defaultViewModelCreationExtras: CreationExtras
        get() = parentFactories?.defaultViewModelCreationExtras ?: CreationExtras.Empty
}
