package fr.moovie.tv.data.remote

import fr.moovie.tv.data.sources.ExtractorRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/**
 * Ce que le téléviseur répond quand on lui demande son état.
 *
 * [Unreachable] n'est **pas** « rien ne joue » : c'est « je ne sais pas ». La
 * distinction est tout l'intérêt du type — les confondre faisait clignoter le
 * mini-lecteur au premier relevé perdu.
 */
sealed interface RemoteStatus {
    /** Le téléviseur a répondu. Ce qu'il dit peut être vide, mais il l'a dit. */
    data class Known(val state: RemoteState) : RemoteStatus

    data object Unreachable : RemoteStatus
}

/**
 * Envoie les touches au téléviseur, depuis le téléphone.
 *
 * Le pendant exact de ce que faisait le `fetch` de la page web, mais en Kotlin :
 * mêmes routes, même corps de requête, même serveur en face. Ce qui change est
 * ce qu'il y a **autour** — un écran natif, donc le vibreur du système au lieu
 * d'une API web que le navigateur refuse.
 *
 * ### Des délais très courts, et un échec silencieux
 *
 * Une touche est un geste, pas une transaction : si elle n'arrive pas en une
 * seconde, la réessayer n'a plus de sens, l'utilisateur a déjà appuyé ailleurs.
 * On préfère donc perdre une touche que faire attendre la suivante. Les appels
 * ne lèvent jamais : ils rendent `false`, dont l'écran se sert pour dire que la
 * TV ne répond plus.
 */
class RemoteClient(private val target: RemoteTarget) {

    /**
     * Client dédié, court en délais, et **sans le DNS applicatif** : on parle à
     * une adresse IP du réseau local. Le résolveur DoH n'a rien à y faire, et
     * l'interroger pour une IP littérale ne ferait qu'ajouter de la latence.
     */
    private val http = ExtractorRegistry.http.newBuilder()
        .connectTimeout(800, TimeUnit.MILLISECONDS)
        .readTimeout(1200, TimeUnit.MILLISECONDS)
        .callTimeout(1500, TimeUnit.MILLISECONDS)
        .dns(okhttp3.Dns.SYSTEM)
        .build()

    suspend fun key(key: RemoteKey): Boolean = post("key", "k=${key.name}")

    /**
     * Ce que le téléviseur lit en ce moment.
     *
     * ### Trois réponses, et surtout pas deux
     *
     * « Rien ne joue » et « pas de réponse » ont d'abord été confondus sous un
     * même `null`, et c'était faux : avec des délais d'une seconde, **un seul
     * relevé perdu sur le Wi-Fi effaçait le mini-lecteur**, que le suivant
     * faisait réapparaître. Le symptôme — un panneau qui clignote — ne
     * ressemblait pas du tout à sa cause.
     *
     * Un silence ne dit rien sur ce qui joue. Il ne doit donc rien effacer :
     * c'est à l'écran de décider combien de silences de suite valent un oubli.
     */
    suspend fun status(): RemoteStatus = withContext(Dispatchers.IO) {
        attempt {
            http.newCall(Request.Builder().url(target.base() + "/state").build())
                .execute().use { response ->
                    if (response.code != 200) return@use RemoteStatus.Unreachable
                    val body = response.body?.string().orEmpty()
                    if (body.isBlank()) RemoteStatus.Unreachable
                    else RemoteStatus.Known(JSON.decodeFromString<RemoteState>(body))
                }
        } ?: RemoteStatus.Unreachable
    }

    /**
     * Remplace le contenu du champ qui a le focus sur le téléviseur.
     *
     * Remplace, et n'ajoute pas : le serveur écrit directement dans le champ
     * quand celui-ci s'est annoncé. C'est ce qui permet de corriger un mot au
     * lieu de le concaténer au précédent.
     */
    suspend fun text(value: String): Boolean =
        post("text", "t=" + URLEncoder.encode(value, "UTF-8"))

