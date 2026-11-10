package com.clozed.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.clozed.code.ui.theme.Surface3
import com.clozed.code.ui.theme.TextFaint
import com.clozed.code.ui.theme.TextLo

/** A reusable bottom sheet that lets the user pick one item from a list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickerSheet(
    title: String,
    items: List<T>,
    label: (T) -> String,
    subtitle: (T) -> String? = { null },
    onPick: (T) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            if (items.isEmpty()) {
                Text("No entries.", color = TextFaint, modifier = Modifier.padding(vertical = 12.dp))
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                items(items) { item ->
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onPick(item) }
                            .padding(vertical = 10.dp),
                    ) {
                        Text(label(item), style = MaterialTheme.typography.bodyLarge)
                        subtitle(item)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextLo) }
                    }
                }
            }
        }
    }
}

/** A settings row with a title, optional subtitle and a trailing tap action. */
@Composable
fun RowItem(title: String, subtitle: String? = null, value: String? = null, onClick: (() -> Unit)? = null) {
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .background(Surface3, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(Modifier.fillMaxWidth(0.7f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextLo) }
        }
        value?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextFaint) }
    }
}

/** A thin colored banner used for transient status ("Switching key..."). */
@Composable
fun Banner(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    )
}
