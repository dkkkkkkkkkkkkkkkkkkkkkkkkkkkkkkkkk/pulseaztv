# PulseAZ TV — Android

PulseAZ TV is an Android HLS player and directory for Azerbaijani television channels.

## Features

- Native Android player built with AndroidX Media3 / ExoPlayer.
- Channel search, categories and locally saved favourites.
- Channel cards with remote logos where a public logo image is available, plus clean initials as a fallback.
- Official-site fallback for channels where a direct HLS endpoint was not found or is known to be restricted.
- A portable M3U playlist is included at `app/src/main/assets/pulseaz-az.m3u`.

## Build a debug APK

The GitHub Actions workflow `.github/workflows/android-apk.yml` builds the debug APK on pushes to the Android project.

1. Open the repository Actions tab.
2. Select **Build PulseAZ TV APK**.
3. Open the latest successful run.
4. Download the **PulseAZ-TV-debug-apk** artifact and extract `app-debug.apk`.

The project uses JDK 17, Gradle 8.13, Android Gradle Plugin 8.13.2, Kotlin 2.3.0, Android API 36 and AndroidX Media3 1.11.1.

## Stream and rights note

The M3U URLs are sourced from the publicly maintained [iptv-org Azerbaijan stream list](https://github.com/iptv-org/iptv/blob/master/streams/az.m3u). The list is community-maintained, not an assurance that every endpoint is controlled by the channel or currently available. Some broadcaster streams may be temporarily offline, geo-restricted, or require a change by the provider. CBC Sport's listed HLS endpoint is marked geo-blocked, so the app deliberately directs users to the official site instead.

The app does not host, record, proxy, restream or bypass access controls for any channel. Channel owners may change or withdraw streams at any time. Use the official channel website when a stream is unavailable, and follow the broadcaster's terms and regional restrictions.
