package com.lanremote.android.ui.help

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material.icons.filled.WifiFind
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.lanremote.android.data.model.TVKey

// O que cada controle faz, em palavras simples. Um catálogo só alimenta o painel de "segure para
// aprender", o Guia dos botões, o TalkBack e a legenda dos botões, para que nunca discordem.
// Textos portados do RemoteHelp.swift do iOS, com "iPhone" trocado por "celular".

data class ControlHelp(
    val id: String,
    /** Legenda no próprio botão (vazia nos que só mostram ícone ou cor). */
    val label: String,
    /** Título no painel, no guia e no TalkBack. */
    val title: String,
    val detail: String,
    val icon: ImageVector,
    /** Selo pequeno, ex.: os botões laterais do celular. */
    val tip: String? = null,
    /** Cor das teclas coloridas. */
    val tint: Color? = null
)

object Help {
    // Ligar e escolher a fonte
    val powerOff = ControlHelp("power", "Desligar", "Desligar a TV",
        "Coloca a TV em standby. Para ligar de novo sem levantar, use o botão Ligar do app.",
        Icons.Filled.PowerSettingsNew)
    val powerOn = ControlHelp("power-on", "Ligar", "Ligar a TV",
        "Acorda a TV Box pelo Wi-Fi e ela liga a TV pelo HDMI (SIMPLINK). A imagem volta em segundos; o controle, em até 1 minuto.",
        Icons.Filled.PowerSettingsNew)
    val input = ControlHelp("input", "Entrada", "Entrada (fonte da imagem)",
        "Troca entre TV (antena/cabo), HDMI 1, HDMI 2, AV… Cada toque passa para a próxima.",
        Icons.AutoMirrored.Filled.Input)
    val keypad = ControlHelp("keypad", "Números", "Teclado numérico",
        "Mostra os números para ir direto a um canal: toque 1 e 3 para o canal 13.",
        Icons.Filled.Dialpad)
    val digit = ControlHelp("digit", "", "Número do canal",
        "Digita o número do canal. Espere um instante depois do último dígito.",
        Icons.Filled.Tag)
    val previousChannel = ControlHelp("previous", "Último", "Último canal",
        "Volta para o canal que você estava vendo antes. Bom para alternar entre dois canais.",
        Icons.Filled.SwapHoriz)
    val info = ControlHelp("info", "Info", "Informações",
        "Mostra o nome do programa, o horário e os dados do canal atual.",
        Icons.Outlined.Info)

    // Navegação
    val quickMenu = ControlHelp("qmenu", "Ajuste rápido", "Ajuste rápido (Q.Menu)",
        "Atalhos sem sair do que está vendo: modo de imagem, modo de som, formato da tela e timer para desligar.",
        Icons.Filled.Tune)
    val home = ControlHelp("home", "Início", "Início (Smart TV)",
        "Abre a tela inicial da LG, com os aplicativos, as entradas e os ajustes.",
        Icons.Filled.Home)
    val menu = ControlHelp("menu", "Configurações", "Configurações da TV",
        "Menu completo: imagem, som, canais, rede, horário e opções como o SIMPLINK.",
        Icons.Outlined.Settings)
    val arrows = ControlHelp("arrows", "", "Setas",
        "Movem a seleção nos menus e nos aplicativos da TV.",
        Icons.Filled.OpenWith)
    val ok = ControlHelp("ok", "OK", "OK (confirmar)",
        "Confirma o item selecionado no menu ou no aplicativo.",
        Icons.Outlined.CheckCircle)
    val back = ControlHelp("back", "Voltar", "Voltar",
        "Volta um passo: fecha a tela ou o menu que está aberto agora.",
        Icons.AutoMirrored.Filled.ArrowBack)
    val exit = ControlHelp("exit", "Sair", "Sair dos menus",
        "Fecha todos os menus de uma vez e volta direto para o que você estava assistindo.",
        Icons.Filled.Close)
    val guide = ControlHelp("epg", "Programação", "Guia de programação",
        "Mostra o que está passando agora e o que vem depois em cada canal da TV aberta.",
        Icons.Filled.CalendarMonth)

    // Volume e canal
    val volumeUp = ControlHelp("vol+", "", "Aumentar volume",
        "Aumenta o volume da TV um ponto por toque.", Icons.AutoMirrored.Filled.VolumeUp, tip = "Botão lateral +")
    val volumeDown = ControlHelp("vol-", "", "Diminuir volume",
        "Diminui o volume da TV um ponto por toque.", Icons.AutoMirrored.Filled.VolumeDown, tip = "Botão lateral −")
    val volume = ControlHelp("vol", "Volume", "Volume + / −",
        "Aumenta ou diminui o volume da TV, um ponto por toque. Os botões laterais do celular também funcionam.",
        Icons.AutoMirrored.Filled.VolumeUp, tip = "Botões laterais")
    val mute = ControlHelp("mute", "Mudo", "Mudo",
        "Corta o som na hora. Toque de novo para o som voltar no mesmo volume.",
        Icons.AutoMirrored.Filled.VolumeOff)
    val liveTV = ControlHelp("live", "TV ao vivo", "TV ao vivo",
        "Sai do aplicativo ou do menu e volta para os canais da antena ou do cabo.",
        Icons.Filled.LiveTv)
    val channelUp = ControlHelp("ch+", "", "Próximo canal",
        "Passa para o próximo canal da lista.", Icons.Filled.KeyboardArrowUp)
    val channelDown = ControlHelp("ch-", "", "Canal anterior",
        "Volta para o canal anterior da lista.", Icons.Filled.KeyboardArrowDown)
    val channel = ControlHelp("ch", "Canal", "Canal ˄ / ˅",
        "Passa para o próximo canal da lista ou volta para o anterior.", Icons.Filled.FormatListNumbered)

