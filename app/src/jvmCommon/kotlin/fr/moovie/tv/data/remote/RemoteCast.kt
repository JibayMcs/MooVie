package fr.moovie.tv.data.remote

/**
 * Les lectures que ce téléviseur **ne doit pas enregistrer**.
 *
 * ## Pourquoi ça existe
 *
 * Quand le téléphone diffuse un titre et que les deux appareils n'écrivent pas
 * au même endroit, le téléviseur n'est qu'un écran. Y laisser une progression et
 * une ligne d'historique polluerait un compte qui n'a rien demandé : on
 * retrouverait sur la box des titres qu'on n'y a jamais regardés, et le rail
 * « Reprendre » du salon se remplirait de ce que quelqu'un d'autre a lancé
 * depuis son téléphone.
 *
 * ## Pourquoi une clé et non un drapeau
 *
 * Un simple booléen « la lecture en cours ne s'enregistre pas » est un piège :
 * s'il reste allumé — une sortie inattendue, un processus tué, une lecture
 * lancée pendant qu'il traîne — le téléviseur **cesse d'enregistrer ses propres
 * lectures**, et rien ne le dit. Le défaut serait invisible et durerait jusqu'à
 * ce que quelqu'un remarque que sa box ne retient plus rien.
 *
 * En retenant la **clé du média**, l'oubli ne peut porter que sur ce titre-là :
 * tout ce qu'on lance ensuite s'enregistre normalement, puisque la clé ne
 * correspond plus.
 */
object RemoteCast {

    @Volatile
    private var ephemeralKey: String? = null

    /** Ce média est diffusé depuis un téléphone d'un autre compte : rien à écrire. */
    fun markEphemeral(mediaKey: String) {
        ephemeralKey = mediaKey.takeIf { it.isNotBlank() }
    }

    /** Vrai si cette lecture-ci ne doit rien laisser derrière elle. */
    fun isEphemeral(mediaKey: String): Boolean =
        mediaKey.isNotBlank() && mediaKey == ephemeralKey

    /**
     * Oublie la marque. Appelé quand une lecture **locale** démarre : le
     * téléviseur reprend la main sur son propre historique.
     */
    fun clear() {
        ephemeralKey = null
    }
}
