package com.pixelbatteryhealth.domain

import android.net.Uri
import com.pixelbatteryhealth.data.BatteryBugreportParser
import com.pixelbatteryhealth.data.BugreportBackupManager
import com.pixelbatteryhealth.data.BugreportTextFinder
import com.pixelbatteryhealth.data.BugreportZipExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.zip.ZipException

class ImportBugreportUseCase(
    private val extractor: BugreportZipExtractor,
    private val textFinder: BugreportTextFinder = BugreportTextFinder(),
    private val parser: BatteryBugreportParser = BatteryBugreportParser(),
    private val backupManager: BugreportBackupManager? = null,
) {
    suspend fun import(uri: Uri, shouldBackup: Boolean = false): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (shouldBackup) {
                backupManager?.backupToDownloads(uri)
            }

            val extracted = extractor.extract(uri)
            val textFile = textFinder.findBestTextFile(extracted.root)
                ?: return@withContext ImportResult.Failure(ImportError.NoBugreportTextFound)
            val report = parser.parse(
                file = textFile,
                sourceNames = listOf(extracted.sourceName, textFile.name, extracted.root.name),
            )

            if (report.estimatedCapacityMah == null && report.cycleCount == null && report.androidHealth == null) {
                ImportResult.Failure(ImportError.MissingBatteryData)
            } else {
                ImportResult.Success(report)
            }
        } catch (_: ZipException) {
            ImportResult.Failure(ImportError.InvalidZip)
        } catch (error: Throwable) {
            ImportResult.Failure(ImportError.ReadFailed(error.message ?: "Could not read bugreport"))
        }
    }
}
