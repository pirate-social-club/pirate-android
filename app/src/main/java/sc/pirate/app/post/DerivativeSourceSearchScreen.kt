package sc.pirate.app.post

import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.api.model.DerivativeSource
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.FormNote
import sc.pirate.app.ui.FormTone
import sc.pirate.app.ui.PhosphorIcons

data class DerivativeSourceSearchState(
    val query: String = "",
    val results: List<DerivativeSource> = emptyList(),
    val selectedIds: Set<String> = emptySet(),
    val loading: Boolean = false,
    val error: String? = null,
)

class DerivativeSourceSearchViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<sc.pirate.app.PirateApp>()
    private val communityRepository get() = app.repositories.communityRepository
    private val _state = MutableStateFlow(DerivativeSourceSearchState())
    val state: StateFlow<DerivativeSourceSearchState> = _state.asStateFlow()
    private var communityId: String? = null
    private var searchJob: Job? = null

    fun configure(communityId: String, selectedIds: List<String>) {
        val id = communityId.trim().takeIf { it.isNotBlank() } ?: return
        if (this.communityId == id && _state.value.selectedIds == selectedIds.toSet()) return
        this.communityId = id
        _state.value = _state.value.copy(
            selectedIds = selectedIds.mapNotNull { it.trim().takeIf { value -> value.isNotBlank() } }.toSet(),
            error = null,
        )
        search(immediate = true)
    }

    fun updateQuery(query: String) {
        _state.value = _state.value.copy(query = query, error = null)
        search(immediate = false)
    }

    fun toggleSource(sourceId: String) {
        val id = sourceId.trim().takeIf { it.isNotBlank() } ?: return
        val current = _state.value.selectedIds
        _state.value = _state.value.copy(
            selectedIds = if (id in current) current - id else current + id,
        )
    }

    private fun search(immediate: Boolean) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (!immediate) delay(300L)
            val id = communityId ?: return@launch
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val result = communityRepository.listDerivativeSources(
                    communityId = id,
                    kind = "song",
                    query = _state.value.query.trim().takeIf { it.isNotBlank() },
                    limit = 25,
                )
                _state.value = _state.value.copy(
                    loading = false,
                    results = result.items,
                )
            } catch (error: Exception) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = error.message ?: "Could not search source tracks.",
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DerivativeSourceSearchScreen(
    communityId: String,
    initialSelectedIds: List<String>,
    viewModel: DerivativeSourceSearchViewModel,
    onBack: () -> Unit,
    onDone: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(communityId, initialSelectedIds) {
        viewModel.configure(communityId, initialSelectedIds)
    }

    Scaffold(
        modifier = modifier,
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            TopAppBar(
                title = { Text("Select song", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            PhosphorIcons.CaretLeft,
                            contentDescription = "Back",
                            tint = PirateTokens.colors.textPrimary,
                        )
                    }
                },
                actions = {
                    TextButton(onClick = { onDone(state.selectedIds.toList()) }) {
                        Text("Done")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::updateQuery,
                label = { Text("Search songs") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            if (state.error != null) {
                FormNote(message = state.error!!, tone = FormTone.Error)
            }
            if (state.loading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = PirateTokens.colors.accentDanger)
                }
            }
            if (!state.loading && state.results.isEmpty()) {
                Text(
                    text = "No tracks found.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = PirateTokens.colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.results, key = { it.id }) { source ->
                    val sourceRef = sourceUpstreamAssetRef(source)
                    DerivativeSourceRow(
                        source = source,
                        selected = sourceRef in state.selectedIds,
                        onClick = { viewModel.toggleSource(sourceRef) },
                    )
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
private fun DerivativeSourceRow(
    source: DerivativeSource,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (selected) PirateTokens.colors.accentDanger.copy(alpha = 0.10f) else PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.lg),
        border = BorderStroke(
            1.dp,
            if (selected) PirateTokens.colors.accentDanger else PirateTokens.colors.borderSoft,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) PhosphorIcons.CheckCircle else PhosphorIcons.MusicNotes,
                contentDescription = null,
                tint = if (selected) PirateTokens.colors.accentDanger else PirateTokens.colors.textSecondary,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = source.title.ifBlank { "Untitled track" },
                    style = MaterialTheme.typography.titleMedium,
                    color = PirateTokens.colors.textPrimary,
                )
                Text(
                    text = sourceSubtitle(source),
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                )
            }
        }
    }
}

private fun sourceUpstreamAssetRef(source: DerivativeSource): String =
    "story:asset:${source.asset}"

private fun sourceSubtitle(source: DerivativeSource): String {
    val creator = source.creatorDisplayName
        ?: source.creatorHandle
        ?: source.creatorUser.takeIf { it.isNotBlank() }
        ?: "Unknown artist"
    val license = source.licensePreset?.takeIf { it.isNotBlank() } ?: "license unknown"
    val share = source.commercialRevSharePct?.let { " · $it% royalty share" }.orEmpty()
    return "$creator · $license$share"
}
