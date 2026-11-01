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
Google Photos does not expose the shared-album contents through a stable official API. The importer uses public shared-album HTML and may stop working if Google changes its page format. Some albums may not import completely. It does not make photos private or remove Google's existing album-sharing permissions.

The source is independent of Tempo.