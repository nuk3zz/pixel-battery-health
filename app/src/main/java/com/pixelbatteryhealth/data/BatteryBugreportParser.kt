package com.pixelbatteryhealth.data

import com.pixelbatteryhealth.domain.AndroidBatteryHealth
import com.pixelbatteryhealth.domain.BatteryReport
import java.io.File
import kotlin.math.roundToInt

class BatteryBugreportParser(
    private val modelDetector: PixelModelDetector = PixelModelDetector(),
) {
    fun parse(file: File, sourceNames: List<String> = emptyList()): BatteryReport =
        file.useLines { lines -> parse(lines, sourceNames) }

    fun parse(text: String, sourceNames: List<String> = emptyList()): BatteryReport =
        parse(text.lineSequence(), sourceNames)

    fun parse(lines: Sequence<String>, sourceNames: List<String> = emptyList()): BatteryReport {
        var estimatedCapacityMah: Int? = null
        var lastLearnedCapacityMah: Int? = null
        var minLearnedCapacityMah: Int? = null
        var maxLearnedCapacityMah: Int? = null
        var designCapacityMah: Int? = null
        var cycleCount: Int? = null
        var androidHealth: Int? = null
        var temperatureRaw: Double? = null
        var fallbackTemperatureRaw: Double? = null
        var batteryServiceTemperatureRaw: Double? = null
        var voltageRaw: Long? = null
        var fallbackVoltageRaw: Long? = null
        var batteryServiceVoltageRaw: Long? = null
        var batteryAsoc: Int? = null
        
        var modelMatch = modelDetector.detectFromSourceNames(sourceNames)
        var inBatteryServiceSection = false

        val capacityNumber = "([0-9][0-9,.]*)"
        val estimatedRegex = Regex("Estimated battery capacity\\s*:\\s*$capacityNumber\\s*mAh", RegexOption.IGNORE_CASE)
        val lastLearnedRegex = Regex("Last learned battery capacity\\s*:\\s*$capacityNumber\\s*mAh", RegexOption.IGNORE_CASE)
        val minLearnedRegex = Regex("Min learned battery capacity\\s*:\\s*$capacityNumber\\s*mAh", RegexOption.IGNORE_CASE)
        val maxLearnedRegex = Regex("Max learned battery capacity\\s*:\\s*$capacityNumber\\s*mAh", RegexOption.IGNORE_CASE)
        val designRegex = Regex("(?:Design\\s+capacity|Battery\\s+design\\s+capacity|original\\s+design\\s+capacity)\\s*[:=]\\s*$capacityNumber(?:\\s*mAh)?", RegexOption.IGNORE_CASE)
        val cycleCountRegex = Regex("(?:android\\.os\\.extra\\.CYCLE_COUNT|batteryCycleCount|cycle[_ ]count)\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val asocRegex = Regex("mSavedBatteryAsoc\\s*[:=]\\s*(-?\\d+)", RegexOption.IGNORE_CASE)
        val explicitHealthRegex = Regex("(?:android\\.os\\.extra\\.HEALTH|(?:^|\\s)health)\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val batteryHealthRegex = Regex("^\\s*health\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val explicitTemperatureRegex = Regex("(?:android\\.os\\.extra\\.TEMPERATURE|Battery\\s+temperature)\\s*[:=]\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val batteryTemperatureRegex = Regex("^\\s*temperature\\s*:\\s*(-?\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE)
        val explicitVoltageRegex = Regex("(?:android\\.os\\.extra\\.VOLTAGE|Battery\\s+voltage|voltage_now)\\s*[:=]\\s*(\\d+)", RegexOption.IGNORE_CASE)
        val batteryVoltageRegex = Regex("^\\s*voltage\\s*:\\s*(\\d+)", RegexOption.IGNORE_CASE)

        fun parseCapacity(regex: Regex, line: String): Int? {
            val value = regex.find(line)?.groupValues?.get(1)
                ?.replace(",", "")
                ?.toDoubleOrNull()
                ?.roundToInt()
            return value?.takeIf { it in 1_000..10_000 }
        }

        for (line in lines) {
            if (line.contains("Current Battery Service state:", ignoreCase = true) ||
                line.contains("DUMP OF SERVICE battery:", ignoreCase = true)
            ) {
                inBatteryServiceSection = true
            } else if (inBatteryServiceSection && line.startsWith("DUMP OF SERVICE ")) {
                inBatteryServiceSection = false
            }

            estimatedCapacityMah = estimatedCapacityMah ?: parseCapacity(estimatedRegex, line)
            lastLearnedCapacityMah = lastLearnedCapacityMah ?: parseCapacity(lastLearnedRegex, line)
            minLearnedCapacityMah = minLearnedCapacityMah ?: parseCapacity(minLearnedRegex, line)
            maxLearnedCapacityMah = maxLearnedCapacityMah ?: parseCapacity(maxLearnedRegex, line)
            
            designCapacityMah = designCapacityMah ?: parseCapacity(designRegex, line)

            cycleCount = cycleCount ?: cycleCountRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            androidHealth = androidHealth
                ?: explicitHealthRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                ?: batteryHealthRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                    ?.takeIf { inBatteryServiceSection }
            temperatureRaw = temperatureRaw
                ?: explicitTemperatureRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            batteryTemperatureRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()?.let { value ->
                if (inBatteryServiceSection) {
                    batteryServiceTemperatureRaw = batteryServiceTemperatureRaw ?: value
                } else {
                    fallbackTemperatureRaw = fallbackTemperatureRaw ?: value
                }
            }
            voltageRaw = voltageRaw
                ?: explicitVoltageRegex.find(line)?.groupValues?.get(1)?.toLongOrNull()
            batteryVoltageRegex.find(line)?.groupValues?.get(1)?.toLongOrNull()?.let { value ->
                if (inBatteryServiceSection) {
                    batteryServiceVoltageRaw = batteryServiceVoltageRaw ?: value
                } else {
                    fallbackVoltageRaw = fallbackVoltageRaw ?: value
                }
            }
            batteryAsoc = batteryAsoc ?: asocRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf { it in 1..100 }

            modelDetector.detectFromBugreportLine(line)?.let { candidate ->
                if (modelMatch == null || candidate.confidence > modelMatch.confidence) {
                    modelMatch = candidate
                }
            }
        }

        return BatteryReport(
            pixelModel = modelMatch?.model,
            estimatedCapacityMah = estimatedCapacityMah,
            lastLearnedCapacityMah = lastLearnedCapacityMah,
            minLearnedCapacityMah = minLearnedCapacityMah,
            maxLearnedCapacityMah = maxLearnedCapacityMah,
            parsedDesignCapacityMah = designCapacityMah,
            cycleCount = cycleCount,
            androidHealth = androidHealth?.let(AndroidBatteryHealth::fromRaw),
            temperatureCelsius = (temperatureRaw ?: batteryServiceTemperatureRaw ?: fallbackTemperatureRaw)?.let { raw ->
                if (kotlin.math.abs(raw) > 80.0) raw / 10.0 else raw
            },
            voltageText = (voltageRaw ?: batteryServiceVoltageRaw ?: fallbackVoltageRaw)?.let { raw ->
                when {
                    raw >= 1_000_000L -> "%.2f V".format(raw / 1_000_000.0)
                    raw >= 10_000L -> "%.2f V".format(raw / 1_000.0)
                    raw >= 1_000L -> "${raw} mV"
                    else -> raw.toString()
                }
            },
            batteryAsoc = batteryAsoc
        )
    }
}
