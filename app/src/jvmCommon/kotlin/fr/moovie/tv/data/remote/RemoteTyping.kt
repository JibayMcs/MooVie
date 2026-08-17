package fr.moovie.tv.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicLong

/**
 * Un champ de saisie qui a le focus sur le téléviseur.
 *
 * [label] est ce que le champ demande (« Rechercher un film… », « Clé API
 * TMDB ») : sans lui, le téléphone afficherait un rectangle vide et il faudrait
 * lever les yeux vers la télé pour savoir ce qu'on remplit — exactement le
 * geste que la télécommande existe pour éviter.
 */
@Serializable
data class TypingField(val label: String = "", val value: String = "")

/**
 * Tout ce que le téléviseur raconte de lui en un seul relevé.
 *
 * Un seul objet plutôt qu'une route par sujet : le téléphone interroge la TV
 * chaque seconde, et deux requêtes au lieu d'une doubleraient le réveil radio
 * pour rien. C'est aussi ce qui garantit que la lecture et la saisie décrivent
 * **le même instant** — deux relevés séparés pourraient montrer un clavier pour
 * un champ déjà refermé.
 *
 * Les deux champs sont indépendants : on peut taper dans la recherche pendant
 * qu'un épisode tourne.
 */
@Serializable
data class RemoteState(
    val now: NowPlaying? = null,
    val typing: TypingField? = null,
    /**
     * Empreinte de la destination de synchro du téléviseur, vide s'il n'en a
     * pas. Le téléphone la compare à la sienne pour décider si le téléviseur a
     * le droit d'enregistrer ce qu'il diffuse — voir [PlayRequest.record].
     */
    val syncFingerprint: String = "",
)

/**
 * Le champ de saisie actif du téléviseur, vu du serveur d'appairage.
 *
 * ### Ce que ça change pour qui tient le téléphone
 *
 * La saisie existait déjà, mais **à l'aveugle** : il fallait deviner qu'un champ
 * attendait quelque chose, ouvrir soi-même le clavier du téléphone, et taper
 * sans savoir ce qu'il y avait déjà. Annoncer le champ permet au téléphone
 * d'ouvrir son clavier au bon moment, et de le refermer quand le champ perd le
 * focus.
 *
 * ### La discipline des identifiants
 *
 * Deux champs voisins échangent leur focus sans ordre garanti : celui qui le
 * prend peut s'annoncer **avant** que celui qui le perd ne se retire. Sans
 * garde, le second effacerait l'annonce du premier et le clavier se fermerait
 * en changeant de champ. D'où [blur], qui n'efface que si l'appelant est bien
 * celui qui est affiché.
 */
object RemoteTyping {

    private val _field = MutableStateFlow<TypingField?>(null)

    /** Le champ actif, ou null si le téléviseur n'attend aucune saisie. */
    val field: StateFlow<TypingField?> = _field.asStateFlow()

    /** Identifiant du champ actuellement annoncé. Voir la discipline ci-dessus. */
    private val owner = AtomicLong(0)
    private val ids = AtomicLong(0)

    /**
     * Comment écrire dans le champ, posé par lui tant qu'il a le focus.
     *
     * Écrire directement plutôt que de simuler des touches : l'injection clavier
     * **ajoute** à la fin, si bien que corriger une recherche depuis le
     * téléphone concaténait l'ancienne et la nouvelle. Elle reste le repli quand
     * aucun champ ne s'est annoncé — un champ d'une version antérieure, ou d'un
     * écran qu'on n'a pas instrumenté.
     */
    @Volatile
    private var setter: ((String) -> Unit)? = null

    /** Un identifiant neuf, à garder pour la durée de vie d'un champ. */
    fun nextId(): Long = ids.incrementAndGet()

    /** Ce champ prend le focus : c'est lui que le téléphone doit remplir. */
    fun focus(id: Long, label: String, value: String, write: (String) -> Unit) {
        owner.set(id)
        setter = write
        _field.value = TypingField(label, value)
    }

    /** Le contenu a changé sur le téléviseur — au clavier, ou par nous. */
    fun updateValue(id: Long, value: String) {
        if (owner.get() != id) return
        _field.value = _field.value?.copy(value = value)
    }

    /** Ce champ rend le focus. Sans effet si un autre s'est déjà annoncé. */
    fun blur(id: Long) {
        if (!owner.compareAndSet(id, 0)) return
        setter = null
        _field.value = null
    }

    /** Écrit dans le champ actif. Faux si aucun ne s'est annoncé. */
    fun write(text: String): Boolean {
        val write = setter ?: return false
        write(text)
        return true
    }
}
