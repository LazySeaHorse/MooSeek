let songs = [];
let filteredSongs = [];
let currentSong = null;
let player = null;
let clusterize = null;
let lyrics = [];
let lyricsInterval = null;

document.addEventListener('DOMContentLoaded', async () => {
    // Initialize player
    player = new Plyr('#player', {
        controls: ['play-large', 'play', 'progress', 'current-time', 'duration', 'mute', 'volume']
    });
    
    // Load saved API key
    const savedApiKey = localStorage.getItem('musixmatchApiKey');
    if (savedApiKey) {
        document.getElementById('apiKey').value = savedApiKey;
    }
    
    // Load songs
    await loadSongs();
    
    // Initialize clusterize
    clusterize = new Clusterize({
        rows: generateRows(filteredSongs),
        scrollId: 'scrollArea',
        contentId: 'contentArea',
        rows_in_block: 50,
        blocks_in_cluster: 4,
        show_no_data_row: true,
        no_data_text: 'No songs found',
        callbacks: {
            clusterChanged: function() {
                attachTrackClickHandlers();
            }
        }
    });
    
    // Event listeners
    document.getElementById('search').addEventListener('input', handleSearch);
    document.getElementById('sort').addEventListener('change', handleSort);
    document.getElementById('settings-btn').addEventListener('click', openSettings);
    document.getElementById('save-settings').addEventListener('click', saveSettings);
    document.getElementById('close-settings').addEventListener('click', closeSettings);
    
    // Player events
    player.on('play', () => {
        if (currentSong) {
            loadLyrics(currentSong);
        }
    });
    
    player.on('pause', () => {
        clearInterval(lyricsInterval);
    });
    
    player.on('ended', () => {
        clearInterval(lyricsInterval);
        playNext();
    });
});

async function loadSongs() {
    try {
        const response = await fetch('/list');
        songs = await response.json();
        filteredSongs = [...songs];
        updateTrackList();
    } catch (error) {
        console.error('Failed to load songs:', error);
    }
}

function generateRows(songList) {
    return songList.map((song, index) => {
        const duration = formatDuration(song.duration);
        return `<div class="track-item" data-index="${index}">
            <div class="track-title">${escapeHtml(song.title)}</div>
            <div class="track-info">${escapeHtml(song.artist)} • ${escapeHtml(song.album)} • ${duration}</div>
        </div>`;
    });
}

function attachTrackClickHandlers() {
    document.querySelectorAll('.track-item').forEach(item => {
        item.addEventListener('click', function() {
            const index = parseInt(this.dataset.index);
            playSong(filteredSongs[index]);
            
            // Update active state
            document.querySelectorAll('.track-item').forEach(el => el.classList.remove('active'));
            this.classList.add('active');
        });
    });
}

function playSong(song) {
    currentSong = song;
    player.source = {
        type: 'audio',
        sources: [{
            src: `/stream/${song.path}`,
            type: getMimeType(song.path)
        }]
    };
    player.play();
}

function playNext() {
    if (!currentSong) return;
    
    const currentIndex = filteredSongs.findIndex(s => s.id === currentSong.id);
    if (currentIndex < filteredSongs.length - 1) {
        playSong(filteredSongs[currentIndex + 1]);
        updateActiveTrack(currentIndex + 1);
    }
}

function updateActiveTrack(index) {
    document.querySelectorAll('.track-item').forEach(el => el.classList.remove('active'));
    const activeItem = document.querySelector(`.track-item[data-index="${index}"]`);
    if (activeItem) {
        activeItem.classList.add('active');
        activeItem.scrollIntoView({ behavior: 'smooth', block: 'center' });
    }
}

