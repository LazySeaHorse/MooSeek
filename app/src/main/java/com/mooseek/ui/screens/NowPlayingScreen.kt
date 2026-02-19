package com.mooseek.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mooseek.PlaybackState
import com.mooseek.ui.components.AlbumArt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    playbackState: PlaybackState,
    onPlayPauseClick: () -> Unit,
    onSkipNextClick: () -> Unit,
    onSkipPreviousClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onBackClick: () -> Unit,
    onShuffleClick: () -> Unit,
    onShuffleStrategySelected: (String) -> Unit,
    availableStrategies: List<String>,
    modifier: Modifier = Modifier
) {
    val currentSong = playbackState.currentSong
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (currentSong == null) {
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No song playing",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(32.dp))
                
                // Album Art
                AlbumArt(
                    albumId = currentSong.albumId,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Song Info
                Text(
                    text = currentSong.title,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Text(
                    text = currentSong.artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Text(
                    text = currentSong.album,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Seek Bar
                var sliderPosition by remember { mutableFloatStateOf(0f) }
                var isUserSeeking by remember { mutableStateOf(false) }
                
                LaunchedEffect(playbackState.currentPosition) {
                    if (!isUserSeeking && playbackState.duration > 0) {
                        sliderPosition = playbackState.currentPosition.toFloat()
                    }
                }
                
                Slider(
                    value = sliderPosition,
                    onValueChange = {
                        isUserSeeking = true
                        sliderPosition = it
                    },
                    onValueChangeFinished = {
                        isUserSeeking = false
                        onSeek(sliderPosition.toLong())
                    },
                    valueRange = 0f..playbackState.duration.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth()
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatTime(playbackState.currentPosition),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = formatTime(playbackState.duration),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Shuffle and Repeat Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    var showStrategyMenu by remember { mutableStateOf(false) }
                    
                    // Shuffle Button with Strategy Selector
                    Box {
                        IconButton(
                            onClick = { showStrategyMenu = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shuffle,
                                contentDescription = "Shuffle",
                                tint = if (playbackState.shuffleEnabled) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showStrategyMenu,
                            onDismissRequest = { showStrategyMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Text(if (playbackState.shuffleEnabled) "Disable Shuffle" else "Enable Shuffle")
                                },
                                onClick = {
                                    onShuffleClick()
                                    showStrategyMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Shuffle,
                                        contentDescription = null
                                    )
                                }
                            )
                            
                            if (availableStrategies.isNotEmpty()) {
                                HorizontalDivider()
                                
                                availableStrategies.forEach { strategy ->
                                    DropdownMenuItem(
                                        text = { Text(strategy) },
                                        onClick = {
                                            onShuffleStrategySelected(strategy)
                                            showStrategyMenu = false
                                        },
                                        leadingIcon = {
                                            if (strategy == playbackState.currentShuffleStrategy) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Repeat Button (placeholder for future implementation)
                    IconButton(onClick = { /* TODO: Implement repeat */ }) {
                        Icon(
                            imageVector = Icons.Default.Repeat,
                            contentDescription = "Repeat",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Playback Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onSkipPreviousClick,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipPrevious,
                            contentDescription = "Previous",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                    
                    FilledIconButton(
                        onClick = onPlayPauseClick,
                        modifier = Modifier.size(72.dp)
                    ) {
                        Icon(
                            imageVector = if (playbackState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = onSkipNextClick,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Next",
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatTime(timeMs: Long): String {
    val seconds = (timeMs / 1000) % 60
    val minutes = (timeMs / (1000 * 60)) % 60
    return String.format("%d:%02d", minutes, seconds)
}
