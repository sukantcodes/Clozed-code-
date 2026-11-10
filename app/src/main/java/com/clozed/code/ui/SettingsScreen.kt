package com.clozed.code.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.data.Screen
import com.clozed.code.engine.StoredProvider
import com.clozed.code.ui.theme.Brand
import com.clozed.code.ui.theme.InfoBlue
import com.clozed.code.ui.theme.TextFaint

/** Settings: providers & keys, agent, project, MCP, permissions, backup. */
@Composable
fun SettingsScreen(vm: AppViewModel) {
    var modelPicker by remember { mutableStateOf<StoredProvider?>(null) }
    var keyEditor by remember { mutableStateOf<StoredProvider?>(null) }

    LazyColumn(
        Modifier.fillMaxWidth().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { SectionHeader("Agent", "How the agent behaves in this project") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AgentChip("build", vm)
                AgentChip("plan", vm)
                Spacer(Modifier.size(4.dp))
                Text("Plan mode is read-only.", color = TextFaint, style = MaterialTheme.typography.bodySmall)
            }
        }

        item { SectionHeader("Working folder", vm.ui.project ?: "Not set") }
        item { RowItem("Choose project folder", vm.ui.project, "Change") { vm.setScreen(Screen.FolderPicker) } }
        item { StorageGuard(vm) }

        item { SectionHeader("Providers & API keys", "${vm.providers.count { it.enabled }} connected") }
        items(vm.providers.filter { it.enabled || it.id in com.clozed.code.engine.Catalog.featured }, key = { it.id }) { p ->
            ProviderRow(
                vm = vm, p = p,
                onKeys = { keyEditor = p },
                onModel = { modelPicker = p },
            )
        }

        item { SectionHeader("Agent setup") }
        item { RowItem("Skills", "${vm.skills.size} available", "Open") { vm.setScreen(Screen.Skills) } }
        item { RowItem("MCP servers", "${vm.mcpServers.size} configured", "Open") { vm.setScreen(Screen.Mcp) } }
        item { RowItem("Permissions", "Allow / ask / deny rules", "Open") { vm.setScreen(Screen.Permissions) } }
        item { RowItem("Providers & API keys backup", "Export or import JSON", "Open") { vm.setScreen(Screen.ProviderBackup) } }
        item { RowItem("Advanced", "Endpoint, headers, effort", "Open") { vm.setScreen(Screen.Advanced) } }
    }

    modelPicker?.let { p ->
        PickerSheet(
            title = "Model for ${p.name}",
            items = vm.modelsFor(p.id),
            label = { it },
            subtitle = { null },
            onPick = { vm.selectModel(p.id, it); modelPicker = null },
            onDismiss = { modelPicker = null },
        )
    }

    keyEditor?.let { p -> KeyDialog(vm, p, onClose = { keyEditor = null }) }
}

@Composable
private fun AgentChip(agent: String, vm: AppViewModel) {
    val selected = vm.ui.agent == agent
    if (selected) {
        Button(onClick = { vm.selectAgent(agent) }) { Text(agent) }
    } else {
        OutlinedButton(onClick = { vm.selectAgent(agent) }) { Text(agent) }
    }
}

@Composable
private fun ProviderRow(
    vm: AppViewModel,
    p: StoredProvider,
    onKeys: () -> Unit,
    onModel: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(com.clozed.code.ui.theme.Surface3, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text(p.name, style = MaterialTheme.typography.titleMedium)
                Text(p.api, style = MaterialTheme.typography.bodySmall, color = TextFaint)
            }
            Pill(if (p.enabled) "on" else "off", if (p.enabled) Brand else TextFaint)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { vm.toggleProvider(p) }) { Text(if (p.enabled) "Disable" else "Enable") }
            OutlinedButton(onClick = onKeys) { Text("API key (${p.keys.count { it.isNotBlank() }})") }
            OutlinedButton(onClick = onModel) { Text("Model") }
        }
        if (vm.ui.provider == p.id) Pill("selected · ${vm.ui.model}", InfoBlue)
    }
}

@Composable
private fun KeyDialog(vm: AppViewModel, p: StoredProvider, onClose: () -> Unit) {
    var draft by remember { mutableStateOf("") }
    val stored = remember(p, vm.providers) { vm.providers.firstOrNull { it.id == p.id } ?: p }
    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("${p.name} API keys") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Keys are stored in plain text so backups can restore them.", style = MaterialTheme.typography.bodySmall, color = TextFaint)
                for ((i, k) in stored.keys.withIndex()) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}. ${k.take(6)}…${k.takeLast(4)}", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { vm.removeKey(stored, i) }) { Icon(Icons.Filled.Clear, "Remove") }
                    }
                }
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    label = { Text("New API key") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (draft.isNotBlank()) { vm.addKey(stored, draft.trim()); draft = "" } }) {
                Icon(Icons.Filled.Add, "Add"); Text("Add key")
            }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Done") } },
    )
}
