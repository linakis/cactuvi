# IPTV Player - Simple Xtream Codes Client

A clean, modern Android IPTV player built from scratch based on the IP-Pro app structure, but simplified and focused on core functionality.

## Features

### ✅ Implemented
- **Xtream Codes API Integration** - Full support for Xtream Codes providers
- **Live TV Channels** - Browse and watch live TV with category filtering
- **Movies (VOD)** - Browse on-demand movies with category filtering
- **TV Series** - Browse series with category filtering
- **Video Player** - ExoPlayer-based player with HLS/DASH support
- **Category Filtering** - Filter content by categories
- **Clean Architecture** - MVVM pattern with Repository layer
- **Android TV Support** - Works on both mobile and Android TV
- **No MAC Address Spoofing Needed** - Your provider (cf.cdn-959.me) doesn't require it!

### 🚧 To Be Implemented
- Series episodes playback
- Favorites functionality
- Search functionality
- Resume playback
- EPG (Electronic Program Guide)
- Parental controls

## Project Structure

```
app/src/main/java/com/iptv/app/
├── data/
│   ├── api/                    # Retrofit API interfaces
│   │   ├── XtreamApiService.kt
│   │   └── ApiClient.kt
│   ├── models/                 # Data models
│   │   ├── LoginResponse.kt
│   │   ├── LiveChannel.kt
│   │   ├── Movie.kt
│   │   ├── Series.kt
│   │   └── Category.kt
│   └── repository/             # Repository layer
│       └── ContentRepository.kt
├── ui/
│   ├── playlist/               # Add playlist screen
│   │   └── AddPlaylistActivity.kt
│   ├── home/                   # Main navigation
│   │   └── HomeActivity.kt
│   ├── live/                   # Live TV
│   │   └── LiveTvFragment.kt
│   ├── movies/                 # Movies
│   │   └── MoviesFragment.kt
│   ├── series/                 # Series
│   │   └── SeriesFragment.kt
│   ├── player/                 # Video player
│   │   └── PlayerActivity.kt
│   └── common/                 # Shared adapters
│       ├── LiveChannelAdapter.kt
│       ├── MovieAdapter.kt
│       └── SeriesAdapter.kt
├── utils/                      # Utilities
│   ├── CredentialsManager.kt
│   └── StreamUrlBuilder.kt
└── MainActivity.kt             # Launcher activity
```

## Your Credentials

The app is ready to use with your Xtream Codes provider:

```
Server: http://cf.cdn-959.me
Username: 2bd16b40497f
Password: fc8edbab6b
```

**Important Note:** Your provider does NOT require MAC address spoofing. The authentication works with just username and password!

## How to Build

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 8 or higher
- Android SDK with API 21-34

### Steps

1. **Open the project in Android Studio:**
   ```bash
   cd /Users/nlinakis/Development/iptv/iptv-app
   # Open in Android Studio
   ```

2. **Sync Gradle:**
   - Android Studio will prompt to sync Gradle files
   - Click "Sync Now"

3. **Build the app:**
   - Click Run → Run 'app' (or press Shift+F10)
   - Select your device/emulator

## How to Use

### First Launch

1. The app will open the "Add Playlist" screen
2. Enter your credentials:
   - Server URL: `http://cf.cdn-959.me`
   - Username: `2bd16b40497f`
   - Password: `fc8edbab6b`
3. Click "Test Connection" to verify
4. Click "Save" to proceed

### Navigation

- **Bottom Navigation** (Mobile):
  - Live TV: Browse live channels
  - Movies: Browse VOD content
  - Series: Browse TV series
  - Search: Search content (coming soon)

- **Category Filter**:
  - Use the spinner at the top to filter by category
  - Select "All Categories" to see everything

### Playing Content

- **Live TV**: Tap any channel to start playing
- **Movies**: Tap any movie to start playing
- **Series**: Tap to see details (episodes playback coming soon)

## API Implementation Details

### Xtream Codes API Endpoints Used

| Endpoint | Usage |
|----------|-------|
| `player_api.php?username=X&password=Y` | Authentication |
| `player_api.php?action=get_live_streams` | Get live TV channels |
| `player_api.php?action=get_vod_streams` | Get movies |
| `player_api.php?action=get_series` | Get TV series |
| `player_api.php?action=get_live_categories` | Get live categories |
| `player_api.php?action=get_vod_categories` | Get movie categories |
| `player_api.php?action=get_series_categories` | Get series categories |
| `player_api.php?action=get_series_info&series_id=X` | Get series details |

### Stream URL Format

- **Live TV**: `http://cf.cdn-959.me/live/2bd16b40497f/fc8edbab6b/{stream_id}.ts`
- **Movies**: `http://cf.cdn-959.me/movie/2bd16b40497f/fc8edbab6b/{stream_id}.{extension}`
- **Series**: `http://cf.cdn-959.me/series/2bd16b40497f/fc8edbab6b/{episode_id}.{extension}`

## Technology Stack

- **Language**: Kotlin
- **Architecture**: MVVM
- **Networking**: Retrofit 2.9.0 + OkHttp 4.12.0
- **Video Player**: ExoPlayer (Media3) 1.2.1
- **Image Loading**: Glide 4.16.0
- **JSON Parsing**: Gson
- **Coroutines**: Kotlin Coroutines 1.7.3
- **UI**: Material Design 3

## Differences from IP-Pro

Your simplified app differs from IP-Pro in these ways:

| Feature | IP-Pro | Your App |
|---------|--------|----------|
| Authentication | Proprietary backend + Xtream | Xtream only |
| MAC Address | Required for IP-Pro backend | Not needed |
| Architecture | MVC (Activity-based) | MVVM (Fragment-based) |
| Database | Realm | None (future: Room) |
| M3U Support | Yes | No |
| EPG/Catch-up TV | Yes | No (yet) |
| External Players | VLC, MX Player | ExoPlayer only |
| Subtitle Support | Yes | No (yet) |
| TMDB Integration | Yes | No |

## Next Steps

To complete the app, you should add:

1. **Series Episodes Playback** - Call `get_series_info` API and display seasons/episodes
2. **Favorites** - Store favorite channels/movies locally
3. **Search** - Implement search across all content
4. **Resume Playback** - Save playback position
5. **Better Error Handling** - Retry logic, offline mode
6. **EPG Support** - Show program guide for live channels
7. **Parental Controls** - PIN protection for adult content

## Troubleshooting

### Connection Issues
- Ensure you have internet connectivity
- Check if the server URL is correct (must start with http:// or https://)
- Verify credentials are correct

### Playback Issues
- Some streams may require specific codecs
- Check if your device supports HLS/DASH formats
- Try different network conditions

### Build Issues
- Make sure all Gradle dependencies are synced
- Clean and rebuild: Build → Clean Project, then Build → Rebuild Project
- Check minimum SDK version (21)

## License

This is a personal project based on analyzing the IP-Pro decompiled source code for educational purposes.

## Credits

- Based on IP-Pro app structure analysis
- Built from scratch with modern Android architecture
- Uses Google's ExoPlayer for media playback
