package fr.moovie.tv.data.sources

import fr.moovie.tv.core.sources.port.HttpGateway
import fr.moovie.tv.data.net.AppDns
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Client HTTP brut des cibles JVM.
 *
 * Portait le nom `ExtractorRegistry.http` avant que le registre ne devienne
 * commun : un `OkHttpClient` ne peut pas vivre dans un source set que
 * Kotlin/Native compile aussi. Le déplacement ne change ni sa configuration ni
 * son cycle de vie — c'est le même client, au même réglage, sous un autre nom.
 *
 * Reste exposé pour le relais local et le client de télécommande, qui
 * transfèrent de vrais médias en flux et ne se ramènent pas au port — celui-ci
 * matérialise la réponse en mémoire. Les providers, eux, sont tous passés au
 * port et vivent désormais en commun.
 */
object ClientExtraction {
    val http: OkHttpClient = OkHttpClient.Builder()
        // DoH : les domaines sources sont bloqués par DNS chez les FAI.
        .dns(AppDns)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
}

/**
 * **Plafonnée dans le temps**, contrairement au client brut.
 *
 * `readTimeout` ne protège que des silences : tant que des octets arrivent, il
 * ne se déclenche jamais. Une requête documentaire lancée par erreur sur un
 * média (une page HTML attendue, un film de deux gigaoctets servi) tient alors
 * son fil indéfiniment — et comme le corps est lu par un appel bloquant,
 * annuler la coroutine ne l'interrompt pas. Mesuré : quelques appels de ce
 * genre suffisent à saturer le pool de coroutines partagé, et tout ce qui
 * l'utilise gèle derrière, DataStore compris.
 *
 * `callTimeout` borne l'appel entier, lecture du corps incluse. Trente secondes
 * sont très larges pour une page ou un manifeste, et laissent le client brut —
 * celui des segments et des téléchargements, qui transfèrent de vrais médias —
 * sans plafond.
 */
actual val passerelleSources: HttpGateway = OkHttpGateway(
    ClientExtraction.http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build(),
)
