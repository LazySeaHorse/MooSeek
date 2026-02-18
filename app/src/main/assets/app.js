let songs = [];
let filteredSongs = [];
let currentSong = null;
let player = null;
let clusterize = null;
let lyrics = [];
let plainLyricsText = '';
let lyricsInterval = null;
let shuffleEnabled = false;
let shuffleID = 0;
let shuffleIndex = 0;
let repeatMode = 'off'; // 'off', 'all', 'one'
let isSeeking = false;
let lyricsMode = 'off'; // 'off', 'synced', 'plain'

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
    document.getElementById('close-settings').addEventListener('click', closeSettings);
    document.getElementById('shuffle-btn').addEventListener('click', toggleShuffle);
    document.getElementById('repeat-btn').addEventListener('click', toggleRepeat);
    document.getElementById('prev-btn').addEventListener('click', playPrevious);
    document.getElementById('next-btn').addEventListener('click', playNext);
    document.getElementById('clear-search').addEventListener('click', clearSearch);
    document.getElementById('play-pause-btn').addEventListener('click', togglePlayPause);

    // Lyrics mode selector
    document.querySelectorAll('.lyrics-mode-btn').forEach(btn => {
        btn.addEventListener('click', function () {
            const mode = this.dataset.mode;
            setLyricsMode(mode);
        });
    });

    // Sort menu items
    document.querySelectorAll('.menu-item').forEach(item => {
        item.addEventListener('click', function () {
            const sortBy = this.dataset.sort;
            handleSort(sortBy);
            document.getElementById('sort-menu').style.display = 'none';
        });
    });

    // Close sort menu when clicking outside
    document.addEventListener('click', function (e) {
        const sortMenu = document.getElementById('sort-menu');
        const sortBtn = document.getElementById('sort-btn');
        if (!sortMenu.contains(e.target) && !sortBtn.contains(e.target)) {
            sortMenu.style.display = 'none';
        }
    });

    // Player events
    player.addEventListener('play', () => {
        updatePlayPauseButton(true);
        // Don't auto-load lyrics on play
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

    // Clear lyrics when changing songs
    lyrics = [];
    plainLyricsText = '';
    clearInterval(lyricsInterval);

    // Reset lyrics display to off state
    lyricsMode = 'off';
    const selector = document.querySelector('.lyrics-mode-selector');
    selector.setAttribute('data-active', 'off');
    document.querySelectorAll('.lyrics-mode-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.mode === 'off');
    });
    document.getElementById('lyrics-container').innerHTML = '';
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
        shuffleIndex++;
        if (shuffleIndex >= filteredSongs.length) {
            if (repeatMode === 'all') {
                shuffleID = Math.floor(Math.random() * 2147483647);
                shuffleIndex = 0;
            } else {
                shuffleIndex--;
                return;
            }
        }
        const mappedIndex = millerShuffleLite(shuffleIndex, shuffleID, filteredSongs.length);
        playSong(filteredSongs[mappedIndex]);
        updateActiveTrack(mappedIndex);
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
        if (shuffleIndex > 0) {
            shuffleIndex--;
            const mappedIndex = millerShuffleLite(shuffleIndex, shuffleID, filteredSongs.length);
            playSong(filteredSongs[mappedIndex]);
            updateActiveTrack(mappedIndex);
        }
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
    if (shuffleEnabled) {
        shuffleID = Math.floor(Math.random() * 2147483647);
        shuffleIndex = -1; // will be incremented to 0 on first playNext
    }
    const btn = document.getElementById('shuffle-btn');
    btn.classList.toggle('active', shuffleEnabled);
    btn.title = shuffleEnabled ? 'Shuffle: On' : 'Shuffle: Off';
}

// Miller Shuffle Algorithm - Lite variant
// Source: https://github.com/RondeSC/Miller_Shuffle_Algo
// Produces a unique shuffled index for each input index (0 to nlim-1)
// without needing to store the shuffled array.
function millerShuffleLite(inx, mixID, nlim) {
    if (nlim <= 1) return 0;
    if (nlim <= 2) return ((Math.floor(mixID / (Math.floor(inx / 2) + 1)) + inx) % nlim);

    var p1 = 52639, p2 = 33703;
    var randR = ((mixID ^ (13 * Math.floor(inx / nlim))) >>> 0);
    var si = ((randR % nlim) + inx) % nlim;

    var r1 = randR % 1063;
    var r2 = randR % 3631;
    var rx = Math.floor(randR / nlim) % nlim + 1;
    var rx2 = Math.floor(randR / 131) % nlim + 1;

    if (si % 3 === 0) si = (((si / 3) * p1 + r1) % Math.floor((nlim + 2) / 3)) * 3;
    if (si % 2 === 0) si = (((si / 2) * p2 + r2) % Math.floor((nlim + 1) / 2)) * 2;
    if ((si ^ rx2) < nlim) si = si ^ rx2;
    if (si < rx) si = ((rx - si - 1) * p2 + r1 + r2) % rx;
    else si = ((si - rx) * p1 + r2) % (nlim - rx) + rx;

    return si;
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

    // Show loading state
    document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-loading">Loading lyrics...</div>';

    try {
        const duration = Math.floor(song.duration / 1000);

        // Attempt 1: exact match with title + artist + duration
        const exactUrl = `https://lrclib.net/api/get?artist_name=${encodeURIComponent(song.artist)}&track_name=${encodeURIComponent(song.title)}&duration=${duration}`;
        const exactResponse = await fetch(exactUrl);

        if (exactResponse.ok) {
            const data = await exactResponse.json();
            if (data.syncedLyrics || data.plainLyrics) {
                applyLyricsData(data);
                return;
            }
        }

        // Attempt 2: search with album + artist
        if (song.album) {
            const searchUrl = `https://lrclib.net/api/search?artist_name=${encodeURIComponent(song.artist)}&album_name=${encodeURIComponent(song.album)}`;
            const searchResponse = await fetch(searchUrl);

            if (searchResponse.ok) {
                const results = await searchResponse.json();
                const match = results.find(r => r.syncedLyrics || r.plainLyrics);
                if (match) {
                    applyLyricsData(match);
                    return;
                }
            }
        }

        // No results found — show manual search
        showManualLyricsSearch(song);
    } catch (error) {
        console.error('Failed to load lyrics:', error);
        showManualLyricsSearch(song);
    }
}

