package com.mooseek.shuffle

import com.mooseek.models.Song

/**
 * Standard random shuffle implementation.
 * Uses Kotlin's built-in shuffled() function.
 */
class StandardShuffle : ShuffleStrategy {
    
    override val name: String = "Standard Shuffle"
    
    override val description: String = "Random shuffle using standard algorithm"
    
    override fun shuffle(songs: List<Song>, currentIndex: Int): List<Song> {
        if (songs.isEmpty()) return songs
        
        // If there's a current song, keep it at the front
        if (currentIndex >= 0 && currentIndex < songs.size) {
            val currentSong = songs[currentIndex]
            val otherSongs = songs.filterIndexed { index, _ -> index != currentIndex }
            return listOf(currentSong) + otherSongs.shuffled()
        }
        
        return songs.shuffled()
    }
}
