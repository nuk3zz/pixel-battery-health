package com.pixelbatteryhealth.data

import com.pixelbatteryhealth.domain.PixelModel

class PixelModelDetector {
    private val aliases = PixelModel.longestAliasesFirst
    private val aliasRegexes = aliases.map { (_, alias) ->
        Regex(
            pattern = "(?<![A-Za-z0-9])${Regex.escape(alias)}(?![A-Za-z0-9])",
            option = RegexOption.IGNORE_CASE,
        )
    }

    fun detectFromSourceNames(sourceNames: List<String>): PixelModelMatch? =
        sourceNames.firstNotNullOfOrNull(::detectAlias)?.let { model ->
            PixelModelMatch(model, SOURCE_NAME_CONFIDENCE)
        }

    fun detectFromBugreportLine(line: String): PixelModelMatch? {
        val confidence = line.modelEvidenceConfidence() ?: return null
        return detectAlias(line)?.let { model -> PixelModelMatch(model, confidence) }
    }

    private fun detectAlias(text: String): PixelModel? {
        val normalized = text
            .replace('[', ' ')
            .replace(']', ' ')
            .replace('_', ' ')
            .replace('-', ' ')

        return aliasRegexes.indices.firstNotNullOfOrNull { index ->
            aliases[index].first.takeIf { aliasRegexes[index].containsMatchIn(normalized) }
        }
    }

    private fun String.modelEvidenceConfidence(): Int? {
        val trimmed = trimStart()
        return when {
            contains("ro.product.model", ignoreCase = true) -> MODEL_PROPERTY_CONFIDENCE
            DEVICE_PROPERTY_MARKERS.any { contains(it, ignoreCase = true) } -> DEVICE_PROPERTY_CONFIDENCE
            FINGERPRINT_MARKERS.any { contains(it, ignoreCase = true) } -> FINGERPRINT_CONFIDENCE
            HEADER_PREFIXES.any { trimmed.startsWith(it, ignoreCase = true) } -> HEADER_CONFIDENCE
            else -> null
        }
    }

    private companion object {
        const val MODEL_PROPERTY_CONFIDENCE = 100
        const val DEVICE_PROPERTY_CONFIDENCE = 90
        const val FINGERPRINT_CONFIDENCE = 80
        const val HEADER_CONFIDENCE = 70
        const val SOURCE_NAME_CONFIDENCE = 60

        val DEVICE_PROPERTY_MARKERS = listOf(
            "ro.product.device",
            "ro.product.name",
            "ro.build.product",
        )

        val FINGERPRINT_MARKERS = listOf(
            "ro.build.fingerprint",
            "build fingerprint",
        )

        val HEADER_PREFIXES = listOf("Product:", "Device:", "Model:")
    }
}

data class PixelModelMatch(
    val model: PixelModel,
    val confidence: Int,
)
