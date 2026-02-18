let songs = [];
let filteredSongs = [];
let currentSong = null;
let player = null;
let clusterize = null;
let lyrics = [];
let lyricsInterval = null;
let shuffleEnabled = false;
let repeatMode = 'off'; // 'off', 'all', 'one'
let isSeeking = false;

document.addEventListener('DOMContentLoaded', async () => {
    // Initialize audio element
    player = document.getElementById('player');
    player.volume = 1;
    
    // Progress bar interaction
    const progressBar = document.getElementById('progress-bar');
    const progressFill = document.getElementById('progress-fill');
    const progressHandle = document.getElementById('progress-handle');
    
    progressBar.addEventListener('mousedown', startSeeking);
    progressBar.addEventListener('click', seek);
    
    function startSeeking(e) {
        isSeeking = true;
        progressBar.classList.add('seeking');
        seek(e);
        
        const onMouseMove = (e) => seek(e);
        const onMouseUp = () => {
            isSeeking = false;
            progressBar.classList.remove('seeking');
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        };
        
        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
    }
    
    function seek(e) {
        const rect = progressBar.getBoundingClientRect();
        const percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));
        
        if (player.duration) {
            player.currentTime = percent * player.duration;
            updateProgress();
        }
    }
    
    // Update progress bar
    function updateProgress() {
        if (!isSeeking && player.duration) {
            const percent = (player.currentTime / player.duration) * 100;
            progressFill.style.width = percent + '%';
            progressHandle.style.left = percent + '%';
            
            document.getElementById('current-time').textContent = formatTime(player.currentTime);
            document.getElementById('total-time').textContent = formatTime(player.duration);
        }
    }
    
    player.addEventListener('timeupdate', updateProgress);
    player.addEventListener('loadedmetadata', updateProgress);

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
            clusterChanged: function () {
                attachTrackClickHandlers();
            }
        }
    });

    // Event listeners
    document.getElementById('search').addEventListener('input', handleSearch);
    document.getElementById('sort-btn').addEventListener('click', toggleSortMenu);
    document.getElementById('settings-btn').addEventListener('click', openSettings);
    document.getElementById('save-settings').addEventListener('click', saveSettings);
    document.getElementById('close-settings').addEventListener('click', closeSettings);
    document.getElementById('shuffle-btn').addEventListener('click', toggleShuffle);
    document.getElementById('repeat-btn').addEventListener('click', toggleRepeat);
    document.getElementById('prev-btn').addEventListener('click', playPrevious);
    document.getElementById('next-btn').addEventListener('click', playNext);
    document.getElementById('clear-search').addEventListener('click', clearSearch);
    document.getElementById('play-pause-btn').addEventListener('click', togglePlayPause);
    
    // Sort menu items
    document.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', function() {
            const sortBy = this.dataset.sort;
            handleSort(sortBy);
            document.getElementById('sort-menu').style.display = 'none';
        });
    });
    
    // Close sort menu when clicking outside
    document.addEventListener('click', function(e) {
        const sortMenu = document.getElementById('sort-menu');
        const sortBtn = document.getElementById('sort-btn');
        if (!sortMenu.contains(e.target) && !sortBtn.contains(e.target)) {
            sortMenu.style.display = 'none';
        }
    });

    // Player events
    player.addEventListener('play', () => {
        updatePlayPauseButton(true);
        if (currentSong) {
            loadLyrics(currentSong);
        }
    });

    player.addEventListener('pause', () => {
        updatePlayPauseButton(false);
        clearInterval(lyricsInterval);
    });

    player.addEventListener('ended', () => {
        clearInterval(lyricsInterval);
        if (repeatMode === 'one' && currentSong) {
            playSong(currentSong);
        } else {
            playNext();
        }
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
        item.addEventListener('click', function () {
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
    player.src = `/stream/${song.path}`;
    player.load();
    player.play();
    updateNowPlaying(song);
}

function updateNowPlaying(song) {
    const titleEl = document.getElementById('now-playing-title');
    const artistEl = document.getElementById('now-playing-artist');
    
    if (song) {
        titleEl.textContent = song.title;
        artistEl.textContent = `${song.artist}${song.album ? ' • ' + song.album : ''}`;
    } else {
        titleEl.textContent = 'No song playing';
        artistEl.textContent = '';
    }
}

function playNext() {
    if (!currentSong || filteredSongs.length === 0) return;

    if (shuffleEnabled) {
        const randomIndex = Math.floor(Math.random() * filteredSongs.length);
        playSong(filteredSongs[randomIndex]);
        updateActiveTrack(randomIndex);
        return;
    }

    const currentIndex = filteredSongs.findIndex(s => s.id === currentSong.id);
    if (currentIndex < filteredSongs.length - 1) {
        playSong(filteredSongs[currentIndex + 1]);
        updateActiveTrack(currentIndex + 1);
    } else if (repeatMode === 'all') {
        playSong(filteredSongs[0]);
        updateActiveTrack(0);
    }
}

function playPrevious() {
    if (!currentSong || filteredSongs.length === 0) return;

    // If more than 3 seconds into the song, restart it
    if (player.currentTime > 3) {
        player.currentTime = 0;
        return;
    }

    if (shuffleEnabled) {
        const randomIndex = Math.floor(Math.random() * filteredSongs.length);
        playSong(filteredSongs[randomIndex]);
        updateActiveTrack(randomIndex);
        return;
    }

    const currentIndex = filteredSongs.findIndex(s => s.id === currentSong.id);
    if (currentIndex > 0) {
        playSong(filteredSongs[currentIndex - 1]);
        updateActiveTrack(currentIndex - 1);
    } else if (repeatMode === 'all') {
        playSong(filteredSongs[filteredSongs.length - 1]);
        updateActiveTrack(filteredSongs.length - 1);
    }
}

function togglePlayPause() {
    if (player.paused) {
        player.play();
    } else {
        player.pause();
    }
}

function updatePlayPauseButton(isPlaying) {
    const btn = document.getElementById('play-pause-btn');
    const icon = document.getElementById('play-pause-icon');
    
    if (isPlaying) {
        icon.src = 'pause.svg';
        icon.alt = 'Pause';
        btn.title = 'Pause';
    } else {
        icon.src = 'play.svg';
        icon.alt = 'Play';
        btn.title = 'Play';
    }
}

function toggleShuffle() {
    shuffleEnabled = !shuffleEnabled;
    const btn = document.getElementById('shuffle-btn');
    btn.classList.toggle('active', shuffleEnabled);
    btn.title = shuffleEnabled ? 'Shuffle: On' : 'Shuffle: Off';
}

function toggleRepeat() {
    const btn = document.getElementById('repeat-btn');
    const icon = document.getElementById('repeat-icon');
    const modes = ['off', 'all', 'one'];
    const labels = { off: 'Repeat: Off', all: 'Repeat: All', one: 'Repeat: One' };
    const icons = { off: 'repeat.svg', all: 'repeat.svg', one: 'repeat-one.svg' };

    const currentIndex = modes.indexOf(repeatMode);
    repeatMode = modes[(currentIndex + 1) % modes.length];

    btn.dataset.mode = repeatMode;
    btn.title = labels[repeatMode];
    icon.src = icons[repeatMode];
    btn.classList.toggle('active', repeatMode !== 'off');
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

function formatTime(seconds) {
    if (!seconds || isNaN(seconds)) return '0:00';
    const mins = Math.floor(seconds / 60);
    const secs = Math.floor(seconds % 60);
    return `${mins}:${secs.toString().padStart(2, '0')}`;
}

function handleSearch(e) {
    const query = e.target.value.toLowerCase();
    const clearBtn = document.getElementById('clear-search');
    
    // Show/hide clear button
    clearBtn.style.display = query ? 'flex' : 'none';
    
    filteredSongs = songs.filter(song =>
        song.title.toLowerCase().includes(query) ||
        song.artist.toLowerCase().includes(query) ||
        song.album.toLowerCase().includes(query)
    );
    updateTrackList();
}

function clearSearch() {
    const searchInput = document.getElementById('search');
    searchInput.value = '';
    document.getElementById('clear-search').style.display = 'none';
    filteredSongs = [...songs];
    updateTrackList();
}

function toggleSortMenu() {
    const menu = document.getElementById('sort-menu');
    menu.style.display = menu.style.display === 'none' ? 'block' : 'none';
}

function handleSort(sortBy) {
    // Update active state
    document.querySelectorAll('.menu-item').forEach(item => {
        item.classList.toggle('active', item.dataset.sort === sortBy);
    });

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