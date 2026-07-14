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
    fun calculatesPixel9HealthFromEstimatedCapacity() {
        val report = parser.parse(
            text = """
                [ro.product.model]: [Pixel 9]
                [ro.product.device]: [tokay]
                Estimated battery capacity: 4,230.0 mAh
                mSavedBatteryAsoc=77
            """.trimIndent(),
        )

        assertEquals(PixelModel.Pixel9, report.pixelModel)
        assertEquals(4700, report.designCapacityMah)
        assertEquals(4230, report.estimatedCapacityMah)
        assertEquals(90.0, report.healthPercent!!, 0.01)
    }

    @Test
    fun capsCapacityAboveTypicalRatingAtOneHundredPercent() {
        val report = parser.parse(
            text = """
                [ro.product.model]: [Pixel 9]
                Estimated battery capacity: 4877 mAh
                Last learned battery capacity: 4877 mAh
            """.trimIndent(),
        )

        assertEquals(4877, report.estimatedCapacityMah)
        assertEquals(4700, report.designCapacityMah)
        assertEquals(100.0, report.healthPercent!!, 0.01)
        assertEquals(true, report.exceedsTypicalCapacity)
        assertEquals(BatterySummaryStatus.Excellent, report.summaryStatus)
    }

    @Test
    fun detectsPixel9FromBuildFingerprintAndZipName() {
        val fingerprintReport = parser.parse(
            text = "[ro.build.fingerprint]: [google/tokay/tokay:15/AP4A.250205.002/1234567:user/release-keys]",
        )
        val filenameReport = parser.parse(
            text = "Estimated battery capacity: 4558 mAh",
            sourceNames = listOf("bugreport-tokay-AP4A.250205.002-2026-07-14.zip"),
        )

        assertEquals(PixelModel.Pixel9, fingerprintReport.pixelModel)
        assertEquals(PixelModel.Pixel9, filenameReport.pixelModel)
    }

    @Test
    fun contentModelOverridesMisleadingFilename() {
        val report = parser.parse(
            text = "[ro.product.model]: [Pixel 9]",
            sourceNames = listOf("old-pixel-7-pro-notes.zip"),
        )

        assertEquals(PixelModel.Pixel9, report.pixelModel)
    }

    @Test
    fun explicitModelPropertyBeatsLaterFingerprintMention() {
        val report = parser.parse(
            text = """
                [ro.product.model]: [Pixel 9]
                crash attachment build fingerprint: google/cheetah/cheetah:16/test
            """.trimIndent(),
        )

        assertEquals(PixelModel.Pixel9, report.pixelModel)
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

    @Test
    fun readsStateOnlyFromBatteryServiceAndDoesNotGuessCycles() {
        val report = parser.parse(
            text = """
                DUMP OF SERVICE thermalservice:
                  temperature: 810
                  voltage: 9999
                mSavedBatteryUsage=785
                DUMP OF SERVICE battery:
                Current Battery Service state:
                  health: 2
                  voltage: 4012
                  temperature: 287
            """.trimIndent(),
        )

        assertNull(report.cycleCount)
        assertEquals(AndroidBatteryHealth.Good, report.androidHealth)
        assertEquals(28.7, report.temperatureCelsius!!, 0.01)
        assertEquals("4012 mV", report.voltageText)
    }

    @Test
    fun ignoresInvalidCapacityAndAsocValues() {
        val report = parser.parse(
            text = """
                Product: tokay
                Estimated battery capacity: 47000 mAh
                mSavedBatteryAsoc=-1
            """.trimIndent(),
        )

        assertNull(report.estimatedCapacityMah)
        assertNull(report.batteryAsoc)
        assertNull(report.healthPercent)
    }

    @Test(expected = kotlinx.coroutines.CancellationException::class)
    fun parsingDoesNotSwallowCancellation() {
        parser.parse(
            text = "Estimated battery capacity: 4877 mAh",
            checkCancelled = { throw kotlinx.coroutines.CancellationException("cancelled") },
        )
    }
}
