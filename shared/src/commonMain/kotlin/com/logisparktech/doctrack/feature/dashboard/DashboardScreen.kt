package com.logisparktech.doctrack.feature.dashboard

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Autorenew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logisparktech.doctrack.app.theme.LocalStatusColors
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.domain.model.DocumentWithExpiry
import com.logisparktech.doctrack.feature.reminders.NotificationPermissionBanner
import com.logisparktech.doctrack.ui.components.DocumentCard
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.InlineEmpty
import com.logisparktech.doctrack.ui.components.SectionHeader
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun DashboardScreen(
    contentPadding: PaddingValues,
    onOpenDocument: (Long) -> Unit,
    onAddDocument: () -> Unit,
    onOpenDocuments: (String) -> Unit,
    viewModel: DashboardViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = strings

    if (state.loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(
            start = 16.dp, end = 16.dp,
            top = contentPadding.calculateTopPadding() + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item { HeroHeader(total = state.total, onClick = { onOpenDocuments("") }) }
        item { NotificationPermissionBanner() }
        item { StatsGrid(state, onOpenDocuments) }

        if (state.total == 0) {
            item {
                EmptyState(
                    icon = Icons.Outlined.FolderOpen,
                    title = s.welcomeTitle,
                    message = s.welcomeMessage,
                    action = {
                        Button(onClick = onAddDocument) {
                            Icon(Icons.Outlined.Add, null)
                            Spacer(Modifier.size(8.dp))
                            Text(s.addDocument)
                        }
                    },
                )
            }
            return@LazyColumn
        }

        section(s.todaysExpirations, state.todayList, s.nothingExpiringToday, onOpenDocument)
        section(
            s.expiringSoon, state.expiringSoonList.take(5), s.nothingExpiringSoon, onOpenDocument,
            onSeeAll = if (state.expiringSoonList.size > 5) ({ onOpenDocuments(DocumentStatus.EXPIRING_SOON.name) }) else null,
        )
        section(s.upcomingDocuments, state.upcomingList, s.noUpcomingDocuments, onOpenDocument)
        section(s.recentlyAdded, state.recentList, null, onOpenDocument, onSeeAll = { onOpenDocuments("") })
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    items: List<DocumentWithExpiry>,
    emptyText: String?,
    onOpen: (Long) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (items.isEmpty() && emptyText == null) return
    item(key = "header_$title") {
        SectionHeader(title, actionLabel = if (onSeeAll != null) strings.seeAll else null, onAction = onSeeAll)
    }
    if (items.isEmpty()) {
        item(key = "empty_$title") { InlineEmpty(emptyText!!) }
    } else {
        items(items, key = { "${title}_${it.document.id}" }) { DocumentCard(it, onClick = { onOpen(it.document.id) }) }
    }
}

@Composable
private fun HeroHeader(total: Int, onClick: () -> Unit) {
    val s = strings
    val today = koinInject<DateProvider>().today()
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    Card(
        onClick = onClick,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.linearGradient(listOf(primary, tertiary)))
                .padding(20.dp),
        ) {
            Text(dateFormatter.format(today, withSuffix = true), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
            Spacer(Modifier.height(4.dp))
            Text(s.appName, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary)
            Text(s.tagline, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f))
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(s.num(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                Spacer(Modifier.size(8.dp))
                Text(s.totalDocuments, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(bottom = 6.dp))
            }
        }
    }
}

@Composable
private fun StatsGrid(state: DashboardState, onOpenDocuments: (String) -> Unit) {
    val s = strings
    val colors = LocalStatusColors.current
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(s.activeDocuments, state.active, Icons.Outlined.CheckCircle, colors.active, Modifier.weight(1f)) {
                onOpenDocuments(DocumentStatus.ACTIVE.name)
            }
            StatCard(s.expiringSoon, state.expiringSoon, Icons.Outlined.Schedule, colors.expiringSoon, Modifier.weight(1f)) {
                onOpenDocuments(DocumentStatus.EXPIRING_SOON.name)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(s.expiredDocuments, state.expired, Icons.Outlined.ErrorOutline, colors.expired, Modifier.weight(1f)) {
                onOpenDocuments(DocumentStatus.EXPIRED.name)
            }
            StatCard(s.upcomingRenewals, state.upcomingRenewals, Icons.Outlined.Autorenew, colors.renewal, Modifier.weight(1f)) {
                onOpenDocuments(UPCOMING_RENEWAL_FILTER)
            }
        }
    }
}

const val UPCOMING_RENEWAL_FILTER = "UPCOMING_RENEWAL"

@Composable
private fun StatCard(label: String, value: Int, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Box(
                Modifier.size(36.dp).background(color.copy(alpha = 0.14f), MaterialTheme.shapes.small),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.height(10.dp))
            Text(strings.num(value), style = MaterialTheme.typography.headlineSmall)
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

