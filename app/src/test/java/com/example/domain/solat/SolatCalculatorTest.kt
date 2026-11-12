package com.example.domain.solat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class SolatCalculatorTest {

    @Test
    fun qiblaBearing_globalCities_matchExpectedAzimuths() {
        val kl = SolatCalculator.qiblaBearingDegrees(3.1390, 101.6869)
        assertEquals(292.5, kl, 1.5)

        val london = SolatCalculator.qiblaBearingDegrees(51.5072, -0.1276)
        assertEquals(119.0, london, 1.5)

        val nyc = SolatCalculator.qiblaBearingDegrees(40.7128, -74.0060)
        assertEquals(58.5, nyc, 1.5)
    }

    @Test
    fun calculateDaySchedule_kualaLumpur_ordersAllSixTimesChronologically() {
        val tz = TimeZone.getTimeZone("Asia/Kuala_Lumpur")
        val cal = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 12, 12, 0, 0)
        }
        val schedule = SolatCalculator.calculateDaySchedule(
            calendar = cal,
            latitude = 3.1390,
            longitude = 101.6869,
            timeZone = tz,
            locationLabel = "Kuala Lumpur, Malaysia",
            method = CalculationMethod.JAKIM
        )

        assertEquals(6, schedule.entries.size)
        val minutes = schedule.entries.map { it.time.totalMinutes }
        for (i in 0 until minutes.lastIndex) {
            assertTrue("Expected ${schedule.entries[i].name} < ${schedule.entries[i + 1].name}", minutes[i] < minutes[i + 1])
        }
        assertTrue(schedule.hijriLabel.endsWith("AH"))
    }
}
