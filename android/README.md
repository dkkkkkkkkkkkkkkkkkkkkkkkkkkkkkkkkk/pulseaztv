# PulseAZ TV — Android

PulseAZ TV is a native Android player and directory for Azerbaijani television and radio streams.

## Version 1.6

- Native AndroidX Media3 / ExoPlayer playback with HLS and MPEG-DASH support.
- Multiple public provider pages per channel: broadcaster player pages plus public aggregators where available. The user can choose a source manually; automatic fallback also tries the list.
- Provider pages are loaded invisibly and never drawn on screen. Only openly exposed HTTPS media URLs are handed to the native player; no external browser is opened.
- Channel-aware page selection activates the requested channel on provider pages that list multiple channels, then probes HTML5, JWPlayer, Video.js and visible Play controls.
- Audio controls include a volume slider, volume up/down and mute/unmute.
- Video track selection is capped at 3840×2160 (4K). The app can play 4K only if the selected source actually provides a 4K rendition and the phone can decode it; it does not manufacture or upscale real 4K detail from a lower-resolution source.
- TV and Radio sections, category filters, search and locally saved favourites remain available. Azərbaycan Beynəlxalq Radiosu has been removed from the radio list as requested.
- Updated high-resolution channel logo sources and a newly generated PulseAZ TV launcher/app logo.
- Referrer/Origin/User-Agent request metadata is passed when exposed by the public player. Cookies are only reused for the exact stream URL.
- HTTP failures (403, 404, 5xx) are shown in the player status. The app does not bypass DRM, sign-in, geo restrictions, paid subscriptions, signed-token rules or other access controls.
- Redesigned dark navy interface with an ambient animated gradient, refined typography, animated channel cards, live badge and radio visualizer.
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

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.0, Android API 36 and AndroidX Media3 1.11.1 (ExoPlayer, HLS and DASH). App version is 1.6.0 (versionCode 7). AzTV's paid Cinerama source and Kanal 35 have been removed.

## Logo sources

The launcher icon is an original PulseAZ TV design generated for this project. Channel identification artwork uses the AzTV (2019), Mədəniyyət TV (2019) and Naxçıvan TV (2025) images listed as CC0 on Wikimedia Commons, the AnewZ logo served by AnewZ's own website, and the Xəzər TV image supplied on TVSTREAM.AZ. Channel marks are displayed only to identify the selected broadcaster.
