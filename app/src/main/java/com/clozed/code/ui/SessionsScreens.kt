package com.clozed.code.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.data.Screen
import com.clozed.code.ui.theme.ErrorRed
import com.clozed.code.ui.theme.TextFaint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The list of saved sessions, with open/delete and a New-session button. */
@Composable
fun SessionsScreen(vm: AppViewModel) {
    val fmt = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Sessions", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(onClick = { vm.newSession(); vm.setScreen(Screen.Chat) }) { Text("New") }
        }
        if (vm.sessionList.isEmpty()) {
            EmptyState("No sessions yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.sessionList, key = { it.id }) { s ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.openSession(s.id) }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "${s.agent ?: "build"} · ${s.model ?: ""} · ${fmt.format(Date(s.updatedAt))}",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextFaint,
                            )
                        }
                        IconButton(onClick = { vm.deleteSession(s.id) }) {
                            Icon(Icons.Filled.Delete, "Delete", tint = ErrorRed)
                        }
                    }
                }
            }
        }
    }
}
