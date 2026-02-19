package com.mooseek.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mooseek.ThemeMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    serverEnabled: Boolean,
    serverUrl: String?,
    selectedShuffleStrategy: String,
    themeMode: ThemeMode,
    onServerToggle: (Boolean) -> Unit,
    onShuffleStrategySelected: (String) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // Streaming Server Section
            SettingsSection(title = "Streaming Server") {
                SwitchSettingItem(
                    title = "Enable Server",
                    subtitle = if (serverEnabled && serverUrl != null) {
                        "Server running at $serverUrl"
                    } else {
                        "Stream music to browsers on your network"
                    },
                    checked = serverEnabled,
                    onCheckedChange = onServerToggle
                )
            }
            
            HorizontalDivider()
            
            // Playback Section
            SettingsSection(title = "Playback") {
                var showShuffleDialog by remember { mutableStateOf(false) }
                
                ClickableSettingItem(
                    title = "Shuffle Algorithm",
                    subtitle = selectedShuffleStrategy,
                    onClick = { showShuffleDialog = true }
                )
                
                if (showShuffleDialog) {
                    ShuffleStrategyDialog(
                        currentStrategy = selectedShuffleStrategy,
                        strategies = listOf(
                            "Standard Shuffle",
                            "Miller Shuffle",
                            "Weighted Shuffle"
                        ),
                        onStrategySelected = {
                            onShuffleStrategySelected(it)
                            showShuffleDialog = false
                        },
                        onDismiss = { showShuffleDialog = false }
                    )
                }
            }
            
            HorizontalDivider()
            
            // Appearance Section
            SettingsSection(title = "Appearance") {
                var showThemeDialog by remember { mutableStateOf(false) }
                
                ClickableSettingItem(
                    title = "Theme",
                    subtitle = when (themeMode) {
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.SYSTEM -> "System Default"
                    },
                    onClick = { showThemeDialog = true }
                )
                
                if (showThemeDialog) {
                    ThemeModeDialog(
                        currentMode = themeMode,
                        onModeSelected = {
                            onThemeModeSelected(it)
                            showThemeDialog = false
                        },
                        onDismiss = { showThemeDialog = false }
                    )
                }
            }
            
            HorizontalDivider()
            
            // About Section
            SettingsSection(title = "About") {
                InfoSettingItem(
                    title = "Version",
                    subtitle = "1.0.0"
                )
                InfoSettingItem(
                    title = "MooSeek",
                    subtitle = "Local music player with experimental shuffle algorithms"
                )
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        content()
    }
}

@Composable
private fun SwitchSettingItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun ClickableSettingItem(
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoSettingItem(
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ShuffleStrategyDialog(
    currentStrategy: String,
    strategies: List<String>,
    onStrategySelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Shuffle Algorithm") },
        text = {
            Column {
                strategies.forEach { strategy ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStrategySelected(strategy) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = strategy == currentStrategy,
                            onClick = { onStrategySelected(strategy) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(text = strategy)
                            Text(
                                text = getStrategyDescription(strategy),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun ThemeModeDialog(
    currentMode: ThemeMode,
    onModeSelected: (ThemeMode) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Theme") },
        text = {
            Column {
                ThemeMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onModeSelected(mode) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = mode == currentMode,
                            onClick = { onModeSelected(mode) }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (mode) {
                                ThemeMode.LIGHT -> "Light"
                                ThemeMode.DARK -> "Dark"
                                ThemeMode.SYSTEM -> "System Default"
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

private fun getStrategyDescription(strategy: String): String {
    return when (strategy) {
        "Standard Shuffle" -> "Random shuffle"
        "Miller Shuffle" -> "Every song plays exactly once"
        "Weighted Shuffle" -> "Favors recently added songs"
        else -> ""
    }
}
