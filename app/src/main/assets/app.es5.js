/*
 * MooSeek - ES5 Compatible Version
 * This file is loaded by older browsers that do not support ES modules.
 * Modern browsers will load app.js via <script type="module"> instead.
 */

var songs = [];
var filteredSongs = [];
var currentSong = null;
var player = null;
var clusterize = null;
var lyrics = [];
var plainLyricsText = '';
var lyricsInterval = null;
var shuffleEnabled = false;
var repeatMode = 'off'; // 'off', 'all', 'one'
var isSeeking = false;
var lyricsMode = 'off'; // 'off', 'synced', 'plain'

document.addEventListener('DOMContentLoaded', function () {
    // Initialize audio element
    player = document.getElementById('player');
    player.volume = 1;

    // Progress bar interaction
    var progressBar = document.getElementById('progress-bar');
    var progressFill = document.getElementById('progress-fill');
    var progressHandle = document.getElementById('progress-handle');

    progressBar.addEventListener('mousedown', startSeeking);
    progressBar.addEventListener('click', seek);

    // Touch support for progress bar
    progressBar.addEventListener('touchstart', function (e) {
        e.preventDefault();
        isSeeking = true;
        progressBar.classList.add('seeking');
        seekTouch(e);

        var onTouchMove = function (e) { e.preventDefault(); seekTouch(e); };
        var onTouchEnd = function () {
            isSeeking = false;
            progressBar.classList.remove('seeking');
            document.removeEventListener('touchmove', onTouchMove);
            document.removeEventListener('touchend', onTouchEnd);
        };

        document.addEventListener('touchmove', onTouchMove);
        document.addEventListener('touchend', onTouchEnd);
    });

    function seekTouch(e) {
        var touch = e.touches[0];
        var rect = progressBar.getBoundingClientRect();
        var percent = Math.max(0, Math.min(1, (touch.clientX - rect.left) / rect.width));

        if (player.duration) {
            player.currentTime = percent * player.duration;
            updateProgress();
        }
    }

    function startSeeking(e) {
        isSeeking = true;
        progressBar.classList.add('seeking');
        seek(e);

        var onMouseMove = function (e) { seek(e); };
        var onMouseUp = function () {
            isSeeking = false;
            progressBar.classList.remove('seeking');
            document.removeEventListener('mousemove', onMouseMove);
            document.removeEventListener('mouseup', onMouseUp);
        };

        document.addEventListener('mousemove', onMouseMove);
        document.addEventListener('mouseup', onMouseUp);
    }

    function seek(e) {
        var rect = progressBar.getBoundingClientRect();
        var percent = Math.max(0, Math.min(1, (e.clientX - rect.left) / rect.width));

        if (player.duration) {
            player.currentTime = percent * player.duration;
            updateProgress();
        }
    }

    // Update progress bar
    function updateProgress() {
        if (!isSeeking && player.duration) {
            var percent = (player.currentTime / player.duration) * 100;
            progressFill.style.width = percent + '%';
            progressHandle.style.left = percent + '%';

            document.getElementById('current-time').textContent = formatTime(player.currentTime);
            document.getElementById('total-time').textContent = formatTime(player.duration);
        }
    }

    player.addEventListener('timeupdate', updateProgress);
    player.addEventListener('loadedmetadata', updateProgress);

    // Load songs, then initialize everything else
    loadSongs(function () {
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
        var lyricsBtns = document.querySelectorAll('.lyrics-mode-btn');
        for (var i = 0; i < lyricsBtns.length; i++) {
            lyricsBtns[i].addEventListener('click', function () {
                var mode = this.getAttribute('data-mode');
                setLyricsMode(mode);
            });
        }

        // Sort menu items
        var menuItems = document.querySelectorAll('.menu-item');
        for (var j = 0; j < menuItems.length; j++) {
            menuItems[j].addEventListener('click', function () {
                var sortBy = this.getAttribute('data-sort');
                handleSort(sortBy);
                document.getElementById('sort-menu').style.display = 'none';
            });
        }

        // Close sort menu when clicking outside
        document.addEventListener('click', function (e) {
            var sortMenu = document.getElementById('sort-menu');
            var sortBtn = document.getElementById('sort-btn');
            if (!sortMenu.contains(e.target) && !sortBtn.contains(e.target)) {
                sortMenu.style.display = 'none';
            }
        });

        // Player events
        player.addEventListener('play', function () {
            updatePlayPauseButton(true);
        });

        player.addEventListener('pause', function () {
            updatePlayPauseButton(false);
            clearInterval(lyricsInterval);
        });

        player.addEventListener('ended', function () {
            clearInterval(lyricsInterval);
            if (repeatMode === 'one' && currentSong) {
                playSong(currentSong);
            } else {
                playNext();
            }
        });
    });
});

