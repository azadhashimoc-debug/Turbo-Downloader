package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.DownloadFilter
import com.example.ui.DownloadViewModel
import com.example.ui.components.AddDownloadDialog
import com.example.ui.components.DownloadDetailsDialog
import com.example.ui.components.DownloadItemCard
import com.example.ui.components.DownloadStatsCard
import com.example.ui.components.SampleLinksBottomSheet
import com.example.ui.components.SettingsBottomSheet
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.ElectricCyanLight
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ObsidianBorder
import com.example.ui.theme.ObsidianCard
import com.example.ui.theme.ObsidianDeep
import com.example.util.FormatUtils

class MainActivity : ComponentActivity() {

    companion object {
        /**
         * Public contract for another app you control (e.g. a custom browser) to hand off
         * a download directly - no chooser dialog, no relying on the system intercepting a
         * link the way Chrome never does. Send an explicit intent:
         *
         * ```
         * Intent(ACTION_ADD_DOWNLOAD).apply {
         *     setPackage("com.aistudio.downloadmanager.kxvt")
         *     putExtra(EXTRA_URL, url)                 // required
         *     putExtra(EXTRA_FILE_NAME, suggestedName)  // optional
         * }.let(context::startActivity)
         * ```
         */
        const val ACTION_ADD_DOWNLOAD = "com.aistudio.downloadmanager.kxvt.action.ADD_DOWNLOAD"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_FILE_NAME = "extra_file_name"
    }

    private val viewModel: DownloadViewModel by viewModels()

    private val notificationPermissionLauncher =
        registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        requestNotificationPermissionIfNeeded()

        // Handle Intent (View link / Share link)
        handleIncomingIntent(intent)

