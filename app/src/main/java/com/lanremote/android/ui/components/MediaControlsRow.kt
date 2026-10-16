package com.lanremote.android.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lanremote.android.data.model.TVKey

@Composable
fun MediaControlsRow(
    onKeyClick: (TVKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RemoteButton(
            onClick = { onKeyClick(TVKey.REWIND) },
            icon = Icons.Default.FastRewind,
            text = "Retroceder",
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(42.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.PLAY) },
            icon = Icons.Default.PlayArrow,
            text = "Reproduzir",
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(42.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.PAUSE) },
            icon = Icons.Default.Pause,
            text = "Pausar",
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(42.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.STOP) },
            icon = Icons.Default.Stop,
            text = "Parar",
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(42.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.FAST_FORWARD) },
            icon = Icons.Default.FastForward,
            text = "Avançar",
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(42.dp)
        )
    }
}
