# Security review

Fix Audio was reviewed before its first public release (October 2026). Each finding was checked by tracing the input from where it comes in to where it's used, and the fixes were tested on a TCL C7K.

Nothing directly exploitable was found. Six weaknesses were fixed, plus one problem that came up while fixing them.

## Summary

| # | Severity | Finding | Fix |
|---|---|---|---|
| 1 | Medium | The TV's debugging approval prompt looks the same for this app and for any other device | The setup screen shows the app's key, and the README says to deny any other prompt |
| 2 | Low–medium | The app's adb key could leave the TV in a backup | Backups turned off |
| 3 | Low | Any app could fake the log lines the app acts on | Only lines written by system processes are trusted |
| 4 | Low | A fake log line with a huge number could stop automatic mode | Bad lines are skipped |
| 5 | Low | The HDMI input id was put into a shell command without checking it | The id must have a safe format and be on the TV's own input list |
| 6 | Info | Downloads and build tools couldn't be verified | Signing certificate published, and Gradle and every dependency checksum-checked |
| 7 | — | Fix 3 at first also rejected the TV's own system lines | System user ids below 10000 are trusted, except the adb shell |

## Details

### 1. The approval prompt can be confused with someone else's

Automatic mode needs **Network debugging**, which accepts connections on port 5555 from any device on the home network. Each device still has to be approved on the TV, but the "Allow USB debugging?" prompt looks the same whoever asks, and one approval gives full control of the TV.

**Fix:** the setup screen shows the app's key fingerprint (MD5, the same format as the TV's prompt), so you can check that the two match. The README tells you to press **Deny** on any prompt that appears when you haven't just opened Fix Audio, how to revoke approvals, and to leave Network debugging off if you only use the one-click button.

**Checked:** the fingerprint on the setup screen matched the TV's own record of the approved key.

### 2. The adb key could be copied off the TV by backups

The app creates its own adb key, which the TV approves for full control. Android backups were allowed by default, so the key could end up in a cloud backup or on another device. Whoever had it, on the same network, could control the TV without a prompt.

**Fix:** `android:allowBackup="false"` in [AndroidManifest.xml](../app/src/main/AndroidManifest.xml).

**Checked:** the TV no longer lists the app as allowing backup.

### 3. Any app can fake the log lines the app trusts

Writing to the Android log needs no permission. A malicious app could write the "silent audio" pattern to make the app jump to Home and back (up to 2 times in 5 minutes), or write a fake "input opened" line to change the HDMI input the app switches to.

**Fix:** the app reads the log with `logcat -v uid`, which shows who wrote each line, and the detector ignores lines from apps (`u0_a123`), app uids (10000 and above) and the adb shell (`shell`, 2000). Apps can't change who a line is shown as written by. See `fromSystem` in [Detector.kt](../app/src/main/java/com/hemma/fixaudio/Detector.kt).

**Checked:** a fake silent pattern written on the TV did not trigger a fix. Covered by the unit tests `linesWrittenByAppsOrShellAreIgnored` and `numericUsersAreTrustedOnlyBelowAppRange`.

### 4. An oversized number could switch off automatic mode

A line such as `ChCount = 99999999999` made the number conversion throw, which dropped the log connection for 5 seconds. Repeated every few seconds, this would keep automatic mode off. Fix 3 already blocks fake lines; this fix makes the parser safe on its own.

**Fix:** `toIntOrNull()`, so bad lines are skipped.

**Checked:** after the fake line was written on the TV, the connection stayed up. Unit test `oversizedNumbersAreSkippedWithoutThrowing`.

### 5. The input id went into a shell command unchecked

The HDMI input id can come from the log (see 3) and is placed inside quotes in the `am start -d '…'` command. Android's URI encoding doesn't encode `'`, so a crafted id could break out of the quotes. It couldn't form a new command, because spaces, `;`, `&`, `|`, `$` and backticks are encoded, but that was safe only by luck.

**Fix:** an input id is used only if it matches `[\w.]+/[\w.]+/\w+` (no quotes or shell characters) and the TV lists it as one of its own HDMI inputs. See `INPUT_ID` in [Detector.kt](../app/src/main/java/com/hemma/fixaudio/Detector.kt) and `isValidInput` in [MainActivity.kt](../app/src/main/java/com/hemma/fixaudio/MainActivity.kt).

**Checked:** unit test `inputIdsWithShellCharactersAreNotRemembered` rejects `HDMI1'x` and accepts the real ids `HDMI200004` and `HW16`.

### 6. Downloads and build tools couldn't be verified

People had no way to check that the APK they downloaded is the published one, and the build didn't check the tools and libraries it downloads.

**Fix:**

- The README lists the release signing certificate's SHA-256, and each release lists the APK's SHA-256 (see [Check your download](../README.md#check-your-download)).
- The Gradle wrapper is pinned with `distributionSha256Sum`.
- Gradle dependency verification ([verification-metadata.xml](../gradle/verification-metadata.xml)) checks the checksum of every dependency. The build fails if one changes.

**Checked:** the recorded checksums for dadb (the adb library) and Android's aapt2 tool match the ones on Maven Central and Google's Maven repository.

### 7. Problem found while testing fix 3

The TV's main system process (system_server) shows up in the log as the number `1000` instead of a name. The first version of fix 3 treated every number as untrusted, so the app would have quietly stopped learning which HDMI input you use.

**Fix:** numeric user ids below 10000 are trusted, except 2000 (the adb shell). Unit tests now use real `( 1000:  542)` lines.

## Checked and fine

- **No secrets in the repo.** The release signing key and its passwords are kept out of git. The adb key is created on each TV and isn't built into the APK.
- **Release build** is signed and not debuggable.
- **The background service** can't be started by other apps.
- **Other apps can open the Fix Audio screen** or send it the boot signal. That only does what pressing Home and reselecting the input already does.
- **No data collected.** The app's only network connection is to the TV itself (`127.0.0.1`).

## Trade-off

Because faked log lines are now ignored, automatic mode can't be tested by writing fake lines on the TV. It's tested with real silences and with unit tests that replay four real logs from the C7K (TrueHD, DTS, wake from standby, and disc menu to DTS film).

## Reporting a problem

Found a security problem? Please [open an issue](../../../issues) without exploit details and ask for a private contact, or use GitHub's private vulnerability reporting if it's turned on for this repo.