function loadSongs(callback) {
    var xhr = new XMLHttpRequest();
    xhr.open('GET', '/list');
    xhr.onload = function () {
        if (xhr.status >= 200 && xhr.status < 300) {
            try {
                songs = JSON.parse(xhr.responseText);
                filteredSongs = songs.slice();
                updateTrackList();
            } catch (e) {
                console.error('Failed to parse songs:', e);
            }
        } else {
            console.error('Failed to load songs: HTTP ' + xhr.status);
        }
        if (callback) callback();
    };
    xhr.onerror = function () {
        console.error('Failed to load songs: network error');
        if (callback) callback();
    };
    xhr.send();
}

function generateRows(songList) {
    var rows = [];
    for (var i = 0; i < songList.length; i++) {
        var song = songList[i];
        var duration = formatDuration(song.duration);
        rows.push(
            '<div class="track-item" data-index="' + i + '">' +
                '<div class="track-title">' + escapeHtml(song.title) + '</div>' +
                '<div class="track-info">' + escapeHtml(song.artist) + ' \u2022 ' + escapeHtml(song.album) + ' \u2022 ' + duration + '</div>' +
            '</div>'
        );
    }
    return rows;
}

function attachTrackClickHandlers() {
    var items = document.querySelectorAll('.track-item');
    for (var i = 0; i < items.length; i++) {
        items[i].addEventListener('click', function () {
            var index = parseInt(this.getAttribute('data-index'));
            playSong(filteredSongs[index]);

            // Update active state
            var allItems = document.querySelectorAll('.track-item');
            for (var j = 0; j < allItems.length; j++) {
                allItems[j].classList.remove('active');
            }
            this.classList.add('active');
        });
    }
}

function playSong(song) {
    currentSong = song;
    player.src = '/stream/' + song.path;
    player.load();
    player.play();
    updateNowPlaying(song);

    // Clear lyrics when changing songs
    lyrics = [];
    plainLyricsText = '';
    clearInterval(lyricsInterval);

    // Reset lyrics display to off state
    lyricsMode = 'off';
    var selector = document.querySelector('.lyrics-mode-selector');
    selector.setAttribute('data-active', 'off');
    var btns = document.querySelectorAll('.lyrics-mode-btn');
    for (var i = 0; i < btns.length; i++) {
        if (btns[i].getAttribute('data-mode') === 'off') {
            btns[i].classList.add('active');
        } else {
            btns[i].classList.remove('active');
        }
    }
    document.getElementById('lyrics-container').innerHTML = '';
}

function updateNowPlaying(song) {
    var titleEl = document.getElementById('now-playing-title');
    var artistEl = document.getElementById('now-playing-artist');

    if (song) {
        titleEl.textContent = song.title;
        artistEl.textContent = song.artist + (song.album ? ' \u2022 ' + song.album : '');
    } else {
        titleEl.textContent = 'No song playing';
        artistEl.textContent = '';
    }
}

function playNext() {
    if (!currentSong || filteredSongs.length === 0) return;

    if (shuffleEnabled) {
        var randomIndex = Math.floor(Math.random() * filteredSongs.length);
        playSong(filteredSongs[randomIndex]);
        updateActiveTrack(randomIndex);
        return;
    }

    var currentIndex = -1;
    for (var i = 0; i < filteredSongs.length; i++) {
        if (filteredSongs[i].id === currentSong.id) {
            currentIndex = i;
            break;
        }
    }

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
        var randomIndex = Math.floor(Math.random() * filteredSongs.length);
        playSong(filteredSongs[randomIndex]);
        updateActiveTrack(randomIndex);
        return;
    }

    var currentIndex = -1;
    for (var i = 0; i < filteredSongs.length; i++) {
        if (filteredSongs[i].id === currentSong.id) {
            currentIndex = i;
            break;
        }
    }

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
    var btn = document.getElementById('play-pause-btn');
    var icon = document.getElementById('play-pause-icon');

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
    var btn = document.getElementById('shuffle-btn');
    if (shuffleEnabled) {
        btn.classList.add('active');
    } else {
        btn.classList.remove('active');
    }
    btn.title = shuffleEnabled ? 'Shuffle: On' : 'Shuffle: Off';
}

