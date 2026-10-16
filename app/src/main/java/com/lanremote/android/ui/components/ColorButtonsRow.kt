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

/** Vermelho, verde, amarelo e azul: o que cada um faz aparece na TV; segurar explica. */
@Composable
fun ColorButtonsRow(
    onKey: (TVKey) -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RemoteKeys.colors.forEach { key ->
            val help = RemoteKeys.helpFor(key) ?: return@forEach
            RemoteButton(
                help = help,
                onClick = { onKey(key) },
                onExplain = onExplain,
                label = "",
                icon = null,
                backgroundColor = help.tint ?: Color.Gray,
                borderColor = Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
            )
        }
    }
}
