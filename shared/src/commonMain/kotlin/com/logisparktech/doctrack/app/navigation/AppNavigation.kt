package com.logisparktech.doctrack.app.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.logisparktech.doctrack.app.theme.CompactLabel
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.core.notification.NotificationNavigator
import com.logisparktech.doctrack.feature.add_document.DocumentFormScreen
import com.logisparktech.doctrack.feature.attachments.AttachmentViewerScreen
import com.logisparktech.doctrack.feature.backup.BackupScreen
import com.logisparktech.doctrack.feature.calendar.CalendarScreen
import com.logisparktech.doctrack.feature.categories.CategoriesScreen
import com.logisparktech.doctrack.feature.dashboard.DashboardScreen
import com.logisparktech.doctrack.feature.document_details.DocumentDetailScreen
import com.logisparktech.doctrack.feature.documents.DocumentsScreen
import com.logisparktech.doctrack.feature.reminders.RemindersScreen
import com.logisparktech.doctrack.feature.renewal.RenewalScreen
import com.logisparktech.doctrack.feature.settings.SettingsScreen
import kotlin.reflect.KClass
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

private data class Tab(
    val route: Any,
    val routeClass: KClass<*>,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
)

@Composable
fun AppNavigation() {
    val nav = rememberNavController()
    val s = strings
    val snackbar = remember { SnackbarHostState() }
    val backStack by nav.currentBackStackEntryAsState()
    val destination = backStack?.destination

    val tabs = listOf(
        Tab(HomeRoute, HomeRoute::class, s.navHome, Icons.Outlined.Home, Icons.Filled.Home),
        Tab(DocumentsRoute(), DocumentsRoute::class, s.navDocuments, Icons.Outlined.Description, Icons.Filled.Description),
        Tab(CalendarRoute, CalendarRoute::class, s.navCalendar, Icons.Outlined.CalendarMonth, Icons.Filled.CalendarMonth),
        Tab(NotificationsRoute, NotificationsRoute::class, s.navNotifications, Icons.Outlined.Notifications, Icons.Filled.Notifications),
        Tab(SettingsRoute, SettingsRoute::class, s.navSettings, Icons.Outlined.Settings, Icons.Filled.Settings),
    )
    val onTab = tabs.any { destination.isOn(it.routeClass) }
    val showFab = destination.isOn(HomeRoute::class) || destination.isOn(DocumentsRoute::class)

    // Notification taps open the related document.
    val pendingDocument by NotificationNavigator.pendingDocumentId.collectAsStateWithLifecycle()
    LaunchedEffect(pendingDocument) {
        pendingDocument?.let {
            nav.navigate(DocumentDetailRoute(it)) { launchSingleTop = true }
            NotificationNavigator.consume()
        }
    }

    Scaffold(
        bottomBar = {
            if (onTab) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEach { tab ->
                        val selected = destination.isOn(tab.routeClass)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { nav.navigateToTab(tab.route) },
                            icon = { Icon(if (selected) tab.selectedIcon else tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label, style = CompactLabel, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (showFab) {
                ExtendedFloatingActionButton(
                    onClick = { nav.navigate(DocumentFormRoute()) },
                    icon = { Icon(Icons.Outlined.Add, null) },
                    text = { Text(s.addDocument) },
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = HomeRoute,
            enterTransition = { fadeIn(tween(220)) + slideInHorizontally(tween(220)) { it / 12 } },
            exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) },
            popExitTransition = { fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 12 } },
        ) {
            composable<HomeRoute> {
                DashboardScreen(
                    contentPadding = padding,
                    onOpenDocument = { nav.navigate(DocumentDetailRoute(it)) },
                    onAddDocument = { nav.navigate(DocumentFormRoute()) },
                    onOpenDocuments = { status -> nav.navigateToTab(DocumentsRoute(status)) },
                )
            }
            composable<DocumentsRoute> { entry ->
                val route = entry.toRoute<DocumentsRoute>()
                DocumentsScreen(
                    initialStatus = route.status,
                    contentPadding = padding,
                    onOpenDocument = { nav.navigate(DocumentDetailRoute(it)) },
                    onAddDocument = { nav.navigate(DocumentFormRoute()) },
                )
            }
            composable<CalendarRoute> {
                CalendarScreen(
                    contentPadding = padding,
                    onOpenDocument = { nav.navigate(DocumentDetailRoute(it)) },
                    onAddForDate = { nav.navigate(DocumentFormRoute(presetExpiry = it.toString())) },
                )
            }
            composable<NotificationsRoute> {
                RemindersScreen(contentPadding = padding, onOpenDocument = { nav.navigate(DocumentDetailRoute(it)) })
            }
            composable<SettingsRoute> {
                SettingsScreen(
                    contentPadding = padding,
                    snackbar = snackbar,
                    onOpenCategories = { nav.navigate(CategoriesRoute) },
                    onOpenBackup = { nav.navigate(BackupRoute) },
                )
            }
            composable<DocumentDetailRoute> { entry ->
                val route = entry.toRoute<DocumentDetailRoute>()
                DocumentDetailScreen(
                    viewModel = koinViewModel { parametersOf(route.id) },
                    onBack = { nav.popBackStack() },
                    onEdit = { nav.navigate(DocumentFormRoute(id = it)) },
                    onRenew = { nav.navigate(RenewRoute(it)) },
                    onOpenAttachment = { nav.navigate(AttachmentRoute(it)) },
                )
            }
            composable<DocumentFormRoute> { entry ->
                val route = entry.toRoute<DocumentFormRoute>()
                DocumentFormScreen(
                    viewModel = koinViewModel { parametersOf(route.id, route.presetExpiry) },
                    onClose = { nav.popBackStack() },
                    onSaved = { id, wasEdit ->
                        if (wasEdit) nav.popBackStack()
                        else nav.navigate(DocumentDetailRoute(id)) {
                            popUpTo<DocumentFormRoute> { inclusive = true }
                        }
                    },
                )
            }
            composable<RenewRoute> { entry ->
                val route = entry.toRoute<RenewRoute>()
                RenewalScreen(
                    viewModel = koinViewModel { parametersOf(route.id) },
                    onBack = { nav.popBackStack() },
                    onSaved = { nav.popBackStack() },
                )
            }
            composable<AttachmentRoute> { entry ->
                AttachmentViewerScreen(entry.toRoute<AttachmentRoute>().id, onBack = { nav.popBackStack() })
            }
            composable<CategoriesRoute> {
                CategoriesScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() })
            }
            composable<BackupRoute> {
                BackupScreen(viewModel = koinViewModel(), onBack = { nav.popBackStack() })
            }
        }
    }
}

private fun NavDestination?.isOn(routeClass: KClass<*>): Boolean = this?.hasRoute(routeClass) == true

private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = route !is DocumentsRoute || route.status.isEmpty() }
        launchSingleTop = true
        restoreState = route !is DocumentsRoute || route.status.isEmpty()
    }
}

