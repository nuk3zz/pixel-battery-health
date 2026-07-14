package com.pixelbatteryhealth.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelbatteryhealth.domain.BatteryReport
import com.pixelbatteryhealth.domain.ImportBugreportUseCase
import com.pixelbatteryhealth.domain.ImportError
import com.pixelbatteryhealth.domain.ImportProgress
import com.pixelbatteryhealth.domain.ImportResult
import com.pixelbatteryhealth.domain.ImportStage
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatteryHealthUiState(
    val isLoading: Boolean = false,
    val isWaitingForBugreport: Boolean = false,
    val report: BatteryReport? = null,
    val errorMessage: String? = null,
    val importProgress: ImportProgress? = null,
)

class BatteryHealthViewModel(
    private val importBugreport: ImportBugreportUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryHealthUiState())
    val uiState: StateFlow<BatteryHealthUiState> = _uiState.asStateFlow()
    private var importJob: Job? = null
    private var importGeneration = 0

    fun import(uri: Uri, shouldBackup: Boolean = false) {
        importJob?.cancel()
        val generation = ++importGeneration
        _uiState.value = BatteryHealthUiState(
            isLoading = true,
            importProgress = ImportProgress(ImportStage.Preparing),
        )
        importJob = viewModelScope.launch {
            val result = importBugreport.import(
                uri = uri,
                shouldBackup = shouldBackup,
                onProgress = { progress ->
                    if (generation == importGeneration) {
                        _uiState.value = _uiState.value.copy(
                            isLoading = true,
                            importProgress = progress,
                        )
                    }
                },
            )
            if (generation == importGeneration) {
                _uiState.value = when (result) {
                    is ImportResult.Success -> BatteryHealthUiState(report = result.report)
                    is ImportResult.Failure -> BatteryHealthUiState(errorMessage = result.error.toMessage())
                }
            }
        }
    }

    fun cancelImport() {
        importGeneration += 1
        importJob?.cancel()
        importJob = null
        _uiState.value = BatteryHealthUiState(
            errorMessage = "Import cancelled. You can select the ZIP again when ready.",
        )
    }

    fun startGuidedBugreportFlow() {
        _uiState.value = BatteryHealthUiState(isWaitingForBugreport = true)
    }

    fun stopWaitingForBugreport() {
        _uiState.value = _uiState.value.copy(isWaitingForBugreport = false)
    }

    private fun ImportError.toMessage(): String = when (this) {
        ImportError.InvalidZip -> "That file is not a readable bugreport ZIP."
        ImportError.NoBugreportTextFound -> "No bugreport text file was found inside the ZIP."
        ImportError.MissingBatteryData -> "The bugreport was found, but battery data was missing."
        is ImportError.TimedOut -> "Import timed out while ${stage.activityLabel()}. Try the ZIP again."
        is ImportError.ReadFailed -> "Import failed while ${stage.activityLabel()}: $message"
    }

    private fun ImportStage.activityLabel(): String = when (this) {
        ImportStage.Preparing -> "preparing the file"
        ImportStage.SavingCopy -> "saving a copy"
        ImportStage.Extracting -> "extracting the ZIP"
        ImportStage.FindingText -> "finding the bugreport text"
        ImportStage.Parsing -> "parsing battery data"
    }
}
