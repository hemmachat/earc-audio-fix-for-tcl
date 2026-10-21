package com.hemmachat.fixaudio

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Pairwise cases for picking the other id of the saved input's HDMI port, generated from app/src/test/pict/sameport.pict
 * with `pict sameport.pict /r:1 /e:sameport.seed` (the seed adds the two layouts the C7K really has).
 * Expected results come from the spec, not from running the code: a saved port gives its first CEC device,
 * a saved CEC device gives its port; nothing if there is none, it isn't listed, or it has shell characters.
 * Another port's device is never picked.
 */
class SamePortPairwiseTest {
    private val cases = listOf(
        // Saved PortCecDevices PartnerId Order OtherPort Expected
        "Port 1 Safe PortFirst None device1",
        "CecDevice 1 Safe DevicesFirst WithDevice port",
        "Port 0 Safe DevicesFirst WithDevice none",
        "Unlisted 2 Safe DevicesFirst None none",
        "CecDevice 2 NotListed PortFirst WithDevice none",
        "CecDevice 1 NotListed DevicesFirst None none",
        "Unlisted 0 Safe PortFirst None none",
        "Port 2 ShellChars PortFirst WithDevice none",
        "CecDevice 1 ShellChars DevicesFirst None none",
        "Unlisted 1 Safe PortFirst WithDevice none",
    )

    private val tv = "com.tcl.tvinput/.TvPassThroughService/"

    @Test
    fun pairwiseSamePortPicks() {
        val failures = cases.filter { case ->
            val (saved, devices, partner, order, otherPort, expected) = case.split(" ")
            val n = devices.toInt()
            // ShellChars applies to the partner: the port if a device is saved, else the first device
            val port = tv + if (partner == "ShellChars" && saved == "CecDevice") "HW16';reboot;'" else "HW16"
            val deviceIds = (1..n).map { tv + if (it == 1 && partner == "ShellChars" && saved == "Port") "HDMI2'0" else "HDMI20000$it" }
            val portRow = listOf(port to null)
            val deviceRows = deviceIds.map { it to port }
            val others = if (otherPort == "WithDevice") listOf("${tv}HW17" to null, "${tv}HDMI300004" to "${tv}HW17") else emptyList()
            val ports = if (partner == "NotListed") emptyList() else portRow
            val hdmi = others + if (order == "PortFirst") ports + deviceRows else deviceRows + ports
            val input = when (saved) {
                "Port" -> port
                "CecDevice" -> deviceIds.first()
                else -> "${tv}HW18"
            }
            val want = when (expected) {
                "device1" -> deviceIds.first()
                "port" -> port
                else -> null
            }
            MainActivity.samePort(hdmi, input) != want
        }
        assertEquals("cases with the wrong result", emptyList<String>(), failures)
    }

    @Test
    fun realC7kListSwitchesBothWays() {
        // as the C7K lists them on 2026-10-22: 4 ports, the UB450 as a CEC device on HDMI 2
        val hdmi = listOf("HW15", "HW16", "HW17", "HW18").map { "$tv$it" to null } + ("${tv}HDMI200004" to "${tv}HW16")
        assertEquals("${tv}HW16", MainActivity.samePort(hdmi, "${tv}HDMI200004"))
        assertEquals("${tv}HDMI200004", MainActivity.samePort(hdmi, "${tv}HW16"))
        assertEquals("a port without a device falls back to Home", null, MainActivity.samePort(hdmi, "${tv}HW17"))
    }

    private operator fun <T> List<T>.component6() = this[5]
}
