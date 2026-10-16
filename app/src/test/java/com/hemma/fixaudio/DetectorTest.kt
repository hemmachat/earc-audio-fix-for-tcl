package com.hemma.fixaudio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorTest {
    private fun tx(t: String, ch: Int, khz: Int, mode: String) = listOf(
        "10-16 $t I/        (  369): [HAL_AUDIO_eARC_TxOutputSetSequence][9647]DIGITAL_TX eARC ATOP ChCount = $ch, Freq = $khz",
        "10-16 $t I/        (  369): [HAL_AUDIO_eARC_TxOutputSetSequence][9758]u8eARC_mode == SPDIF_OUT_$mode",
    )

    private fun input(t: String, type: String, rate: Int, ch: Int) =
        "10-16 $t I/C2MtkHdmiCap(  469): preCheck: Config update !!! outMediaType = $type, sampleRateInfo = $rate, channelCountInfo = $ch"

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
