package fr.moovie.tv.data.tmdb

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import platform.Foundation.NSURLRequestUseProtocolCachePolicy

/**
 * Côté iOS le cache disque vient de `NSURLCache`, que NSURLSession consulte de
 * lui-même — il n'y a rien à installer, seulement une politique à choisir.
 *
 * La différence avec la JVM est à connaître : le client OkHttp **impose** une
 * durée de six heures en réécrivant l'en-tête `Cache-Control` de TMDB, qui est
 * très court, et sait resservir une réponse périmée quand le réseau est absent.
 * `NSURLCache` respecte l'en-tête tel qu'il arrive et n'a pas d'équivalent du
 * repli périmé. En pratique : iOS rafraîchira plus souvent, et un écran ouvert
 * hors ligne restera vide là où Android afficherait la version en cache.
 *
 * Rétablir les deux comportements demanderait un `URLProtocol` ou le plugin
 * `HttpCache` de Ktor avec un stockage sur fichier. C'est faisable, ce n'est
 * pas fait, et cela ne bloque personne : les écrans traitent déjà l'absence de
 * données.
 */
actual val clientTmdb: HttpClient = HttpClient(Darwin) {
    // Même contrat que côté JVM : lever sur un statut non-2xx, pour que
    // `TmdbRepository` puisse reconnaître une clé refusée.
    expectSuccess = true
    engine {
        configureRequest {
            setCachePolicy(NSURLRequestUseProtocolCachePolicy)
        }
    }
    install(ContentNegotiation) {
        json(Json { ignoreUnknownKeys = true })
    }
}