    // Recursos da TV
    val apps = ControlHelp("apps", "Apps", "Aplicativos",
        "Abre a lista de aplicativos da Smart TV, como YouTube e o navegador.",
        Icons.Filled.Apps)
    val aspect = ControlHelp("ratio", "Formato", "Formato da imagem",
        "Muda entre 16:9, 4:3, zoom e ajustar à tela. Use quando a imagem estiver esticada ou com faixas pretas.",
        Icons.Filled.AspectRatio)
    val threeD = ControlHelp("3d", "3D", "Modo 3D",
        "Liga ou desliga o modo 3D. Só faz efeito em TVs e vídeos com 3D.",
        Icons.Filled.ViewInAr)
    val simplink = ControlHelp("simplink", "SIMPLINK", "SIMPLINK (HDMI-CEC)",
        "Abre o menu dos aparelhos ligados no HDMI (TV Box, PS5). Deixe ativado: é por ele que o app liga a TV.",
        Icons.Filled.Link)

    // Reprodução
    val rewind = ControlHelp("rew", "", "Retroceder",
        "Volta o vídeo ou a música que está tocando na TV (pen drive, apps da LG).", Icons.Filled.FastRewind)
    val play = ControlHelp("play", "", "Reproduzir",
        "Começa ou retoma o vídeo ou a música que está tocando na TV.", Icons.Filled.PlayArrow)
    val pause = ControlHelp("pause", "", "Pausar",
        "Pausa o vídeo ou a música. Toque em Reproduzir para continuar.", Icons.Filled.Pause)
    val stop = ControlHelp("stop", "", "Parar",
        "Encerra a reprodução e volta para a lista de arquivos.", Icons.Filled.Stop)
    val forward = ControlHelp("ff", "", "Avançar",
        "Adianta o vídeo ou a música que está tocando na TV.", Icons.Filled.FastForward)
    val media = ControlHelp("media", "", "Reprodução",
        "Retroceder, reproduzir, pausar, parar e avançar: para vídeos e músicas tocando na TV (pen drive, apps da LG).",
        Icons.Filled.SlowMotionVideo)

    // Botões coloridos: o significado vem do que estiver na tela.
    private const val COLOR_DETAIL =
        "Faz o que a TV mostrar na parte de baixo da tela para essa cor (no guia, nos arquivos do pen drive)."
    val red = ControlHelp("red", "", "Botão vermelho", COLOR_DETAIL, Icons.Filled.Circle, tint = Color(0xFFD93333))
    val green = ControlHelp("green", "", "Botão verde", COLOR_DETAIL, Icons.Filled.Circle, tint = Color(0xFF33BF4D))
    val yellow = ControlHelp("yellow", "", "Botão amarelo", COLOR_DETAIL, Icons.Filled.Circle, tint = Color(0xFFF2BF1A))
    val blue = ControlHelp("blue", "", "Botão azul", COLOR_DETAIL, Icons.Filled.Circle, tint = Color(0xFF3373E6))
    val colors = ControlHelp("colors", "", "Botões coloridos",
        "Mudam conforme a tela: quando servem para algo, a TV mostra embaixo o que cada cor faz (no guia, nos arquivos do pen drive).",
        Icons.Filled.Circle)

    // O app
    val discovery = ControlHelp("discovery", "", "Buscar TV na rede",
        "Procura a TV LG no Wi-Fi e preenche o IP sozinho.", Icons.Filled.WifiFind)
    val guideSheet = ControlHelp("guide-sheet", "", "Guia dos botões",
        "Abre a lista com o que cada botão do controle faz.", Icons.AutoMirrored.Outlined.HelpOutline)
    val settings = ControlHelp("settings", "", "Ajustes do app",
        "IP da TV, pareamento e a TV Box que liga a TV pelo HDMI.", Icons.Filled.Settings)
    val volumeStatus = ControlHelp("vol-status", "", "Volume atual",
        "Volume que a TV informou agora (ou Mudo).", Icons.AutoMirrored.Filled.VolumeUp)
    val inputStatus = ControlHelp("input-status", "", "Entrada atual",
        "Fonte que a TV está mostrando: TV, HDMI, AV…", Icons.Filled.Tv)
}

