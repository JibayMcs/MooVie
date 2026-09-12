package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.model.MediaRef
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Sonde de diagnostic — PAS un test unitaire (réseau + abonnement debrid requis).
 *
 * Déroule la chaîne entière sur de vrais titres : TMDB → IMDB → addon → flux →
 * sonde de jouabilité. C'est la seule mesure qui répond à la question posée au
 * départ : est-ce que l'abonnement sert vraiment de source à cette application,
 * et le lien obtenu joue-t-il *maintenant* ?
 *
 *     ./gradlew :app:desktopTest --tests '*StremioEndToEndProbeTest*' \
 *         -Dmoovie.probe=1 -Dmoovie.tmdb=<clé> -Dmoovie.stremio=<url addon>
 *
 * Les deux se passent en propriété parce qu'aucun magasin de réglages n'existe
 * hors de l'application. **Aucune valeur par défaut** : une sonde qui viserait
 * un addon écrit en dur dans les sources publierait l'abonnement de qui l'a
 * écrit.
 */
class StremioEndToEndProbeTest {

    private val films = listOf(
        Triple(438631, "Dune", "2021"),
        Triple(872585, "Oppenheimer", "2023"),
        Triple(27205, "Inception", "2010"),
    )

    /** Une série, pour vérifier la forme d'identifiant `tt…:saison:épisode`. */
    private val episode = MediaRef.Episode(1396, "Breaking Bad", "2008", season = 1, episode = 1)

    @Test
    fun probeChain() = runBlocking {
        val cle = System.getProperty("moovie.tmdb")
        val addon = System.getProperty("moovie.stremio")
        if (System.getProperty("moovie.probe") == null || cle.isNullOrBlank() || addon.isNullOrBlank()) {
            println(
                "[sonde stremio] ignorée (relancer avec -Dmoovie.probe=1 " +
                    "-Dmoovie.tmdb=<clé> -Dmoovie.stremio=<url addon>)",
            )
            return@runBlocking
        }

        // Les deux valeurs sont imposées : la sonde tourne hors de
        // l'application et n'a aucun magasin de réglages à interroger.
        val provider = StremioProvider(
            ExtractorRegistry.gateway,
            addonUrlImpose = addon,
            cleTmdbImposee = cle,
        )
        val medias = films.map { (id, titre, annee) -> MediaRef.Movie(id, titre, annee) } + episode

        for (media in medias) {
            val liens = provider.sourcesFor(media)
            val parLangue = liens.groupingBy { it.language ?: "?" }.eachCount()
            println("\n════ ${media.title} — ${liens.size} flux $parLangue")

            if (liens.isEmpty()) {
                // Trois causes possibles, et elles n'appellent pas la même
                // action : pas d'IMDB chez TMDB, addon muet, ou rien en cache.
                println("   aucun flux — vérifier l'IMDB du titre et l'état du debrid")
                continue
            }

            // Les trois premiers seulement : la sonde de jouabilité coûte une
            // requête par flux, et un titre populaire en rend des dizaines.
            for (lien in liens.take(3)) {
                val flux = ExtractorRegistry.resolve(lien)
                if (flux == null) {
                    println("   ✗ non résolu  ${lien.variant}  ${lien.url.take(60)}…")
                    continue
                }
                // C'est ici que se joue le correctif du 403 sur HEAD : sans lui
                // cette ligne rendait false pour tous les liens Real-Debrid.
                val jouable = isStreamPlayable(flux)
                val marque = if (jouable) "✓" else "✗ injouable"
                println("   $marque ${lien.language} ${lien.variant} → ${flux.format}")
            }
        }
    }
}
