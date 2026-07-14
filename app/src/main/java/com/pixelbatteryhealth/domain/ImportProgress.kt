package com.pixelbatteryhealth.domain

enum class ImportStage {
    Preparing,
    SavingCopy,
    Extracting,
    FindingText,
    Parsing,
}

data class ImportProgress(
    val stage: ImportStage,
    val fraction: Float? = null,
)
