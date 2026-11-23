# Mochy Ext

> **Unofficial Community Fork** — Built on top of [**Mochy Music**](https://github.com/realidkroo/mochy-music) by **realidkroo** (Roo), **Keycii** (key), and upstream contributors. Free and open source under **GPL-3.0**.

**Mochy Ext** is an extended, personal/community fork of Mochy Music (`com.mochyy.mochymusic.ext`) that installs side-by-side alongside official Mochy. It tracks upstream releases while adding wallpaper-matched theming, a self-hosted Module Store, Google Drive cloud backup, and extra crash/low-RAM protections.

<p>
  <a href="https://github.com/Rizky088/Mochy-Music-Custom/releases/latest">
    <img src="https://img.shields.io/github/v/release/Rizky088/Mochy-Music-Custom?style=for-the-badge&color=89b4fa&label=Latest%20Release" alt="Latest Release" />
  </a>
  <a href="https://github.com/realidkroo/mochy-music">
    <img src="https://img.shields.io/badge/Upstream-Mochy%20Music-cba6f7?style=for-the-badge" alt="Upstream Mochy Music" />
  </a>
  <a href="https://discord.gg/4DAafDMxZb">
    <img src="https://img.shields.io/badge/Official%20Mochy-Discord-5865F2?style=for-the-badge" alt="Official Mochy Discord" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-GPL--3.0-a6e3a1?style=for-the-badge" alt="GPL-3.0 License" />
  </a>
</p>

> [!IMPORTANT]
> **Support & Bug Reports:** Please **do not** report Mochy Ext bugs in the official Mochy Music repository or official support channels. Open an issue in [Rizky088/Mochy-Music-Custom/issues](https://github.com/Rizky088/Mochy-Music-Custom/issues) instead. If you want the official, unmodified app, download it directly from [realidkroo/mochy-music](https://github.com/realidkroo/mochy-music/releases).

---

## What Is Different in Mochy Ext?

- **Side-by-Side Install:** Uses package ID `com.mochyy.mochymusic.ext`, so you can keep Official Mochy installed without conflicts.
- **Unified Wallpaper-Matched UI:** Extracts a consistent dark palette from your wallpaper across Home, Library, Profile, Stats/Recap, Settings, Dev Space, and the Module Store, plus optional seamless 1080p live video wallpaper support.
- **Community Module Store:** Self-hosted module catalog with multi-tag filtering, creator-approval removal policy (any module listed without its creator's permission is removed upon request), and per-module SHA-256 confirmation for untrusted code modules.
- **Google Drive Cloud Backups:** Uses Google's Android custom-URI OAuth flow (no `127.0.0.1` loopback timeout or floating-window workaround needed) plus a one-tap **Delete old backups** button.
- **Stability & 4 GB RAM Fixes:** Hardened backup restore (clears stale SQLite `-wal`/`-shm` journals and guards Room 43–45 migrations), safer `.zip` module importing, skippable battery optimization setup prompt + Settings recommendation, and fixed high-refresh VRR blur detection.

---

## How to Install

1. Open the [**Releases**](https://github.com/Rizky088/Mochy-Music-Custom/releases/latest) page.
2. Download the APK for your device:
   - **`arm64` (Recommended):** Smaller and faster; works on almost all modern Android phones and tablets.
   - **`universal`:** Larger APK that supports 32-bit ARM (`armeabi-v7a`), x86/x86_64 devices, and older hardware.
3. Install the APK. Future Mochy Ext releases can be installed directly over the previous version to keep your library and settings.

---

## License & Notice

Mochy Ext is licensed under the **GNU General Public License v3.0** ([LICENSE](LICENSE)), the same license as upstream Mochy Music. All modifications are open source, free, and contain no paywalls or ads. See [CREDITS.md](CREDITS.md) for full attribution.

---

<!-- mochy-credit:start · f208ad37 -->
## Forking Mochy

Forks are welcome, that's what open source is for! Just two asks:

- **Keep the credit.** If you rename it, please keep credits of this app, the other project that helped mochy possible.
- **Keep it free.** Mochy is not monetized and never will be. Please don't put features behind a paywall. A donation page is fine.

<!-- mochy-credit:end -->

---

## These incredible project helped mochy a lot! check them out too :3

- **ViviMusic** by [vivizzz007Z](https://github.com/vivizzz007Z) )[Their repo](https://github.com/vivizzz007/vivi-music)) -- For the base app. This app is a fork from this project.
- **Metrolist** by [Mostafa Alagamy](https://github.com/mostafaalagamy) ([Repository](https://github.com/mostafaalagamy/Metrolist)) -- For the base framework.
- **SimpMusic** by [maxrave-dev](https://github.com/maxrave-dev) ([Repository](https://github.com/maxrave-dev/SimpMusic)) -- For the lyrics API provider.
- **Archivetune** by [Rukamori](https://github.com/rukamori) ([Repository](https://github.com/rukamori/ArchiveTune)) -- For design and architectural inspiration.
- **RiMusic / InnerTube** by [fast4x](https://github.com/fast4x) ([Repository](https://github.com/fast4x/RiMusic)) -- For YouTube Music InnerTube API service wrapper.
- **LRCLib** by [tranxuanthang](https://github.com/tranxuanthang) ([Repository](https://github.com/tranxuanthang/lrclib) / [Website](https://lrclib.net/)) -- For synchronized LRC lyrics provider integration.
- **Monochrome Music** by [monochrome-music](https://github.com/monochrome-music) ([Repository](https://github.com/monochrome-music/monochrome) / ([Website](https://monochrome.st)) -- Flac streams, visit their web, very cool one.
- **Auto Eq** ([Web](https://autoeq.app)) -- The equalizer database
- **lrc.red** ([Web](https://lrc.red)) -- Word-timed lyrics for millions of songs, free for everyone
- **InnertubeX** by [nyxiereal](https://github.com/nyxiereal) [Their repo](https://github.com/metrolistGroup/innertubex) For The Youtube stream extration lib

---

## Open source APIs mochy talks to!!

These are the open source apis and server we get gets its lyrics, streams and data from. They r free for everyone because of these good people, so go give them a star!

- **LyricsPlus** by [Ibra Al Tabian](https://github.com/ibratabian17) ([repos](https://github.com/ibratabian17/LyricsPlus)) s
- **Better Lyrics API** by [Boidushya](https://github.com/boidushya) ([repos](https://github.com/better-lyrics/api)) 
- **LRCLIB** by [tranxuanthang](https://github.com/tranxuanthang) ([repos](https://github.com/tranxuanthang/lrclib) / [Website](https://lrclib.net/)) 
- **SimpMusic Lyrics** by [maxrave-dev](https://github.com/maxrave-dev) ([repos](https://github.com/maxrave-dev/SimpMusic))
- **Unison Lyrics** ([repos](https://github.com/better-lyrics/unison))

---
