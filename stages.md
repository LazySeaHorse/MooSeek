# MooSeek Local Playback Implementation - Stages

## Project Context

MooSeek is currently a local network music streaming app that runs an HTTP server on Android to stream music to browsers on the same network. We're transforming it into a full-featured local music player with Material Design 3 UI, while keeping the streaming feature as a secondary option.

### Current Architecture
- **Language**: Kotlin
- **Min SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Current Components**:
  - `MediaRepository.kt` - Scans music from MediaStore (already working)
  - `MediaServerService.kt` - NanoHTTPD server for streaming
  - `MainActivity.kt` - Simple log display
  - `Song.kt` - Data model with id, title, artist, album, duration, path, etc.
  - Web UI in assets/ folder for remote playback

### Goal
Build a native Android music player with:
1. Local playback using ExoPlayer/Media3
2. Material Design 3 UI (Jetpack Compose)
3. Experimental shuffle algorithms (Miller Shuffle, custom algorithms)
4. Keep streaming feature as optional toggle

---

## Implementation Stages

### Stage 1: Core Playback Infrastructure ✅
**Goal**: Set up ExoPlayer and MediaSession for audio playback

**Status**: COMPLETED

#### Dependencies to Add (app/build.gradle.kts)
```kotlin
// Media playback
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-session:1.2.1")
implementation("androidx.media3:media3-ui:1.2.1")

// Jetpack Compose
implementation(platform("androidx.compose:compose-bom:2024.02.00"))
implementation("androidx.compose.ui:ui")
implementation("androidx.compose.material3:material3")
implementation("androidx.compose.ui:ui-tooling-preview")
implementation("androidx.activity:activity-compose:1.8.2")
implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")
debugImplementation("androidx.compose.ui:ui-tooling")

// Image loading for album art
implementation("io.coil-kt:coil-compose:2.5.0")

// Navigation
implementation("androidx.navigation:navigation-compose:2.7.6")
```

#### Files to Create
1. **`MusicPlaybackService.kt`** - Extends MediaSessionService
   - Manages MediaSession
   - Handles playback commands (play, pause, skip, seek)
   - Manages notification
   - Integrates with ExoPlayer

2. **`MusicPlayer.kt`** - Wrapper around ExoPlayer
   - Initialize ExoPlayer instance
   - Load and prepare media items
   - Handle playback state changes
   - Expose playback controls

3. **`PlaybackState.kt`** - Data classes for UI state
   - Current song info
   - Playback position
   - Play/pause state
   - Queue information

#### Files to Modify
- **`AndroidManifest.xml`** - Add MusicPlaybackService declaration
- **`app/build.gradle.kts`** - Add dependencies and enable Compose

#### Key Implementation Notes
- Use `MediaSessionService` instead of regular Service for proper media integration
- ExoPlayer handles all codec support, buffering, and gapless playback
- MediaSession provides Android Auto, Bluetooth, and system integration for free
- Service should be foreground with media-style notification

---

### Stage 2: Material Design 3 UI with Compose ✅
**Goal**: Build modern UI for browsing and playing music

**Status**: COMPLETED

#### Files to Create
1. **`ui/screens/LibraryScreen.kt`** - Main music library view
   - LazyColumn of songs
   - Search bar
   - Sort options (title, artist, album, date added)
   - Pull song list from MediaRepository

2. **`ui/screens/NowPlayingScreen.kt`** - Full-screen player
   - Large album art
   - Song title, artist, album
   - Playback controls (prev, play/pause, next)
   - Seek bar with time labels
   - Shuffle and repeat toggles

3. **`ui/components/BottomPlayerBar.kt`** - Mini player
   - Shows current song
   - Play/pause button
   - Expands to NowPlayingScreen on tap

4. **`ui/components/SongListItem.kt`** - Reusable song row
   - Album art thumbnail
   - Title, artist, duration
   - Tap to play

5. **`ui/theme/Theme.kt`** - Material 3 theme setup
   - Dynamic color support
   - Light/dark theme

6. **`viewmodels/MusicViewModel.kt`** - UI state management
   - Observe playback state from service
   - Handle user actions (play, pause, skip)
   - Manage song list and search

#### Files to Modify
- **`MainActivity.kt`** - Replace with Compose UI
  - Set up navigation
  - Initialize ViewModel
  - Bind to MusicPlaybackService

#### UI Structure
```
MainActivity (Compose)
├── Scaffold
│   ├── TopAppBar (search, settings)
│   ├── Content
│   │   ├── LibraryScreen (default)
│   │   └── NowPlayingScreen (navigable)
│   └── BottomPlayerBar (persistent)
```

#### Key Implementation Notes
- Use `rememberSaveable` for search state
- Coil for loading album art from MediaStore URIs
- Material 3 dynamic colors with `dynamicColorScheme()`
- Handle permission requests in Compose with `rememberLauncherForActivityResult`

---

### Stage 3: Shuffle Algorithm Framework ✅
**Goal**: Create pluggable shuffle system for experimentation

**Status**: COMPLETED

#### Files to Create
1. **`shuffle/ShuffleStrategy.kt`** - Interface
   ```kotlin
   interface ShuffleStrategy {
       fun shuffle(songs: List<Song>, currentIndex: Int = -1): List<Song>
       val name: String
       val description: String
   }
   ```

2. **`shuffle/StandardShuffle.kt`** - Random shuffle
   - Basic `List.shuffled()`

3. **`shuffle/MillerShuffle.kt`** - Port from web version
   - Every song plays exactly once
   - Previous button works correctly
   - Algorithm from: https://github.com/RondeSC/Miller_Shuffle_Algo

4. **`shuffle/WeightedShuffle.kt`** - Experimental
   - Favor recently added songs
   - Or favor higher play counts (future feature)

