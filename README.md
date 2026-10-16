# Fix Audio — silent eARC sound on TCL Google TVs

An Android TV app that works around a TCL firmware bug. With this bug, a Blu-ray or 4K player connected to the TV plays **no sound** through an eARC soundbar or AVR, with **Dolby TrueHD / Atmos** and **DTS** soundtracks. The usual workaround is to leave the HDMI input and come back to it. This app does that for you: in one click, or automatically when the bug happens.

## The bug

Symptoms:

- The disc menu has sound, but the film is **silent**. Netflix and other TV apps are fine.
- Changing the TV's digital audio output (PCM ↔ Auto / Atmos / Passthrough) sometimes brings the sound back, and sometimes doesn't.
- Pressing Home and selecting the HDMI input again brings the sound back.

The cause, from the TV's own logs (`adb logcat`):

1. When you switch to an HDMI input, the TV sets up its eARC output for whatever audio is playing **at that moment**. That's usually the player's menu sound: 2ch, 48kHz PCM.
2. When the film starts, the player switches to TrueHD (or another format). The TV detects this (`C2MtkHdmiCap: ... outMediaType = audio/vnd.dolby.mlp`) and switches to passthrough, but it **never sets up the eARC output again** (no new `HAL_AUDIO_eARC_TxOutputSetSequence`).
3. The TrueHD stream goes out on a link still set to 2ch 48kHz PCM. The soundbar can't decode it, so you hear silence.

When it works, the log shows the eARC output being set up as `ChCount = 8, Freq = 192, SPDIF_OUT_NONPCM`.

## Tested on

| | |
|---|---|
| TV | TCL C7K, Google TV (Android 14), firmware V8-T653T01-LF1V665 |
| Player | Panasonic DP-UB450 on HDMI 2, Dolby/DTS output set to Bitstream |
| Soundbar | Samsung HW-Q930D on the TV's eARC port (HDMI 1) |

Verified on that setup: the real bug happened after waking from standby, and automatic mode fixed it by itself within 3 seconds (8ch 192kHz bitstream output afterwards). The same fix works for DTS (`audio/vnd.dts`). The app survives standby. The one-click button works.

Other TCL Google TVs with the same MediaTek chip and software may have the same bug. **Only the setup above has been tested.** On other TVs the automatic mode does nothing unless it sees the exact log lines above, so it won't do any harm. The one-click button should work on most Android TVs, but it has only been tested on the C7K.

## What it does

- **One click:** opening **Fix Audio** leaves the HDMI input and goes back to it 3 seconds later. The first time, it asks which input your player is on.
- **Automatic (optional):** a background service reads the TV's log, as described above. If the input changes to a new audio format and the TV doesn't set up its eARC output for it within 2.5 seconds, the service presses Home and reselects the input. It does this at most 2 times in 5 minutes.

Automatic mode needs to read system logs and press Home. A normal app can't do that on Android 13+, so the app connects to the **TV's own adb (debugging) port** on `127.0.0.1:5555`. The TV asks you to approve this once. The only commands the app runs over that connection are `logcat` (filtered to the 3 log lines above), `input keyevent KEYCODE_HOME` and `am start` for the HDMI input. See [WatcherService.kt](app/src/main/java/com/hemma/fixaudio/WatcherService.kt).

## Install

1. On the TV: **Settings → System → About**, then click **Build** 7 times to turn on Developer options.
2. **Settings → System → Developer options**: turn on **USB debugging** and **Network debugging** (the second is only needed for automatic mode).
3. Install the APK from [Releases](../../releases), either way:
   - From a computer: `adb connect <tv-ip>` and then `adb install fix-audio.apk`
   - On the TV: with the *Downloader* app, using the APK link
4. Open **Fix Audio** from the TV's apps, pick your player's HDMI input, and when the **"Allow USB debugging?"** prompt appears, tick **Always allow** and press **Allow**. Then press Back.

The app starts again after a reboot. If TCL's background-app blocker stops it after standby, opening Fix Audio once starts it again.

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
