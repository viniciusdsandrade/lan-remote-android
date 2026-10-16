package com.lanremote.android.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark

@Composable
fun NumericKeypad(
    isVisible: Boolean,
    onKeyClick: (TVKey) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder, shape)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Row 1: 1, 2, 3
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_1) }, text = "1", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_2) }, text = "2", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_3) }, text = "3", modifier = Modifier.weight(1f).height(46.dp))
            }
            // Row 2: 4, 5, 6
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_4) }, text = "4", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_5) }, text = "5", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_6) }, text = "6", modifier = Modifier.weight(1f).height(46.dp))
            }
            // Row 3: 7, 8, 9
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_7) }, text = "7", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_8) }, text = "8", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_9) }, text = "9", modifier = Modifier.weight(1f).height(46.dp))
            }
            // Row 4: -, 0, Q.View
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                RemoteButton(onClick = { onKeyClick(TVKey.DASH) }, text = "-", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.NUMBER_0) }, text = "0", modifier = Modifier.weight(1f).height(46.dp))
                RemoteButton(onClick = { onKeyClick(TVKey.PREVIOUS_CHANNEL) }, text = "Q.View", modifier = Modifier.weight(1f).height(46.dp))
            }
        }
    }
}
