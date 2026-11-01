# Shuffleframe

A standalone Android app for randomised Google Photos shared-album slideshows.

## Install
Download the latest testing APK from [GitHub Releases](https://github.com/hasbreo/Shuffleframe/releases).
The app starts empty, so add your own Google Photos shared-album links.

## Features
- Combine any number of shared albums, including photos and recognised videos.
- One randomly generated, **fixed navigation order** per slideshow. Swipe right or tap the left 25% to go to the **previous** item; swipe left or tap the right 25% for **next**. The middle 50% toggles the overlay.
- Only the Shuffle button creates a fresh random order.
- Offline photo/video-poster caching with up to **six parallel downloads**, round-robin across albums; watch progress and storage consumption on the home screen.
- On video entries, use **Play / Pause** and the seek bar. Videos are streamed from Google Photos as needed (videos themselves are not pre-downloaded). In auto slideshow mode, videos are started and played before advancing to the next item.
- Dark emerald interface with custom rounded dialogs and adaptive launcher icon.

## Updating from older releases
v0.2 used a temporary signing key. If Android won't update that older APK, save your album links first, uninstall it, then install the latest build. Subsequent test builds use the cached signing key when available, but long-term production signing remains outstanding.

## Google Photos limitations
Google does not officially support accessing all photos from arbitrary shared-album links via a public API. The importer parses the shared album HTML; image and video detection is best effort. Albums with extra pages may be incomplete. Some videos may not play due to Google's media delivery restrictions. Refresh an existing album once after updating to v0.4 to recognise its videos.

Your album links and cached photos remain in the app's own on-device data. Removing or clearing app data can delete them, but never affects your originals in Google Photos.
