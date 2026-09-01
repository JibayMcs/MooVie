package fr.moovie.tv.data.profile

import fr.moovie.tv.data.store.DEFAULT_PROFILE_ID
import kotlinx.serialization.Serializable

/**
 * Un profil : un nom, une couleur, et rien d'autre.
 *
 * Pas de mot de passe, pas de code, pas de contrôle parental — d'où « légers ».
 * Ce que la feature sépare, c'est la *progression*, pas l'accès : sur une télé
 * partagée, le problème quotidien est que « Reprendre la lecture » propose
 * l'épisode de quelqu'un d'autre, pas qu'on puisse voir ce que les autres
 * regardent. Verrouiller demanderait une saisie à la télécommande à chaque
 * lancement, pour un bénéfice que personne n'a réclamé.
 */
@Serializable
data class Profile(
    val id: String,
    /**
     * Vide pour le profil d'origine : l'écran affiche alors le libellé traduit.
     *
     * Le nommer à la création aurait figé une langue dans les données — un
     * « Principal » écrit en français resterait tel quel après un passage en
     * anglais. Renommer reste possible, et remplit alors ce champ.
     */
    val name: String = "",
    /** Index dans la palette d'accents : un profil doit se reconnaître de loin. */
    val colorIndex: Int = 0,
    val createdAt: Long = 0,
) {
    val isDefault: Boolean get() = id == DEFAULT_PROFILE_ID

    companion object {
        /**
         * Le profil que toute installation possède, y compris celles d'avant la
         * feature. Il n'est jamais créé : il est déduit, ce qui évite d'avoir à
         * écrire quoi que ce soit au premier lancement d'une version qui vient
         * d'introduire les profils.
         */
        val Default = Profile(id = DEFAULT_PROFILE_ID)

        /** Nombre d'accents disponibles, pour tourner dessus à la création. */
        const val COLOR_COUNT = 6
    }
}
