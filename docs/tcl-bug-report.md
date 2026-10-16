# Bug report for TCL: eARC output not reconfigured when the HDMI input's audio format changes

For TCL / MediaTek engineers, and for anyone reporting this to TCL support. The logs below come from one TV. They're shared to help fix the firmware so that [Fix Audio](../README.md) is no longer needed.

## Summary

When the audio format on an HDMI input changes during playback (for example, a Blu-ray menu in stereo PCM changes to a Dolby TrueHD or DTS film), the TV detects the new format, but **doesn't run the eARC TX output setup again** (`HAL_AUDIO_eARC_TxOutputSetSequence`). The eARC link stays set up for the previous format, usually 2ch / 48 kHz PCM, and the eARC soundbar plays **silence**.

Re-opening the HDMI input (Home, then select the input again) runs the setup for the current format, and the sound returns at once.

## Environment

| | |
|---|---|
| TV | TCL C7K, Google TV, Android 14 (SDK 34), MediaTek |
| Firmware | V8-T653T01-LF1V665 |
| TV sound output | eARC on, Digital Audio Out = Atmos (`tcl_sys_audio_spdifmode_hdmi_arc` = 4). Also seen with PCM (1). |
| Source | Panasonic DP-UB450 on HDMI 2 (`com.tcl.tvinput/.TvPassThroughService/HW16`, CEC device `HDMI200004`); Dolby and DTS output = Bitstream |
| Sink | Samsung HW-Q930D on HDMI 1 (eARC) |

## Steps to reproduce

Any of these, with the source set to Bitstream:

1. **Disc start:** select the HDMI input while the disc menu plays stereo PCM, then start a film with a TrueHD or DTS soundtrack.
2. **Wake from standby** with a TrueHD disc loaded, then start the film.
3. **Change the audio language** during playback, for example English TrueHD to another language in DTS, and back.

**Expected:** the eARC output is set up again for the new format (for TrueHD: `ChCount = 8, Freq = 192, SPDIF_OUT_NONPCM`), and the soundbar plays it.

**Actual:** `C2MtkHdmiCap` reports the new format, but no `HAL_AUDIO_eARC_TxOutputSetSequence` follows. The output stays at `ChCount = 2, Freq = 48, SPDIF_OUT_PCM`, and the soundbar is silent. It doesn't happen every time.

## Evidence

### Case 3, language switch: 68 seconds of silence, then recovery by re-opening the input

From [03-language-switch-and-recovery.log](logs/03-language-switch-and-recovery.log):

```
07:53:07.383  HAL_AUDIO_eARC_TxOutputSetSequence  ChCount = 2, Freq = 48 / SPDIF_OUT_PCM
07:53:07.399  C2MtkHdmiCap  Config update  outMediaType = audio/vnd.dolby.mlp, sampleRateInfo = 192000
              (no TxOutputSetSequence for 68 seconds; soundbar silent)
07:54:15.201  TvInputManagerService  createSessionInternalLocked(inputId=.../HDMI200004)   <- input re-opened
07:54:15.810  C2MtkHdmiCap  Config update  outMediaType = audio/vnd.dolby.mlp, sampleRateInfo = 192000
07:54:16.367  HAL_AUDIO_eARC_TxOutputSetSequence  ChCount = 8, Freq = 192 / SPDIF_OUT_NONPCM   <- sound back
```

The same file shows the problem with DTS (07:52:38, 07:56:20, 07:57:46) and TrueHD (07:56:53, 07:58:11). Each time the input was re-opened, the output was set up correctly and the sound returned. Lines tagged `FixAudio` are from our workaround app.

### Case 1, disc start: TrueHD on a 2ch PCM output

From [01-truehd-silent.log](logs/01-truehd-silent.log):

```
20:33:58.294  HAL_AUDIO_eARC_TxOutputSetSequence  ChCount = 2, Freq = 48 / SPDIF_OUT_PCM   <- menu sound
20:33:59.250  C2MtkHdmiCap  Config update  outMediaType = audio/vnd.dolby.mlp, sampleRateInfo = 192000
20:33:59.308  audio_hw_primary  utils_force_recover_digital_output_format_for_bypass: EarcOutput Force recover topology for bypass case.
              (no TxOutputSetSequence follows)
```

`Force recover topology for bypass case` runs, but it doesn't lead to a new eARC TX setup.

### Case 1 with DTS

From [02-dts-silent.log](logs/02-dts-silent.log):

```
21:09:07.664  HAL_AUDIO_eARC_TxOutputSetSequence  ChCount = 2, Freq = 48 / SPDIF_OUT_PCM
21:09:08.502  C2MtkHdmiCap  Config update  outMediaType = audio/vnd.dts, sampleRateInfo = 192000
21:09:08.559  audio_hw_primary  EarcOutput Force recover topology for bypass case.
              (no TxOutputSetSequence follows)
```

Logs 01 and 02 end about a second after the failure. Log 03 is a live recording and shows how long the silence lasts.

### Things we ruled out

- **The TV's Digital Audio Out setting (PCM or Atmos)** isn't the cause. Changing it in the menu sometimes brings the sound back, but only because it sometimes triggers a new eARC setup. In our logs, 2 out of 3 menu changes never reached the audio driver.
- **Setting `tcl_sys_audio_spdifmode_hdmi_arc` with `settings put`** doesn't trigger a new eARC setup.
- **The eARC link** stays connected the whole time; the soundbar keeps showing eARC.

## Likely cause

The eARC TX output is configured when an HDMI input session starts, and on some setting changes, but not when `C2MtkHdmiCap` reports a format change within the same session. The bypass "force recover" path runs on the format change, but doesn't call `HAL_AUDIO_eARC_TxOutputSetSequence` for the new format.

**Suggested fix:** when the HDMI input's audio format, sample rate or channel count changes, run the eARC TX output setup for the new format, the same way it's done when the input is opened.

## Workaround

Press Home, then select the HDMI input again. Over adb:

```
adb shell input keyevent KEYCODE_HOME
adb shell am start -a android.intent.action.VIEW -d "content://android.media.tv/passthrough/com.tcl.tvinput%2F.TvPassThroughService%2FHDMI200004"
```

[Fix Audio](../README.md) does this automatically when it sees the fault in the log.

## How these logs were captured and filtered

Captured with `adb logcat -b all -v time` (log 03: `-v time,uid`, recorded live). To keep personal data out, the published files contain only lines from audio and HDMI components: `audio_hw_primary`, `audio_hw_tcl`, `thal_audio`, `C2MtkHdmiCap`, `TclSoundSetting`, `TclAudioServiceEx`, `TvInputManagerService` session lines, the eARC driver (`HAL_AUDIO_eARC_*`, `[eARC]`, `MDrv_AUDIO_HDMI_ARC_*`), eARC lines from `HdmiControlService`, and `FixAudio`. Full logs are available on request.

To capture your own: turn on USB debugging, run `adb logcat -b all -v time > tv.log`, reproduce the silence, and stop after about 30 seconds.

| File | What it shows |
|---|---|
| [01-truehd-silent.log](logs/01-truehd-silent.log) | Disc menu (PCM) to TrueHD film, 16 Oct 2026 |
| [02-dts-silent.log](logs/02-dts-silent.log) | Disc menu (PCM) to DTS film, 16 Oct 2026 |
| [03-language-switch-and-recovery.log](logs/03-language-switch-and-recovery.log) | Repeated audio language changes, the failures and each recovery, 17 Oct 2026 |
