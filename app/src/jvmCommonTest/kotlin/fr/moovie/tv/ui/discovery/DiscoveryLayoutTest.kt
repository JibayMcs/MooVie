package fr.moovie.tv.ui.discovery

import kotlin.test.Test
import kotlin.test.assertEquals

class DiscoveryLayoutTest {

    @Test
    fun mobilePortraitKeepsThreePostersAndTheActionVisible() {
        assertEquals(4, cardsThatFit(448f, 118f, 16f))
    }

    @Test
    fun televisionUsesTheAvailableSalonWidth() {
        assertEquals(8, cardsThatFit(960f, 152f, 40f))
    }

    @Test
    fun desktopWindowGainsCardsWhenItWidens() {
        assertEquals(11, cardsThatFit(1280f, 152f, 40f))
    }

    @Test
    fun narrowWindowStillExposesTheActionAfterOnePoster() {
        assertEquals(2, cardsThatFit(220f, 118f, 16f))
    }
}
