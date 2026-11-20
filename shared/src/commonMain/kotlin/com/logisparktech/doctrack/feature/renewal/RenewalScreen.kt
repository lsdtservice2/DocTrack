package com.logisparktech.doctrack.feature.renewal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.logisparktech.doctrack.core.common.DateProvider
import com.logisparktech.doctrack.core.localization.dateFormatter
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.usecase.RenewalValidationError
import com.logisparktech.doctrack.feature.document_details.DetailCard
import com.logisparktech.doctrack.ui.components.BackButton
import com.logisparktech.doctrack.ui.components.DateField
import com.logisparktech.doctrack.ui.components.EmptyState
import com.logisparktech.doctrack.ui.components.InfoRow
import org.koin.compose.koinInject

@Composable
fun RenewalScreen(viewModel: RenewalViewModel, onBack: () -> Unit, onSaved: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val s = strings
    val f = dateFormatter
    val today = koinInject<DateProvider>().today()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.saved) { if (state.saved) onSaved() }
    LaunchedEffect(state.failed) { if (state.failed) snackbar.showSnackbar(s.unknownError) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.renewDocument) }, navigationIcon = { BackButton(onBack) }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val doc = state.document
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            doc == null -> EmptyState(Icons.Outlined.SearchOff, s.documentNotFound, modifier = Modifier.padding(padding))
            else -> Column(
                Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(doc.name, style = MaterialTheme.typography.titleLarge)
                DetailCard(s.currentDetails) {
                    InfoRow(s.issueDate, f.formatOrNull(doc.issueDate))
                    InfoRow(s.expiryDate, f.formatOrNull(doc.expiryDate))
                }
                DetailCard(s.newDetails) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        DateField(s.newIssueDate, state.newIssueDate, today, viewModel::setIssueDate, Modifier.fillMaxWidth())
                        val expiryError = when {
                            RenewalValidationError.NEW_EXPIRY_REQUIRED in state.errors -> s.errorNewExpiryRequired
                            RenewalValidationError.EXPIRY_BEFORE_ISSUE in state.errors -> s.errorExpiryBeforeIssue
                            RenewalValidationError.NOT_AFTER_PREVIOUS in state.errors -> s.errorNewExpiryNotAfterOld
                            else -> null
                        }
                        DateField(
                            s.newExpiryDate, state.newExpiryDate, today, viewModel::setExpiryDate, Modifier.fillMaxWidth(),
                            isError = expiryError != null, supportingText = expiryError,
                        )
                        DateField(s.renewalDateLabel, state.renewalDate, today, viewModel::setRenewalDate, Modifier.fillMaxWidth())
                        OutlinedTextField(
                            value = state.notes,
                            onValueChange = viewModel::setNotes,
                            label = { Text(s.renewalNotes) },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) {
                    if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Text(s.renewDocument)
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}
