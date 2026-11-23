# Mochy Ext

> **Unofficial Community Fork** — Built on top of [**Mochy Music**](https://github.com/realidkroo/mochy-music) by **realidkroo** (Roo), **Keycii** (key), and upstream contributors. Free and open source under **GPL-3.0**.

**Mochy Ext** is a free, open-source community fork of Mochy Music that installs side-by-side alongside Official Mochy. It stays synced with upstream updates while adding extra visual customization, font options, song hiding, a community Module Store, and Google Drive backups.

<p>
  <a href="https://github.com/Rizky088/Mochy-Music-Custom/releases/latest">
    <img src="https://img.shields.io/github/v/release/Rizky088/Mochy-Music-Custom?style=for-the-badge&color=89b4fa&label=Latest%20Release" alt="Latest Release" />
  </a>
  <a href="https://github.com/realidkroo/mochy-music">
    <img src="https://img.shields.io/badge/Upstream-Mochy%20Music-cba6f7?style=for-the-badge" alt="Upstream Mochy Music" />
  </a>
  <a href="https://discord.com/users/967219459137871893">
    <img src="https://img.shields.io/badge/Bug%20Reports-Discord%20DM-5865F2?style=for-the-badge" alt="Report Bugs on Discord" />
  </a>
  <a href="https://discord.gg/4DAafDMxZb">
    <img src="https://img.shields.io/badge/Official%20Mochy-Discord-313244?style=for-the-badge" alt="Official Mochy Discord" />
  </a>
  <a href="LICENSE">
    <img src="https://img.shields.io/badge/License-GPL--3.0-a6e3a1?style=for-the-badge" alt="GPL-3.0 License" />
  </a>
</p>

> [!IMPORTANT]
> **Support & Bug Reports:** Please **do not** report Mochy Ext bugs in the official Mochy Music repo or official Mochy support channels.
> Instead, report Mochy Ext bugs on [**GitHub Issues**](https://github.com/Rizky088/Mochy-Music-Custom/issues) or reach out directly on [**Discord**](https://discord.com/users/967219459137871893).
> If you want the official, unmodified app, download it from [**realidkroo/mochy-music**](https://github.com/realidkroo/mochy-music/releases).

---

## What's Different in Mochy Ext?

- **Installs Side-by-Side:** Uses a separate package (`com.mochyy.mochymusic.ext`), so you can keep Official Mochy installed at the same time.
- **Wallpaper-Matched UI & Live Wallpapers:** Automatically matches colors across Home, Library, Profile, Stats, Settings, Dev Space, Player menus, and the Module Store to your wallpaper, with optional 1080p live video wallpaper support and toggles to hide the top cover glow or bottom dark fade.
- **System & Custom Fonts:** Switch between Mochy's default font, your phone's system font, or import your own `.ttf` / `.otf` font file in **Settings → Customisation**.
- **Hide / Block Songs:** Use **Don't recommend** on any song (from the player or any 3-dot menu) to skip it automatically and keep it out of radios and recommendations. View and unhide tracks anytime in **Library → Hidden**.
- **Lyrics Tools Everywhere:** Access **Lyrics provider**, **Lyrics offset**, **Lyrics translation**, and **Edit lyrics** from any built-in or module player menu.
- **Community Module Store:** Browse and install community modules with category filters, safety prompts for code modules, and a creator-approval removal policy (any module listed without its creator's permission is removed upon request).
- **Google Drive Cloud Backups:** Back up and restore your library directly with Google Drive, plus a one-tap **Delete old backups** option.
- **Built-In Fork Updates:** In-app update popups download and install new Mochy Ext releases directly.
- **Extra Stability & Low-RAM Care:** Smoother performance on 4 GB RAM devices and optional battery optimization prompts to help prevent background playback stops.

---

## How to Install

1. Open the [**Latest Release**](https://github.com/Rizky088/Mochy-Music-Custom/releases/latest) page.
2. Download the APK for your device:
   - **`arm64` (Recommended):** Smaller file for almost all modern Android phones and tablets.
   - **`universal`:** Larger file that works on older 32-bit Android devices and other architectures.
3. Install the APK. Future Mochy Ext updates can be installed directly over your existing app without losing your library or settings.

---

## License & Credits

Mochy Ext is licensed under the **GNU General Public License v3.0** ([LICENSE](LICENSE)), the same license as upstream Mochy Music. It is completely free, ad-free, and open source. See [CREDITS.md](CREDITS.md) for full attribution.

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
