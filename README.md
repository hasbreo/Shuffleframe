# Shuffleframe

A standalone Android app for randomised Google Photos shared-album slideshows.

## Install
Download the latest testing APK from [GitHub Releases](https://github.com/hasbreo/Shuffleframe/releases).
The app starts empty, so add your own Google Photos shared-album links.

## Features
- Combine any number of shared albums, including photos and recognised videos.
- One randomly generated, **fixed navigation order** per slideshow. Swipe right or tap the left 25% to go to the **previous** item; swipe left or tap the right 25% for **next**. The middle 50% toggles the overlay. The tap-zone help caption has been removed.
- Only the Shuffle button creates a fresh random order. After reaching the end, the same sequence starts over; it does not re-randomise by itself.
- Background caching of full photos and videos without a fixed six-download limit, interleaved across albums. Actual simultaneous connections depend on Android, network and Google rate limits; progress and storage use appear on the home screen.
- Videos automatically play when you swipe onto them. They use the fully downloaded offline copy when available, or stream while a background download is underway. Playback has a play/pause button and seek bar; auto slideshow moves on after the video finishes.
- Dark emerald interface with custom rounded dialogs and adaptive launcher icon.

## Updating from older releases
v0.2 used a temporary signing key. If Android won't update that older APK, save your album links first, uninstall it, then install the latest build. Subsequent test builds use the cached signing key when available, but long-term production signing remains outstanding.

## Google Photos limitations
Google does not officially support accessing all photos from arbitrary shared-album links via a public API. The importer parses the shared album HTML; image and video detection is best effort. Albums with extra pages may be incomplete. Some videos may not play due to Google's media delivery restrictions. Refresh an existing album once after updating to v0.4 to recognise its videos.

Your album links and cached photos remain in the app's own on-device data. Removing or clearing app data can delete them, but never affects your originals in Google Photos.
