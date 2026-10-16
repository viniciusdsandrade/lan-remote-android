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
import com.lanremote.android.ui.theme.ColorBlue
import com.lanremote.android.ui.theme.ColorGreen
import com.lanremote.android.ui.theme.ColorRed
import com.lanremote.android.ui.theme.ColorYellow

@Composable
fun ColorButtonsRow(
    onKeyClick: (TVKey) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RemoteButton(
            onClick = { onKeyClick(TVKey.RED) },
            text = "●",
            backgroundColor = ColorRed,
            contentColor = Color.White,
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(38.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.GREEN) },
            text = "●",
            backgroundColor = ColorGreen,
            contentColor = Color.White,
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(38.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.YELLOW) },
            text = "●",
            backgroundColor = ColorYellow,
            contentColor = Color.White,
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(38.dp)
        )
        RemoteButton(
            onClick = { onKeyClick(TVKey.BLUE) },
            text = "●",
            backgroundColor = ColorBlue,
            contentColor = Color.White,
            shapeRadius = 12.dp,
            modifier = Modifier.weight(1f).height(38.dp)
        )
    }
}