async function loadLyrics(song) {
    clearInterval(lyricsInterval);
    document.getElementById('lyrics-container').innerHTML = 'Loading lyrics...';
    
    const apiKey = localStorage.getItem('musixmatchApiKey');
    if (!apiKey) {
        document.getElementById('lyrics-container').innerHTML = 'Please set MusixMatch API key in settings';
        return;
    }
    
    try {
        const response = await fetch(`/lyrics?artist=${encodeURIComponent(song.artist)}&title=${encodeURIComponent(song.title)}&apiKey=${apiKey}`);
        const data = await response.json();
        
        if (data.message && data.message.body && data.message.body.subtitle) {
            lyrics = parseLyrics(data.message.body.subtitle.subtitle_body);
            displayLyrics();
            startLyricsSync();
        } else {
            document.getElementById('lyrics-container').innerHTML = 'No lyrics found';
        }
    } catch (error) {
        console.error('Failed to load lyrics:', error);
        document.getElementById('lyrics-container').innerHTML = 'Failed to load lyrics';
    }
}

function parseLyrics(lyricsText) {
    const lines = lyricsText.split('\n');
    const parsed = [];
    
    for (const line of lines) {
        const match = line.match(/\[(\d{2}):(\d{2}\.\d{2})\](.*)/);
        if (match) {
            const minutes = parseInt(match[1]);
            const seconds = parseFloat(match[2]);
            const time = minutes * 60 + seconds;
            const text = match[3].trim();
            parsed.push({ time, text });
        }
    }
    
    return parsed;
}

function displayLyrics() {
    const container = document.getElementById('lyrics-container');
    container.innerHTML = lyrics.map((line, index) => 
        `<div class="lyrics-line" data-index="${index}">${line.text || '♪'}</div>`
    ).join('');
}

function startLyricsSync() {
    lyricsInterval = setInterval(() => {
        const currentTime = player.currentTime;
        const activeLine = findActiveLyricLine(currentTime);
        
        document.querySelectorAll('.lyrics-line').forEach(el => el.classList.remove('active'));
        
        if (activeLine !== -1) {
            const activeEl = document.querySelector(`.lyrics-line[data-index="${activeLine}"]`);
            if (activeEl) {
                activeEl.classList.add('active');
                activeEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
            }
        }
    }, 100);
}

function findActiveLyricLine(currentTime) {
    for (let i = lyrics.length - 1; i >= 0; i--) {
        if (currentTime >= lyrics[i].time) {
            return i;
        }
    }
    return -1;
}

function handleSearch(e) {
    const query = e.target.value.toLowerCase();
    filteredSongs = songs.filter(song => 
        song.title.toLowerCase().includes(query) ||
        song.artist.toLowerCase().includes(query) ||
        song.album.toLowerCase().includes(query)
    );
    updateTrackList();
}

function handleSort(e) {
    const sortBy = e.target.value;
    
    filteredSongs.sort((a, b) => {
        switch (sortBy) {
            case 'title':
                return a.title.localeCompare(b.title);
            case 'dateAdded':
                return b.dateAdded - a.dateAdded;
            case 'dateModified':
                return b.dateModified - a.dateModified;
            default:
                return 0;
        }
    });
    
    updateTrackList();
}

function updateTrackList() {
    if (clusterize) {
        clusterize.update(generateRows(filteredSongs));
        setTimeout(attachTrackClickHandlers, 0);
    }
}

function openSettings() {
    document.getElementById('settings-modal').style.display = 'block';
}

function closeSettings() {
    document.getElementById('settings-modal').style.display = 'none';
}

function saveSettings() {
    const apiKey = document.getElementById('apiKey').value;
    localStorage.setItem('musixmatchApiKey', apiKey);
    closeSettings();
}

function formatDuration(ms) {
    const seconds = Math.floor(ms / 1000);
    const minutes = Math.floor(seconds / 60);
    const remainingSeconds = seconds % 60;
    return `${minutes}:${remainingSeconds.toString().padStart(2, '0')}`;
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function getMimeType(path) {
    const ext = path.split('.').pop().toLowerCase();
    const mimeTypes = {
        'mp3': 'audio/mpeg',
        'm4a': 'audio/mp4',
        'ogg': 'audio/ogg',
        'flac': 'audio/flac'
    };
    return mimeTypes[ext] || 'audio/*';
}