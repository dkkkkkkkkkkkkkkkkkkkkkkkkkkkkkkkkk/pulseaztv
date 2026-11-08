# PulseAZ TV — Android

PulseAZ TV is a native Android player and directory for Azerbaijani television and radio streams.

## Version 1.4

- Redesigned dark navy interface with teal/blue accents, rounded cards, animated list entrances and a live indicator.
- Native AndroidX Media3 / ExoPlayer playback. Channel selection never opens a website or an external browser.
- TV and Radio sections, category filters, search and locally saved favourites.
- Per-stream fallback URLs where an alternate public stream is available.
- Video quality dialog exposes the video tracks actually offered by the selected HLS source. It cannot invent resolutions that the broadcaster does not provide.
- Player resize modes: FIT, ZOOM and FILL.
- Radio playback for public direct HTTPS MP3/AAC/HLS URLs, with an animated audio visualizer.
- SVG interface icons replace emoji/glyph placeholders; channel logos try multiple public icon sources with branded initials as a fallback.
- On selected channels, a transparent Android WebView remains attached beneath the native player and monitors the official public player and its resource list for up to 18 seconds. It captures only publicly exposed HTTPS `.m3u8` requests and passes them to Media3; no webpage is visible and no external browser is launched.
- The resolver polls for delayed iframe/player initialization, reads official-player Referer/Origin/User-Agent request metadata, and reuses only cookies scoped to the actual stream URL.
- Common HTTP failures (403, 404, 5xx) are explained in the player status. The app tests alternative sources and remembers successful non-expiring URLs per channel.
- Logo float, live-badge pulse/scale, typography refinement, card entrance motion and the radio equalizer add subtle visual effects.
- Referer/Origin headers are sent when needed by the public player. The app does not bypass DRM, sign-in, geo restrictions, tokens, or other access controls.
- Adaptive buffer targets and bounded source discovery improve startup and recovery on unstable networks.
- Portable TV + radio M3U playlist at `app/src/main/assets/pulseaz-az.m3u`.

## Stream catalogue sources

TV stream candidates were researched against the public [iptv-org Azerbaijan stream list](https://github.com/iptv-org/iptv/blob/master/streams/az.m3u). This is a community-maintained index, not an official guarantee from each broadcaster. It also explicitly marks CBC Sport as geo-blocked; that stream is intentionally not included.

Radio candidates were researched against the Azerbaijan station directory at [FreqTrail](https://www.freqtrail.com/stations/AZ), which lists direct stream URLs and identifies some as currently working. Only HTTPS candidates have been included in this build. Directory checks do not guarantee that a station will remain online.

The application does not host, record, proxy, restream, or bypass DRM, sign-in, geographic restrictions, signed-token restrictions or other access controls for media. No broadcaster website is embedded or opened from the app. Streams may stop working, become region-restricted, or change without notice. A channel is not marked as successfully playing until the player itself receives media.

## Build a debug APK

The GitHub Actions workflow `.github/workflows/android-apk.yml` builds the debug APK on pushes to the Android project.

1. Open the repository Actions tab.
2. Select **Build PulseAZ TV APK**.
3. Open the latest successful run.
4. Download the **PulseAZ-TV-debug-apk** artifact and extract `app-debug.apk`.

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.0, Android API 36 and AndroidX Media3 1.11.1. App version is 1.4.0 (versionCode 5). AzTV's paid Cinerama source and Kanal 35 have been removed.
