package com.mooseek

import com.mooseek.models.Song

data class PlaybackState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val originalQueue: List<Song> = emptyList(),
    val currentShuffleStrategy: String = "Standard Shuffle"
)

enum class RepeatMode {
    OFF,
    ALL,
    ONE
}
