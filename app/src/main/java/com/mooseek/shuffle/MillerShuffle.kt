package com.mooseek.shuffle

import com.mooseek.models.Song
import kotlin.random.Random

/**
 * Miller Shuffle Algorithm implementation.
 * Ensures every song plays exactly once before repeating.
 * Maintains history for proper Previous button functionality.
 * 
 * Based on: https://github.com/RondeSC/Miller_Shuffle_Algo
 */
class MillerShuffle : ShuffleStrategy {
    
    override val name: String = "Miller Shuffle"
    
    override val description: String = 
        "Every song plays exactly once. Previous button works correctly."
    
    private val playHistory = mutableListOf<Song>()
    private var remainingSongs = mutableListOf<Song>()
    
    override fun shuffle(songs: List<Song>, currentIndex: Int): List<Song> {
        if (songs.isEmpty()) return songs
        
        // Initialize or reset if song list changed
        if (remainingSongs.isEmpty() || !songs.containsAll(remainingSongs)) {
            reset(songs)
        }
        
        // If there's a current song, ensure it's first
        if (currentIndex >= 0 && currentIndex < songs.size) {
            val currentSong = songs[currentIndex]
            
            // Remove current song from remaining if present
            remainingSongs.remove(currentSong)
            
            // Build the queue: current song + shuffled remaining
            val shuffledRemaining = remainingSongs.shuffled(Random.Default)
            remainingSongs.clear()
            remainingSongs.addAll(shuffledRemaining)
            
            return listOf(currentSong) + shuffledRemaining
        }
        
        // No current song, just shuffle all
        val shuffled = remainingSongs.shuffled(Random.Default)
        remainingSongs.clear()
        remainingSongs.addAll(shuffled)
        return shuffled
    }
    
    /**
     * Mark a song as played and move it to history
     */
    fun markAsPlayed(song: Song) {
        remainingSongs.remove(song)
        playHistory.add(song)
        
        // If all songs played, reset for next cycle
        if (remainingSongs.isEmpty() && playHistory.isNotEmpty()) {
            remainingSongs.addAll(playHistory)
            playHistory.clear()
        }
    }
    
    /**
     * Get the previous song from history
     */
    fun getPreviousSong(): Song? {
        return if (playHistory.isNotEmpty()) {
            val song = playHistory.removeAt(playHistory.lastIndex)
            remainingSongs.add(0, song)
            song
        } else {
            null
        }
    }
    
    /**
     * Reset the shuffle state
     */
    fun reset(songs: List<Song>) {
        playHistory.clear()
        remainingSongs.clear()
        remainingSongs.addAll(songs)
    }
    
    /**
     * Get remaining songs count
     */
    fun getRemainingCount(): Int = remainingSongs.size
}
