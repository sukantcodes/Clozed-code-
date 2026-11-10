package com.clozed.code.ui

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.engine.McpServer
import com.clozed.code.engine.Skills
import com.clozed.code.ui.theme.Brand
import com.clozed.code.ui.theme.TextFaint

/** Skills bundled in assets and discovered from the project. */
@Composable
fun SkillsScreen(vm: AppViewModel) {
    var open by remember { mutableStateOf<Skills.Skill?>() }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Skills", style = MaterialTheme.typography.titleLarge)
        if (vm.skills.isEmpty()) {
            EmptyState("No skills yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(vm.skills, key = { it.id }) { s ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(s.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            OutlinedButton(onClick = { open = s }) { Text("View") }
                        }
                        Text(s.description, style = MaterialTheme.typography.bodySmall, color = TextFaint)
                    }
                }
            }
        }
    }
    open?.let {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(it.name, style = MaterialTheme.typography.titleMedium, color = Brand)
            Text(it.body.take(4000), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { open = null }) { Text("Close") }
        }
    }
}

/** MCP: connect servers and list their tools. */
@Composable
fun McpScreen(vm: AppViewModel) {
    var draft by remember { mutableStateOf(McpServer(id = "srv", name = "", url = "")) }
    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("MCP servers", style = MaterialTheme.typography.titleLarge)
        if (vm.mcpServers.isEmpty()) {
            Text("No servers yet.", color = TextFaint, style = MaterialTheme.typography.bodySmall)
        }
        LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(vm.mcpServers, key = { it.id }) { s ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name.ifBlank { s.id }, style = MaterialTheme.typography.bodyLarge)
                        Text(s.url, style = MaterialTheme.typography.bodySmall, color = TextFaint)
                    }
                    OutlinedButton(onClick = { vm.toggleMcp(s) }) { Text(if (s.enabled) "Disable" else "Enable") }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = draft.name, onValueChange = { draft = draft.copy(name = it, id = it.ifBlank { "srv" }) },
                label = { Text("Name (e.g. context7)") }, modifier = Modifier.weight(0.4f),
            )
            OutlinedTextField(
                value = draft.url, onValueChange = { draft = draft.copy(url = it) },
                label = { Text("https://mcp.example.com/mcp") }, modifier = Modifier.weight(0.6f),
            )
        }
        Button(onClick = { if (draft.url.isNotBlank()) { vm.addMcp(draft); draft = McpServer("srv", "", "") } }) {
            Text("Add server")
        }
    }
}
