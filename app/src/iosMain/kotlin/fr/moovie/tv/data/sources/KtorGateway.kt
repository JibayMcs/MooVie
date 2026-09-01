package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpMethod
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import fr.moovie.tv.core.sources.port.NetworkProfile
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import io.ktor.http.HttpMethod as MethodeKtor

/**
 * Implémentation Ktor/Darwin de [HttpGateway] — pendant iOS de `OkHttpGateway`.
 *
 * Même découpage qu'en JVM : deux clients dérivés du même socle, qui ne
 * diffèrent que par le suivi des redirections. Le variant sans suivi sert aux
 * chaînes que l'appelant déroule à la main (VOE en enchaîne 28).
 *
 * ## Ce que cette passerelle ne fait pas : le DNS-over-HTTPS
 *
 * Côté JVM, `AppDns` branche un résolveur DoH sur OkHttp pour contourner le
 * blocage DNS des FAI sur les domaines sources — c'est porteur, pas cosmétique :
 * sans lui, plusieurs catalogues sont simplement introuvables chez les abonnés
 * concernés.
 *
 * **NSURLSession n'expose aucun point d'entrée de résolution.** Le moteur Darwin
 * n'a donc pas d'équivalent, et les trois contournements possibles échouent
 * chacun pour une raison propre :
 *
 * - `NEDNSSettingsManager` exige l'entitlement Network Extension, qu'un profil
 *   de développement sideloadé n'obtient pas ;
 * - résoudre en DoH puis se connecter à l'IP littérale casse la validation TLS,
 *   le certificat étant émis pour le nom d'hôte et non pour l'adresse ;
 * - `URLProtocol` personnalisé ne s'applique pas aux connexions HTTPS
 *   directes.
 *
 * Il reste une voie, non empruntée ici : connexion par IP **plus** réécriture de
 * la politique de confiance via `SecTrustSetPolicies` dans le
 * `handleChallenge` du moteur Darwin, pour valider le certificat contre le nom
 * d'origine. C'est faisable et c'est la bonne cible, mais cela demande un
 * cinterop Security.framework et une vérification sur appareil réel.
 *
 * En attendant, iOS utilise le résolveur du système. L'utilisateur qui subit un
 * blocage FAI peut installer un profil DNS chiffré à l'échelle du système
 * (Réglages > Général > VPN et gestion de l'appareil), ce qui couvre l'app sans
 * qu'elle ait à en connaître l'existence.
 */
class KtorGateway(private val client: HttpClient) : HttpGateway {

    /**
     * `followRedirects` est figé à la construction du client Ktor, pas
     * négociable par requête : il faut donc un second client. `config { }` clone
     * la configuration du premier, moteur et timeouts compris.
     */
    private val sansRedirection: HttpClient by lazy {
        client.config { followRedirects = false }
    }

    override suspend fun fetch(request: HttpRequest): HttpResponse? = runCatching {
        // [NetworkProfile.BROWSER] n'a pas plus d'implémentation ici qu'en JVM :
        // la requête part sur le client par défaut, et une source qui l'exigerait
        // répond 403 — un échec que l'appelant traite déjà.
        val http = if (request.followRedirects) client else sansRedirection

        val reponse = http.request(request.url) {
            method = when (request.method) {
                HttpMethod.GET -> MethodeKtor.Get
                HttpMethod.HEAD -> MethodeKtor.Head
                HttpMethod.POST -> MethodeKtor.Post
            }
            request.headers.forEach { (nom, valeur) -> header(nom, valeur) }

            if (request.method == HttpMethod.POST) {
                val json = request.json
                if (json != null) {
                    contentType(ContentType.Application.Json)
                    setBody(json)
                } else {
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                request.form.orEmpty().forEach { (c, v) -> append(c, v) }
                            },
                        ),
                    )
                }
            }
        }

        HttpResponse(
            status = reponse.status.value,
            // URL finale après redirections, comme côté OkHttp : c'est elle qui
            // doit servir de base au Referer chez les hébergeurs à alias
            // tournants, pas celle demandée.
            url = reponse.request.url.toString(),
            headers = reponse.headers.entries()
                .associate { (nom, valeurs) -> nom to valeurs.firstOrNull().orEmpty() },
            // Texte **ou** octets, jamais les deux : le corps est un flux à
            // usage unique.
            body = when {
                request.method == HttpMethod.HEAD -> null
                request.binary -> null
                else -> reponse.bodyAsText()
            },
            bytes = if (request.binary && request.method != HttpMethod.HEAD) {
                litPlafonne(reponse)
            } else {
                null
            },
        )
    }.getOrNull()

    /**
     * Lit un corps binaire, **borné** — même plafond et même raison qu'en JVM.
     *
     * Un `Range` est une demande, pas une garantie : mesuré sur SwiftFlow, un
     * proxy intermédiaire ne transmet pas l'en-tête, le serveur répond 200 avec
     * le fichier entier, et une lecture non bornée entreprend de charger
     * plusieurs gigaoctets en mémoire pour y lire quatre nombres. Sur iPhone
     * c'est la terminaison par le système, plus brutale encore que
     * l'`OutOfMemoryError` que ce projet a déjà payé côté Android.
     *
     * Le plafond dépasse le plus gros appel légitime (512 Ko d'en-tête MP4) : il
     * ne tronque donc jamais une réponse correctement bornée.
     */
    private suspend fun litPlafonne(reponse: io.ktor.client.statement.HttpResponse): ByteArray {
        val canal = reponse.bodyAsChannel()
        val tampon = ByteArray(PLAFOND_BINAIRE)
        var lus = 0
        while (lus < tampon.size && !canal.isClosedForRead) {
            val n = canal.readAvailable(tampon, lus, tampon.size - lus)
            if (n < 0) break
            lus += n
        }
        return if (lus == tampon.size) tampon else tampon.copyOf(lus)
    }

    private companion object {
        /** Un `moov` de long métrage pèse quelques centaines de kilo-octets. */
        const val PLAFOND_BINAIRE = 1 shl 20
    }
}

/**
 * Client Ktor de l'app, réglé comme son homologue OkHttp.
 *
 * Un seul client pour tout le processus : NSURLSession maintient un pool de
 * connexions par session, et en créer un par requête reconstruirait une
 * poignée de main TLS à chaque appel — coûteux sur réseau mobile.
 */
fun clientMoovieIos(): HttpClient = HttpClient(Darwin) {
    // Ktor ne lève pas sur les statuts non-2xx par défaut, et c'est ce qu'il
    // faut : la cascade d'extraction lit les 302 et les 403 comme des
    // informations, pas comme des pannes.
    expectSuccess = false
    followRedirects = true

    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
}
