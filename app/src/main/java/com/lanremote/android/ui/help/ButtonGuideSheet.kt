package com.lanremote.android.ui.help

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lanremote.android.ui.theme.SurfaceDark
import com.lanremote.android.ui.theme.TextPrimary
import kotlinx.coroutines.launch

private val SectionTitle = Color(0xFF8A8A94)
private val DetailText = Color(0xFFA6A6AB)

/** Guia dos botões: o que cada controle faz, na ordem da tela. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ButtonGuideSheet(onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SurfaceDark
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Guia dos botões",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                modifier = Modifier
                    .weight(1f)
                    .semantics { heading() }
            )
            TextButton(
                onClick = { scope.launch { sheetState.hide() }.invokeOnCompletion { onDismiss() } },
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Concluir", color = Color(0xFF73B8FF), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            GuideSection.all.forEach { section ->
                item(key = section.title) {
                    Text(
                        section.title.uppercase(),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.8.sp,
                        color = SectionTitle,
                        modifier = Modifier
                            .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 6.dp)
                            .semantics { heading() }
                    )
                }
                items(section.items, key = { it.id }) { help -> GuideRow(help) }
            }
        }
    }
}

@Composable
private fun GuideRow(help: ControlHelp) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {}
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        if (help.id == Help.colors.id) ColorKeysIcon() else HelpIcon(help, size = 32.dp)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(help.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                help.tip?.let { TipBadge(it) }
            }
            Text(help.detail, fontSize = 12.sp, lineHeight = 16.sp, color = DetailText)
        }
    }
}

/** As quatro cores num quadrado, para a linha dos botões coloridos. */
@Composable
private fun ColorKeysIcon() {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.5.dp))
            .background(Color(0xFF38383D)),
        contentAlignment = Alignment.Center
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Dot(Help.red.tint); Dot(Help.green.tint) }
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) { Dot(Help.yellow.tint); Dot(Help.blue.tint) }
        }
    }
}

@Composable
private fun Dot(color: Color?) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color ?: Color.Gray)
    )
}
