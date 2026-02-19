package com.mooseek

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mooseek.ui.screens.LibraryScreen
import com.mooseek.ui.screens.NowPlayingScreen
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
            MooSeekTheme {
                MusicApp()
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

@Composable
fun MusicApp(
    viewModel: MusicViewModel = viewModel()
) {
    val songs by viewModel.filteredSongs.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    
    var showNowPlaying by remember { mutableStateOf(false) }
    
    Scaffold(
        bottomBar = {
            BottomPlayerBar(
                playbackState = playbackState,
                onPlayPauseClick = {
                    if (playbackState.isPlaying) {
                        viewModel.pause()
                    } else {
                        viewModel.play()
                    }
                },
                onBarClick = { showNowPlaying = true }
            )
        }
    ) { paddingValues ->
        if (showNowPlaying) {
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
                onBackClick = { showNowPlaying = false }
            )
        } else {
            LibraryScreen(
                songs = songs,
                searchQuery = searchQuery,
                onSearchQueryChange = { viewModel.updateSearchQuery(it) },
                onSongClick = { song -> viewModel.playSong(song) },
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
