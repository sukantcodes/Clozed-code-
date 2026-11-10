package com.clozed.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.clozed.code.ui.theme.ErrorRed
import com.clozed.code.ui.theme.InfoBlue
import com.clozed.code.ui.theme.Mono
import com.clozed.code.ui.theme.Surface2
import com.clozed.code.ui.theme.Surface3
import com.clozed.code.ui.theme.TextFaint
import com.clozed.code.ui.theme.TextLo
import com.clozed.code.ui.theme.ToolSlate
import com.clozed.code.ui.theme.WarnAmber

/** A small label used for statuses ("thinking", provider names, counts). */
@Composable
fun Pill(text: String, color: Color = InfoBlue) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Surface3)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/** Section heading used throughout the settings screens. */
@Composable
fun SectionHeader(title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextLo)
    }
}

/** A card that frames a tool invocation and its output. */
@Composable
fun ToolCard(name: String, input: String, output: String, running: Boolean, ok: Boolean) {
    val accent = when {
        running -> WarnAmber
        !ok -> ErrorRed
        else -> ToolSlate
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Surface2)
            .border(1.dp, Surface3, RoundedCornerShape(10.dp))
            .padding(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(name, fontWeight = FontWeight.SemiBold, color = accent, fontFamily = Mono, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.width(8.dp))
            Pill(if (running) "running" else if (ok) "done" else "failed", accent)
        }
        if (input.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(input, fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = TextFaint, maxLines = 6)
        }
        if (output.isNotBlank()) {
            Spacer(Modifier.size(6.dp))
            Text(output.take(4000), fontFamily = Mono, style = MaterialTheme.typography.bodySmall, color = TextLo)
        }
    }
}

/** Renders a permission request with its buttons. */
@Composable
fun PermRow(
    title: String,
    detail: String,
    options: List<String>,
    onChoose: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface2)
            .padding(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = WarnAmber)
        if (detail.isNotBlank()) Text(detail, style = MaterialTheme.typography.bodySmall, color = TextLo)
        Spacer(Modifier.size(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            for (o in options) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Surface3)
                        .clickable { onChoose(o) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) { Text(o, style = MaterialTheme.typography.bodyMedium) }
            }
        }
    }
}

@Composable
fun EmptyState(text: String) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text, color = TextFaint, style = MaterialTheme.typography.bodyMedium)
    }
}
