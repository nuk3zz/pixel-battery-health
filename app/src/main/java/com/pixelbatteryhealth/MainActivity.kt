package com.pixelbatteryhealth

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.pixelbatteryhealth.data.BugreportBackupManager
import com.pixelbatteryhealth.data.BugreportZipExtractor
import com.pixelbatteryhealth.domain.BatteryReport
import com.pixelbatteryhealth.domain.BatterySummaryStatus
import com.pixelbatteryhealth.domain.ImportBugreportUseCase
import com.pixelbatteryhealth.domain.ImportProgress
import com.pixelbatteryhealth.domain.ImportStage
import com.pixelbatteryhealth.presentation.BatteryHealthUiState
import com.pixelbatteryhealth.presentation.BatteryHealthViewModel
import com.pixelbatteryhealth.ui.theme.PixelBatteryHealthTheme
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PixelBatteryHealthTheme {
                val context = LocalContext.current.applicationContext
                val vm: BatteryHealthViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            return BatteryHealthViewModel(
                                ImportBugreportUseCase(
                                    extractor = BugreportZipExtractor(context),
                                    backupManager = BugreportBackupManager(context),
                                ),
                            ) as T
                        }
                    },
                )
                val state by vm.uiState.collectAsState()

                LaunchedEffect(Unit) {
                    intent.sharedZipUri(context)?.let { uri ->
                        vm.import(uri, shouldBackup = true)
                    }
                }

                PixelBatteryHealthApp(
                    state = state,
                    onImport = vm::import,
                    onStartBugreport = {
                        vm.startGuidedBugreportFlow()
                        openDeveloperOptions()
                    },
                    onCancelWaiting = vm::stopWaitingForBugreport,
                    onCancelImport = vm::cancelImport,
                )
            }
        }
    }

    private fun openDeveloperOptions() {
        val intent = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)
        runCatching { startActivity(intent) }
            .onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
                    .onFailure {
                        Toast.makeText(this, "Could not open Settings", Toast.LENGTH_SHORT).show()
                    }
            }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PixelBatteryHealthApp(
    state: BatteryHealthUiState,
    onImport: (Uri) -> Unit,
    onStartBugreport: () -> Unit,
    onCancelWaiting: () -> Unit,
    onCancelImport: () -> Unit,
) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onImport(uri)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pixel Battery Health", fontWeight = FontWeight.SemiBold) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val screen = when {
                state.isLoading -> AppScreen.Loading
                state.isWaitingForBugreport -> AppScreen.Waiting
                state.report != null -> AppScreen.Report
                else -> AppScreen.Empty
            }
            AnimatedContent(targetState = screen, label = "content") { currentScreen ->
                when (currentScreen) {
                    AppScreen.Loading -> LoadingState(
                        progress = state.importProgress,
                        onCancel = onCancelImport,
                    )
                    AppScreen.Waiting -> WaitingForBugreportState(
                        errorMessage = state.errorMessage,
                        onCancelWaiting = onCancelWaiting,
                    )
                    AppScreen.Report -> state.report?.let { ReportContent(it) }
                    AppScreen.Empty -> EmptyState(state.errorMessage)
                }
            }

            Button(
                onClick = onStartBugreport,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.isLoading,
            ) {
                Text("Create Bugreport", fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { launcher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !state.isLoading,
            ) {
                Text("Load Bugreport ZIP", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun EmptyState(errorMessage: String?) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroCard(
            title = "Ready",
            subtitle = "Create a new Android bugreport, share it here, or load an existing ZIP.",
            status = BatterySummaryStatus.Unknown,
            percent = null,
        )
        if (errorMessage != null) {
            ErrorCard(errorMessage)
        }
    }
}

@Composable
private fun WaitingForBugreportState(
    errorMessage: String?,
    onCancelWaiting: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                CircularProgressIndicator()
                Text("Waiting for bugreport", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "In Developer Options, tap Bug report. When Android finishes, use the notification share action and choose Pixel Battery Health.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Button(
                    onClick = onCancelWaiting,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text("Stop Waiting")
                }
            }
        }
        if (errorMessage != null) {
            ErrorCard(errorMessage)
        }
    }
}

