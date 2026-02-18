# MooSeek

**MooSeek** is a tiny (5MB) app that lets you stream your entire music collection from your Android phone to any device on your local network — no cloud, no syncing, no bloat. If it has a browser (even your TV!), it works.

> [!NOTE]
> Coded to life with some help from Claude 4 Opus

## Why?
I have a huge collection of music on my phone, and I wanted to play it on different devices around my home without syncing or uploading everything. Existing solutions were bloated, clunky, or required too much setup. MooSeek is lightweight, fast, and just works — open your browser and hit play.

## Features
- **Stream your music library** from your Android phone to any device on the same Wi-Fi network
- **Modern web player**: Search, sort, and play your tracks in a clean Material Design 3 UI ([ft. Plyr](https://github.com/sampotts/plyr))
- **Lyrics support**: Integrated lyrics fetching using LRCLIB
- **No sync or upload**: Your music stays on your phone
- **Works on anything with a browser**: TV, laptop, tablet, etc.

[![rtdhydrtxfvdgcvc.png](https://i.postimg.cc/28PqWc9G/rtdhydrtxfvdgcvc.png)](https://postimg.cc/YhzqK3HL)

## How it works
- The Android app runs a tiny web server on your phone (port 8080)
- Your music library is indexed and served over HTTP
- The web UI (served from your phone) lets you browse and play music from any browser on the same network
- Playback state is synchronized using WebSockets, so you can control playback from any device
- (WIP) A dedicated remote control UI is being developed for your phone

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
   - Lyrics are automatically fetched and displayed for your tracks using the LRCLIB API

## To Do
- [ ] Finish and polish the remote control feature (control playback from your phone)
- [ ] Signed APK
- [ ] Better UI? Maybe
- [ ] iOS app

## License
GPL-3.0 license