        setContent {
            MyApplicationTheme {
                DownloadManagerScreen(viewModel = viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU) return
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            this,
            android.Manifest.permission.POST_NOTIFICATIONS
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            ACTION_ADD_DOWNLOAD -> {
                val url = intent.getStringExtra(EXTRA_URL)?.trim()
                if (!url.isNullOrBlank() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    val fileName = intent.getStringExtra(EXTRA_FILE_NAME)?.trim()
                    // Trusted first-party caller - start immediately, no confirmation dialog.
                    viewModel.startDownload(url, fileName)
                }
            }
            Intent.ACTION_VIEW -> {
                val dataUri = intent.dataString
                if (!dataUri.isNullOrBlank() && (dataUri.startsWith("http://") || dataUri.startsWith("https://"))) {
                    viewModel.handleIncomingUrl(dataUri)
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        val extractedUrl = extractUrl(sharedText)
                        if (extractedUrl != null) {
                            viewModel.handleIncomingUrl(extractedUrl)
                        } else if (sharedText.startsWith("http://") || sharedText.startsWith("https://")) {
                            viewModel.handleIncomingUrl(sharedText)
                        }
                    }
                }
            }
        }
    }

    private fun extractUrl(text: String): String? {
        val urlRegex = Regex("""https?://[^\s]+""", RegexOption.IGNORE_CASE)
        val match = urlRegex.find(text)
        return match?.value
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(viewModel: DownloadViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var isSearchExpanded by remember { mutableStateOf(false) }
    var hasAllFilesAccess by remember {
        mutableStateOf(com.example.util.StorageAccessHelper.hasAllFilesAccess())
    }

    LaunchedEffect(Unit) {
        viewModel.snackbarEvent.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    // The permission can only be toggled from the system Settings screen, so re-check it
    // whenever the user comes back to the app (e.g. right after granting/revoking it there).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasAllFilesAccess = com.example.util.StorageAccessHelper.hasAllFilesAccess()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ObsidianDeep,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Obsidian Kinetic Icon Badge
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            ElectricCyan.copy(alpha = 0.25f),
                                            Color(0xFF0F1B2E)
                                        )
                                    )
                                )
                                .border(1.dp, ElectricCyan.copy(alpha = 0.5f), RoundedCornerShape(11.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = null,
                                tint = ElectricCyan,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "TurboLoad",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 19.sp,
                                        letterSpacing = (-0.4).sp
                                    ),
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Surface(
                                    color = ElectricCyan.copy(alpha = 0.15f),
                                    border = BorderStroke(0.6.dp, ElectricCyan.copy(alpha = 0.4f)),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "TURBO",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        ),
                                        color = ElectricCyanLight,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                    )
                                }
                            }
                            if (uiState.activeCount > 0) {
                                Text(
                                    text = "${uiState.activeCount} aktiv yükləmə • ${FormatUtils.formatSpeed(uiState.totalSpeedBytes)}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = ElectricCyanLight
                                )
                            } else {
                                Text(
                                    text = "Obsidian Kinetic Engine",
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                    color = Color(0xFF64748B)
                                )
                            }
                        }
                    }
                },
                actions = {
                    // Quick Samples Button
                    IconButton(
                        onClick = { viewModel.setShowSampleSheet(true) },
                        modifier = Modifier.testTag("btn_open_samples")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDownload,
                            contentDescription = "Nümunə test faylları",
                            tint = ElectricCyan
                        )
                    }

                    // Search Button
                    IconButton(
                        onClick = {
                            isSearchExpanded = !isSearchExpanded
                            if (!isSearchExpanded) viewModel.setSearchQuery("")
                        },
                        modifier = Modifier.testTag("btn_toggle_search")
                    ) {
                        Icon(
                            imageVector = if (isSearchExpanded) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = "Axtarış",
                            tint = Color.White
                        )
                    }

                    // Settings Button
                    IconButton(
                        onClick = { viewModel.setShowSettingsDialog(true) },
                        modifier = Modifier.testTag("btn_open_settings")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Tənzimləmələr",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = ObsidianDeep
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.setShowAddDialog(true) },
                containerColor = ElectricCyan,
                contentColor = Color(0xFF002026),
                shape = CircleShape,
                modifier = Modifier
                    .border(2.dp, ElectricCyanLight.copy(alpha = 0.5f), CircleShape)
                    .testTag("add_download_fab")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Yeni yükləmə əlavə et",
                    modifier = Modifier.size(28.dp)
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field if expanded
            AnimatedVisibility(
                visible = isSearchExpanded,
                enter = slideInVertically() + fadeIn(),
                exit = slideOutVertically() + fadeOut()
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .testTag("search_text_field"),
                    placeholder = { Text("Fayl adı və ya URL axtarın...", color = Color(0xFF64748B)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ElectricCyan,
                        unfocusedBorderColor = ObsidianBorder,
                        focusedContainerColor = ObsidianCard,
                        unfocusedContainerColor = ObsidianCard,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = ElectricCyan)
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotBlank()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Təmizlə", tint = Color.White)
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp)
                )
            }

            // Main Content List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 85.dp)
            ) {
                // Dashboard Stats Card (Always visible at top)
                item {
                    DownloadStatsCard(
                        totalSpeed = uiState.totalSpeedBytes,
                        activeCount = uiState.activeCount,
                        completedCount = uiState.completedCount,
                        freeStorage = uiState.freeStorageText,
                        engineMode = uiState.engineMode,
                        onOpenSettings = { viewModel.setShowSettingsDialog(true) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }

                // Filter Tabs Bar
                item {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(DownloadFilter.values()) { filter ->
                            val count = when (filter) {
                                DownloadFilter.ALL -> uiState.downloads.size
                                DownloadFilter.ACTIVE -> uiState.activeCount
                                DownloadFilter.COMPLETED -> uiState.completedCount
                                DownloadFilter.PAUSED -> uiState.pausedCount
                            }

                            val isSelected = uiState.filter == filter

                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setFilter(filter) },
                                label = {
                                    Text(
                                        text = "${filter.title} ($count)",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                shape = RoundedCornerShape(14.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = ElectricCyan.copy(alpha = 0.2f),
                                    selectedLabelColor = ElectricCyanLight,
                                    containerColor = ObsidianCard,
                                    labelColor = Color(0xFF94A3B8)
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = if (isSelected) ElectricCyan else ObsidianBorder
                                ),
                                modifier = Modifier.testTag("filter_chip_${filter.name}")
                            )
                        }
                    }
                }

                // Empty State
                if (uiState.filteredDownloads.isEmpty()) {
                    item {
                        EmptyDownloadsView(
                            filter = uiState.filter,
                            onOpenAdd = { viewModel.setShowAddDialog(true) },
                            onOpenSamples = { viewModel.setShowSampleSheet(true) }
                        )
                    }
                } else {
                    items(
                        items = uiState.filteredDownloads,
                        key = { it.id }
                    ) { download ->
                        DownloadItemCard(
                            download = download,
                            onPause = { viewModel.pauseDownload(download.id) },
                            onResume = { viewModel.resumeDownload(download.id) },
                            onRetry = { viewModel.retryDownload(download.id) },
                            onCancelActive = { viewModel.cancelActiveDownload(download.id) },
                            onDelete = { viewModel.deleteDownload(download.id, deleteFile = true) },
                            onOpen = {
                                val opened = FormatUtils.openDownloadedFile(context, download.filePath)
                                if (!opened) {
                                    Toast.makeText(context, "Faylı açmaq üçün uyğun proqram tapılmadı", Toast.LENGTH_SHORT).show()
                                }
                            },
                            onShare = {
                                FormatUtils.shareDownloadedFile(context, download.filePath)
                            },
                            onClick = { viewModel.setSelectedDetails(download) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }

    // Add Download Dialog
    if (uiState.showAddDialog) {
        AddDownloadDialog(
            initialUrl = uiState.incomingUrl,
            onDismiss = {
                viewModel.setShowAddDialog(false)
                viewModel.clearIncomingUrl()
            },
            onStartDownload = { url, fileName, category ->
                viewModel.startDownload(url, fileName, category)
                viewModel.clearIncomingUrl()
            }
        )
    }

    // Sample Links BottomSheet
    if (uiState.showSampleSheet) {
        SampleLinksBottomSheet(
            onDismiss = { viewModel.setShowSampleSheet(false) },
            onSelectSample = { sample ->
                viewModel.startSampleDownload(sample)
            }
        )
    }

    // Download Details Dialog
    uiState.selectedDownloadDetails?.let { download ->
        DownloadDetailsDialog(
            download = download,
            onDismiss = { viewModel.setSelectedDetails(null) },
            onOpen = {
                FormatUtils.openDownloadedFile(context, download.filePath)
            },
            onShare = {
                FormatUtils.shareDownloadedFile(context, download.filePath)
            },
            onDelete = { deleteFile ->
                viewModel.deleteDownload(download.id, deleteFile)
            }
        )
    }

    // Settings BottomSheet
    if (uiState.showSettingsDialog) {
        SettingsBottomSheet(
            currentEngineMode = uiState.engineMode,
            onEngineModeSelected = { mode ->
                viewModel.setSmartEngineMode(mode)
            },
            onDismiss = { viewModel.setShowSettingsDialog(false) },
            onClearCompleted = { viewModel.clearCompletedDownloads() },
            hasAllFilesAccess = hasAllFilesAccess,
            onRequestStorageAccess = { com.example.util.StorageAccessHelper.requestAllFilesAccess(context) }
        )
    }
}

@Composable
fun EmptyDownloadsView(
    filter: DownloadFilter,
    onOpenAdd: () -> Unit,
    onOpenSamples: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = ObsidianCard
        ),
        border = BorderStroke(1.dp, ObsidianBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(ElectricCyan.copy(alpha = 0.12f))
                    .border(1.dp, ElectricCyan.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = ElectricCyan,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            Text(
                text = when (filter) {
                    DownloadFilter.ALL -> "Heç bir yükləmə yoxdur"
                    DownloadFilter.ACTIVE -> "Hal-hazırda aktiv yükləmə yoxdur"
                    DownloadFilter.COMPLETED -> "Tamamlanmış fayl yoxdur"
                    DownloadFilter.PAUSED -> "Fasilədə və ya xətada olan yükləmə yoxdur"
                },
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                ),
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "İstənilən faylın birbaşa linkini əlavə edərək yükləməyə başlaya və ya sürətli test fayllarından istifadə edə bilərsiniz.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 19.sp),
                color = Color(0xFF94A3B8),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(modifier = Modifier.height(22.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onOpenSamples,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, ObsidianBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                ) {
                    Text("Test Faylları")
                }

                Button(
                    onClick = onOpenAdd,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ElectricCyan,
                        contentColor = Color(0xFF002026)
                    )
                ) {
                    Text("Yeni Link Əlavə Et", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
