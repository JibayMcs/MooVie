package fr.moovie.tv.ui.home

import androidx.compose.runtime.Composable
import fr.moovie.tv.data.home.HomeLayoutEntry
import fr.moovie.tv.data.home.HomeRowKind
import fr.moovie.tv.resources.Res
import fr.moovie.tv.resources.home_continue_watching
import fr.moovie.tv.resources.home_row_recommendations
import fr.moovie.tv.resources.home_row_top_movies
import fr.moovie.tv.resources.home_row_trending_movies
import fr.moovie.tv.resources.home_row_trending_tv
import fr.moovie.tv.resources.watchlist_row
import org.jetbrains.compose.resources.stringResource

/**
 * Nom d'une rangée tel qu'on le montre **hors de l'accueil** : dans la modale
 * d'épinglage et dans l'écran de réorganisation.
 *
 * Les intitulés sont ceux des rangées elles-mêmes, à une exception près : la
 * rangée de recommandations s'appelle « Parce que tu as regardé X » sur
 * l'accueil, où X est le dernier titre terminé. Le désigner ainsi dans une liste
 * de positions serait absurde — le nom changerait au prochain film. On la nomme
 * donc par ce qu'elle est, pas par ce qu'elle contient ce jour-là.
 */
@Composable
fun homeRowLabel(entry: HomeLayoutEntry): String = when (entry.kind) {
    HomeRowKind.RESUME -> stringResource(Res.string.home_continue_watching)
    HomeRowKind.WATCHLIST -> stringResource(Res.string.watchlist_row)
    HomeRowKind.RECOMMENDATIONS -> stringResource(Res.string.home_row_recommendations)
    HomeRowKind.TRENDING_MOVIES -> stringResource(Res.string.home_row_trending_movies)
    HomeRowKind.TRENDING_TV -> stringResource(Res.string.home_row_trending_tv)
    HomeRowKind.TOP_MOVIES -> stringResource(Res.string.home_row_top_movies)
    HomeRowKind.GENRE -> entry.genre?.name.orEmpty()
}
