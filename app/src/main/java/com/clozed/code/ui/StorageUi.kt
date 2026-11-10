package com.clozed.code.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.clozed.code.AppViewModel
import com.clozed.code.ui.theme.TextFaint
import com.clozed.code.ui.theme.WarnAmber

/**
 * Storage gate. The tools operate on `/storage/emulated/0/ClozedProjects`,
 * which needs "All files access" from Android 11 onwards. This block is shown
 * until that access is granted; the agent refuses to start a turn without it.
 */
@Composable
fun StorageGuard(vm: AppViewModel) {
    // `tick` forces a recheck after the user returns from the system screen.
    var tick by remember { mutableStateOf(0) }
    val have = remember(tick) { vm.hasStorageAccess() }

    if (!have) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("All files access", style = MaterialTheme.typography.titleMedium, color = WarnAmber)
            Text(
                "The agent reads and writes your project folder on shared storage. Grant access so the file and shell tools can work.",
                style = MaterialTheme.typography.bodySmall,
                color = TextFaint,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    vm.requestStorageAccess()
                    tick++
                }) { Text("Grant storage access") }
                OutlinedButton(onClick = { tick++ }) { Text("Recheck") }
            }
            Text(
                "On Android 10 and below, allow Storage in the app's permission settings instead.",
                style = MaterialTheme.typography.bodySmall,
                color = TextFaint,
            )
        }
    }
}