function toggleRepeat() {
    var btn = document.getElementById('repeat-btn');
    var icon = document.getElementById('repeat-icon');
    var modes = ['off', 'all', 'one'];
    var labels = { off: 'Repeat: Off', all: 'Repeat: All', one: 'Repeat: One' };
    var icons = { off: 'repeat.svg', all: 'repeat.svg', one: 'repeat-one.svg' };

    var currentIndex = modes.indexOf(repeatMode);
    repeatMode = modes[(currentIndex + 1) % modes.length];

    btn.setAttribute('data-mode', repeatMode);
    btn.title = labels[repeatMode];
    icon.src = icons[repeatMode];
    if (repeatMode !== 'off') {
        btn.classList.add('active');
    } else {
        btn.classList.remove('active');
    }
}

function updateActiveTrack(index) {
    var items = document.querySelectorAll('.track-item');
    for (var i = 0; i < items.length; i++) {
        items[i].classList.remove('active');
    }
    var activeItem = document.querySelector('.track-item[data-index="' + index + '"]');
    if (activeItem) {
        activeItem.classList.add('active');
        // scrollIntoView options may not be supported on old browsers — falls back to instant scroll
        try {
            activeItem.scrollIntoView({ behavior: 'smooth', block: 'center' });
        } catch (e) {
            activeItem.scrollIntoView(false);
        }
    }
}

function loadLyrics(song) {
    clearInterval(lyricsInterval);

    // Show loading state
    document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-loading">Loading lyrics...</div>';

    var duration = Math.floor(song.duration / 1000);
    var url = 'https://lrclib.net/api/get?artist_name=' + encodeURIComponent(song.artist) +
              '&track_name=' + encodeURIComponent(song.title) +
              '&duration=' + duration;

    var xhr = new XMLHttpRequest();
    xhr.open('GET', url);
    xhr.onload = function () {
        if (xhr.status === 404) {
            document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-error">No lyrics found</div>';
            lyrics = [];
            plainLyricsText = '';
            return;
        }

        if (xhr.status < 200 || xhr.status >= 300) {
            document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-error">Failed to load lyrics</div>';
            lyrics = [];
            plainLyricsText = '';
            return;
        }

        try {
            var data = JSON.parse(xhr.responseText);

            // Parse synced lyrics if available
            if (data.syncedLyrics) {
                lyrics = parseSyncedLyrics(data.syncedLyrics);
            } else {
                lyrics = [];
            }

            // Store plain lyrics if available
            plainLyricsText = data.plainLyrics || '';

            displayLyrics();

            if (lyricsMode === 'synced' && lyrics.length > 0 && !player.paused) {
                startLyricsSync();
            }
        } catch (e) {
            console.error('Failed to parse lyrics:', e);
            document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-error">Failed to load lyrics</div>';
            lyrics = [];
            plainLyricsText = '';
        }
    };
    xhr.onerror = function () {
        console.error('Failed to load lyrics: network error');
        document.getElementById('lyrics-container').innerHTML = '<div class="lyrics-error">Failed to load lyrics</div>';
        lyrics = [];
        plainLyricsText = '';
    };
    xhr.send();
}

function parseSyncedLyrics(lyricsText) {
    if (!lyricsText) return [];

    var lines = lyricsText.split('\n');
    var parsed = [];

    for (var i = 0; i < lines.length; i++) {
        var line = lines[i];
        // Match LRC format: [MM:SS.XX] or [MM:SS.XXX]
        var match = line.match(/\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)/);
        if (match) {
            var minutes = parseInt(match[1]);
            var seconds = parseInt(match[2]);
            var centiseconds = match[3].length === 2 ? parseInt(match[3]) : Math.floor(parseInt(match[3]) / 10);
            var time = minutes * 60 + seconds + centiseconds / 100;
            var text = match[4].replace(/^\s+|\s+$/g, ''); // trim polyfill
            parsed.push({ time: time, text: text });
        }
    }

    return parsed;
}

