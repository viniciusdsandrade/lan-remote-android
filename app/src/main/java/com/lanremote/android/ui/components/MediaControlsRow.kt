package com.lanremote.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.help.RemoteKeys

private val MediaBackground = Color(0xFF26262C)
private val PlayGreen = Color(0xFF26733F)

/** Retroceder, reproduzir, pausar, parar e avançar: só ícone; segurar explica cada um. */
@Composable
fun MediaControlsRow(
    onKey: (TVKey) -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RemoteKeys.media.forEach { key ->
            val help = RemoteKeys.helpFor(key) ?: return@forEach
            RemoteButton(
                help = help,
                onClick = { onKey(key) },
                onExplain = onExplain,
                label = "",
                backgroundColor = if (key == TVKey.PLAY) PlayGreen else MediaBackground,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
        }
    }
}
