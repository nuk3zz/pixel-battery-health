package com.pixelbatteryhealth.data

import com.pixelbatteryhealth.domain.AndroidBatteryHealth
import com.pixelbatteryhealth.domain.BatterySummaryStatus
import com.pixelbatteryhealth.domain.PixelModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryBugreportParserTest {
    private val parser = BatteryBugreportParser()

    @Test
    fun parsesRequiredBatteryFields() {
        val report = parser.parse(
            text = """
                BUGREPORT
                Product: cheetah
                Estimated battery capacity: 4212 mAh
                Last learned battery capacity: 4212 mAh
                Min learned battery capacity: 4204 mAh
                Max learned battery capacity: 4214 mAh
                android.os.extra.CYCLE_COUNT=785
                health=2
                temperature: 312
                voltage: 3860
            """.trimIndent(),
        )

        assertEquals(PixelModel.Pixel7Pro, report.pixelModel)
        assertEquals(4212, report.estimatedCapacityMah)
        assertEquals(4212, report.lastLearnedCapacityMah)
        assertEquals(4204, report.minLearnedCapacityMah)
        assertEquals(4214, report.maxLearnedCapacityMah)
        assertEquals(785, report.cycleCount)
        assertEquals(AndroidBatteryHealth.Good, report.androidHealth)
        assertEquals(31.2, report.temperatureCelsius!!, 0.01)
        assertEquals("3860 mV", report.voltageText)
        assertEquals(BatterySummaryStatus.Good, report.summaryStatus)
        assertEquals(84.24, report.healthPercent!!, 0.01)
    }

    @Test
    fun prefersLongerPixelModelNames() {
        val report = parser.parse("Product: Pixel 9 Pro XL\nEstimated battery capacity: 4900 mAh")

        assertEquals(PixelModel.Pixel9ProXl, report.pixelModel)
        assertEquals(96.8, report.healthPercent!!, 0.1)
    }

    @Test
    fun handlesUnknownModelWithoutGuessingHealthPercent() {
        val report = parser.parse("Estimated battery capacity: 4200 mAh")

        assertNull(report.pixelModel)
        assertNull(report.designCapacityMah)
        assertNull(report.healthPercent)
        assertEquals(BatterySummaryStatus.Unknown, report.summaryStatus)
    }
    
    @Test
    fun ignoresRandomModelMentionsInLogs() {
        val report = parser.parse(
            text = """
                some random log line mentioning Pixel 4
                Product: panther
                Estimated battery capacity: 4000 mAh
            """.trimIndent()
        )
        
        assertEquals(PixelModel.Pixel7, report.pixelModel)
    }
}
