# Changelog

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
