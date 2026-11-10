package com.clozed.code.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.data.Screen
import com.clozed.code.engine.Workspace
import com.clozed.code.ui.theme.InfoBlue
import com.clozed.code.ui.theme.TextFaint
import java.io.File

/** A simple directory browser rooted at shared storage. */
@Composable
fun FolderPickerScreen(vm: AppViewModel) {
    val start = remember { File("/storage/emulated/0") }
    var dir by remember { mutableStateOf(WorkspaceRootOr(start)) }

    val entries = remember(dir) {
        dir.listFiles()?.filter { it.isDirectory && !it.name.startsWith(".") }?.sortedBy { it.name.lowercase() } ?: emptyList()
    }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Choose project folder", style = MaterialTheme.typography.titleLarge)
        Text(dir.path, style = MaterialTheme.typography.bodySmall, color = InfoBlue)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
            OutlinedButton(onClick = { dir.parentFile?.let { dir = it } }, enabled = dir.parentFile != null) { Text("Up") }
            Button(onClick = {
                vm.setProject(dir.path)
                vm.setScreen(Screen.Chat)
            }) { Text("Use this folder") }
        }
        LazyColumn(Modifier.fillMaxWidth()) {
            items(entries, key = { it.path }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { dir = f }.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(f.name, style = MaterialTheme.typography.bodyLarge)
                    Text("  ${f.listFiles()?.count { it.isDirectory } ?: 0} folders", style = MaterialTheme.typography.bodySmall, color = TextFaint)
                }
            }
        }
    }
}

private fun WorkspaceRootOr(fallback: File): File =
    Workspace.root?.takeIf { it.isDirectory } ?: fallback