5. **`shuffle/ShuffleManager.kt`** - Manages active strategy
   - Switch between algorithms
   - Apply shuffle to queue
   - Persist selected strategy

#### Files to Modify
- **`MusicPlaybackService.kt`** - Integrate ShuffleManager
- **`NowPlayingScreen.kt`** - Add shuffle algorithm selector
- **`MusicViewModel.kt`** - Expose shuffle options

#### Key Implementation Notes
- Store selected shuffle strategy in SharedPreferences
- When shuffle is toggled, apply current strategy
- Maintain original queue order for un-shuffle
- Miller Shuffle needs to track history for Previous button

---

### Stage 4: Integration & Settings ⏳
**Goal**: Polish and integrate streaming feature as optional

#### Files to Create
1. **`ui/screens/SettingsScreen.kt`** - App settings
   - Toggle for streaming server
   - Shuffle algorithm selector
   - Theme selection (light/dark/auto)
   - About section

2. **`SettingsRepository.kt`** - Persist settings
   - SharedPreferences wrapper
   - Server enabled state
   - Selected shuffle algorithm
   - Theme preference

#### Files to Modify
- **`MainActivity.kt`** - Add navigation to settings
- **`MediaServerService.kt`** - Start only when enabled in settings
- **`MusicViewModel.kt`** - Read settings

#### Features to Add
- Server status indicator in UI when enabled
- Quick toggle for server in notification
- Display server URL when active
- Graceful handling of server start/stop

---

## Data Flow

### Playback Flow
```
User taps song in LibraryScreen
    ↓
MusicViewModel.playSong(song)
    ↓
MusicPlaybackService receives command via MediaController
    ↓
MusicPlayer (ExoPlayer) loads and plays media
    ↓
PlaybackState updates flow to ViewModel
    ↓
UI updates (NowPlayingScreen, BottomPlayerBar)
```

### Shuffle Flow
```
User toggles shuffle in NowPlayingScreen
    ↓
MusicViewModel.toggleShuffle()
    ↓
ShuffleManager.applyShuffle(currentQueue, selectedStrategy)
    ↓
MusicPlaybackService updates queue
    ↓
UI reflects shuffled queue
```

---

## Testing Checklist

### Stage 1
- [ ] Service starts and binds correctly
- [ ] Audio plays from MediaStore URI
- [ ] Play/pause works
- [ ] Skip next/previous works
- [ ] Notification shows and controls work
- [ ] Service survives app closure

### Stage 2
- [ ] Song list displays correctly
- [ ] Search filters songs
- [ ] Tapping song starts playback
- [ ] Now Playing screen shows correct info
- [ ] Bottom bar updates with current song
- [ ] Album art loads correctly
- [ ] Seek bar updates smoothly

### Stage 3
- [ ] Standard shuffle randomizes correctly
- [ ] Miller Shuffle plays each song once
- [ ] Previous button works in Miller Shuffle
- [ ] Shuffle persists across app restarts
- [ ] Can switch between shuffle algorithms

### Stage 4
- [ ] Settings persist correctly
- [ ] Server toggle starts/stops MediaServerService
- [ ] Server URL displays when active
- [ ] Theme changes apply immediately
- [ ] All features work together

---

## Important Notes for Future Sessions

### MediaStore URIs
- Songs are loaded from `MediaStore.Audio.Media.EXTERNAL_CONTENT_URI`
- Use `ContentUris.withAppendedId()` to get playable URIs
- ExoPlayer can play directly from content:// URIs

### Permissions
- Already have `READ_MEDIA_AUDIO` (Android 13+) and `READ_EXTERNAL_STORAGE` (older)
- Already have `FOREGROUND_SERVICE_MEDIA_PLAYBACK`
- No additional permissions needed

### Service Lifecycle
- `MusicPlaybackService` should be foreground when playing
- Use `MediaSessionService` for proper media app behavior
- Service should handle `onTaskRemoved()` to continue playback

### Compose Best Practices
- Use `collectAsStateWithLifecycle()` for Flow observation
- Hoist state to ViewModel, keep composables stateless
- Use `remember` for expensive operations
- Material 3 components: `Card`, `ListItem`, `Slider`, `IconButton`

### ExoPlayer Setup
```kotlin
val player = ExoPlayer.Builder(context)
    .setAudioAttributes(AudioAttributes.DEFAULT, true)
    .setHandleAudioBecomingNoisy(true)
    .setWakeMode(C.WAKE_MODE_LOCAL)
    .build()
```

### Current Dependencies Already in Project
- `androidx.core:core-ktx:1.12.0`
- `androidx.appcompat:appcompat:1.6.1`
- `com.google.android.material:material:1.11.0`
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3`
- `org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0`

---

## Quick Start for New Chat Sessions

1. **Check current branch**: Should be on `feature/local-playback`
2. **Review this file**: Understand which stage you're implementing
3. **Check existing code**: See what's already implemented
4. **Follow stage order**: Don't skip ahead, each builds on previous
5. **Test incrementally**: Verify each stage works before moving on

---

## Build Environment

**IMPORTANT**: This project does NOT have a local Gradle installation. All builds are performed via GitHub Actions.

- No local compilation or testing
- Push changes to trigger CI/CD pipeline
- Review build logs on GitHub Actions for errors
- Use getDiagnostics tool in IDE for syntax checking before pushing

---

## References

- [Media3 Documentation](https://developer.android.com/guide/topics/media/media3)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Material Design 3](https://m3.material.io/)
- [Miller Shuffle Algorithm](https://github.com/RondeSC/Miller_Shuffle_Algo)
- [MediaStore Audio](https://developer.android.com/reference/android/provider/MediaStore.Audio)