/** Uma seção do Guia dos botões, na ordem da tela. */
data class GuideSection(val title: String, val items: List<ControlHelp>) {
    companion object {
        val all: List<GuideSection> = listOf(
            GuideSection("Ligar e escolher a fonte",
                listOf(Help.powerOn, Help.powerOff, Help.input, Help.keypad, Help.previousChannel, Help.info)),
            GuideSection("Navegação",
                listOf(Help.quickMenu, Help.home, Help.menu, Help.arrows, Help.ok, Help.back, Help.exit, Help.guide)),
            GuideSection("Volume e canal", listOf(Help.volume, Help.mute, Help.liveTV, Help.channel)),
            GuideSection("Recursos da TV", listOf(Help.apps, Help.aspect, Help.threeD, Help.simplink)),
            GuideSection("Reprodução", listOf(Help.media)),
            GuideSection("Botões coloridos", listOf(Help.colors)),
            GuideSection("O app", listOf(Help.discovery, Help.guideSheet, Help.settings))
        )
    }
}

/** As teclas que a tela manda, grupo a grupo, e a ajuda de cada uma. */
object RemoteKeys {
    val keypad: List<List<TVKey>> = listOf(
        listOf(TVKey.NUMBER_1, TVKey.NUMBER_2, TVKey.NUMBER_3),
        listOf(TVKey.NUMBER_4, TVKey.NUMBER_5, TVKey.NUMBER_6),
        listOf(TVKey.NUMBER_7, TVKey.NUMBER_8, TVKey.NUMBER_9),
        listOf(TVKey.PREVIOUS_CHANNEL, TVKey.NUMBER_0, TVKey.INFO)
    )
    val navigationTop = listOf(TVKey.QUICK_MENU, TVKey.HOME, TVKey.MENU)
    val arrows = listOf(TVKey.UP, TVKey.DOWN, TVKey.LEFT, TVKey.RIGHT, TVKey.OK)
    val navigationBottom = listOf(TVKey.BACK, TVKey.EXIT, TVKey.EPG)
    val volumeAndChannel = listOf(TVKey.VOLUME_UP, TVKey.VOLUME_DOWN, TVKey.MUTE, TVKey.LIVE_TV, TVKey.CHANNEL_UP, TVKey.CHANNEL_DOWN)
    val features = listOf(TVKey.APPS, TVKey.ASPECT_RATIO, TVKey.KEY_3D, TVKey.SIMPLINK)
    val media = listOf(TVKey.REWIND, TVKey.PLAY, TVKey.PAUSE, TVKey.STOP, TVKey.FAST_FORWARD)
    val colors = listOf(TVKey.RED, TVKey.GREEN, TVKey.YELLOW, TVKey.BLUE)

    /** Todas as teclas com botão na tela (POWER fica no botão Desligar, com confirmação). */
    val all: List<TVKey> = listOf(TVKey.POWER, TVKey.INPUT) + keypad.flatten() + navigationTop + arrows +
        navigationBottom + volumeAndChannel + features + media + colors

    private val digits = setOf(
        TVKey.NUMBER_0, TVKey.NUMBER_1, TVKey.NUMBER_2, TVKey.NUMBER_3, TVKey.NUMBER_4,
        TVKey.NUMBER_5, TVKey.NUMBER_6, TVKey.NUMBER_7, TVKey.NUMBER_8, TVKey.NUMBER_9
    )

    fun isDigit(key: TVKey): Boolean = key in digits

    /** A explicação de cada tecla que tem botão; nulo para teclas sem botão na tela. */
    fun helpFor(key: TVKey): ControlHelp? = when (key) {
        TVKey.POWER -> Help.powerOff
        TVKey.INPUT -> Help.input
        TVKey.PREVIOUS_CHANNEL -> Help.previousChannel
        TVKey.INFO -> Help.info
        TVKey.QUICK_MENU -> Help.quickMenu
        TVKey.HOME -> Help.home
        TVKey.MENU -> Help.menu
        TVKey.UP, TVKey.DOWN, TVKey.LEFT, TVKey.RIGHT -> Help.arrows
        TVKey.OK -> Help.ok
        TVKey.BACK -> Help.back
        TVKey.EXIT -> Help.exit
        TVKey.EPG -> Help.guide
        TVKey.VOLUME_UP -> Help.volumeUp
        TVKey.VOLUME_DOWN -> Help.volumeDown
        TVKey.MUTE -> Help.mute
        TVKey.LIVE_TV -> Help.liveTV
        TVKey.CHANNEL_UP -> Help.channelUp
        TVKey.CHANNEL_DOWN -> Help.channelDown
        TVKey.APPS -> Help.apps
        TVKey.ASPECT_RATIO -> Help.aspect
        TVKey.KEY_3D -> Help.threeD
        TVKey.SIMPLINK -> Help.simplink
        TVKey.REWIND -> Help.rewind
        TVKey.PLAY -> Help.play
        TVKey.PAUSE -> Help.pause
        TVKey.STOP -> Help.stop
        TVKey.FAST_FORWARD -> Help.forward
        TVKey.RED -> Help.red
        TVKey.GREEN -> Help.green
        TVKey.YELLOW -> Help.yellow
        TVKey.BLUE -> Help.blue
        else -> if (isDigit(key)) Help.digit else null
    }
}
