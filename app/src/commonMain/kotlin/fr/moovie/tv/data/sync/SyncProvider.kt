package fr.moovie.tv.data.sync

import fr.moovie.tv.data.sync.providers.B2_DESCRIPTOR

/** Les dépôts distants que l'app sait servir. */
enum class SyncProvider {
    /** Synchro éteinte. C'est le défaut, et ça doit le rester. */
    NONE,

    BACKBLAZE_B2,
}

/**
 * Un identifiant que le fournisseur réclame.
 *
 * Le libellé n'est **pas** ici : ce serait faire entrer une préoccupation
 * d'affichage — et de traduction — dans la couche données. L'écran résout
 * `id` vers une chaîne, ce qui coûte une branche de `when` par champ ajouté.
 */
data class CredentialField(
    val id: String,
    /** Masqué à la saisie et jamais réaffiché en clair. */
    val secret: Boolean = false,
)

/**
 * Ce qu'un fournisseur déclare de lui-même.
 *
 * C'est la pièce qui rend l'ajout d'un fournisseur bon marché : un adaptateur,
 * un descripteur, une entrée dans [SyncProviders]. **L'écran de réglages n'est
 * pas touché** — il lit [fields] et dessine le formulaire tout seul.
 */
data class SyncProviderDescriptor(
    val provider: SyncProvider,
    val fields: List<CredentialField>,
    /**
     * Fabrique l'adaptateur. Rend null si les identifiants sont incomplets —
     * c'est au fournisseur de savoir ce qui lui manque, pas à l'appelant.
     */
    val open: (credentials: Map<String, String>) -> SyncStore?,
)

/**
 * Racine de composition : le seul endroit qui connaisse à la fois le port et
 * ses adaptateurs.
 *
 * Tout le reste du domaine ne voit que [SyncStore]. C'est ce qui permet de
 * tester le moteur de synchro avec un dépôt en mémoire, sans réseau ni compte.
 */
object SyncProviders {

    private val all: List<SyncProviderDescriptor> = listOf(B2_DESCRIPTOR)

    fun descriptor(provider: SyncProvider): SyncProviderDescriptor? =
        all.firstOrNull { it.provider == provider }

    /** Les fournisseurs proposables, hors [SyncProvider.NONE]. */
    fun available(): List<SyncProvider> = all.map { it.provider }

    /** Ouvre un dépôt, ou null si le fournisseur est éteint ou mal renseigné. */
    fun open(provider: SyncProvider, credentials: Map<String, String>): SyncStore? =
        descriptor(provider)?.open?.invoke(credentials)
}
