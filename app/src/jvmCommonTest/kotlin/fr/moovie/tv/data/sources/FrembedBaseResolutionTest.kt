package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.core.sources.port.HttpRequest
import fr.moovie.tv.core.sources.port.HttpResponse
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Résolution du domaine de l'API frembed — **sans réseau**, sur les réponses
 * réelles relevées le 11/09/2026, le jour où ce catalogue s'est tu.
 *
 * Deux pannes s'étaient additionnées, et aucune des deux ne lève quoi que ce
 * soit : le provider rendait simplement une liste vide, indistinguable de « ce
 * titre n'y est pas ». C'est la panne muette que ce projet documente déjà pour
 * wiflix et anime-sama, et elle a survécu à une release.
 *
 *  1. `frembed.casa` redirige vers `frembed.surf`, et l'API **refuse un
 *     `Referer`** qui ne porte pas son propre domaine. Suivre la redirection
 *     sans reposer l'en-tête donne un 403, pas des liens ;
 *  2. `/api/dns/domains` annonce la **vitrine** (`fanstream.buzz`) au même
 *     titre que l'API, tous deux étiquetés `main`. La retenir rend du HTML
 *     Next.js là où on attend du JSON.
 *
 * Le faux serveur ci-dessous rejoue exactement ces trois comportements — la
 * redirection, le contrôle de Referer, la vitrine qui rend du HTML — pour que
 * la règle « un domaine n'est retenu que s'il sert vraiment l'API » soit
 * vérifiée sans dépendre d'un site vivant.
 */
class FrembedBaseResolutionTest {

    /** Les miroirs annoncés, vitrine d'abord — c'est l'ordre qui piégeait. */
    private val domainsJson =
        """[{"domain":"fanstream.buzz","label":"main"},{"domain":"frembed.surf","label":"main"}]"""

    private val filmJson = """
        {"tmdb":"550","title":"Fight Club","links":[
          {"id":49633,"lang":"vf","label":"Voe","host":{"slug":"voe"},"quality":null,
           "url":"/api/stream?type=movie&tmdb=550&server=id:49633"},
          {"id":49634,"lang":"vf","label":"Uqload","host":{"slug":"uqload"},"quality":"HD",
           "url":"/api/stream?type=movie&tmdb=550&server=id:49634"}]}
    """.trimIndent()

    /** La vitrine : une page Next.js, sur *n'importe quel* chemin `/api/…`. */
    private val vitrineHtml = """<!DOCTYPE html><html lang="en"><head><meta charSet="utf-8"/>"""

    private class FakeFrembed(
        private val domainsJson: String,
        private val filmJson: String,
        private val vitrineHtml: String,
    ) : HttpGateway {
        val vus = mutableListOf<String>()

        override suspend fun fetch(request: HttpRequest): HttpResponse? {
            vus += request.url
            val referer = request.headers["Referer"].orEmpty()

            // Le domaine historique ne sert plus rien : il redirige.
            if (request.url.startsWith("https://frembed.casa")) {
                return HttpResponse(
                    status = 302,
                    url = request.url,
                    headers = mapOf("Location" to request.url.replace("frembed.casa", "frembed.surf")),
                )
            }
            // La vitrine répond 200 à tout, en HTML.
            if (request.url.startsWith("https://fanstream.buzz")) {
                return HttpResponse(status = 200, url = request.url, body = vitrineHtml)
            }
            if (request.url.startsWith("https://frembed.surf")) {
                // Le contrôle qui rendait la redirection inutile.
                if (!referer.startsWith("https://frembed.surf")) {
                    return HttpResponse(
                        status = 403,
                        url = request.url,
                        body = """{"error":"Access denied - Invalid Referer"}""",
                    )
                }
                return when {
                    request.url.contains("/api/dns/domains") ->
                        HttpResponse(200, request.url, body = domainsJson)
                    request.url.contains("/api/films") ->
                        HttpResponse(200, request.url, body = filmJson)
                    // `/api/stream` ne rend pas l'URL, il redirige dessus.
                    request.url.contains("/api/stream") -> HttpResponse(
                        status = 302,
                        url = request.url,
                        headers = mapOf(
                            "Location" to "https://tracylocalschool.com/e/" +
                                request.url.substringAfter("server=id:"),
                        ),
                    )
                    else -> HttpResponse(404, request.url, body = "")
                }
            }
            return null
        }
    }

    private fun provider() = FrembedProvider(FakeFrembed(domainsJson, filmJson, vitrineHtml))

    @Test
    fun `le catalogue rend ses liens malgré le déménagement du domaine`() = runTest {
        val links = provider().sourcesFor(MediaRef.Movie(550, "Fight Club", "1999"))

        // La panne se lisait exactement ici : zéro lien, sans erreur.
        assertEquals(2, links.size)
        assertEquals(listOf("voe", "uqload"), links.map { it.hoster })
        assertTrue(links.all { it.language == "VF" })
        assertTrue(links.all { it.url.startsWith("https://tracylocalschool.com/e/") })
    }

    @Test
    fun `la vitrine annoncée comme miroir n'est jamais retenue comme API`() = runTest {
        val gateway = FakeFrembed(domainsJson, filmJson, vitrineHtml)
        val links = FrembedProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club", "1999"))

        assertTrue(links.isNotEmpty())
        // `fanstream.buzz` est le premier `main` de la liste : le retenir était
        // la seconde moitié de la panne. Aucun appel de contenu ne doit y aller.
        assertTrue(
            gateway.vus.none { it.startsWith("https://fanstream.buzz") && it.contains("/api/films") },
            "la vitrine a été interrogée pour du contenu : ${gateway.vus}",
        )
    }

    @Test
    fun `le Referer accompagne le domaine réellement appelé, pas celui de départ`() = runTest {
        val gateway = FakeFrembed(domainsJson, filmJson, vitrineHtml)
        FrembedProvider(gateway).sourcesFor(MediaRef.Movie(550, "Fight Club", "1999"))

        // Un 403 « Invalid Referer » ne se distingue d'une panne réseau qu'ici :
        // si l'en-tête suivait le domaine de départ, la liste serait vide.
        assertTrue(gateway.vus.any { it == "https://frembed.surf/api/films?id=550&idType=tmdb" })
    }
}
