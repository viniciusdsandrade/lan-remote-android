package com.lanremote.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.ButtonBackgroundPressed
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import com.lanremote.android.ui.theme.TextSecondary

@Composable
fun RockerSwitch(
    title: String,
    onPlus: () -> Unit,
    onMinus: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(32.dp)

    Column(
        modifier = modifier
            .width(66.dp)
            .height(156.dp)
            .clip(shape)
            .background(SurfaceDark)
            .border(1.dp, SurfaceBorder, shape),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Plus button
        val plusInteraction = remember { MutableInteractionSource() }
        val isPlusPressed by plusInteraction.collectIsPressedAsState()
        val plusScale by animateFloatAsState(if (isPlusPressed) 0.92f else 1f, label = "plus_scale")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(if (isPlusPressed) ButtonBackgroundPressed else ButtonBackground)
                .clickable(
                    interactionSource = plusInteraction,
                    indication = null,
                    onClick = onPlus
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "$title Mais",
                tint = TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .scale(plusScale)
            )
        }

        // Title Label
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .background(SurfaceDark),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                letterSpacing = 1.sp
            )
        }

        // Minus button
        val minusInteraction = remember { MutableInteractionSource() }
        val isMinusPressed by minusInteraction.collectIsPressedAsState()
        val minusScale by animateFloatAsState(if (isMinusPressed) 0.92f else 1f, label = "minus_scale")

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(if (isMinusPressed) ButtonBackgroundPressed else ButtonBackground)
                .clickable(
                    interactionSource = minusInteraction,
                    indication = null,
                    onClick = onMinus
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Remove,
                contentDescription = "$title Menos",
                tint = TextPrimary,
                modifier = Modifier
                    .size(24.dp)
                    .scale(minusScale)
            )
        }
    }
}
