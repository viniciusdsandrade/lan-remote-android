package com.lanremote.android.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.ui.help.ControlHelp
import com.lanremote.android.ui.theme.ButtonBackground
import com.lanremote.android.ui.theme.SurfaceBorder
import com.lanremote.android.ui.theme.TextPrimary

/**
 * Área de toque de um controle: tocar faz a ação; segurar explica o controle sem enviar nada à
 * TV (háptico de clique longo + painel de ajuda). O TalkBack lê o título do catálogo, a
 * explicação e oferece a ação "Explicar o botão". Alvo de toque de pelo menos 48 dp.
 */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.controlHelp(
    help: ControlHelp,
    onClick: () -> Unit,
    onExplain: (ControlHelp) -> Unit,
    interactionSource: MutableInteractionSource? = null,
    enabled: Boolean = true,
    state: String? = null
): Modifier = this
    .semantics(mergeDescendants = true) {
        contentDescription = help.title
        stateDescription = listOfNotNull(state, help.detail).joinToString(". ")
    }
    .combinedClickable(
        interactionSource = interactionSource,
        indication = ripple(),
        enabled = enabled,
        role = Role.Button,
        onLongClickLabel = "Explicar o botão",
        onLongClick = { onExplain(help) },
        onClick = onClick
    )

/**
 * Botão do controle: ícone em cima e legenda curta embaixo, para reconhecer em vez de lembrar.
 * Tocar faz a ação; segurar explica o botão.
 */
@Composable
fun RemoteButton(
    help: ControlHelp,
    onClick: () -> Unit,
    onExplain: (ControlHelp) -> Unit,
    modifier: Modifier = Modifier,
    label: String = help.label,
    icon: ImageVector? = help.icon,
    backgroundColor: Color = ButtonBackground,
    contentColor: Color = TextPrimary,
    borderColor: Color = SurfaceBorder,
    shape: Shape = RoundedCornerShape(12.dp),
    iconSize: Dp = 20.dp,
    enabled: Boolean = true,
    state: String? = null,
    content: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(if (isPressed) 0.94f else 1f, label = "button_scale")
    val haptic = LocalHapticFeedback.current

    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .scale(scale)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(if (isPressed) backgroundColor.copy(alpha = 0.7f) else backgroundColor)
            .border(1.dp, if (isPressed) Color.White.copy(alpha = 0.3f) else borderColor, shape)
            .controlHelp(
                help = help,
                onClick = onClick,
                onExplain = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onExplain(it)
                },
                interactionSource = interactionSource,
                enabled = enabled,
                state = state
            ),
        contentAlignment = Alignment.Center
    ) {
        if (content != null) {
            content()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
            ) {
                if (icon != null) {
                    Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(iconSize))
                }
                if (label.isNotEmpty()) {
                    Text(
                        text = label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clearAndSetSemantics {}
                    )
                }
            }
        }
    }
}
