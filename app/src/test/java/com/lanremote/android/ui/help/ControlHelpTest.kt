package com.lanremote.android.ui.help

import com.lanremote.android.ui.components.tvDisplayName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlHelpTest {

    @Test
    fun guideCoversEveryHelpOnceWithCompleteText() {
        val items = GuideSection.all.flatMap { it.items }
        assertFalse(items.isEmpty())
        assertEquals("cada controle aparece uma vez no guia", items.size, items.map { it.id }.toSet().size)
        for (item in items) {
            assertTrue(item.id, item.title.isNotBlank())
            assertTrue(item.id, item.detail.isNotBlank())
        }
        assertEquals(
            listOf("Ligar e escolher a fonte", "Navegação", "Volume e canal", "Recursos da TV", "Reprodução", "Botões coloridos", "O app"),
            GuideSection.all.map { it.title }
        )
    }

    @Test
    fun buttonsWithACaptionHaveOne() {
        for (help in listOf(
            Help.powerOn, Help.powerOff, Help.input, Help.keypad, Help.mute, Help.liveTV, Help.quickMenu,
            Help.home, Help.menu, Help.back, Help.exit, Help.guide, Help.apps, Help.aspect, Help.threeD,
            Help.simplink, Help.previousChannel, Help.info
        )) {
            assertTrue("${help.id} aparece num botão e precisa de legenda", help.label.isNotBlank())
        }
        // Reprodução e cores ficam só com ícone ou cor: a explicação vem ao segurar.
        assertTrue((RemoteKeys.media + RemoteKeys.colors).all { RemoteKeys.helpFor(it)!!.label.isEmpty() })
    }

    @Test
    fun everyKeyOnScreenHasHelp() {
        for (key in RemoteKeys.all) {
            val help = RemoteKeys.helpFor(key)
            assertNotNull("${key.name} está na tela e precisa de explicação", help)
            assertTrue(key.name, help!!.title.isNotBlank() && help.detail.isNotBlank())
        }
        assertEquals("sem tecla repetida na tela", RemoteKeys.all.size, RemoteKeys.all.toSet().size)
    }

    @Test
    fun catalogIdsAreUnique() {
        val all = listOf(
            Help.powerOff, Help.powerOn, Help.input, Help.keypad, Help.digit, Help.previousChannel, Help.info,
            Help.quickMenu, Help.home, Help.menu, Help.arrows, Help.ok, Help.back, Help.exit, Help.guide,
            Help.volumeUp, Help.volumeDown, Help.volume, Help.mute, Help.liveTV, Help.channelUp, Help.channelDown,
            Help.channel, Help.apps, Help.aspect, Help.threeD, Help.simplink, Help.rewind, Help.play, Help.pause,
            Help.stop, Help.forward, Help.media, Help.red, Help.green, Help.yellow, Help.blue, Help.colors,
            Help.discovery, Help.guideSheet, Help.settings, Help.volumeStatus, Help.inputStatus
        )
        assertEquals(all.size, all.map { it.id }.toSet().size)
        assertTrue("as teclas coloridas têm a cor", listOf(Help.red, Help.green, Help.yellow, Help.blue).all { it.tint != null })
    }

    @Test
    fun textsSpeakOfTheCellphone() {
        val texts = GuideSection.all.flatMap { it.items } + Help.volumeUp + Help.volumeDown
        assertFalse(texts.any { "iPhone" in it.detail || "iPhone" in it.title })
        assertEquals("Botões laterais", Help.volume.tip)
        assertTrue(Help.volume.detail.contains("celular"))
    }

    @Test
    fun headerName() {
        assertEquals("TV LG 47LN5700", tvDisplayName("47LN5700-SA_11"))
        assertEquals("TV LG", tvDisplayName("LG Smart TV (NetCast)"))
        assertEquals("TV LG", tvDisplayName(""))
    }
}
