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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.data.model.TVKey
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.help.Help
import com.lanremote.android.ui.help.RemoteKeys
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary

/** Teclado numérico recolhível: 1 a 9 e, na última linha, Último · 0 · Info. */
@Composable
fun NumericKeypad(
    isVisible: Boolean,
    onKey: (TVKey) -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        val shape = RoundedCornerShape(16.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(SurfaceDark)
                .border(1.dp, SurfaceBorder, shape)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            RemoteKeys.keypad.forEach { row ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { key ->
                        val help = RemoteKeys.helpFor(key) ?: Help.digit
                        if (RemoteKeys.isDigit(key)) {
                            RemoteButton(
                                help = help.copy(title = "${key.label}: ${help.title}"),
                                onClick = { onKey(key) },
                                onExplain = { onExplain(Help.digit) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            ) {
                                Text(key.label, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            }
                        } else {
                            RemoteButton(
                                help = help,
                                onClick = { onKey(key) },
                                onExplain = onExplain,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(52.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
