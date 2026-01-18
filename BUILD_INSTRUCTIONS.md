# Build Instructions - Quick Start

## Prerequisites

Install Android Studio: https://developer.android.com/studio

## Method 1: Android Studio (Easiest)

1. **Open Android Studio**
2. **Click**: File → Open
3. **Navigate to**: `/Users/nlinakis/Development/iptv/iptv-app`
4. **Click**: Open
5. **Wait** for Gradle sync (bottom right progress bar)
6. **Click**: Run button (green play icon) or press `Shift+F10`
7. **Select** your device/emulator
8. **Wait** for app to install and launch

## Method 2: Command Line

```bash
# Navigate to project
cd /Users/nlinakis/Development/iptv/iptv-app

# Build debug APK
./gradlew assembleDebug

# Output location
# APK: app/build/outputs/apk/debug/app-debug.apk

# Install to connected device
adb install app/build/outputs/apk/debug/app-debug.apk
```

## First Launch

When you first open the app:

1. You'll see "Add Playlist" screen
2. Enter these credentials:
   - **Server URL**: `http://cf.cdn-959.me`
   - **Username**: `2bd16b40497f`
   - **Password**: `fc8edbab6b`
3. Click **"Test Connection"**
   - Should show green "Connection successful!" message
4. Click **"Save"**
5. App will load to Home screen with Live TV

## Testing Checklist

### ✅ Live TV
- Should load 100+ channels
- Tap any channel → video plays
- Category filter works

### ✅ Movies  
- Should load 100+ movies
- Grid layout with posters
- Tap any movie → video plays
- Category filter works

### ✅ Series
- Should load series list
- Tap shows name (playback not implemented yet)

### ✅ Video Player
- Full screen landscape mode
- Play/pause works
- Seek bar works
- Forward/rewind buttons work

## Common Issues

### Issue: Gradle Sync Failed
**Solution**: 
```bash
# In Android Studio Terminal
./gradlew --refresh-dependencies
```

### Issue: App Crashes on Launch
**Solution**: Check Logcat for errors
- Look for red error messages
- Search for "iptv" or "ExoPlayer"

### Issue: Videos Won't Play
**Solution**:
1. Check internet connection
2. Try a different channel/movie
3. Check Logcat for stream URL
4. Test URL in VLC player

### Issue: "Connection Failed" when testing
**Solution**:
1. Check internet connection
2. Verify server URL starts with `http://`
3. Check credentials are correct
4. Try removing trailing slash from URL

## Gradle Wrapper

If you don't have `./gradlew`:

```bash
# Generate wrapper
gradle wrapper --gradle-version 8.2
```

## Min Requirements

- **Android Device/Emulator**: Android 5.0+ (API 21)
- **Android Studio**: Hedgehog (2023.1.1) or newer
- **JDK**: 8 or higher
- **Internet**: Required for streaming

## Project Stats

- **Files**: 40+
- **Lines of Code**: ~3,500
- **Dependencies**: 15 libraries
- **Build Time**: ~30 seconds (first build)
- **APK Size**: ~15-20 MB

## What's Next?

After successful build:

1. Test all features
2. Customize UI colors (res/values/colors.xml)
3. Add app icon (res/mipmap-*/ic_launcher.png)
4. Implement series episodes playback
5. Add favorites functionality
6. Add search feature

## Need Help?

Check these files:
- `README.md` - Full documentation
- `IMPLEMENTATION_SUMMARY.md` - Technical details
- Android Studio → Build → Make Project (Ctrl+F9)
- Android Studio → View → Tool Windows → Logcat

---

**That's it! Your IPTV app should now be running.** 🎉
