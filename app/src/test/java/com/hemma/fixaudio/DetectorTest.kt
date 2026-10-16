package com.hemma.fixaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {
    private fun tx(t: String, ch: Int, khz: Long, mode: String) = listOf(
        "10-16 $t I/        ( root:  369): [HAL_AUDIO_eARC_TxOutputSetSequence][9647]DIGITAL_TX eARC ATOP ChCount = $ch, Freq = $khz",
        "10-16 $t I/        ( root:  369): [HAL_AUDIO_eARC_TxOutputSetSequence][9758]u8eARC_mode == SPDIF_OUT_$mode",
    )

    private fun input(t: String, type: String, rate: Long, ch: Int, user: String = "mediacodec") =
        "10-16 $t I/C2MtkHdmiCap($user:  469): preCheck: Config update !!! outMediaType = $type, sampleRateInfo = $rate, channelCountInfo = $ch"

    private fun Detector.feed(lines: List<String>) = lines.count { onLine(it) }

    @Test
    fun realSilentTrueHdLogTriggersOneFix() {
        val d = Detector()
        val lines = javaClass.getResource("/silent-truehd.log")!!.readText().lines()
        assertEquals("no fix while the menu sound is playing", 0, d.feed(lines))
        // TrueHD detected at 20:33:59.250; no eARC setup follows
        assertTrue(d.tick(Detector.timeOf("10-16 20:34:02.000")!!))
        assertEquals("com.tcl.tvinput/.TvPassThroughService/HDMI200004", d.lastInput)
    }

    /** Replays log lines with a tick every second in between, like WatcherService. Returns fix times. */
    private fun Detector.replay(lines: List<String>): List<Long> {
        val fixes = mutableListOf<Long>()
        var clock: Long? = null
        for (line in lines) {
            val t = Detector.timeOf(line) ?: continue
            var c = clock ?: t
            while (c + 1000 <= t) { c += 1000; if (tick(c)) fixes += c }
            clock = c
            if (onLine(line)) fixes += t
        }
        clock?.let { c -> for (i in 1..10) if (tick(c + i * 1000)) fixes += c + i * 1000 }
        return fixes
    }

    @Test
    fun realWakeFromStandbyLogFixesOnceAfterTrueHd() {
        val lines = javaClass.getResource("/wake-from-standby.log")!!.readText().lines()
        val fixes = Detector().replay(lines)
        // TrueHD detected 21:02:47.822 with output stuck at 2ch 48kHz PCM; "audio/none" while loading must not trigger
        assertEquals(1, fixes.size)
        val trueHd = Detector.timeOf("10-16 21:02:47.822")!!
        assertTrue(fixes[0] in trueHd + 2500..trueHd + 3500) // 2.5 s grace + up to one 1 s tick
    }

    @Test
    fun realDtsLogFixesOnce() {
        val lines = javaClass.getResource("/dts-silent.log")!!.readText().lines()
        val fixes = Detector().replay(lines)
        // DTS detected 21:09:08.502 with output stuck at 2ch 48kHz PCM
        assertEquals(1, fixes.size)
        val dts = Detector.timeOf("10-16 21:09:08.502")!!
        assertTrue(fixes[0] in dts + 2500..dts + 3500)
    }

    @Test
    fun laterLineDoesNotHideAnOverdueFormat() {
        val d = Detector()
        d.feed(tx("21:10:00.000", 2, 48, "PCM"))
        d.onLine(input("21:10:26.376", "audio/vnd.dts", 192000, 2))
        // no ticks ran; the next line arrives 6 s later
        assertTrue(d.onLine(input("21:10:32.555", "audio/raw", 48000, 2)))
    }

    @Test
    fun linesWrittenByAppsOrShellAreIgnored() {
        val d = Detector()
        d.feed(tx("21:10:00.000", 2, 48, "PCM"))
        d.onLine(input("21:10:01.000", "audio/vnd.dts", 192000, 2, user = "u0_a123"))
        d.onLine(input("21:10:01.000", "audio/vnd.dts", 192000, 2, user = "shell"))
        assertFalse(d.tick(Detector.timeOf("10-16 21:10:10.000")!!))
    }

    @Test
    fun numericUsersAreTrustedOnlyBelowAppRange() {
        assertTrue(Detector.fromSystem("10-16 21:13:23.014 D/TvInputManagerService( 1000:  542): x"))
        assertTrue(Detector.fromSystem("10-16 21:13:23.014 I/        ( root:  369): x"))
        assertFalse(Detector.fromSystem("10-16 21:13:23.014 I/C2MtkHdmiCap( 2000:12730): x"))
        assertFalse(Detector.fromSystem("10-16 21:13:23.014 I/C2MtkHdmiCap(10123:12730): x"))
        assertFalse(Detector.fromSystem("10-16 21:13:23.014 I/C2MtkHdmiCap(u0_a123:12730): x"))
        assertFalse(Detector.fromSystem("10-16 21:13:23.014 I/C2MtkHdmiCap(  469): no user shown"))
    }

    @Test
    fun oversizedNumbersAreSkippedWithoutThrowing() {
        val d = Detector()
        d.feed(tx("21:10:00.000", 2, 48, "PCM"))
        d.onLine(input("21:10:01.000", "audio/vnd.dts", 192000, 2))
        d.feed(tx("21:10:01.500", 99999999, 999999999999, "NONPCM"))
        // the bad line didn't count as a matching eARC setup, and nothing threw
        assertTrue(d.tick(Detector.timeOf("10-16 21:10:05.000")!!))
    }

    @Test
    fun inputIdsWithShellCharactersAreNotRemembered() {
        val d = Detector()
        d.onLine("10-16 21:10:00.000 D/TvInputManagerService( 1000:  542): createSessionInternalLocked(inputId=a/b/HDMI1'x, sessionId=1)")
        assertEquals(null, d.lastInput)
        d.onLine("10-16 21:10:01.000 D/TvInputManagerService( 1000:  542): createSessionInternalLocked(inputId=com.tcl.tvinput/.TvPassThroughService/HDMI200004, sessionId=1)")
        assertEquals("com.tcl.tvinput/.TvPassThroughService/HDMI200004", d.lastInput)
    }

    @Test
    fun noAudioWhileLoadingIsLeftAlone() {
        val d = Detector()
        d.feed(tx("21:02:38.272", 2, 48, "PCM"))
        d.onLine(input("21:02:39.235", "audio/none", 0, 2))
        assertFalse(d.tick(Detector.timeOf("10-16 21:02:45.000")!!))
    }

    @Test
    fun trueHdWithMatchingOutputIsLeftAlone() {
        val d = Detector()
        d.feed(tx("20:35:00.000", 2, 48, "PCM"))
        d.onLine(input("20:35:01.000", "audio/vnd.dolby.mlp", 192000, 2))
        d.feed(tx("20:35:01.500", 8, 192, "NONPCM"))
        assertFalse(d.tick(Detector.timeOf("10-16 20:35:10.000")!!))
    }

    @Test
    fun trueHdOnA48kHzBitstreamOutputIsFixed() {
        val d = Detector()
        d.feed(tx("20:40:17.211", 2, 48, "NONPCM"))
        d.onLine(input("20:40:18.000", "audio/vnd.dolby.mlp", 192000, 2))
        assertTrue(d.tick(Detector.timeOf("10-16 20:40:21.000")!!))
    }

    @Test
    fun stereoPcmMenuIsLeftAlone() {
        val d = Detector()
        d.feed(tx("20:33:58.294", 2, 48, "PCM"))
        d.onLine(input("20:33:57.909", "audio/raw", 192000, 2))
        assertFalse(d.tick(Detector.timeOf("10-16 20:34:05.000")!!))
    }

    @Test
    fun stopsAfterTwoFixesInFiveMinutes() {
        val d = Detector()
        d.feed(tx("21:00:00.000", 2, 48, "PCM"))
        var fixes = 0
        for (min in 1..4) {
            d.onLine(input("21:0$min:00.000", "audio/vnd.dolby.mlp", 192000, 2))
            if (d.tick(Detector.timeOf("10-16 21:0$min:05.000")!!)) fixes++
        }
        assertEquals(2, fixes)
    }
}
