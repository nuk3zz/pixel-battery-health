package com.pixelbatteryhealth.data

import com.pixelbatteryhealth.domain.AndroidBatteryHealth
import com.pixelbatteryhealth.domain.BatteryReport
import com.pixelbatteryhealth.domain.PixelModel
import java.io.File

class BatteryBugreportParser {
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
        var voltageRaw: Long? = null
        var batteryAsoc: Int? = null
        
        var headerModel: PixelModel? = null
        var fallbackModel: PixelModel? = null

        val estimatedRegex = Regex("""Estimated battery capacity\s*:\s*(\d+)\s*mAh""", RegexOption.IGNORE_CASE)
        val lastLearnedRegex = Regex("""Last learned battery capacity\s*:\s*(\d+)\s*mAh""", RegexOption.IGNORE_CASE)
        val minLearnedRegex = Regex("""Min learned battery capacity\s*:\s*(\d+)\s*mAh""", RegexOption.IGNORE_CASE)
        val maxLearnedRegex = Regex("""Max learned battery capacity\s*:\s*(\d+)\s*mAh""", RegexOption.IGNORE_CASE)
        val designRegex = Regex("""(?:Design\s+capacity|Battery\s+design\s+capacity|original\s+design\s+capacity)\s*[:=]\s*(\d+)(?:\s*mAh)?""", RegexOption.IGNORE_CASE)
        val cycleCountRegex = Regex("""(?:android\.os\.extra\.CYCLE_COUNT|mSavedBatteryUsage)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        val asocRegex = Regex("""mSavedBatteryAsoc\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)
        val healthRegex = Regex("""(?:^|\s)health\s*=\s*(\d+)""")
        val temperatureRegex = Regex("""(?:Battery\s+temperature|temperature|temp)\s*[:=]\s*(-?\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE)
        val voltageRegex = Regex("""(?:Battery\s+voltage|voltage|voltage_now)\s*[:=]\s*(\d+)""", RegexOption.IGNORE_CASE)

        val aliases = PixelModel.longestAliasesFirst
        val modelRegexes = aliases.map { (_, alias) ->
            Regex("""\b${Regex.escape(alias)}\b""", RegexOption.IGNORE_CASE)
        }

        fun detectModel(text: String): PixelModel? {
            val cleanText = text.replace("[", " ").replace("]", " ")
            val normalized = cleanText.replace('_', ' ').replace('-', ' ')
            for (i in modelRegexes.indices) {
                if (modelRegexes[i].containsMatchIn(normalized)) {
                    return aliases[i].first
                }
            }
            return null
        }

        // Detection from source names
        for (name in sourceNames) {
            headerModel = headerModel ?: detectModel(name)
        }

        var lineCount = 0
        for (line in lines) {
            lineCount++
            estimatedCapacityMah = estimatedCapacityMah ?: estimatedRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            lastLearnedCapacityMah = lastLearnedCapacityMah ?: lastLearnedRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            minLearnedCapacityMah = minLearnedCapacityMah ?: minLearnedRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            maxLearnedCapacityMah = maxLearnedCapacityMah ?: maxLearnedRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            
            val parsedDesign = designRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            if (parsedDesign != null && parsedDesign > 1000) {
                designCapacityMah = designCapacityMah ?: parsedDesign
            }

            cycleCount = cycleCount ?: cycleCountRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            androidHealth = androidHealth ?: healthRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()
            temperatureRaw = temperatureRaw ?: temperatureRegex.find(line)?.groupValues?.get(1)?.toDoubleOrNull()
            voltageRaw = voltageRaw ?: voltageRegex.find(line)?.groupValues?.get(1)?.toLongOrNull()
            batteryAsoc = batteryAsoc ?: asocRegex.find(line)?.groupValues?.get(1)?.toIntOrNull()

            val isHeaderLine = line.contains("ro.product.model", ignoreCase = true) || 
                               line.contains("ro.product.device", ignoreCase = true) ||
                               line.startsWith("Product:", ignoreCase = true) || 
                               line.startsWith("Device:", ignoreCase = true) ||
                               line.startsWith("Model:", ignoreCase = true)
            
            if (isHeaderLine) {
                val detected = detectModel(line)
                if (detected != null) {
                    headerModel = detected
                }
            } else if (headerModel == null && lineCount < 2000) {
                val detected = detectModel(line)
                if (detected != null) {
                    fallbackModel = fallbackModel ?: detected
                }
            }
        }

        return BatteryReport(
            pixelModel = headerModel ?: fallbackModel,
            estimatedCapacityMah = estimatedCapacityMah,
            lastLearnedCapacityMah = lastLearnedCapacityMah,
            minLearnedCapacityMah = minLearnedCapacityMah,
            maxLearnedCapacityMah = maxLearnedCapacityMah,
            parsedDesignCapacityMah = designCapacityMah,
            cycleCount = cycleCount,
            androidHealth = androidHealth?.let(AndroidBatteryHealth::fromRaw),
            temperatureCelsius = temperatureRaw?.let { raw ->
                if (kotlin.math.abs(raw) > 80.0) raw / 10.0 else raw
            },
            voltageText = voltageRaw?.let { raw ->
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
