package com.clozed.code

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.clozed.code.data.Screen
import com.clozed.code.ui.AdvancedScreen
import com.clozed.code.ui.ChatScreen
import com.clozed.code.ui.FolderPickerScreen
import com.clozed.code.ui.McpScreen
import com.clozed.code.ui.PermissionsScreen
import com.clozed.code.ui.ProviderBackupScreen
import com.clozed.code.ui.SessionsScreen
import com.clozed.code.ui.SettingsScreen
import com.clozed.code.ui.SkillsScreen
import com.clozed.code.ui.theme.ClozedTheme
import com.clozed.code.ui.theme.TextLo

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClozedTheme {
                AppRoot(vm)
            }
        }
    }
}

/** Top-level composable: the scaffold, bottom navigation and screen router. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: AppViewModel) {
    val tabs = listOf(
        Triple(Screen.Chat, "Chat", Icons.Filled.Home),
        Triple(Screen.Sessions, "Sessions", Icons.Filled.Menu),
        Triple(Screen.Settings, "Settings", Icons.Filled.Settings),
    )
    val screen = vm.ui.screen
    val onTab = screen in tabs.map { it.first }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (screen) {
                            Screen.Chat -> vm.ui.project?.substringAfterLast('/')?.let { "Clozed Code · $it" } ?: "Clozed Code"
                            else -> screen.name
                        },
                    )
                },
                navigationIcon = {
                    if (!onTab) {
                        IconButton(onClick = { vm.setScreen(Screen.Settings) }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (screen == Screen.Chat) {
                        Text(
                            "${vm.ui.agent} · ${vm.ui.model}",
                            color = TextLo,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (onTab) {
                NavigationBar {
                    for ((s, label, icon) in tabs) {
                        NavigationBarItem(
                            selected = screen == s,
                            onClick = { vm.setScreen(s) },
                            icon = { Icon(icon as ImageVector, contentDescription = label) },
                            label = { Text(label) },
                        )
                    }
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            when (screen) {
                Screen.Chat -> ChatScreen(vm)
                Screen.Sessions -> SessionsScreen(vm)
                Screen.Settings -> SettingsScreen(vm)
                Screen.Skills -> SkillsScreen(vm)
                Screen.Mcp, Screen.Rules -> if (screen == Screen.Mcp) McpScreen(vm) else PermissionsScreen(vm)
                Screen.Permissions -> PermissionsScreen(vm)
                Screen.Advanced -> AdvancedScreen(vm)
                Screen.ProviderBackup -> ProviderBackupScreen(vm)
                Screen.FolderPicker -> FolderPickerScreen(vm)
            }
        }
    }
}
