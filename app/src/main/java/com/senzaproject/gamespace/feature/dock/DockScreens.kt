package com.senzaproject.gamespace.feature.dock

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.senzaproject.gamespace.R
import com.senzaproject.gamespace.core.assistant.DockAddResult
import com.senzaproject.gamespace.core.assistant.DockRepository
import com.senzaproject.gamespace.core.assistant.MAX_DOCK_APPS
import com.senzaproject.gamespace.core.assistant.moveItem
import com.senzaproject.gamespace.core.database.DockAppEntity
import com.senzaproject.gamespace.core.designsystem.GameIcon
import com.senzaproject.gamespace.core.designsystem.GmCard
import com.senzaproject.gamespace.core.designsystem.GmScreen
import com.senzaproject.gamespace.core.designsystem.ReorderableColumn
import com.senzaproject.gamespace.core.gaming.GameIconLoader
import com.senzaproject.gamespace.core.gaming.InstalledApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DockViewModel @Inject constructor(
    private val repository: DockRepository,
    val iconLoader: GameIconLoader,
) : ViewModel() {
    val apps: StateFlow<List<DockAppEntity>> =
        repository.apps.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _installed = MutableStateFlow<List<InstalledApp>?>(null)
    val installed: StateFlow<List<InstalledApp>?> = _installed.asStateFlow()

    fun loadInstalled() {
        if (_installed.value != null) return
        viewModelScope.launch { _installed.value = repository.launchableApps() }
    }

    fun add(app: InstalledApp, onResult: (DockAddResult) -> Unit) {
        viewModelScope.launch { onResult(repository.add(app)) }
    }

    fun remove(packageName: String) {
        viewModelScope.launch { repository.remove(packageName) }
    }

    fun saveOrder(order: List<String>) {
        viewModelScope.launch { repository.reorder(order) }
    }
}

@Composable
fun DockEditorScreen(
    onBack: () -> Unit,
    onAdd: () -> Unit,
    viewModel: DockViewModel = hiltViewModel(),
) {
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    GmScreen(title = stringResource(R.string.screen_dock), onBack = onBack) {
        Text(stringResource(R.string.dk_intro), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        GmCard {
            Text(stringResource(R.string.dk_count, apps.size, MAX_DOCK_APPS), style = MaterialTheme.typography.titleMedium)
            if (apps.isEmpty()) {
                Text(stringResource(R.string.dk_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Local order while dragging; saved to the database when the finger lifts.
            var order by remember(apps) { mutableStateOf(apps) }
            ReorderableColumn(
                items = order,
                itemKey = { it.packageName },
                rowHeight = 64.dp,
                onMove = { from, to -> order = moveItem(order, from, to) },
                onDragEnd = { viewModel.saveOrder(order.map { it.packageName }) },
            ) { app: DockAppEntity, handle ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(
                        Icons.Filled.Menu,
                        contentDescription = stringResource(R.string.pe_drag_handle),
                        modifier = handle,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    GameIcon(app.packageName, app.label, viewModel.iconLoader, size = 40.dp)
                    Text(app.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { viewModel.remove(app.packageName) }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.dk_remove))
                    }
                }
            }
        }
        Button(onClick = onAdd, enabled = apps.size < MAX_DOCK_APPS, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.dk_add))
        }
        if (apps.size >= MAX_DOCK_APPS) {
            Text(stringResource(R.string.dk_full), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun AppPickerScreen(
    onBack: () -> Unit,
    viewModel: DockViewModel = hiltViewModel(),
) {
    val installed by viewModel.installed.collectAsStateWithLifecycle()
    val docked by viewModel.apps.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { viewModel.loadInstalled() }

    val addedText = stringResource(R.string.dk_added)
    val duplicateText = stringResource(R.string.dk_duplicate)
    val fullText = stringResource(R.string.dk_full)

    GmScreen(title = stringResource(R.string.screen_app_picker), onBack = onBack, snackbarHostState = snackbar, scrollable = false) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text(stringResource(R.string.dk_search)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        )
        val list = installed
        if (list == null) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(16.dp))
        } else {
            val dockedPackages = docked.map { it.packageName }.toSet()
            val filtered = list.filter {
                query.isBlank() || it.label.contains(query, ignoreCase = true) || it.packageName.contains(query, ignoreCase = true)
            }
            LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered, key = { it.packageName }) { app ->
                    val inDock = app.packageName in dockedPackages
                    Row(
                        Modifier.fillMaxWidth()
                            .clickable(enabled = !inDock) {
                                viewModel.add(app) { result ->
                                    scope.launch {
                                        snackbar.showSnackbar(
                                            when (result) {
                                                DockAddResult.ADDED -> addedText
                                                DockAddResult.DUPLICATE -> duplicateText
                                                DockAddResult.FULL -> fullText
                                            },
                                        )
                                    }
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        GameIcon(app.packageName, app.label, viewModel.iconLoader, size = 40.dp)
                        androidx.compose.foundation.layout.Column(Modifier.weight(1f)) {
                            Text(app.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (inDock) Text(stringResource(R.string.dk_in_dock), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
