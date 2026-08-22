package fr.moovie.tv.data.cast

import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlin.test.Test

/**
 * Épreuve : que dit un vrai Chromecast de son volume, et obéit-il ?
 *
 * ```
 * ./gradlew :app:desktopTest --tests '*CastVolumeProbeTest' \
 *   -Dmoovie.probe=1 -Dmoovie.cast.host=192.168.1.92
 * ```
 *
 * ## Elle ne touche à rien par défaut
 *
 * `GET_STATUS` est la seule requête du protocole qui ne change rien : elle
 * n'allume pas l'écran, n'interrompt pas ce qui joue et ne prend la main sur
 * rien. La sonde s'y tient — c'est ce qui permet de la lancer sur le téléviseur
 * du salon un soir de film.
 *
 * Ce qu'on cherche à savoir tient en un mot : **`controlType`**. C'est lui qui
 * décide si l'écran doit afficher un curseur ou pas, et il ne se devine pas
 * depuis un poste de développement — une sortie HDMI dont le téléviseur garde
 * la main répond `fixed`, et un curseur bougerait alors sous le doigt sans rien
 * changer au son.
 *
 * ## L'écriture, seulement si on la demande
 *
 * `-Dmoovie.cast.volume=1` fait la vérification complète : relever, régler,
 * relire, **puis remettre le niveau d'origine**. Le son du salon bouge donc
 * pendant deux secondes. C'est la seule preuve qui vaille pour `SET_VOLUME`,
 * mais elle ne s'impose pas d'elle-même.
 */
class CastVolumeProbeTest {

    private val actif = System.getProperty("moovie.probe") == "1"
    private val hote: String? = System.getProperty("moovie.cast.host")
    private val ecrit = System.getProperty("moovie.cast.volume") == "1"

    @Test
    fun `un Chromecast annonce son volume et le laisse regler`() {
        if (!actif || hote.isNullOrBlank()) {
            println("[sonde] inactive — -Dmoovie.probe=1 -Dmoovie.cast.host=<ip>")
            return
        }

        val client = CastClient(hote)
        runBlocking {
            if (!client.connect()) {
                println("[sonde] ⚠️ pas de connexion à $hote")
                return@runBlocking
            }
            // `connect` demande déjà l'état ; on laisse la réponse arriver.
            delay(2_000)

            val depart = client.volume.value
            println("[sonde] récepteur   : $hote")
            println("[sonde] niveau      : ${(depart.level * 100).toInt()} %")
            println("[sonde] coupé       : ${depart.muted}")
            println("[sonde] réglable    : ${depart.reglable}")
            println("[sonde] pas déclaré : ${depart.step}")
            println(
                if (depart.reglable) {
                    "[sonde] ✅ l'écran affichera un curseur, et les touches physiques agiront"
                } else {
                    "[sonde] ℹ️ volume tenu par le téléviseur — ni curseur ni détournement des touches"
                },
            )

            if (!ecrit) {
                println("[sonde] écriture non demandée — -Dmoovie.cast.volume=1 pour l'éprouver")
                client.close()
                return@runBlocking
            }
            if (!depart.reglable) {
                println("[sonde] écriture sautée : l'appareil ne se laisse pas régler")
                client.close()
                return@runBlocking
            }

            // Une cible franchement différente du départ, pour que l'écart ne
            // puisse pas passer pour du bruit d'arrondi.
            val cible = if (depart.level > 0.5) 0.2 else 0.8
            println("[sonde] SET_VOLUME  : ${(cible * 100).toInt()} %")
            client.setVolume(cible)
            delay(2_000)

            val relu = client.volume.value
            println("[sonde] relu        : ${(relu.level * 100).toInt()} %")
            println(
                if (kotlin.math.abs(relu.level - cible) < 0.05) {
                    "[sonde] ✅ le récepteur a obéi, et l'annonce"
                } else {
                    "[sonde] ⚠️ le niveau relu ne suit pas la consigne"
                },
            )

            println("[sonde] restitution : ${(depart.level * 100).toInt()} %")
            client.setVolume(depart.level)
            if (depart.muted) client.setMuted(true)
            delay(1_000)
            client.close()
        }
    }
}
