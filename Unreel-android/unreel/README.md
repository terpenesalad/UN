# Unreel

Social media, minus the endless scroll. Unreel is an Android app that closes Reels, Shorts and other short-form video the moment they open, while leaving your feed, stories and messages alone.

## Features

- **Blocks short-form video inside apps**: Instagram Reels, YouTube Shorts, Facebook Reels (beta), Snapchat Spotlight (beta). TikTok is blocked as a whole app, since it's all short-form.
- **Blocks short-form in browsers**: youtube.com/shorts, Instagram/Facebook Reels and TikTok in Chrome, Firefox, Samsung Internet, Edge, Brave, Opera, Vivaldi and DuckDuckGo.
- **Your own website blocklist**: add any site (e.g. `reddit.com`); subdomains are covered.
- **Daily time limits per app**: when you hit the limit you're sent home with a stop screen. "5 more minutes" is available unless strict mode is on.
- **Strict mode**: turning anything off, raising a limit or removing a site makes you wait 60 seconds first.
- **Adult content filter (opt-in)**: a DNS-only local VPN that sends lookups to Cloudflare for Families (1.1.1.3). No other traffic goes through it.
- **Stats**: social time today, short-form videos blocked, a 7-day chart and per-app usage.
- **Private**: no account, no servers, no analytics. Everything is stored on the phone.

## Get the APK (no Android Studio needed)

1. Create a new repository on GitHub and upload everything in this folder (or `git push` it).
2. Open the repo's **Actions** tab. The "Build APK" workflow runs automatically on every push to `main` (or click **Run workflow**).
3. When it finishes (about 3–5 minutes), open the run and download **Unreel-apk** under Artifacts. Unzip it to get `Unreel.apk`.
4. To publish a downloadable release instead, push a tag: `git tag v1.0.0 && git push --tags`. The APK is attached to a GitHub Release.

## Install on your phone

1. Copy `Unreel.apk` to your phone and open it. Allow "Install unknown apps" for your file manager or browser when asked.
2. Open Unreel and tap **Turn on protection**.
3. **Android 13 and later:** the Unreel switch in Accessibility settings will be greyed out because the app wasn't installed from the Play Store. Go to *Settings → Apps → Unreel*, tap **⋮** (top right) → **Allow restricted settings**, then turn Unreel on in Accessibility.
4. Some phones (Xiaomi, Oppo, Huawei, Samsung) aggressively kill background services. If blocking stops after a while, set Unreel's battery usage to **Unrestricted** in its App info.

## Build locally

Open the folder in Android Studio (Ladybug or newer) and press Run, or from a terminal with JDK 17 and the Android SDK:

```
./gradlew assembleDebug
```

## How it works

| Piece | File | What it does |
|---|---|---|
| Guard service | `guard/GuardService.kt` | Accessibility service. Tracks the foreground app each second, sends **Back** when a short-form viewer is on screen, sends **Home** + stop screen on limits and blocked apps, and reads browser address bars. |
| Detection rules | `data/Catalog.kt` | View IDs (Instagram, YouTube) and selected-tab labels (Facebook, Snapchat) that identify short-form screens. |
| DNS filter | `filter/DnsFilterService.kt` | `VpnService` that only routes a fake DNS address, forwards queries to 1.1.1.3 and writes replies back. |
| Storage | `data/Prefs.kt`, `data/UsageStore.kt` | SharedPreferences, on-device only. |
| UI | `ui/` | Jetpack Compose: Today, Apps and Settings tabs, strict-mode gate, stop screen. |

## Keeping detection working

Instagram, YouTube and friends change their layouts from time to time. If a block stops working after an app update:

1. Connect the phone with USB debugging on, open the Reels/Shorts screen, and run `adb shell uiautomator dump /sdcard/ui.xml && adb pull /sdcard/ui.xml` (or use Android Studio's Layout Inspector).
2. Find a `resource-id` that only exists on that screen and add it to the app's `viewIds` in `data/Catalog.kt`.
3. Push; GitHub builds a new APK.

## Known limits

- Reels that autoplay inline in the Instagram home feed aren't blocked; tapping one to open the full-screen viewer is.
- Someone can still turn off the accessibility service in system settings. Strict mode adds friction inside the app only.
- Only one VPN can run at a time, so the adult content filter can't be combined with another VPN app.
- DNS over TCP isn't forwarded (rarely needed); apps using their own encrypted DNS can bypass the filter.
- The APK is signed with a debug key that's generated fresh on each GitHub build, so installing a new build over an old one may require uninstalling first. Add your own keystore in `app/build.gradle.kts` before a public release.
- Publishing on Google Play requires a prominent disclosure (included), an accessibility declaration in the Play Console, and a privacy policy.
