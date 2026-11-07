package com.senzaproject.gamespace.core.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringMathTest {
    // ---- /proc/stat ----

    @Test fun procStat_sumsFieldsAndTreatsIowaitAsIdle() {
        val text = "cpu  100 0 50 800 50 0 0 0 0 0\ncpu0 50 0 25 400 25 0 0 0 0 0\n"
        assertEquals(CpuTimes(idle = 850, total = 1000), parseProcStat(text))
    }

    @Test fun procStat_rejectsBadInput() {
        assertNull(parseProcStat(""))
        assertNull(parseProcStat("intr 1 2 3"))
        assertNull(parseProcStat("cpu  1 2"))
    }

    @Test fun cpuUsage_isBusyShareBetweenSamples() {
        val previous = CpuTimes(idle = 850, total = 1000)
        val current = CpuTimes(idle = 900, total = 1100)
        assertEquals(50, cpuUsagePercent(previous, current))
    }

    @Test fun cpuUsage_isNullWhenCountersDoNotAdvance() {
        val sample = CpuTimes(idle = 10, total = 100)
        assertNull(cpuUsagePercent(sample, sample))
        assertNull(cpuUsagePercent(CpuTimes(idle = 50, total = 100), CpuTimes(idle = 40, total = 200)))
    }

    @Test fun processCpu_isShareOfAllCores() {
        assertEquals(12.5f, processCpuPercent(500, 1000, 4)!!, 0.001f)
        assertEquals(100f, processCpuPercent(9000, 1000, 4)!!, 0.001f)
    }

    @Test fun processCpu_rejectsInvalidIntervals() {
        assertNull(processCpuPercent(10, 0, 4))
        assertNull(processCpuPercent(10, 1000, 0))
        assertNull(processCpuPercent(-1, 1000, 4))
    }

    // ---- /proc/cpuinfo, cpufreq, sysfs ----

    @Test fun cpuHardware_armUsesHardwareLine() {
        val text = "Processor\t: AArch64 Processor rev 0\nHardware\t: Qualcomm Technologies, Inc SM8550\n"
        assertEquals("Qualcomm Technologies, Inc SM8550", parseCpuHardware(text))
    }

    @Test fun cpuHardware_x86UsesModelName() {
        assertEquals("Intel(R) Core(TM) i5", parseCpuHardware("model name\t: Intel(R) Core(TM) i5\n"))
    }

    @Test fun cpuHardware_missingIsNull() {
        assertNull(parseCpuHardware("processor\t: 0\nBogoMIPS\t: 38.40\n"))
    }

    @Test fun khzToMhz_convertsAndRejectsBadValues() {
        assertEquals(1804, khzToMhz("1804800\n"))
        assertNull(khzToMhz("0"))
        assertNull(khzToMhz("abc"))
        assertNull(khzToMhz(null))
    }

    @Test fun parseLeadingInt_readsFirstNumber() {
        assertEquals(12, parseLeadingInt("12 %"))
        assertNull(parseLeadingInt("n/a"))
        assertNull(parseLeadingInt(null))
    }

    @Test fun zoneTemperature_handlesMilliDegreesAndDegrees() {
        assertEquals(42f, normalizeZoneTemperature(42000)!!, 0.001f)
        assertEquals(38f, normalizeZoneTemperature(38)!!, 0.001f)
        assertEquals(-5f, normalizeZoneTemperature(-5000)!!, 0.001f)
    }

    @Test fun zoneTemperature_rejectsImplausibleValues() {
        assertNull(normalizeZoneTemperature(900_000))
        assertNull(normalizeZoneTemperature(-200_000))
    }

    @Test fun vulkanVersion_decodesPackedValue() {
        assertEquals("1.3", vulkanVersionString((1 shl 22) or (3 shl 12)))
        assertEquals("1.1", vulkanVersionString((1 shl 22) or (1 shl 12) or 82))
        assertNull(vulkanVersionString(0))
    }

    @Test fun appendCapped_keepsNewestValues() {
        assertEquals(listOf(2, 3, 4), appendCapped(listOf(1, 2, 3), 4, 3))
        assertEquals(listOf(1), appendCapped(emptyList(), 1, 3))
    }

    // ---- battery and thermal ----

    @Test fun powerSource_mapsPluggedFlags() {
        assertEquals(PowerSource.NONE, powerSourceOf(0))
        assertEquals(PowerSource.AC, powerSourceOf(1))
        assertEquals(PowerSource.USB, powerSourceOf(2))
        assertEquals(PowerSource.WIRELESS, powerSourceOf(4))
        assertEquals(PowerSource.AC, powerSourceOf(3))
        assertEquals(PowerSource.UNKNOWN, powerSourceOf(16))
    }

    @Test fun batteryHealth_mapsCodes() {
        assertEquals(BatteryHealth.GOOD, batteryHealthOf(2))
        assertEquals(BatteryHealth.OVERHEAT, batteryHealthOf(3))
        assertEquals(BatteryHealth.COLD, batteryHealthOf(7))
        assertEquals(BatteryHealth.UNKNOWN, batteryHealthOf(1))
        assertEquals(BatteryHealth.UNKNOWN, batteryHealthOf(99))
    }

    @Test fun thermalLevel_mapsAndroidStatus() {
        assertEquals(ThermalLevel.NONE, thermalLevelOf(0))
        assertEquals(ThermalLevel.SEVERE, thermalLevelOf(3))
        assertEquals(ThermalLevel.SHUTDOWN, thermalLevelOf(6))
        assertEquals(ThermalLevel.UNKNOWN, thermalLevelOf(-1))
        assertEquals(ThermalLevel.UNKNOWN, thermalLevelOf(7))
    }

    @Test fun thermalWarning_startsAtModerate() {
        assertFalse(isThermalWarning(ThermalLevel.NONE))
        assertFalse(isThermalWarning(ThermalLevel.LIGHT))
        assertTrue(isThermalWarning(ThermalLevel.MODERATE))
        assertTrue(isThermalWarning(ThermalLevel.SHUTDOWN))
        assertFalse(isThermalWarning(ThermalLevel.UNKNOWN))
    }

    // ---- network ----

    @Test fun networkType_prefersTheRealTransportOverVpn() {
        assertEquals(NetworkType.WIFI, networkTypeOf(wifi = true, cellular = false, ethernet = false, bluetooth = false, vpn = true))
        assertEquals(NetworkType.CELLULAR, networkTypeOf(false, true, false, false, false))
        assertEquals(NetworkType.VPN, networkTypeOf(false, false, false, false, true))
        assertEquals(NetworkType.OTHER, networkTypeOf(false, false, false, false, false))
    }

    @Test fun bytesPerSecond_scalesByInterval() {
        assertEquals(2000L, bytesPerSecond(1000, 3000, 1000))
        assertEquals(4000L, bytesPerSecond(1000, 3000, 500))
    }

    @Test fun bytesPerSecond_isNullForUnsupportedOrResetCounters() {
        assertNull(bytesPerSecond(-1, 3000, 1000))
        assertNull(bytesPerSecond(1000, -1, 1000))
        assertNull(bytesPerSecond(5000, 3000, 1000))
        assertNull(bytesPerSecond(1000, 3000, 0))
    }

    @Test fun ping_summaryCountsLossAndJitter() {
        val stats = summarizePing(listOf(10, 20, null, 30))
        assertEquals(4, stats.sent)
        assertEquals(3, stats.received)
        assertEquals(25, stats.lossPercent)
        assertEquals(10, stats.minMs)
        assertEquals(20, stats.avgMs)
        assertEquals(30, stats.maxMs)
        assertEquals(10, stats.jitterMs)
    }

    @Test fun ping_emptyAndAllLost() {
        val empty = summarizePing(emptyList())
        assertEquals(0, empty.sent)
        assertEquals(0, empty.lossPercent)
        assertNull(empty.avgMs)

        val lost = summarizePing(listOf(null, null))
        assertEquals(100, lost.lossPercent)
        assertNull(lost.minMs)
        assertNull(lost.jitterMs)
    }

    @Test fun ping_jitterNeedsTwoReplies() {
        assertNull(summarizePing(listOf(15, null)).jitterMs)
    }
}
