# PulseAZ TV — Android

PulseAZ TV is a native Android television and radio player for Azerbaijani channels.

## Version 1.7.0 — Final integration

- Imported and deduplicated the previous FCP Player channel catalogue: 41 TV channels and 33 radio stations.
- Kept Kanal 35 and Azərbaycan Beynəlxalq Radiosu excluded, as requested.
- Native AndroidX Media3 playback with HLS and MPEG-DASH support.
- Automatic multi-page and multi-stream fallback. Provider URLs and page names stay internal; the application has no visible source list, external browser or embedded webpage.
- Hidden resolver can inspect public provider pages for direct media manifests and public API responses. It does not bypass paid access, login, DRM, geo restrictions or server-side access controls.
- Restored the legacy Yoda public-token request, with a short expiry-aware cache and token application restricted to Yoda CDN media URLs.
- Added support for publicly returned Catcast and Mediabay manifest fields, where the service returns a directly playable HTTPS media URL.
- XMLTV programme guide for the imported channels with programme title, time and description.
- TV/radio search, categories, local favourites and recent-history filter.
- Radio track metadata lookup for stations that supply a public now-playing feed.
- Background radio playback through an Android foreground service and notification.
- TV picture-in-picture support.
- Video track selection can adapt up to 3840×2160 (4K), only when the selected stream offers a 4K rendition and the phone supports decoding it. Lower-resolution video is not converted into genuine 4K.
- Volume slider, volume up/down, mute and unmute.
- Original PulseAZ launcher icon, refined typography, high-quality logo fallbacks, animated ambient background and channel-card motion.
- No exported M3U playlist or source directory is bundled in the final APK.

## Important playback notes

The app cannot make a broadcaster's server available when the server is offline, rejects the request, requires a paid account, requires a restricted IP/region, or protects content with DRM. It only plays direct media URLs that the selected public player/API exposes. Stream availability can change without an app update.

Logo images that cannot be fetched at a sufficient resolution use a clean channel-initial fallback rather than an enlarged blurry favicon.

## Build

The GitHub Actions workflow `.github/workflows/android-apk.yml` builds the Android debug APK.

- JDK 17
- Gradle 8.13
- Android Gradle Plugin 8.13.2
- Kotlin 2.3.0
- Android API 36
- AndroidX Media3 1.11.1

App version: **1.7.0** (versionCode 8).
