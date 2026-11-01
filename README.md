# Shuffleframe

A standalone Android app for randomised Google Photos shared-album slideshows.

## Usage
1. Open the app. **No albums are preloaded.**
2. Tap **Add shared Google Photos album** and paste your own link.
3. Select one or multiple albums using the checkboxes.
4. Tap **Start random shuffle** to swipe through images or enable auto-play.

The shuffle doesn't repeat an image until its current pass is exhausted. Links stay on your phone in the app's preferences. The app never asks you to sign into Google.

## Download APK
Open the **Actions → Shuffleframe APK** workflow, pick a successful run, and download the **Shuffleframe-APK** artifact. Extract the `app-debug.apk` from its ZIP and install it.

## Caveat
### Offline caching
Album images are downloaded into private app storage using Android WorkManager after an album is imported or refreshed, and the cache is reused for offline slideshows. Cache progress appears on the home screen. Downloaded images count against your phone's storage; clearing the app's storage removes them.

### Navigation
Tap the left or right 25% of the photo to advance to the next shuffled picture. Tap the centre 50% to fade the viewer controls away or bring them back. Swipe in either horizontal direction to advance through the shuffled photos. Use the on-screen Back button to revisit an earlier photo.

### Updating from v0.2
v0.2 was built with a temporary debug signing key. Android may refuse to install v0.3 over it with "App not installed". In that case, save your existing album links and uninstall v0.2 before installing v0.3. The new build caches its signing key in GitHub Actions for future development builds, although a permanent private signing key is recommended for long-term releases.

### Shared album compatibility
Google Photos does not expose the shared-album contents through a stable official API. The importer uses public shared-album HTML and may stop working if Google changes its page format. Some albums may not import completely. It does not make photos private or remove Google's existing album-sharing permissions.

The source is independent of Tempo.