package com.mooseek.shuffle

import com.mooseek.models.Song
import kotlin.random.Random

/**
 * Weighted shuffle that favors recently added songs.
 * Songs added more recently have a higher probability of being played sooner.
 */
class WeightedShuffle : ShuffleStrategy {
    
    override val name: String = "Weighted Shuffle"
    
    override val description: String = 
        "Favors recently added songs. Newer tracks play more frequently."
    
    override fun shuffle(songs: List<Song>, currentIndex: Int): List<Song> {
        if (songs.isEmpty()) return songs
        
        val currentSong = if (currentIndex >= 0 && currentIndex < songs.size) {
            songs[currentIndex]
        } else {
            null
        }
        
        // Separate current song from others
        val songsToShuffle = if (currentSong != null) {
            songs.filter { it.id != currentSong.id }
        } else {
            songs
        }
        
        if (songsToShuffle.isEmpty()) {
            return listOfNotNull(currentSong)
        }
        
        // Calculate weights based on dateAdded
        val maxDate = songsToShuffle.maxOf { it.dateAdded }
        val minDate = songsToShuffle.minOf { it.dateAdded }
        val dateRange = maxDate - minDate
        
        val weightedSongs = if (dateRange > 0) {
            songsToShuffle.map { song ->
                // Normalize date to 0-1 range, newer = higher weight
                val normalizedDate = (song.dateAdded - minDate).toDouble() / dateRange
                // Weight: 1.0 (oldest) to 3.0 (newest)
                val weight = 1.0 + (normalizedDate * 2.0)
                song to weight
            }
        } else {
            // All songs have same date, equal weights
            songsToShuffle.map { it to 1.0 }
        }
        
        // Weighted random selection
        val shuffled = mutableListOf<Song>()
        val remaining = weightedSongs.toMutableList()
        
        while (remaining.isNotEmpty()) {
            val totalWeight = remaining.sumOf { it.second }
            var random = Random.nextDouble(totalWeight)
            
            var selectedIndex = 0
            for (i in remaining.indices) {
                random -= remaining[i].second
                if (random <= 0) {
                    selectedIndex = i
                    break
                }
            }
            
            shuffled.add(remaining[selectedIndex].first)
            remaining.removeAt(selectedIndex)
        }
        
        // Add current song at the front if present
        return if (currentSong != null) {
            listOf(currentSong) + shuffled
        } else {
            shuffled
        }
    }
}