    /**
     * Déplace la lecture à une position absolue.
     *
     * Absolue et non relative : le doigt vise un endroit de la barre. Un
     * décalage calculé depuis la dernière position connue — vieille d'une
     * seconde au plus — atterrirait systématiquement à côté, et l'erreur
     * s'accumulerait à chaque geste.
     */
    suspend fun seek(positionMs: Long): Boolean = post("seek", "p=$positionMs")

    /**
     * Demande au téléviseur de lire ce titre.
     *
     * On envoie **l'identifiant TMDB, pas la source** : c'est le téléviseur qui
     * résout, avec ses propres catalogues et sa propre connexion. Voir
     * [PlayRequest] pour ce que ce choix implique.
     *
     * Faux couvre deux cas que l'appelant doit distinguer de la réussite, sans
     * avoir à les distinguer entre eux : le téléviseur n'a pas répondu, ou il a
     * répondu qu'il n'était pas en état de recevoir (409). Dans les deux cas il
     * ne faut pas basculer sur la télécommande, qui ne montrerait rien.
     */
    suspend fun play(request: PlayRequest): Boolean = post(
        "play",
        buildString {
            append("id=").append(request.tmdbId)
            append("&tv=").append(if (request.isTv) "1" else "0")
            if (request.season > 0) append("&s=").append(request.season)
            if (request.episode > 0) append("&e=").append(request.episode)
            append("&t=").append(encode(request.title))
            append("&st=").append(encode(request.subtitle))
            append("&art=").append(encode(request.artwork))
            if (request.positionMs > 0) append("&pos=").append(request.positionMs)
            if (request.durationMs > 0) append("&dur=").append(request.durationMs)
        },
    )

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    /**
     * Le téléviseur répond-il, et ce jeton vaut-il encore ?
     *
     * Les deux à la fois : une adresse joignable avec un jeton révoqué rend 404,
     * ce qui est bien un « non ». C'est ce qui permet de dire « le téléviseur ne
     * répond pas » aussi bien quand il est éteint que quand on a oublié les
     * télécommandes depuis ses réglages.
     *
     * Sur `/ping` et non sur `/remote` : cette dernière **arme la session** sur
     * le téléviseur, et regarder si la télécommande est joignable ne doit rien
     * changer à son état.
     */
    suspend fun ping(): Boolean = withContext(Dispatchers.IO) {
        attempt {
            http.newCall(Request.Builder().url(target.base() + "/ping").build())
                .execute().use { it.isSuccessful }
        } ?: false
    }

    private suspend fun post(route: String, body: String): Boolean = withContext(Dispatchers.IO) {
        attempt {
            val request = Request.Builder()
                .url("${target.base()}/$route")
                .post(body.toRequestBody(FORM))
                .build()
            http.newCall(request).execute().use { it.isSuccessful }
        } ?: false
    }

    /**
     * Exécute [block], ou rend null si le réseau a échoué — **mais laisse
     * passer l'annulation**.
     *
     * `runCatching` attrape tout, y compris la `CancellationException` que Kotlin
     * lève pour démonter une coroutine. Quitter l'écran de télécommande annule
     * ses appels en vol, et chacun ressortait alors en « échec réseau » : le
     * téléphone en concluait que le téléviseur ne répondait plus et effaçait sa
     * présence. Le bouton flottant disparaissait donc **parce qu'on avait fermé
     * la télécommande**, ce qui ne ressemble en rien à sa cause.
     */
    private inline fun <T> attempt(block: () -> T): T? = try {
        block()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (failed: Exception) {
        null
    }

    private companion object {
        val FORM = "application/x-www-form-urlencoded".toMediaType()

        /**
         * `ignoreUnknownKeys` : c'est ce qui permet à un téléviseur plus récent
         * que le téléphone d'ajouter un champ sans faire tomber le mini-lecteur
         * de l'ancien. Les deux moitiés se mettent à jour séparément — l'une par
         * le Play Store d'un téléphone, l'autre par l'updater d'une box.
         */
        val JSON = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
