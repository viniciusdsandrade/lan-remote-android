package com.lanremote.android.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.OkBlue
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary

@Composable
fun CircularDPad(
    onUp: () -> Unit,
    onDown: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onOk: () -> Unit,
    modifier: Modifier = Modifier
) {
    val size = 210.dp
    val buttonSize = 54.dp
    val centerSize = 74.dp

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(SurfaceDark)
            .border(2.dp, SurfaceBorder, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        // Up Button
        RemoteButton(
            onClick = onUp,
            icon = Icons.Default.ArrowUpward,
            text = "Cima",
            shapeRadius = 27.dp,
            backgroundColor = ButtonBackground,
            contentColor = TextPrimary,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = (-58).dp)
        )

        // Down Button
        RemoteButton(
            onClick = onDown,
            icon = Icons.Default.ArrowDownward,
            text = "Baixo",
            shapeRadius = 27.dp,
            backgroundColor = ButtonBackground,
            contentColor = TextPrimary,
            modifier = Modifier
                .size(buttonSize)
                .offset(y = 58.dp)
        )

        // Left Button
        RemoteButton(
            onClick = onLeft,
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            text = "Esquerda",
            shapeRadius = 27.dp,
            backgroundColor = ButtonBackground,
            contentColor = TextPrimary,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = (-58).dp)
        )

        // Right Button
        RemoteButton(
            onClick = onRight,
            icon = Icons.AutoMirrored.Filled.ArrowForward,
            text = "Direita",
            shapeRadius = 27.dp,
            backgroundColor = ButtonBackground,
            contentColor = TextPrimary,
            modifier = Modifier
                .size(buttonSize)
                .offset(x = 58.dp)
        )

        // Center OK Button
        RemoteButton(
            onClick = onOk,
            text = "OK",
            shapeRadius = 37.dp,
            backgroundColor = OkBlue,
            contentColor = TextPrimary,
            modifier = Modifier.size(centerSize)
        )
    }
}
