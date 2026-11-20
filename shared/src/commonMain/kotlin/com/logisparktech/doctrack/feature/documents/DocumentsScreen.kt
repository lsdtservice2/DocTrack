package com.logisparktech.doctrack.feature.documents

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.model.DocumentStatus
import com.logisparktech.doctrack.ui.components.CategoryBadge
import com.logisparktech.doctrack.ui.components.DocumentCard
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.categoryLabel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun DocumentsScreen(
    initialStatus: String,
    contentPadding: PaddingValues,
    onOpenDocument: (Long) -> Unit,
    onAddDocument: () -> Unit,
    viewModel: DocumentsViewModel = koinViewModel(key = "documents_$initialStatus") { parametersOf(initialStatus) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = strings

    Column(Modifier.fillMaxSize().padding(top = contentPadding.calculateTopPadding())) {
        Text(
            s.myDocuments,
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            placeholder = { Text(s.searchDocuments) },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = if (state.query.isNotEmpty()) {
                { IconButton(onClick = { viewModel.setQuery("") }) { Icon(Icons.Outlined.Close, s.clear) } }
            } else null,
            singleLine = true,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        val statusOptions = listOf(
            null to s.allStatuses,
            DocumentStatus.ACTIVE to s.statusActive,
            DocumentStatus.EXPIRING_SOON to s.statusExpiringSoon,
            DocumentStatus.EXPIRED to s.statusExpired,
            DocumentStatus.RENEWAL_IN_PROGRESS to s.statusRenewalInProgress,
            DocumentStatus.NO_EXPIRY to s.statusNoExpiry,
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.statusFilter.upcomingRenewal) {
                item {
                    FilterChip(
                        selected = true,
                        onClick = { viewModel.setStatus(StatusFilter()) },
                        label = { Text(s.upcomingRenewals) },
                        trailingIcon = { Icon(Icons.Outlined.Close, null, Modifier.size(16.dp)) },
                    )
                }
            }
            items(statusOptions) { (status, label) ->
                FilterChip(
                    selected = !state.statusFilter.upcomingRenewal && state.statusFilter.status == status,
                    onClick = { viewModel.setStatus(StatusFilter(status)) },
                    label = { Text(label) },
                )
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                FilterChip(
                    selected = state.categoryId == null,
                    onClick = { viewModel.setCategory(null) },
                    label = { Text(s.allCategories) },
                )
            }
            items(state.categories, key = { it.id }) { category ->
                FilterChip(
                    selected = state.categoryId == category.id,
                    onClick = { viewModel.setCategory(if (state.categoryId == category.id) null else category.id) },
                    label = { Text(categoryLabel(category)) },
                    leadingIcon = { CategoryBadge(category, size = 20.dp) },
                )
            }
        }

        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.totalCount == 0 -> EmptyState(
                icon = Icons.Outlined.FolderOpen,
                title = s.noDocumentsFound,
                message = s.welcomeMessage,
                action = {
                    Button(onClick = onAddDocument) {
                        Icon(Icons.Outlined.Add, null)
                        Spacer(Modifier.size(8.dp))
                        Text(s.addDocument)
                    }
                },
            )
            state.items.isEmpty() -> EmptyState(icon = Icons.Outlined.SearchOff, title = s.noDocumentsMatch)
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp, end = 16.dp, top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 88.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(state.items, key = { it.document.id }) { item ->
                    DocumentCard(item, onClick = { onOpenDocument(item.document.id) }, modifier = Modifier.animateItem())
                }
            }
        }
    }
}