function displayLyrics() {
    var container = document.getElementById('lyrics-container');

    if (lyricsMode === 'off') {
        container.innerHTML = '';
        return;
    }

    if (lyricsMode === 'synced') {
        if (lyrics.length === 0) {
            // If no synced lyrics but plain lyrics exist, show plain lyrics with a note
            if (plainLyricsText) {
                container.innerHTML = '<div class="lyrics-fallback-note">Synced lyrics not available. Showing plain lyrics:</div>' +
                    '<div class="lyrics-plain">' + escapeHtml(plainLyricsText) + '</div>';
            } else {
                container.innerHTML = '<div class="lyrics-error">No synced lyrics available</div>';
            }
            return;
        }
        var html = '';
        for (var i = 0; i < lyrics.length; i++) {
            var lineText = escapeHtml(lyrics[i].text) || '\u266A';
            html += '<div class="lyrics-line" data-index="' + i + '">' + lineText + '</div>';
        }
        container.innerHTML = html;
    } else if (lyricsMode === 'plain') {
        if (!plainLyricsText) {
            container.innerHTML = '<div class="lyrics-error">No plain lyrics available</div>';
            return;
        }
        container.innerHTML = '<div class="lyrics-plain">' + escapeHtml(plainLyricsText) + '</div>';
    }
}

function startLyricsSync() {
    lyricsInterval = setInterval(function () {
        var currentTime = player.currentTime;
        var activeLine = findActiveLyricLine(currentTime);

        var allLines = document.querySelectorAll('.lyrics-line');
        for (var i = 0; i < allLines.length; i++) {
            allLines[i].classList.remove('active');
        }

        if (activeLine !== -1) {
            var activeEl = document.querySelector('.lyrics-line[data-index="' + activeLine + '"]');
            if (activeEl) {
                activeEl.classList.add('active');
                try {
                    activeEl.scrollIntoView({ behavior: 'smooth', block: 'center' });
                } catch (e) {
                    activeEl.scrollIntoView(false);
                }
            }
        }
    }, 100);
}

function findActiveLyricLine(currentTime) {
    for (var i = lyrics.length - 1; i >= 0; i--) {
        if (currentTime >= lyrics[i].time) {
            return i;
        }
    }
    return -1;
}

function formatTime(seconds) {
    if (!seconds || isNaN(seconds)) return '0:00';
    var mins = Math.floor(seconds / 60);
    var secs = Math.floor(seconds % 60);
    return mins + ':' + (secs < 10 ? '0' : '') + secs;
}

function handleSearch(e) {
    var query = e.target.value.toLowerCase();
    var clearBtn = document.getElementById('clear-search');

    // Show/hide clear button
    clearBtn.style.display = query ? 'flex' : 'none';

    filteredSongs = [];
    for (var i = 0; i < songs.length; i++) {
        var song = songs[i];
        if (song.title.toLowerCase().indexOf(query) !== -1 ||
            song.artist.toLowerCase().indexOf(query) !== -1 ||
            song.album.toLowerCase().indexOf(query) !== -1) {
            filteredSongs.push(song);
        }
    }
    updateTrackList();
}

function clearSearch() {
    var searchInput = document.getElementById('search');
    searchInput.value = '';
    document.getElementById('clear-search').style.display = 'none';
    filteredSongs = songs.slice();
    updateTrackList();
}

function toggleSortMenu() {
    var menu = document.getElementById('sort-menu');
    menu.style.display = menu.style.display === 'none' ? 'block' : 'none';
}

function handleSort(sortBy) {
    // Update active state
    var menuItems = document.querySelectorAll('.menu-item');
    for (var i = 0; i < menuItems.length; i++) {
        if (menuItems[i].getAttribute('data-sort') === sortBy) {
            menuItems[i].classList.add('active');
        } else {
            menuItems[i].classList.remove('active');
        }
    }

    filteredSongs.sort(function (a, b) {
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
    var previousMode = lyricsMode;
    lyricsMode = mode;

    // Update selector state
    var selector = document.querySelector('.lyrics-mode-selector');
    selector.setAttribute('data-active', mode);

    // Update button states
    var btns = document.querySelectorAll('.lyrics-mode-btn');
    for (var i = 0; i < btns.length; i++) {
        if (btns[i].getAttribute('data-mode') === mode) {
            btns[i].classList.add('active');
        } else {
            btns[i].classList.remove('active');
        }
    }

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
    var seconds = Math.floor(ms / 1000);
    var minutes = Math.floor(seconds / 60);
    var remainingSeconds = seconds % 60;
    return minutes + ':' + (remainingSeconds < 10 ? '0' : '') + remainingSeconds;
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}
