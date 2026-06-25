package com.pixelbatteryhealth.domain

data class BatteryReport(
    val pixelModel: PixelModel?,
    val estimatedCapacityMah: Int?,
    val lastLearnedCapacityMah: Int?,
    val minLearnedCapacityMah: Int?,
    val maxLearnedCapacityMah: Int?,
    val cycleCount: Int?,
    val androidHealth: AndroidBatteryHealth?,
    val temperatureCelsius: Double?,
    val voltageText: String?,
    val parsedDesignCapacityMah: Int? = null,
    val batteryAsoc: Int? = null,
) {
    // Prefer the hardcoded design capacity for known Pixel models, 
    // fallback to the one parsed from the bugreport if the model is unknown.
    val designCapacityMah: Int? = pixelModel?.designCapacityMah ?: parsedDesignCapacityMah

    val healthPercent: Double? =
        if (batteryAsoc != null) {
            batteryAsoc.toDouble()
        } else if (estimatedCapacityMah != null && designCapacityMah != null) {
            estimatedCapacityMah.toDouble() / designCapacityMah.toDouble() * 100.0
        } else {
            null
        }

    val summaryStatus: BatterySummaryStatus
        get() = when (val percent = healthPercent) {
            null -> BatterySummaryStatus.Unknown
            in 90.0..Double.MAX_VALUE -> BatterySummaryStatus.Excellent
            in 80.0..<90.0 -> BatterySummaryStatus.Good
            else -> BatterySummaryStatus.Poor
        }
}

enum class BatterySummaryStatus(val label: String) {
    Excellent("Excellent"),
    Good("Good"),
    Poor("Poor"),
    Unknown("Unknown"),
}

enum class AndroidBatteryHealth(val rawValue: Int, val label: String) {
    Unknown(1, "Unknown"),
    Good(2, "Good"),
    Overheat(3, "Overheat"),
    Dead(4, "Dead"),
    OverVoltage(5, "Over Voltage"),
    Failure(6, "Failure"),
    Cold(7, "Cold");

    companion object {
        fun fromRaw(value: Int): AndroidBatteryHealth? = entries.firstOrNull { it.rawValue == value }
    }
}

enum class PixelModel(
    val displayName: String,
    val designCapacityMah: Int,
    val aliases: List<String>,
) {
    Pixel1("Pixel", 2770, listOf("Pixel 1", "Sailfish")),
    Pixel1Xl("Pixel XL", 3450, listOf("Pixel XL", "Marlin")),
    Pixel2("Pixel 2", 2700, listOf("Pixel 2", "Walleye")),
    Pixel2Xl("Pixel 2 XL", 3520, listOf("Pixel 2 XL", "Taimen")),
    Pixel3("Pixel 3", 2915, listOf("Pixel 3", "Blueline")),
    Pixel3Xl("Pixel 3 XL", 3430, listOf("Pixel 3 XL", "Crosshatch")),
    Pixel3a("Pixel 3a", 3000, listOf("Pixel 3a", "Sargo")),
    Pixel3aXl("Pixel 3a XL", 3700, listOf("Pixel 3a XL", "Bonito")),
    Pixel4("Pixel 4", 2800, listOf("Pixel 4", "Flame")),
    Pixel4Xl("Pixel 4 XL", 3700, listOf("Pixel 4 XL", "Pixel 4XL", "Coral")),
    Pixel4a("Pixel 4a", 3140, listOf("Pixel 4a", "Sunfish")),
    Pixel4a5g("Pixel 4a (5G)", 3885, listOf("Pixel 4a (5G)", "Bramble")),
    Pixel5("Pixel 5", 4080, listOf("Pixel 5", "Redfin")),
    Pixel5a("Pixel 5a", 4680, listOf("Pixel 5a", "Barbet")),
    Pixel6("Pixel 6", 4614, listOf("Pixel 6", "Oriole")),
    Pixel6Pro("Pixel 6 Pro", 5003, listOf("Pixel 6 Pro", "Raven")),
    Pixel6a("Pixel 6a", 4410, listOf("Pixel 6a", "Bluejay")),
    Pixel7("Pixel 7", 4355, listOf("Pixel 7", "Panther")),
    Pixel7Pro("Pixel 7 Pro", 5000, listOf("Pixel 7 Pro", "Cheetah")),
    Pixel7a("Pixel 7a", 4385, listOf("Pixel 7a", "Lynx")),
    PixelFold("Pixel Fold", 4821, listOf("Pixel Fold", "Felix")),
    Pixel8("Pixel 8", 4575, listOf("Pixel 8", "Shiba")),
    Pixel8Pro("Pixel 8 Pro", 5050, listOf("Pixel 8 Pro", "Husky")),
    Pixel8a("Pixel 8a", 4492, listOf("Pixel 8a", "Akita")),
    Pixel9("Pixel 9", 4700, listOf("Pixel 9", "Tokay")),
    Pixel9Pro("Pixel 9 Pro", 4700, listOf("Pixel 9 Pro", "Caiman")),
    Pixel9ProXl("Pixel 9 Pro XL", 5060, listOf("Pixel 9 Pro XL", "Pixel 9 ProXL", "Komodo")),
    Pixel9ProFold("Pixel 9 Pro Fold", 4650, listOf("Pixel 9 Pro Fold", "Comet")),
    Pixel10("Pixel 10", 4970, listOf("Pixel 10", "Frankel")),
    Pixel10Pro("Pixel 10 Pro", 4870, listOf("Pixel 10 Pro", "Blazer")),
    Pixel10ProXl("Pixel 10 Pro XL", 5200, listOf("Pixel 10 Pro XL", "Mustang")),
    Pixel10ProFold("Pixel 10 Pro Fold", 5015, listOf("Pixel 10 Pro Fold", "Rango"));

    companion object {
        val longestAliasesFirst: List<Pair<PixelModel, String>> =
            entries.flatMap { model -> model.aliases.map { alias -> model to alias } }
                .sortedByDescending { it.second.length }
    }
}

sealed interface ImportError {
    data object InvalidZip : ImportError
    data object NoBugreportTextFound : ImportError
    data object MissingBatteryData : ImportError
    data class ReadFailed(val message: String) : ImportError
}

sealed interface ImportResult {
    data class Success(val report: BatteryReport) : ImportResult
    data class Failure(val error: ImportError) : ImportResult
}
