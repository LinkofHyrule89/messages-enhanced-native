# Changelog

## v0.6.3

- Background download and install. Tapping Update downloads the APK in the background with a progress notification (Downloading %), then installs it, so you can leave the app. On Android 12+ the install is silent when the system allows it; otherwise the usual install prompt is shown, with a notification that has an Install action.
- The app checks for updates in the background every 12 hours. Auto-update downloads and installs. Otherwise it posts an Update available notification with an Update action, and tapping the notification opens the app.
- A failed download or install posts a notification with a Retry action.
- Fix: opening a conversation (including SMS) reliably marks it read; a background refresh could skip the read call. New incoming messages in an open, visible chat are marked read too.
- Fix: the conversation list no longer flashes the pull-to-refresh spinner on every background poll; it only shows for a manual pull, and it uses Material You colors.

## v0.6.2

- Tablet: the conversation list card rounds only its top-left corner.
- Silent updates (Android 12+): the updater installs new versions without a prompt, like Neko, and reopens the app. New Auto-update switch in Settings.
- Note: silent updates only work once this app itself installed the current version. The first update from a manually installed APK (for example 0.6.1) still shows the normal Android install prompt.

## v0.6.1

- Top app bar is taller (68dp) below the status bar instead of squeezing into it, with the title vertically centered.
- More breathing room in the conversation header.
- Tablet: the app bar is a lighter surface over both panes, and the list sits on a darker card with rounded top corners.
- Conversation list "You:" receipts use the web app's circled-check icons (sent, delivered, read).
- Bell button in the conversation header mutes or unmutes notifications (replaces the lock icon; stays in sync with the ⋮ menu).
- App name is now just "Messages Enhanced" (same package, updates install in place).

## v0.6.0

- In-app updater. The app checks GitHub Releases on launch (at most every six hours) and from Settings, then downloads the APK with progress, verifies the size and the sha256 digest when one is published, and installs it with the system package installer.
- The server address is entered on the login screen. No server URL is built into the app.
- Release builds are minified with R8. A tag workflow builds the APK and publishes a GitHub release.
- Conversation list with search, pins, unread state, archive, trash, and a way to start a chat.
- Threads with chat themes, wallpapers, reactions, replies, stars, forward, delete, link previews, media, emoji, and pasted images.
- Group details, read receipts, typing status, and a shared events stream that reconnects.
- Push notifications configured from the server at runtime.
- Tablet layout with a full-width app bar above the list and the thread.
- Settings for the connection, notifications, refresh, and @Grok / @Groq.
