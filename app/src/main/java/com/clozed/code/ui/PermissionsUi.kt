package com.clozed.code.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import com.clozed.code.ui.theme.ErrorRed
import com.clozed.code.ui.theme.TextFaint

/** Permission rules, the global auto-approve toggle and a rule editor. */
@Composable
fun PermissionsScreen(vm: AppViewModel) {
    var rules by remember { mutableStateOf(vm.permRulesList()) }
    var auto by remember { mutableStateOf(vm.autoApproveAll()) }
    var pattern by remember { mutableStateOf("") }
    var action by remember { mutableStateOf("ask") }

    Column(Modifier.fillMaxWidth().padding(12.dp)) {
        Text("Permissions", style = MaterialTheme.typography.titleLarge)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Auto-approve all permissions")
                Text("The agent will not ask before writes, edits or shell commands.", style = MaterialTheme.typography.bodySmall, color = TextFaint)
            }
            Switch(checked = auto, onCheckedChange = { auto = it; vm.setAutoApproveAll(it) })
        }

        Text("Rules (last match wins; * is a wildcard)", style = MaterialTheme.typography.labelLarge)
        if (rules.isEmpty()) Text("No rules yet.", color = TextFaint, style = MaterialTheme.typography.bodySmall)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(rules.withIndex().toList()) { (i, r) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.pattern, style = MaterialTheme.typography.bodyLarge)
                        Text(r.action, style = MaterialTheme.typography.bodySmall, color = TextFaint)
                    }
                    TextButton(onClick = { vm.removePermRule(i); rules = vm.permRulesList() }) {
                        Text("Remove", color = ErrorRed)
                    }
                }
            }
        }

        Text("Add rule", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        OutlinedTextField(value = pattern, onValueChange = { pattern = it }, label = { Text("Pattern, e.g. /storage/emulated/0/**") }, modifier = Modifier.fillMaxWidth())
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (a in listOf("ask", "allow", "deny")) {
                OutlinedButton(onClick = { action = a }) { Text(if (action == a) "[$a]" else a) }
            }
        }
        OutlinedButton(
            onClick = { if (pattern.isNotBlank()) { vm.addPermRule(pattern, action); rules = vm.permRulesList(); pattern = "" } },
            modifier = Modifier.padding(top = 6.dp),
        ) { Text("Add") }
    }
}

/** Advanced: reasoning effort, background work, voice alerts, endpoints. */
@Composable
fun AdvancedScreen(vm: AppViewModel) {
    val efforts = listOf("low", "medium", "high")
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Advanced", style = MaterialTheme.typography.titleLarge)

        SectionHeader("Reasoning effort", "Sent to models that support it")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (e in efforts) {
                OutlinedButton(onClick = { vm.setEffort(e) }) {
                    Text(if (vm.effort() == e) "[$e]" else e)
                }
            }
            OutlinedButton(onClick = { vm.setEffort(null) }) { Text(if (vm.effort() == null) "[default]" else "default") }
        }

        SectionHeader("Background work")
        ToggleRow("Allow background work", "Keep the agent running with a foreground service", vm.backgroundWork()) { vm.setBackgroundWork(it) }
        ToggleRow("Agent alerts", "Notify when a turn finishes", vm.agentAlerts()) { vm.setAgentAlerts(it) }
        ToggleRow("Voice alerts", "Speak \"Your task is completed, sir.\"", vm.voiceAlerts()) { vm.setVoiceAlerts(it) }

        SectionHeader("Endpoints")
        RowItem("User-Agent", null, "ClozedCode-User/1.0")
        RowItem("Site", null, "https://clozedcode.app")
        RowItem("Project config", "Read from clozed.json or opencode.json", null)
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title)
            subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = TextFaint) }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
