package com.logisparktech.doctrack.feature.categories

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.logisparktech.doctrack.core.localization.strings
import com.logisparktech.doctrack.domain.model.Category
import com.logisparktech.doctrack.domain.repository.CategoryRepository
import com.logisparktech.doctrack.ui.components.BackButton
import com.logisparktech.doctrack.ui.components.CategoryBadge
import com.logisparktech.doctrack.ui.components.CategoryIcons
import com.logisparktech.doctrack.ui.components.ConfirmDialog
import com.logisparktech.doctrack.ui.components.InlineEmpty
import com.logisparktech.doctrack.ui.components.SectionHeader
import com.logisparktech.doctrack.ui.components.categoryLabel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface CategoryMessage {
    data class InUse(val count: Int) : CategoryMessage
    data object Exists : CategoryMessage
}

class CategoriesViewModel(private val repository: CategoryRepository) : ViewModel() {
    val categories: StateFlow<List<Category>> =
        repository.observeCategories().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<CategoryMessage?>(null)
    val message: StateFlow<CategoryMessage?> = _message.asStateFlow()

    fun dismissMessage() { _message.value = null }

    /** Returns false when the name collides with an existing category. */
    fun save(id: Long?, name: String, icon: String, color: Long, displayNames: Map<Long, String>): Boolean {
        val trimmed = name.trim()
        val clash = categories.value.any { it.id != id && (it.name.equals(trimmed, true) || displayNames[it.id].equals(trimmed, true)) }
        if (clash) {
            _message.value = CategoryMessage.Exists
            return false
        }
        viewModelScope.launch {
            if (id == null) repository.add(trimmed, icon, color) else repository.update(id, trimmed, icon, color)
        }
        return true
    }

    fun delete(category: Category, onDeleted: () -> Unit) {
        viewModelScope.launch {
            val count = repository.documentCount(category.id)
            if (count > 0) _message.value = CategoryMessage.InUse(count)
            else {
                repository.delete(category.id)
                onDeleted()
            }
        }
    }
}

@Composable
fun CategoriesScreen(viewModel: CategoriesViewModel, onBack: () -> Unit) {
    val s = strings
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Category?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Category?>(null) }
    val displayNames = categories.associate { it.id to categoryLabel(it) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(s.categories) }, navigationIcon = { BackButton(onBack) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { creating = true },
                icon = { Icon(Icons.Outlined.Add, null) },
                text = { Text(s.addCategory) },
            )
        },
    ) { padding ->
        val custom = categories.filter { !it.isDefault }
        val defaults = categories.filter { it.isDefault }
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item { SectionHeader(s.customCategories) }
            if (custom.isEmpty()) item { InlineEmpty(s.noCustomCategories) }
            items(custom, key = { it.id }) { c ->
                CategoryRow(c, onEdit = { editing = c }, onDelete = { deleting = c })
            }
            item { SectionHeader(s.defaultCategories) }
            items(defaults, key = { it.id }) { c -> CategoryRow(c, null, null) }
        }
    }

    if (creating || editing != null) {
        CategoryEditorDialog(
            initial = editing,
            onDismiss = { creating = false; editing = null },
            onSave = { name, icon, color ->
                if (viewModel.save(editing?.id, name, icon, color, displayNames)) {
                    creating = false
                    editing = null
                }
            },
        )
    }
    deleting?.let { c ->
        ConfirmDialog(
            title = s.deleteCategoryTitle,
            message = s.deleteCategoryMessage(categoryLabel(c)),
            confirmLabel = s.delete,
            destructive = true,
            onConfirm = { viewModel.delete(c) { }; deleting = null },
            onDismiss = { deleting = null },
        )
    }
    message?.let { m ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMessage,
            text = {
                Text(
                    when (m) {
                        is CategoryMessage.InUse -> s.categoryInUse(m.count)
                        CategoryMessage.Exists -> s.errorCategoryExists
                    }
                )
            },
            confirmButton = { TextButton(onClick = viewModel::dismissMessage) { Text(s.ok) } },
        )
    }
}

@Composable
private fun CategoryRow(category: Category, onEdit: (() -> Unit)?, onDelete: (() -> Unit)?) {
    val s = strings
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(category, size = 40.dp)
            Spacer(Modifier.width(12.dp))
            Text(categoryLabel(category), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            if (onEdit != null) IconButton(onClick = onEdit) { Icon(Icons.Outlined.Edit, s.edit) }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Outlined.Delete, s.delete, tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryEditorDialog(initial: Category?, onDismiss: () -> Unit, onSave: (String, String, Long) -> Unit) {
    val s = strings
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: "folder") }
    var color by remember { mutableStateOf(initial?.color ?: CategoryIcons.palette.first()) }
    var showError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) s.addCategory else s.editCategory) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CategoryBadge(icon, color, size = 48.dp)
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it; showError = false },
                        label = { Text(s.categoryName) },
                        isError = showError,
                        supportingText = if (showError) ({ Text(s.errorCategoryNameRequired) }) else null,
                        singleLine = true,
                    )
                }
                Text(s.categoryIcon, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryIcons.all.keys.forEach { key ->
                        Surface(
                            onClick = { icon = key },
                            shape = MaterialTheme.shapes.small,
                            color = if (key == icon) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        ) {
                            Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                                Icon(CategoryIcons.of(key), null, tint = if (key == icon) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Text(s.categoryColor, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CategoryIcons.palette.forEach { c ->
                        Surface(onClick = { color = c }, shape = CircleShape, color = Color.Transparent) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .background(Color(c.toInt()), CircleShape)
                                    .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (c == color) Icon(Icons.Outlined.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank()) showError = true else onSave(name, icon, color)
            }) { Text(s.save) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
