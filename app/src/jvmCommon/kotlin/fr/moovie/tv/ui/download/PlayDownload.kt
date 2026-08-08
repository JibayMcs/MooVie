package fr.moovie.tv.ui.download

import fr.moovie.tv.data.download.Download
import fr.moovie.tv.data.download.localStream
import fr.moovie.tv.ui.navigation.Screen

/**
 * L'écran lecteur d'un téléchargement, ou null si le fichier a disparu.
 *
 * Aucune résolution de source : le fichier est sur le disque, et hors ligne
 * personne ne répondrait de toute façon. C'est ce qui fait qu'un téléchargement
 * se lit sans réseau **et** sans TMDB.
 *
 * Factorisé parce que trois endroits en ont besoin — les réglages, l'écran des
 * téléchargements, et cela sur les deux plateformes. La même dizaine de lignes
 * y était déjà copiée deux fois.
 */
fun downloadPlayerScreen(download: Download): Screen.Player? =
    localStream(download.key)?.let { local ->
        Screen.Player(
            streamUrl = local.url,
            mediaKey = download.key,
            title = download.title,
            subtitle = download.subtitle,
            posterUrl = download.imageUrl.orEmpty(),
        )
    }