function applyLyricsData(data) {
    if (data.syncedLyrics) {
        lyrics = parseSyncedLyrics(data.syncedLyrics);
    } else {
        lyrics = [];
    }

    plainLyricsText = data.plainLyrics || '';

    displayLyrics();

    if (lyricsMode === 'synced' && lyrics.length > 0 && !player.paused) {
        startLyricsSync();
    }
}

function showManualLyricsSearch(song) {
    lyrics = [];
    plainLyricsText = '';

    const defaultQuery = song ? `${song.artist} ${song.title}` : '';
    const container = document.getElementById('lyrics-container');
    container.innerHTML =
        '<div class="lyrics-manual-search">' +
        '<div class="lyrics-error">No lyrics found</div>' +
        '<div class="lyrics-search-form">' +
        '<input type="text" class="lyrics-search-input" id="lyrics-search-input" placeholder="Search for lyrics...">' +
        '<button class="lyrics-search-btn" id="lyrics-search-btn">Search</button>' +
        '</div>' +
        '</div>';

    const searchInput = document.getElementById('lyrics-search-input');
    const searchBtn = document.getElementById('lyrics-search-btn');
    searchInput.value = defaultQuery;

    searchBtn.addEventListener('click', () => searchLyricsManual(searchInput.value));
    searchInput.addEventListener('keydown', (e) => {
        if (e.key === 'Enter') searchLyricsManual(searchInput.value);
    });
}

async function searchLyricsManual(query) {
    if (!query.trim()) return;

    document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-loading">Searching...</div>';

    try {
        const url = `https://lrclib.net/api/search?q=${encodeURIComponent(query.trim())}`;
        const response = await fetch(url);

        if (response.ok) {
            const results = await response.json();
            const match = results.find(r => r.syncedLyrics || r.plainLyrics);
            if (match) {
                applyLyricsData(match);
                return;
            }
        }

        // Still no results — show manual search again
        showManualLyricsSearch(currentSong);
    } catch (error) {
        console.error('Manual lyrics search failed:', error);
        showManualLyricsSearch(currentSong);
    }
}

function parseSyncedLyrics(lyricsText) {
    if (!lyricsText) return [];

    const lines = lyricsText.split('\n');
    const parsed = [];

    for (const line of lines) {
        // Match LRC format: [MM:SS.XX] or [MM:SS.XXX]
        const match = line.match(/\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)/);
        if (match) {
            const minutes = parseInt(match[1]);
            const seconds = parseInt(match[2]);
            const centiseconds = match[3].length === 2 ? parseInt(match[3]) : Math.floor(parseInt(match[3]) / 10);
            const time = minutes * 60 + seconds + centiseconds / 100;
            const text = match[4].trim();
            parsed.push({ time, text });
        }
    }

    return parsed;
}

function displayLyrics() {
    const container = document.getElementById('lyrics-container');

    if (lyricsMode === 'off') {
        container.innerHTML = '';
        return;
    }

    if (lyricsMode === 'synced') {
        if (lyrics.length === 0) {
            // If no synced lyrics but plain lyrics exist, show plain lyrics with a note
            if (plainLyricsText) {
                container.innerHTML = `<div class="lyrics-fallback-note">Synced lyrics not available. Showing plain lyrics:</div><div class="lyrics-plain">${escapeHtml(plainLyricsText)}</div>`;
            } else {
                container.innerHTML = '<div class="lyrics-error">No synced lyrics available</div>';
            }
            return;
        }
        container.innerHTML = lyrics.map((line, index) =>
            `<div class="lyrics-line" data-index="${index}">${escapeHtml(line.text) || '♪'}</div>`
        ).join('');
    } else if (lyricsMode === 'plain') {
        if (!plainLyricsText) {
            container.innerHTML = '<div class="lyrics-error">No plain lyrics available</div>';
            return;
        }
        container.innerHTML = `<div class="lyrics-plain">${escapeHtml(plainLyricsText)}</div>`;
    }
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

function setLyricsMode(mode) {
    const previousMode = lyricsMode;
    lyricsMode = mode;

    // Update selector state
    const selector = document.querySelector('.lyrics-mode-selector');
    selector.setAttribute('data-active', mode);

    // Update button states
    document.querySelectorAll('.lyrics-mode-btn').forEach(btn => {
        btn.classList.toggle('active', btn.dataset.mode === mode);
    });

    // Clear interval
    clearInterval(lyricsInterval);

    // If switching from off to synced/plain, fetch lyrics
    if (previousMode === 'off' && mode !== 'off' && currentSong) {
        loadLyrics(currentSong);
    } else {
        // Just update display with existing data
        displayLyrics();

        if (mode === 'synced' && lyrics.length > 0 && !player.paused) {
            startLyricsSync();
        }
    }
}

function openSettings() {
    document.getElementById('settings-modal').style.display = 'block';
}

function closeSettings() {
    document.getElementById('settings-modal').style.display = 'none';
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