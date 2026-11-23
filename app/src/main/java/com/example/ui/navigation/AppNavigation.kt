package com.example.ui.navigation

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.data.AppContainer
import com.example.data.model.FileItem
import com.example.ui.screens.CleanerScreen
import com.example.ui.screens.DashboardScreen
import com.example.ui.screens.FileManagerScreen
import com.example.util.PermissionHelper
import com.example.viewmodel.CleanerViewModel
import com.example.viewmodel.DashboardViewModel
import com.example.viewmodel.FileManagerViewModel
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import java.io.File

@Serializable
data object DashboardRoute

@Serializable
data object CleanerRoute

@Serializable
data object FilesRoute

private data class TopLevelDestination(
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String,
    val isSelected: (androidx.navigation.NavDestination?) -> Boolean,
    val onClick: () -> Unit
)

@Composable
fun StorageLensApp(
    container: AppContainer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    val dashboardViewModel: DashboardViewModel = viewModel(
        factory = DashboardViewModel.Factory(
            appContext = container.appContext,
            repository = container.storageRepository,
            snapshotStore = container.snapshotStore,
            settingsDataStore = container.settingsDataStore
        )
    )
    val cleanerViewModel: CleanerViewModel = viewModel(
        factory = CleanerViewModel.Factory(
            appContext = container.appContext,
            repository = container.storageRepository,
            snapshotStore = container.snapshotStore,
            settingsDataStore = container.settingsDataStore
        )
    )
    val fileManagerViewModel: FileManagerViewModel = viewModel(
        factory = FileManagerViewModel.Factory(
            appContext = container.appContext,
            repository = container.storageRepository,
            settingsDataStore = container.settingsDataStore
        )
    )

    val legacyPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        dashboardViewModel.onPermissionPromptHandled()
        dashboardViewModel.refreshPermissionState(triggerScanIfNewlyGranted = true)
        cleanerViewModel.refreshPermissionState()
        fileManagerViewModel.refreshPermissionAndReload()
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        dashboardViewModel.refreshPermissionState(triggerScanIfNewlyGranted = true)
        cleanerViewModel.refreshPermissionState()
        fileManagerViewModel.refreshPermissionAndReload()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        dashboardViewModel.refreshPermissionState(triggerScanIfNewlyGranted = true)
        cleanerViewModel.refreshPermissionState()
        fileManagerViewModel.refreshPermissionAndReload()
    }

    fun requestStorageAccess() {
        dashboardViewModel.onPermissionPromptHandled()
        if (PermissionHelper.requiresAllFilesSettingsFlow()) {
            val intent = PermissionHelper.createAllFilesAccessIntent(context)
            runCatching {
                settingsLauncher.launch(intent)
            }.onFailure {
                runCatching {
                    settingsLauncher.launch(PermissionHelper.createAllFilesAccessFallbackIntent())
                }
            }
        } else {
            legacyPermissionLauncher.launch(PermissionHelper.legacyPermissions())
        }
    }

    fun openStorageAccessSettings() {
        if (PermissionHelper.requiresAllFilesSettingsFlow()) {
            val intent = PermissionHelper.createAllFilesAccessIntent(context)
            runCatching {
                settingsLauncher.launch(intent)
            }.onFailure {
                runCatching {
                    settingsLauncher.launch(PermissionHelper.createAllFilesAccessFallbackIntent())
                }
            }
        } else {
            runCatching {
                settingsLauncher.launch(PermissionHelper.createAppDetailsSettingsIntent(context))
            }
        }
    }

    fun openSystemStorageSettings() {
        runCatching {
            context.startActivity(PermissionHelper.createSystemStorageSettingsIntent())
        }.onFailure {
            coroutineScope.launch {
                snackbarHostState.showSnackbar("System Storage settings unavailable on this device.")
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val destinations = listOf(
        TopLevelDestination(
            labelRes = R.string.nav_dashboard,
            selectedIcon = Icons.Rounded.Dashboard,
            unselectedIcon = Icons.Outlined.Dashboard,
            testTag = "nav_dashboard",
            isSelected = { dest -> dest?.hasRoute<DashboardRoute>() == true },
            onClick = {
                navController.navigate(DashboardRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        ),
        TopLevelDestination(
            labelRes = R.string.nav_cleaner,
            selectedIcon = Icons.Rounded.CleaningServices,
            unselectedIcon = Icons.Outlined.CleaningServices,
            testTag = "nav_cleaner",
            isSelected = { dest -> dest?.hasRoute<CleanerRoute>() == true },
            onClick = {
                navController.navigate(CleanerRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        ),
        TopLevelDestination(
            labelRes = R.string.nav_files,
            selectedIcon = Icons.Rounded.Folder,
            unselectedIcon = Icons.Outlined.Folder,
            testTag = "nav_files",
            isSelected = { dest -> dest?.hasRoute<FilesRoute>() == true },
            onClick = {
                navController.navigate(FilesRoute) {
                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
    )

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (!useNavRail) {
                    NavigationBar {
                        destinations.forEach { item ->
                            val selected = item.isSelected(currentDestination)
                            NavigationBarItem(
                                selected = selected,
                                onClick = item.onClick,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail {
                        destinations.forEach { item ->
                            val selected = item.isSelected(currentDestination)
                            NavigationRailItem(
                                selected = selected,
                                onClick = item.onClick,
                                icon = {
                                    Icon(
                                        imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                        contentDescription = stringResource(item.labelRes)
                                    )
                                },
                                label = { Text(stringResource(item.labelRes)) },
                                modifier = Modifier.testTag(item.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = DashboardRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<DashboardRoute> {
                        val state by dashboardViewModel.uiState.collectAsStateWithLifecycle()
                        DashboardScreen(
                            uiState = state,
                            onRescanClick = dashboardViewModel::startScan,
                            onCancelScanClick = dashboardViewModel::cancelScan,
                            onChartModeChange = dashboardViewModel::setChartMode,
                            onCategoryClick = { category ->
                                val shortcut = category.toShortcut()
                                fileManagerViewModel.applyCategoryFilter(shortcut)
                                navController.navigate(FilesRoute) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onGrantPermissionClick = { requestStorageAccess() },
                            onDismissRationale = dashboardViewModel::dismissRationaleSheet,
                            onOpenSettingsClick = { openStorageAccessSettings() }
                        )
                    }

                    composable<CleanerRoute> {
                        val state by cleanerViewModel.uiState.collectAsStateWithLifecycle()
                        CleanerScreen(
                            uiState = state,
                            onRescanClick = cleanerViewModel::startScan,
                            onCancelScanClick = cleanerViewModel::cancelScan,
                            onToggleItem = cleanerViewModel::toggleItem,
                            onToggleGroup = cleanerViewModel::toggleGroup,
                            onToggleSelectAll = cleanerViewModel::toggleSelectAll,
                            onUpdateThreshold = cleanerViewModel::updateThreshold,
                            onClearThresholdError = cleanerViewModel::clearThresholdError,
                            onRequestDelete = cleanerViewModel::requestDeleteConfirmation,
                            onDismissDeleteDialog = cleanerViewModel::dismissDeleteConfirmation,
                            onConfirmDelete = {
                                cleanerViewModel.confirmDelete()
                                fileManagerViewModel.invalidateSessionCache()
                            },
                            onDismissSummary = cleanerViewModel::dismissCleanupSummary,
                            onOpenSystemStorageSettings = { openSystemStorageSettings() },
                            onOpenAppPermissionSettings = { openStorageAccessSettings() }
                        )
                    }

                    composable<FilesRoute> {
                        val state by fileManagerViewModel.uiState.collectAsStateWithLifecycle()
                        FileManagerScreen(
                            uiState = state,
                            onShortcutSelect = fileManagerViewModel::applyCategoryFilter,
                            onOpenFolder = fileManagerViewModel::openFolder,
                            onNavigateUp = { fileManagerViewModel.navigateUp() },
                            onBreadcrumbClick = fileManagerViewModel::openFolder,
                            onSearchQueryChange = fileManagerViewModel::updateSearchQuery,
                            onSortChange = fileManagerViewModel::updateSort,
                            onRetryClick = fileManagerViewModel::forceRefresh,
                            onOpenSettingsClick = { openStorageAccessSettings() },
                            onShowNewFolderDialog = fileManagerViewModel::showNewFolderDialog,
                            onDismissNewFolderDialog = fileManagerViewModel::dismissNewFolderDialog,
                            onCreateFolder = fileManagerViewModel::createFolder,
                            onOpenFile = { item ->
                                launchFileOpen(
                                    context = context,
                                    item = item,
                                    onError = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            },
                            onShareFile = { item ->
                                launchFileShare(
                                    context = context,
                                    item = item,
                                    onError = { msg ->
                                        coroutineScope.launch { snackbarHostState.showSnackbar(msg) }
                                    }
                                )
                            },
                            onRequestRename = fileManagerViewModel::requestRename,
                            onDismissRenameDialog = fileManagerViewModel::dismissRenameDialog,
                            onConfirmRename = fileManagerViewModel::confirmRename,
                            onRequestDelete = fileManagerViewModel::requestDelete,
                            onDismissDeleteDialog = fileManagerViewModel::dismissDeleteDialog,
                            onConfirmDelete = fileManagerViewModel::confirmDelete,
                            onShowDetails = fileManagerViewModel::showDetails,
                            onDismissDetails = fileManagerViewModel::dismissDetails,
                            onClearBanner = fileManagerViewModel::clearBannerMessage
                        )
                    }
                }
            }
        }
    }
}

private fun launchFileOpen(
    context: Context,
    item: FileItem,
    onError: (String) -> Unit
) {
    val file = File(item.path)
    if (!file.exists()) {
        onError("File not found: ${item.name}")
        return
    }
    runCatching {
        val intent = PermissionHelper.openFileIntent(context, file, item.mimeType)
        context.startActivity(Intent.createChooser(intent, "Open with"))
    }.onFailure {
        onError("No app available to open ${item.name}.")
    }
}

private fun launchFileShare(
    context: Context,
    item: FileItem,
    onError: (String) -> Unit
) {
    val file = File(item.path)
    if (!file.exists()) {
        onError("File not found: ${item.name}")
        return
    }
    runCatching {
        val intent = PermissionHelper.shareFileIntent(context, file, item.mimeType)
        context.startActivity(intent)
    }.onFailure {
        onError("Unable to share ${item.name}.")
    }
}
