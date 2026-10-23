package fr.moovie.tv.data.net

import java.net.InetAddress
import kotlin.test.Test
import kotlin.test.assertEquals

class Ipv4FirstTest {

    private fun ip(literal: String) = InetAddress.getByName(literal)

    @Test
    fun `l'IPv4 passe devant une IPv6 arrivée la première`() {
        val v6 = ip("2606:4700:20::ac43:4951")
        val v4a = ip("104.26.11.203")
        val v4b = ip("172.67.71.209")

        assertEquals(listOf(v4a, v4b, v6), ipv4First(listOf(v6, v4a, v4b)))
    }

    @Test
    fun `une liste sans IPv4 reste jouable`() {
        val v6a = ip("2606:4700:20::ac43:4951")
        val v6b = ip("2606:4700:20::681a:bcb")

        assertEquals(listOf(v6a, v6b), ipv4First(listOf(v6a, v6b)))
    }
}
