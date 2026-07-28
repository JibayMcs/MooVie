package fr.moovie.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import fr.moovie.tv.ui.navigation.Screen
import fr.moovie.tv.ui.details.DetailsScreen
import fr.moovie.tv.ui.home.HomeScreen
import fr.moovie.tv.ui.player.PlayerScreen
import fr.moovie.tv.ui.settings.SettingsScreen
import fr.moovie.tv.ui.theme.MooVieTheme

/**
 * Activity unique. La navigation est gérée par un simple état `Screen` plutôt
 * que navigation-compose : sur TV, le contrôle explicite du focus/back est plus
 * simple à maîtriser qu'un NavHost. À remplacer si le graphe grossit.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MooVieTheme {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                ) {
                    var screen: Screen by remember { mutableStateOf(Screen.Home) }

                    when (val s = screen) {
                        Screen.Home -> HomeScreen(
                            onOpenTitle = { id, isTv -> screen = Screen.Details(id, isTv) },
                            onOpenSettings = { screen = Screen.Settings },
                        )
                        Screen.Settings -> SettingsScreen(
                            onBack = { screen = Screen.Home },
                        )
                        is Screen.Details -> DetailsScreen(
                            tmdbId = s.tmdbId,
                            isTv = s.isTv,
                            onPlay = { streamUrl -> screen = Screen.Player(streamUrl) },
                            onBack = { screen = Screen.Home },
                        )
                        is Screen.Player -> PlayerScreen(
                            streamUrl = s.streamUrl,
                            onBack = { screen = Screen.Home },
                        )
                    }
                }
            }
        }
    }
}
