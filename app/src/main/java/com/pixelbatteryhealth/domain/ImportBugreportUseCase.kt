package com.pixelbatteryhealth.domain

import android.net.Uri
import com.pixelbatteryhealth.data.BatteryBugreportParser
import com.pixelbatteryhealth.data.BugreportBackupManager
import com.pixelbatteryhealth.data.BugreportTextFinder
import com.pixelbatteryhealth.data.BugreportZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.util.zip.ZipException

class ImportBugreportUseCase(
    private val extractor: BugreportZipExtractor,
    private val textFinder: BugreportTextFinder = BugreportTextFinder(),
    private val parser: BatteryBugreportParser = BatteryBugreportParser(),
    private val backupManager: BugreportBackupManager? = null,
) {
    suspend fun import(
        uri: Uri,
        shouldBackup: Boolean = false,
        onProgress: (ImportProgress) -> Unit = {},
    ): ImportResult {
        var currentStage = ImportStage.Preparing

        fun report(stage: ImportStage, fraction: Float? = null) {
            currentStage = stage
            onProgress(ImportProgress(stage, fraction?.coerceIn(0f, 1f)))
        }

        return try {
            withTimeout(IMPORT_TIMEOUT_MS) {
                withContext(Dispatchers.IO) {
                    val coroutineContext = currentCoroutineContext()
                    val checkCancelled = { coroutineContext.ensureActive() }
                    report(ImportStage.Preparing)

                    if (shouldBackup) {
                        report(ImportStage.SavingCopy)
                        backupManager?.backupToDownloads(
                            uri = uri,
                            onProgress = { report(ImportStage.SavingCopy, it) },
                            checkCancelled = checkCancelled,
                        )
                    }

                    report(ImportStage.Extracting, 0f)
                    val extracted = extractor.extract(
                        uri = uri,
                        onProgress = { report(ImportStage.Extracting, it) },
                        checkCancelled = checkCancelled,
                    )
                    try {
                        report(ImportStage.FindingText, 0f)
                        val textFile = textFinder.findBestTextFile(
                            root = extracted.root,
                            onProgress = { report(ImportStage.FindingText, it) },
                            checkCancelled = checkCancelled,
                        ) ?: return@withContext ImportResult.Failure(ImportError.NoBugreportTextFound)
                        report(ImportStage.Parsing)
                        checkCancelled()
                        val batteryReport = parser.parse(
                            file = textFile,
                            sourceNames = listOf(extracted.sourceName, textFile.name, extracted.root.name),
                            checkCancelled = checkCancelled,
                        )

                        if (batteryReport.estimatedCapacityMah == null && batteryReport.cycleCount == null && batteryReport.androidHealth == null) {
                            ImportResult.Failure(ImportError.MissingBatteryData)
                        } else {
                            ImportResult.Success(batteryReport)
                        }
                    } finally {
                        extracted.root.deleteRecursively()
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            ImportResult.Failure(ImportError.TimedOut(currentStage))
        } catch (_: ZipException) {
            ImportResult.Failure(ImportError.InvalidZip)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Throwable) {
            ImportResult.Failure(
                ImportError.ReadFailed(
                    stage = currentStage,
                    message = error.message ?: "Could not read bugreport",
                ),
            )
        }

    }

    private companion object {
        const val IMPORT_TIMEOUT_MS = 3 * 60 * 1000L
    }
}
