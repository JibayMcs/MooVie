package fr.moovie.tv.data.subtitles

import fr.moovie.tv.core.sources.model.MediaRef
import fr.moovie.tv.core.subtitles.usecase.rankSubtitles
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire.
 *
 * Question posée : **que se passe-t-il réellement sans compte utilisateur ?**
 *
 * Deux sources se contredisaient. Notre note de backlog affirmait que le
 * téléchargement exigeait un compte ; la documentation officielle annonce « 5
 * subtitles per IP's per 24 hours » pour le consumer seul. La réponse décide de
 * l'interface entière : à 5 par jour, un sous-titre est une ressource rare qu'on
 * ne dépense que sur un geste délibéré ; sans compte du tout, il faut un
 * parcours de connexion avant même de pouvoir livrer la fonctionnalité.
 *
 * On mesure donc plutôt que de parier, comme pour la qualité vidéo ou les
 * sous-titres forcés — les deux fois, la mesure avait démenti l'attendu.
 *
 * Ce que la sonde établit :
 * 1. la recherche répond-elle avec la seule clé applicative ?
 * 2. les résultats déclarent-ils une cadence (`fps`) ? C'est elle qui remplace
 *    le `moviehash`, impossible sur nos flux ;
 * 3. `/download` passe-t-il sans jeton, et que rend-il comme quota ?
 * 4. `/infos/user` est-il bien fermé sans compte ?
 *
 * Lancer :
 *   ./gradlew :app:desktopTest --tests "*OpenSubtitlesQuotaProbeTest*" -Dmoovie.probe=1
 *
 * **Attention : l'étape 3 consomme un téléchargement du quota du jour.**
 * Elle n'est donc jouée qu'avec `-Dmoovie.probe.download=1`, en plus du reste.
 */
class OpenSubtitlesQuotaProbeTest {

    @Test
    fun probeOpenSubtitlesQuota() = runBlocking {
        if (System.getProperty("moovie.probe") == null) return@runBlocking

        val key = System.getProperty("moovie.opensubtitles.key").orEmpty()
        if (key.isBlank()) {
            println("\n[sonde] aucune clé — renseigne opensubtitles.properties. Abandon.")
            return@runBlocking
        }
        println("\n[sonde] clé présente (${key.length} caractères), aucun compte connecté.")

        val api = OpenSubtitlesApi(key)
        val catalog = OpenSubtitlesCatalog(api)

        // ── 1. Recherche : gratuite et illimitée, d'après la doc ──────────────
        // Fight Club (550) : très diffusé, donc beaucoup de candidats et de quoi
        // juger la variété des cadences déclarées.
        val media = MediaRef.Movie(tmdbId = 550, title = "Fight Club", year = "1999")
        val candidates = catalog.search(media, listOf("fr", "en"))

        println("\n=== 1. Recherche (film) ===")
        println("candidats : ${candidates.size}")
        if (candidates.isEmpty()) {
            println("[sonde] recherche vide — vérifier la clé, le User-Agent (403) ou le réseau.")
            return@runBlocking
        }

        // ── 2. Cadence déclarée : la donnée qui remplace le moviehash ─────────
        val withFps = candidates.count { it.fps != null }
        println("\n=== 2. Cadence (fps) ===")
        println("déclarée sur %d / %d candidats (%d %%)".format(
            withFps, candidates.size, withFps * 100 / candidates.size,
        ))
        println("valeurs vues : " + candidates.mapNotNull { it.fps }.distinct().sorted())

        println("\n=== 3. Classement (5 premiers, flux supposé à 23,976) ===")
        println("%-12s %-5s %-9s %-8s %-7s %s".format(
            "fileId", "lang", "fps", "dl", "trusted", "release",
        ))
        rankSubtitles(candidates, listOf("fr", "en"), streamFps = 23.976)
            .take(5)
            .forEach {
                println("%-12s %-5s %-9s %-8d %-7s %s".format(
                    it.fileId, it.language, it.fps ?: "—", it.downloads, it.fromTrusted,
                    it.release.take(40),
                ))
            }

        // ── 4. /infos/user : censé exiger un compte ───────────────────────────
        println("\n=== 4. /infos/user sans compte ===")
        api.userInfo()
            .onSuccess { println("réponse : ${it.data}") }
            .onFailure { println("refusé → ${it.asOsFailure()}  (attendu : Unauthorized)") }

        // ── 5. Série : la recherche par saison/épisode répond-elle ? ──────────
        val episode = MediaRef.Episode(
            tmdbId = 1396, title = "Breaking Bad", year = "2008", season = 1, episode = 1,
        )
        val epCandidates = catalog.search(episode, listOf("fr", "en"))
        println("\n=== 5. Épisode (Breaking Bad S1E1) ===")
        println("candidats : ${epCandidates.size}")

        // ── 6. Téléchargement : consomme le quota, donc sur demande ───────────
        if (System.getProperty("moovie.probe.download") == null) {
            println("\n=== 6. Téléchargement — non joué ===")
            println("ajoute -Dmoovie.probe.download=1 pour le tenter (coûte 1 du quota du jour).")
            return@runBlocking
        }

        val target = rankSubtitles(candidates, listOf("fr"), streamFps = 23.976).firstOrNull()
        if (target == null) {
            println("\n[sonde] aucun candidat français à télécharger.")
            return@runBlocking
        }
        println("\n=== 6. Téléchargement de ${target.fileId} (${target.language}) ===")
        api.requestDownload(target.fileId.toLong())
            .onSuccess { resp ->
                println("lien obtenu        : ${resp.link.take(60)}…")
                println("fichier            : ${resp.fileName}")
                println("restant aujourd'hui: ${resp.remaining}")
                println("déjà demandés      : ${resp.requests}")
                println("remise à zéro (UTC): ${resp.resetTimeUtc}")
                println("message            : ${resp.message}")

                val content = api.fetchFile(resp.link).getOrNull()
                println("contenu récupéré   : ${content?.length ?: 0} caractères")
                println("premières lignes   :\n" + content?.lineSequence()?.take(4)?.joinToString("\n"))
            }
            .onFailure {
                println("refusé → ${it.asOsFailure()}")
                println("corps  : ${(it as? OsHttpException)?.body}")
            }
    }
}
