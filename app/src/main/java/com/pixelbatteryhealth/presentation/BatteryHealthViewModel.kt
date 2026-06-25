package com.pixelbatteryhealth.presentation

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pixelbatteryhealth.domain.BatteryReport
import com.pixelbatteryhealth.domain.ImportBugreportUseCase
import com.pixelbatteryhealth.domain.ImportError
import com.pixelbatteryhealth.domain.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BatteryHealthUiState(
    val isLoading: Boolean = false,
    val isWaitingForBugreport: Boolean = false,
    val report: BatteryReport? = null,
    val errorMessage: String? = null,
)

class BatteryHealthViewModel(
    private val importBugreport: ImportBugreportUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BatteryHealthUiState())
    val uiState: StateFlow<BatteryHealthUiState> = _uiState.asStateFlow()

    fun import(uri: Uri, shouldBackup: Boolean = false) {
        _uiState.value = BatteryHealthUiState(isLoading = true)
        viewModelScope.launch {
            _uiState.value = when (val result = importBugreport.import(uri, shouldBackup)) {
                is ImportResult.Success -> BatteryHealthUiState(report = result.report)
                is ImportResult.Failure -> BatteryHealthUiState(errorMessage = result.error.toMessage())
            }
        }
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
        is ImportError.ReadFailed -> message
    }
}
