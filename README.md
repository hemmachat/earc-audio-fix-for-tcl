# Fix Audio: no sound from TrueHD, Atmos or DTS over eARC on TCL Google TVs

**Your TCL TV plays the Blu-ray menu, then the film starts and your soundbar goes silent?** On the TCL C7K it's caused by a bug in the TV's software, and other TCL Google TVs may have it too. Fix Audio is a free, unofficial Android TV app that brings the sound back in one click, or does it automatically within a few seconds.

> **Before you install:** Fix Audio is an unofficial app. It isn't made or endorsed by TCL or Google. It needs Developer options, and automatic mode needs USB debugging, which lowers your TV's security. It comes with no warranty, and you use it at your own risk. Read the [Disclaimer](#disclaimer) and [Security](#security) sections first. It isn't on Google Play, and the FAQ [explains why](#why-isnt-it-on-the-google-play-store).

**[Download the APK](../../releases/latest)** · Tested on TCL C7K (Google TV) · Last updated: 17 October 2026

## Is this your problem?

```
Blu-ray / 4K player ──HDMI──► TCL Google TV ──eARC──► Soundbar / AV receiver
   (Bitstream:                    ▲
    TrueHD, DTS)        bug: eARC output stays set up
                        for the menu's 2ch PCM sound
```

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

The full bug report for TCL, with the TV logs, is in [docs/tcl-bug-report.md](docs/tcl-bug-report.md). If you contact TCL support about this, link to it.

## What Fix Audio does

- **One click:** open **Fix Audio** and the sound comes back. The app leaves the HDMI input and returns to it 3 seconds later. It finds your player's input by itself, or asks once if it can't tell.
- **Automatic (optional):** the app watches for this exact fault and fixes it by itself. When the film starts or you change the audio language, and the TV doesn't set up its eARC output within 2.5 seconds, the app switches to Home and back. You see a short flicker, then the sound returns. If 2 fixes in a row don't bring the sound back, it stops trying for 5 minutes.
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
- **Changing the audio language:** each switch between a TrueHD and a DTS track brought on the bug; the app fixed all 4 switches in 2 minutes, each within about 3 seconds.
- **Day to day:** the app survives standby and starts again by itself after a reboot or an update.
- **First-time install:** from a USB stick with X-plore; the app found the player's input by itself.

Other TCL Google TVs with the same MediaTek chip may have the same bug. On a TV without it, automatic mode does nothing, because it only acts on the exact log lines above. The one-click button should work on most Android TVs.

**Tried it on another TV?** [Open an issue](../../issues) with your TV model, firmware version, player and soundbar, and whether it worked.

### How automatic mode works

Automatic mode needs to read system logs and press Home. A normal app can't do that on Android 13+, so the app connects to the **TV's own adb (debugging) port** on `127.0.0.1:5555`. The TV asks you to approve this once.

The only commands the app runs over that connection:

- `logcat`, filtered to the 3 log lines above
- `input keyevent KEYCODE_HOME`
- `am start`, to open the HDMI input
- `appops set com.hemmachat.fixaudio AUTO_START allow`
- `cmd deviceidle whitelist +com.hemmachat.fixaudio`

The last two exist because TCL TVs block apps from starting themselves after a reboot or an update unless they have TCL's `AUTO_START` permission. After a reboot, Android also only lets apps that are exempt from battery optimisation start a background service. The app gives both permissions to itself only. A TV has no battery, so the exemption costs nothing. On other TVs, a command that doesn't apply fails harmlessly. See [WatcherService.kt](app/src/main/java/com/hemmachat/fixaudio/WatcherService.kt).

## Disclaimer

Fix Audio is an independent project. It isn't made, endorsed or supported by TCL, Google, Dolby, DTS, Samsung, Panasonic or any other company named here. Their names are used only to describe which devices and audio formats the app works with. TCL, Google TV, Android TV, Dolby, Dolby Atmos, Dolby TrueHD, DTS and the other product names are trademarks of their owners.

The description of the TV's behaviour comes from testing one TCL C7K (firmware V8-T653T01-LF1V665) and reading its logs. Other models and firmware versions may behave differently.

Use at your own risk. Turning on Developer options and USB debugging changes your TV's settings and security (see [Security](#security)). The app comes with no warranty, and a TV update may change or fix the behaviour it works around.

## Install

By installing Fix Audio you accept the [Disclaimer](#disclaimer) above.

It takes about 10 minutes, and you only do it once. There are three ways to get the app onto the TV; pick one in step 2. The menu names below are from a TCL C7K; other TVs may word them slightly differently.

### Words you'll see

- **App file (`.apk`):** the Fix Audio app as a single file, like a photo or a document.
- **Developer options:** a hidden menu in the TV's Settings. Step 1 shows how to unlock it.
- **USB debugging:** a setting in Developer options. Despite the name, you don't need a USB cable. It lets Fix Audio press Home and switch inputs for you.
- **Unknown sources:** the TV setting that allows apps from outside the Play Store.

### Step 1: Turn on Developer options

1. On the Home screen, select the **gear icon** (top right), then **Settings**.
2. Go to **System → About**.
3. Scroll down to **Android TV OS Build** and press **OK** on the remote **7 times**. The TV shows **"You are now a developer!"**
4. Press **Back** to leave Settings.

Installing from a computer (option B) and automatic mode both need this. Google TV devices are also reported to need it before they let you install apps from outside the Play Store. On the C7K, the Settings code doesn't seem to check, but we haven't been able to test without it, so turn it on whichever option you choose.

### Step 2: Install the app

#### Option A: From a USB stick (simplest)

You need a computer, a USB stick, and the free **X-plore File Manager** app from the Play Store. (TCL's own Media Player app can't install apps on the C7K: TCL has switched that off.)

On the computer:

1. Go to the [latest release](../../releases/latest) and, under **Assets**, download **fix-audio.apk**. Optional: [check the download](#check-your-download).
2. Copy **fix-audio.apk** onto the USB stick, at the top level, not inside a folder. Most USB sticks already use a format the TV can read (FAT32 or exFAT).
3. Wait for the copy to finish, then **eject** the stick (on a Mac: the eject button next to it in Finder) and unplug it. Unplugging too early can leave an empty or broken file.

On the TV:

4. Open the **Google Play Store**, search for **X-plore**, and install **X-plore File Manager** by Lonely Cat Games. It's free, with ads.
5. Plug the USB stick into a USB port on the TV, and open **X-plore**.
6. Open the USB drive and select **fix-audio.apk**. On a Mac-made stick you may also see **._fix-audio.apk**; ignore that one.
7. The first time, the TV says: *"For your security, your TV currently isn't allowed to install unknown apps from this source. You can change this in Settings."* That's normal. Select **Settings**, switch **X-plore** on, and if the TV warns about unknown sources, select **OK**. Press **Back**, then select **fix-audio.apk** again.
8. The TV asks: *"Do you want to install this app?"* Select **Install**. When it says *"App installed."*, select **Done**. Unplug the USB stick.
9. Optional, for security: switch X-plore off again under **Settings → Apps → Security & Restrictions → Unknown sources**. Turn it back on when you install an update.

#### Option B: From a computer over the network

For people who already use **adb** (Android's command-line tool).

1. On the TV, go to **Settings → System → Developer options** and switch **USB debugging** on. The TV asks *"Allow USB debugging?"*: select **OK**. On the C7K, this also lets a computer connect over your home network. See [Security](#security).
2. Find the TV's IP address under **Settings → Network & Internet**, by selecting your network.
3. Download **fix-audio.apk** from the [latest release](../../releases/latest), then run:

   ```
   adb connect <tv-ip>
   adb install fix-audio.apk
   ```

4. On the TV, a second *"Allow USB debugging?"* appears, showing your computer's key. Select **Allow**.

#### Option C: On the TV only, with the Downloader app

No computer needed, but you have to type a long web address with the remote.

1. Open the **Google Play Store** on the TV, search for **Downloader**, and install **Downloader by AFTVnews**. It's free.
2. Let Downloader install apps: go to **Settings → Apps → Security & Restrictions → Unknown sources** and switch **Downloader** on. If the TV warns about unknown sources, select **OK**.
3. Open **Downloader**. If it asks for permission to access files, select **Allow**.
4. Select the address box, type this address exactly, then select **Go**:

   ```
   github.com/hemmachat/earc-audio-fix-for-tcl/releases/latest/download/fix-audio.apk
   ```

5. The TV asks: *"Do you want to install this app?"* Select **Install**. When it says *"App installed."*, select **Done**.
6. Downloader may offer to delete the downloaded file. Select **Delete**; you don't need it any more.

### Step 3: Open Fix Audio once

1. Go to **Settings → Apps → See all apps → Fix Audio → Open**. Next time you can find it in the **Apps** row on the Home screen.
2. Fix Audio shows *"Fixing the sound…"*, then returns to your player's input. It finds your player's input by itself. If it can't, it asks *"Which HDMI input is your Blu-ray player plugged into?"*: choose it once.

The **one-click fix is now ready**: whenever a film is silent, open Fix Audio. If that's all you want, you're done, and you can skip step 4.

### Step 4 (optional): Turn on automatic mode

Read [Security](#security) first. Automatic mode needs **USB debugging**, which on the TCL C7K also lets other devices on your home network ask to control the TV.

You'll see **two different pop-ups called "Allow USB debugging?"**. That's expected.

1. Go to **Settings → System → Developer options** and switch **USB debugging** on.
   **First pop-up:** *"Allow USB debugging?"* with *"USB debugging is intended for development purposes only…"*. This is the TV checking you meant it. Select **OK**.
2. Open **Fix Audio**. It shows *"One-time setup for automatic mode"* and a key, for example `3F:A2:07:…`.
3. **Second pop-up:** *"Allow USB debugging?"* with *"The computer's RSA key fingerprint is:"* and a key. The TV calls Fix Audio "this computer"; that's normal.
   - Check the key matches the one on Fix Audio's screen. If it doesn't, select **Cancel**.
   - Tick **Always allow from this computer**.
   - Select **Allow**.
4. Press **Back**. From now on, the app fixes silent films by itself within a few seconds.

### Updating

Install the new **fix-audio.apk** the same way. This time the TV asks *"Do you want to update this app?"*: select **Update**. Your settings are kept. **After an update, open Fix Audio once:** Android doesn't let an app start itself until it has been opened after installing. After that, automatic mode starts again by itself after updates and reboots. If it ever stops after standby, opening Fix Audio once starts it again.

## FAQ

### Why does my TCL TV play no sound from my Blu-ray player over eARC?
On the TCL C7K tested, the silence usually starts when the film begins, not on the menu. The TV sets up its eARC output for the menu's stereo PCM sound and doesn't update it when the film switches to Dolby TrueHD, Atmos or DTS. The soundbar receives a stream it can't decode. See [Why it happens](#why-it-happens).

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

### Is it safe to turn on USB debugging?
Read [Security](#security) first. If you only want the one-click button, leave USB debugging off.

### Why isn't it on the Google Play Store?
Automatic mode needs things Android doesn't allow normal store apps to do:

- **Read the TV's system log.** That's the only place the fault shows up. Android reserves log access (`READ_LOGS`) for system apps and developer tools: it's "not for use by third-party applications, because log entries can contain the user's private information" ([Android source](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android14-release/core/res/AndroidManifest.xml)).
- **Press Home and switch input from the background.** Sending key presses to other apps (`INJECT_EVENTS`) is limited to system apps, and since Android 10, apps in the background can only open screens in [a few listed cases](https://developer.android.com/guide/components/activities/background-starts).

So the app connects to the TV's own debugging port instead, which works only after **you** turn on Developer options and USB debugging. A store app can't do that for you.

Google Play would bring its own problems too:

- To restart after a reboot on a TCL TV, the app exempts itself from battery optimisation. Play's [Device and Network Abuse policy](https://support.google.com/googleplay/android-developer/answer/9888379) lists "apps that are not eligible for allowlisting and attempt to bypass system power management" as a violation.
- A background service in a Play TV app must use the [`specialUse` type](https://developer.android.com/develop/background-work/services/fgs/service-types), which Google reviews by hand.
- New personal developer accounts must first run a closed test with [12 testers for 14 days](https://support.google.com/googleplay/android-developer/answer/14151465).

Apps that work through adb are allowed on Play, so a store version isn't ruled out. It would probably need to drop the battery exemption. For now the app is only on [GitHub](../../releases/latest).

## Security

**USB debugging is powerful.** On the TCL C7K (and probably other TCL Google TVs) it also turns on debugging over your home network (port 5555), so with it on, any device on your network can *ask* to control the TV. One "Allow" gives that device full control (installing apps, reading the screen, and more).

- **Only press Allow right after opening Fix Audio.** The setup screen shows the app's key, for example `3F:A2:07:…`. The TV's prompt shows a key too, and the two must match. If an "Allow USB debugging?" pop-up with a key appears at any other time, and you aren't connecting from your own computer (install option B), select **Cancel**.
- **If you only want the one-click button**, leave USB debugging **off**. The one-click fix doesn't need it.
- **To undo all approvals:** **Developer options → Revoke USB debugging authorizations**.

What the app does to stay safe:

- It creates its own adb key on your TV. The key never leaves the app's private storage, and the app is excluded from backups.
- Any app can write to the Android log, so Fix Audio only trusts log lines written by system processes (`logcat -v uid`). Lines written by other apps are ignored.
- Before an HDMI input id is used in a command, the app checks that it looks like an HDMI input id and that it's on the TV's own list of inputs.
- If 2 automatic fixes in a row don't help, the app stops for 5 minutes, so it can't get stuck switching inputs.

The app had a security review before release. Six weaknesses were found and fixed, and none was directly exploitable. See [docs/security-review.md](docs/security-review.md) for each finding, its fix and how it was checked.

## Check your download

The release APK is signed with this certificate (SHA-256):

```
28:E0:61:19:71:10:AA:CB:EC:DE:EF:32:ED:1B:FF:45:96:E9:DD:EC:AB:BC:5E:81:4C:17:AF:CB:D6:A1:73:90
```

Check it with `apksigner verify --print-certs fix-audio.apk`. Each release also lists the APK's SHA-256. The build checks the checksums of Gradle and every dependency (`gradle/verification-metadata.xml`).

## Uninstall

TV **Settings → Apps → Fix Audio → Uninstall**, or `adb uninstall com.hemmachat.fixaudio`.

## Build

Needs Android SDK 34 and JDK 17.

```
./gradlew testDebugUnitTest assembleDebug
```

The unit tests replay a real log from the silent-TrueHD case ([silent-truehd.log](app/src/test/resources/silent-truehd.log)).

## Licence

MIT, see [LICENSE](LICENSE).

The APK includes these open source libraries, all under the [Apache License 2.0](https://www.apache.org/licenses/LICENSE-2.0): [dadb](https://github.com/mobile-dev-inc/dadb), [Okio](https://github.com/square/okio), the Kotlin standard library and JetBrains annotations.
