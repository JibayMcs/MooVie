package fr.moovie.tv.data.net

import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Le disjoncteur DoH ne s'ouvre que sur une panne du résolveur, jamais sur une
 * réponse négative : OkHttp enveloppe les deux dans la même exception.
 */
class AppDnsTest {

    @Test
    fun `un NXDOMAIN est une reponse, pas une panne`() {
        assertFalse(AppDns.panneReseau(UnknownHostException("inconnu.example: NXDOMAIN")))
    }

    @Test
    fun `un delai de connexion au resolveur est une panne`() {
        val erreur = UnknownHostException("torrentio.strem.fun").apply {
            initCause(SocketTimeoutException("connect timed out"))
        }
        assertTrue(AppDns.panneReseau(erreur))
    }

    @Test
    fun `une connexion refusee hors enveloppe est aussi une panne`() {
        assertTrue(AppDns.panneReseau(ConnectException("Connection refused")))
    }

    @Test
    fun `une erreur de programmation n ouvre pas le disjoncteur`() {
        assertFalse(AppDns.panneReseau(IllegalStateException("bug")))
    }
}
