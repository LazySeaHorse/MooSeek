# MooSeek

**MooSeek** is a tiny (5MB) app that lets you stream your entire music collection from your Android phone to any device on your local network — no cloud, no syncing, no bloat. If it has a browser (even your TV!), it works.

## Why?
I have a huge collection of music on my phone, and I wanted to play it on different devices around my home without syncing or uploading everything. Existing solutions were bloated, clunky, or required too much setup. MooSeek is lightweight, fast, and just works — open your browser and hit play.

## Features
- **Stream your music library** from your Android phone to any device on the same Wi-Fi network
- **Modern web player**: Search, sort, and play your tracks in a clean Material Design 3 UI
- **Smart lyrics**: Auto-fetches lyrics via [LRCLIB](https://lrclib.net) with multi-step fallback (title+artist → album+artist → manual search)
- **Works everywhere**: Old browsers and Smart TVs are supported via an automatic ES5 fallback (`module/nomodule` pattern)
- **Miller Shuffle**: Shuffle mode uses the [Miller Shuffle algorithm](https://github.com/RondeSC/Miller_Shuffle_Algo) — every song plays exactly once, and Previous actually goes back
- **No sync or upload**: Your music stays on your phone

[![rtdhydrtxfvdgcvc.png](https://i.postimg.cc/28PqWc9G/rtdhydrtxfvdgcvc.png)](https://postimg.cc/YhzqK3HL)

## How it works
- The Android app runs a tiny HTTP server on your phone (NanoHTTPD, port 8080)
- Your music library is indexed from Android's MediaStore and served as JSON
- The web UI (HTML/CSS/JS, served from the app's assets) lets you browse, search, sort, and play music from any browser on the same network
- Modern browsers load `app.js` via `<script type="module">`; older browsers/Smart TVs automatically get `app.es5.js` via `<script nomodule>` — no polyfills or build tools needed

## Setup
1. **Install the APK**
   - Grab the APK from the releases page
   - _Note: The APK is unsigned. You must sign it yourself to install on a real device_
2. **Run the app**
   - Grant storage permissions so MooSeek can index your music
   - The app will show the server address (e.g., `http://192.168.1.42:8080`)
3. **Open the web player**
   - On any device connected to the same Wi-Fi, open the server address in a browser
   - Enjoy your music!
4. **Lyrics**
   - Select Synced or Plain mode to fetch lyrics from the LRCLIB API
   - If an exact match isn't found, MooSeek retries with album+artist, and if that fails too, shows a search box so you can manually look up lyrics

## To Do
- [ ] Remote control feature (control playback from your phone)
- [ ] Signed APK for easier installation
- [ ] Better UI? Maybe
- [ ] iOS app

## License
GPL-3.0 license
