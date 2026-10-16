# Fix Audio: no sound from TrueHD, Atmos or DTS over eARC on TCL Google TVs

**Your TCL TV plays the Blu-ray menu, then the film starts and your soundbar goes silent?** That's a bug in the TCL software. Fix Audio is a free Android TV app that brings the sound back in one click, or does it automatically within a few seconds.

**[Download the APK](../../releases/latest)** · Tested on TCL C7K (Google TV) · Last updated: 16 October 2026

## Is this your problem?

It probably is if you have:

- a **Blu-ray / 4K player or streaming box** plugged into a **TCL Google TV**;
- a **soundbar or AV receiver** on the TV's **eARC** port;
- the disc **menu has sound**, but the **film is silent**, mostly with **Dolby TrueHD / Atmos** or **DTS** soundtracks;
- the sound comes back when you **press Home and select the HDMI input again**;
- switching the TV's **Digital Audio Out** between PCM and Auto/Passthrough fixes it **sometimes**, and not other times;
- Netflix and the TV's other apps still sound fine.

**It probably won't help if:**

- TrueHD always arrives as **stereo or multichannel PCM** instead of silence, or the TV sends **only PCM** whatever you do. Other TCL owners report this, and it looks like a different problem ([C8K thread](https://www.avforums.com/threads/c8k-passtrough-issues.2537143/), [C935 thread](https://www.avforums.com/threads/tcl-c935-has-no-dolby-truehd-signal-output-by-earc.2437293/)).
- The silence happens in **apps on the TV** (Netflix, Prime Video, Kodi). Fix Audio only handles HDMI inputs.
- Reselecting the input **doesn't** bring the sound back.

## Why it happens

The TV's own logs (`adb logcat`) show the cause:

1. When you open an HDMI input, the TV sets up its eARC output for the audio playing **at that moment**. That's usually the player's menu sound: 2-channel, 48kHz PCM.
2. When the film starts, the player switches to TrueHD or DTS. The TV notices (`C2MtkHdmiCap: ... outMediaType = audio/vnd.dolby.mlp`) but **never sets up the eARC output again**: no new `HAL_AUDIO_eARC_TxOutputSetSequence`.
3. The film's soundtrack is sent down a link still set to 2-channel PCM. The soundbar can't decode it, so you hear silence.

Reselecting the input makes the TV set up the output again, this time for the format actually playing (`ChCount = 8, Freq = 192, SPDIF_OUT_NONPCM`). That's why the input-switch trick works. It's also why changing the audio setting only works sometimes: that also forces a fresh setup, but not every time.

## What Fix Audio does

- **One click:** open **Fix Audio** and the sound comes back. The app leaves the HDMI input and returns to it 3 seconds later. It asks once which input your player is on.
- **Automatic (optional):** the app watches for this exact fault and fixes it by itself. When the film starts and the TV doesn't set up its eARC output within 2.5 seconds, the app switches to Home and back. You see a short flicker, then the sound returns. It does this at most twice in 5 minutes.
- **No PCM workaround needed:** keep the player on **Bitstream**, so the soundbar gets the real TrueHD / Atmos / DTS soundtrack.

## Will it work on my TV?

| | |
|---|---|
| **Tested and working** | TCL C7K, Google TV (Android 14), firmware V8-T653T01-LF1V665 |
| **Player tested** | Panasonic DP-UB450, Dolby/DTS output set to Bitstream |
| **Soundbar tested** | Samsung HW-Q930D on the TV's eARC port |
| **Not tested** | Other TCL Google TVs, such as the C8K, C855, QM7 and QM8 |

What was checked on the C7K:

- **TrueHD after standby:** the real bug happened, and automatic mode fixed it within 3 seconds.
- **DTS:** going from the disc menu to a DTS film brought on the bug, and the app restored the sound within about 7 seconds.
- **Day to day:** the app survives standby and starts again by itself after a reboot or an update.

Other TCL Google TVs with the same MediaTek chip may have the same bug. On a TV without it, automatic mode does nothing, because it only acts on the exact log lines above. The one-click button should work on most Android TVs.

**Tried it on another TV?** [Open an issue](../../issues) with your TV model, firmware version, player and soundbar, and whether it worked.

### How automatic mode works

Automatic mode needs to read system logs and press Home. A normal app can't do that on Android 13+, so the app connects to the **TV's own adb (debugging) port** on `127.0.0.1:5555`. The TV asks you to approve this once.

The only commands the app runs over that connection:

- `logcat`, filtered to the 3 log lines above
- `input keyevent KEYCODE_HOME`
- `am start`, to open the HDMI input
- `appops set com.hemma.fixaudio AUTO_START allow`
- `cmd deviceidle whitelist +com.hemma.fixaudio`

The last two exist because TCL TVs block apps from starting themselves after a reboot or an update unless they have TCL's `AUTO_START` permission. After a reboot, Android also only lets apps that are exempt from battery optimisation start a background service. The app gives both permissions to itself only. A TV has no battery, so the exemption costs nothing. On other TVs, a command that doesn't apply fails harmlessly. See [WatcherService.kt](app/src/main/java/com/hemma/fixaudio/WatcherService.kt).

## Install

1. On the TV: **Settings → System → About**, then click **Build** 7 times to turn on Developer options.
2. **Settings → System → Developer options**: turn on **USB debugging** and **Network debugging** (the second is only needed for automatic mode).
3. Install the APK from [Releases](../../releases), either way:
   - From a computer: `adb connect <tv-ip>` and then `adb install fix-audio.apk`
   - On the TV: with the *Downloader* app, using the APK link
4. Open **Fix Audio** from the TV's apps, pick your player's HDMI input, and when the **"Allow USB debugging?"** prompt appears, tick **Always allow** and press **Allow**. Then press Back.

**After installing or updating, open Fix Audio once.** Android doesn't let a newly installed app start itself until it has been opened. After that, with automatic mode set up, the app starts again by itself after an update or a reboot. If TCL's background-app blocker stops it after standby, opening Fix Audio once starts it again.

## FAQ

### Why does my TCL TV play no sound from my Blu-ray player over eARC?
On a TCL Google TV, the silence usually starts when the film begins, not on the menu. The TV sets up its eARC output for the menu's stereo PCM sound and doesn't update it when the film switches to Dolby TrueHD, Atmos or DTS. The soundbar receives a stream it can't decode. See [Why it happens](#why-it-happens).

### Why does switching HDMI inputs bring the sound back?
Opening an HDMI input is the moment the TV sets up its eARC output. Reselecting the input while the film is playing makes the TV set it up again, this time for TrueHD or DTS. Fix Audio does exactly that, automatically.

### Should I set my Blu-ray player or TV to PCM instead?
You can, but you lose Dolby Atmos and the lossless soundtrack, and it doesn't fix the cause. In testing, changing the TV's audio setting only helped because it sometimes forced the eARC output to be set up again. With Fix Audio you can keep **Bitstream** and get Atmos.

### Does it work with my Sony, LG, Sonos or Samsung soundbar, or my AV receiver?
The bug is in how the TV sets up its eARC output, so the soundbar brand shouldn't matter. Only the Samsung HW-Q930D has been tested.

### Can I avoid the problem without an app?
Yes. Connect the player to the soundbar's or receiver's HDMI input, so the sound doesn't go through the TV at all. The catch: the soundbar must pass through 4K HDR / Dolby Vision video, and you may need to adjust lip-sync, because the TV now delays only the picture.

### Does it fix silent Netflix or other TV apps?
No. It only handles sources plugged into the TV's HDMI inputs.

### Is it safe to turn on Network debugging?
Read [Security](#security) first. If you only want the one-click button, leave Network debugging off.

## Security

**Network debugging is powerful.** With it on, any device on your home network can *ask* to control the TV. One "Allow" gives that device full control (installing apps, reading the screen, and more).

- **Only press Allow right after opening Fix Audio.** The setup screen shows the app's key, for example `3F:A2:07:…`. The TV's prompt shows a key too, and the two must match. If a debugging prompt appears at any other time, press **Deny**.
- **If you only want the one-click button**, leave Network debugging **off**.
- **To undo all approvals:** **Developer options → Revoke USB debugging authorizations**.

What the app does to stay safe:

- It creates its own adb key on your TV. The key never leaves the app's private storage, and the app is excluded from backups.
- Any app can write to the Android log, so Fix Audio only trusts log lines written by system processes (`logcat -v uid`). Lines written by other apps are ignored.
- Before an HDMI input id is used in a command, the app checks that it looks like an HDMI input id and that it's on the TV's own list of inputs.
- Automatic fixes are limited to 2 every 5 minutes.

## Check your download

The release APK is signed with this certificate (SHA-256):

```
28:E0:61:19:71:10:AA:CB:EC:DE:EF:32:ED:1B:FF:45:96:E9:DD:EC:AB:BC:5E:81:4C:17:AF:CB:D6:A1:73:90
```

Check it with `apksigner verify --print-certs fix-audio.apk`. Each release also lists the APK's SHA-256. The build checks the checksums of Gradle and every dependency (`gradle/verification-metadata.xml`).

## Uninstall

TV **Settings → Apps → Fix Audio → Uninstall**, or `adb uninstall com.hemma.fixaudio`.

## Build

Needs Android SDK 34 and JDK 17.

```
./gradlew testDebugUnitTest assembleDebug
```

The unit tests replay a real log from the silent-TrueHD case ([silent-truehd.log](app/src/test/resources/silent-truehd.log)).

## Licence

MIT. No warranty: this works around a firmware bug that TCL may fix (or change) in an update.
