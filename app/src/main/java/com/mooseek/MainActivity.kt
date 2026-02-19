package com.mooseek

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mooseek.ui.screens.LibraryScreen
import com.mooseek.ui.screens.NowPlayingScreen
import com.mooseek.ui.screens.SettingsScreen
import com.mooseek.ui.components.BottomPlayerBar
import com.mooseek.ui.theme.MooSeekTheme
import com.mooseek.viewmodels.MusicViewModel

class MainActivity : ComponentActivity() {
    
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions handled, UI will update automatically
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkAndRequestPermissions()
        
        setContent {
            val viewModel: MusicViewModel = viewModel()
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            
            MooSeekTheme(themeMode = themeMode) {
                MusicApp(viewModel = viewModel)
            }
        }
    }
    
    private fun checkAndRequestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_MEDIA_AUDIO)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        
        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicApp(
    viewModel: MusicViewModel
) {
    val songs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val serverEnabled by viewModel.serverEnabled.collectAsStateWithLifecycle()
    val serverUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    val shuffleStrategy by viewModel.shuffleStrategy.collectAsStateWithLifecycle()
    
    var currentScreen by remember { mutableStateOf(Screen.LIBRARY) }
    
    Scaffold(
        topBar = {
            if (currentScreen == Screen.LIBRARY) {
                TopAppBar(
                    title = { Text("MooSeek") },
                    actions = {
                        IconButton(onClick = { currentScreen = Screen.SETTINGS }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (currentScreen != Screen.SETTINGS) {
                BottomPlayerBar(
                    playbackState = playbackState,
                    onPlayPauseClick = {
                        if (playbackState.isPlaying) {
                            viewModel.pause()
                        } else {
                            viewModel.play()
                        }
                    },
                    onBarClick = { currentScreen = Screen.NOW_PLAYING }
                )
            }
        }
    ) { paddingValues ->
        when (currentScreen) {
            Screen.LIBRARY -> {
                LibraryScreen(
                    songs = songs,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                    onSongClick = { song -> viewModel.playSong(song) },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            Screen.NOW_PLAYING -> {
                NowPlayingScreen(
                    playbackState = playbackState,
                    onPlayPauseClick = {
                        if (playbackState.isPlaying) {
                            viewModel.pause()
                        } else {
                            viewModel.play()
                        }
                    },
                    onSkipNextClick = { viewModel.skipToNext() },
                    onSkipPreviousClick = { viewModel.skipToPrevious() },
                    onSeek = { position -> viewModel.seekTo(position) },
                    onBackClick = { currentScreen = Screen.LIBRARY },
                    onShuffleClick = { viewModel.toggleShuffle() },
                    onShuffleStrategySelected = { strategy -> viewModel.setShuffleStrategy(strategy) },
                    availableStrategies = listOf("Standard Shuffle", "Miller Shuffle", "Weighted Shuffle")
                )
            }
            Screen.SETTINGS -> {
                SettingsScreen(
                    serverEnabled = serverEnabled,
                    serverUrl = serverUrl,
                    selectedShuffleStrategy = shuffleStrategy,
                    themeMode = viewModel.themeMode.value,
                    onServerToggle = { viewModel.toggleServer(it) },
                    onShuffleStrategySelected = { viewModel.setShuffleStrategy(it) },
                    onThemeModeSelected = { viewModel.setThemeMode(it) },
                    onBackClick = { currentScreen = Screen.LIBRARY },
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}

private enum class Screen {
    LIBRARY,
    NOW_PLAYING,
    SETTINGS
}
