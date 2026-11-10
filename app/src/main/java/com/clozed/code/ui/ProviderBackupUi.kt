package com.clozed.code.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.ui.theme.ErrorRed
import com.clozed.code.ui.theme.WarnAmber

/** Export/import the provider + key backup as plain-text JSON. */
@Composable
fun ProviderBackupScreen(vm: AppViewModel) {
    var text by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>() }

    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Providers & API keys", style = MaterialTheme.typography.titleLarge)
        Text(
            "The backup includes your API keys in plain text.",
            color = WarnAmber,
            style = MaterialTheme.typography.bodySmall,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { text = vm.exportBackup(); message = "Backup created." }) { Text("Export") }
            OutlinedButton(onClick = {
                message = runCatching { "Imported ${vm.importBackup(text)} provider(s)." }
                    .getOrElse { "This is not a valid backup." }
            }) { Text("Import") }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Backup JSON") },
            textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth().height(320.dp),
        )
        message?.let {
            Text(it, color = if (it.startsWith("Imported")) com.clozed.code.ui.theme.Brand else ErrorRed, style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** A small plain-text editor dialog for a single file. */
@Composable
fun CustomEditDialog(
    path: String,
    initial: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit ${path.substringAfterLast('/')}") },
        text = {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(draft); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