@Composable
private fun LoadingState(
    progress: ImportProgress?,
    onCancel: () -> Unit,
) {
    val currentProgress = progress ?: ImportProgress(ImportStage.Preparing)
    val fraction = currentProgress.fraction
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (fraction != null) {
                CircularProgressIndicator(progress = { fraction })
            } else {
                CircularProgressIndicator()
            }
            Text(
                currentProgress.stage.title(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                currentProgress.stage.description(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            if (fraction != null) {
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    "${(fraction * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text("Cancel Import")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportContent(report: BatteryReport) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HeroCard(
            title = report.healthPercent?.let { "${it.roundPercent()}%" } ?: "--",
            subtitle = report.pixelModel?.displayName ?: "Pixel model not detected",
            status = report.summaryStatus,
            percent = report.healthPercent,
        )

        if (report.exceedsTypicalCapacity) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Text(
                    text = "Measured capacity is above the typical design rating, so battery health is shown as 100%.",
                    modifier = Modifier.padding(16.dp),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            DetailCard("Estimated", report.estimatedCapacityMah?.mah() ?: "Missing")
            DetailCard("Design", report.designCapacityMah?.mah() ?: "Unknown")
            DetailCard("Cycles", report.cycleCount?.toString() ?: "Missing")
            DetailCard("Health", report.androidHealth?.label ?: "Missing")
            DetailCard("Temperature", report.temperatureCelsius?.let { "%.1f °C".format(Locale.US, it) } ?: "Missing")
            DetailCard("Voltage", report.voltageText ?: "Missing")
        }

        if (report.lastLearnedCapacityMah != null || report.minLearnedCapacityMah != null || report.maxLearnedCapacityMah != null) {
            DetailGroup(
                title = "Learned capacity",
                items = listOf(
                    "Last" to report.lastLearnedCapacityMah?.mah(),
                    "Min" to report.minLearnedCapacityMah?.mah(),
                    "Max" to report.maxLearnedCapacityMah?.mah(),
                ),
            )
        }
    }
}

private enum class AppScreen {
    Loading,
    Waiting,
    Report,
    Empty,
}

private fun ImportStage.title(): String = when (this) {
    ImportStage.Preparing -> "Preparing bugreport"
    ImportStage.SavingCopy -> "Saving shared ZIP"
    ImportStage.Extracting -> "Extracting ZIP"
    ImportStage.FindingText -> "Finding bugreport text"
    ImportStage.Parsing -> "Parsing battery data"
}

private fun ImportStage.description(): String = when (this) {
    ImportStage.Preparing -> "Checking the selected file."
    ImportStage.SavingCopy -> "Saving the shared bugreport to Downloads."
    ImportStage.Extracting -> "Unpacking the ZIP into secure app cache."
    ImportStage.FindingText -> "Scanning extracted text files for battery data."
    ImportStage.Parsing -> "Reading model, capacity, cycles, and battery state."
}

@Composable
private fun HeroCard(
    title: String,
    subtitle: String,
    status: BatterySummaryStatus,
    percent: Double?,
) {
    val accent = when (status) {
        BatterySummaryStatus.Excellent -> Color(0xFF34A853)
        BatterySummaryStatus.Good -> MaterialTheme.colorScheme.primary
        BatterySummaryStatus.Poor -> MaterialTheme.colorScheme.error
        BatterySummaryStatus.Unknown -> MaterialTheme.colorScheme.secondary
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                    ),
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryGlyph(accent, percent)
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        title,
                        fontSize = 54.sp,
                        lineHeight = 58.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AssistChip(
                onClick = {},
                label = { Text(status.label, fontWeight = FontWeight.SemiBold) },
            )
        }
    }
}

@Composable
private fun BatteryGlyph(color: Color, percent: Double?) {
    Box(
        modifier = Modifier
            .size(82.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .width(34.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((((percent ?: 0.0).coerceIn(0.0, 100.0) / 100.0) * 52).dp)
                    .background(color),
            )
        }
    }
}

@Composable
private fun DetailCard(label: String, value: String) {
    Card(
        modifier = Modifier.width(160.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailGroup(title: String, items: List<Pair<String, String?>>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            items.filter { it.second != null }.forEach { (label, value) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(value.orEmpty(), fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            textAlign = TextAlign.Start,
        )
    }
}

private fun Double.roundPercent(): String = String.format(Locale.US, "%.0f", this)

private fun Int.mah(): String = "$this mAh"

private fun Intent.sharedZipUri(context: android.content.Context): Uri? {
    if (action != Intent.ACTION_SEND && action != Intent.ACTION_SEND_MULTIPLE) return null
    data?.let { return it }

    val streamUri = when (action) {
        Intent.ACTION_SEND -> singleStreamUri()
        Intent.ACTION_SEND_MULTIPLE -> streamUris().zipFirst(context)
        else -> null
    }
    return streamUri ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
}

private fun Intent.singleStreamUri(): Uri? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM)
    }

private fun Intent.streamUris(): List<Uri> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
    } else {
        @Suppress("DEPRECATION")
        getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM).orEmpty()
    }

private fun List<Uri>.zipFirst(context: android.content.Context): Uri? =
    firstOrNull { uri -> uri.displayName(context)?.endsWith(".zip", ignoreCase = true) == true }
        ?: firstOrNull()

private fun Uri.displayName(context: android.content.Context): String? =
    context.contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
