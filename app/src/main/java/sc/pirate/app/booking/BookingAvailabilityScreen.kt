package sc.pirate.app.booking

import android.app.Application
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import sc.pirate.app.PirateApp
import sc.pirate.app.api.model.BookingSlotsResponse
import sc.pirate.app.api.model.ResolvedBookingSlot
import sc.pirate.app.theme.PirateTokens
import sc.pirate.app.ui.ButtonVariant
import sc.pirate.app.ui.FeedSkeletons
import sc.pirate.app.ui.PhosphorIcons
import sc.pirate.app.ui.PirateButton
import sc.pirate.app.ui.StatusCard
import sc.pirate.app.ui.StatusTone

data class BookingAvailabilityUiState(
    val loading: Boolean = true,
    val data: BookingSlotsResponse? = null,
    val error: String? = null,
)

class BookingAvailabilityViewModel(application: Application) : AndroidViewModel(application) {
    private val app get() = getApplication<PirateApp>()
    private val _state = MutableStateFlow(BookingAvailabilityUiState())
    val state: StateFlow<BookingAvailabilityUiState> = _state.asStateFlow()

    fun load(hostUserId: String) {
        val host = hostUserId.trim()
        if (host.isBlank()) {
            _state.value = BookingAvailabilityUiState(loading = false, error = "Host profile unavailable.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val from = Instant.now()
                val data = app.apiClient.bookings.listHostSlots(
                    hostUserId = host,
                    from = from.toString(),
                    to = from.plusSeconds(14L * 24L * 60L * 60L).toString(),
                    timezone = ZoneId.systemDefault().id,
                )
                _state.value = BookingAvailabilityUiState(loading = false, data = data)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                _state.value = BookingAvailabilityUiState(
                    loading = false,
                    error = error.message ?: "This host is not currently bookable.",
                )
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun BookingAvailabilityScreen(
    hostUserId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BookingAvailabilityViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val state by viewModel.state.collectAsState()
    LaunchedEffect(hostUserId) { viewModel.load(hostUserId) }
    Scaffold(
        modifier = modifier,
        containerColor = PirateTokens.colors.bgPage,
        topBar = {
            TopAppBar(
                title = { Text("Available sessions", color = PirateTokens.colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(PhosphorIcons.CaretLeft, contentDescription = "Back", tint = PirateTokens.colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = PirateTokens.colors.bgPage),
            )
        },
    ) { padding ->
        when {
            state.loading && state.data == null -> FeedSkeletons(
                count = 4,
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            state.error != null -> Column(
                modifier = Modifier.padding(padding).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StatusCard(
                    title = "Availability unavailable",
                    description = state.error.orEmpty(),
                    tone = StatusTone.Warning,
                )
                PirateButton(
                    text = "Try again",
                    onClick = { viewModel.load(hostUserId) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            else -> BookingAvailabilityContent(
                data = state.data ?: BookingSlotsResponse("UTC", ZoneId.systemDefault().id),
                loading = state.loading,
                onRefresh = { viewModel.load(hostUserId) },
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun BookingAvailabilityContent(
    data: BookingSlotsResponse,
    loading: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zone = runCatching { ZoneId.of(data.viewerTimezone) }.getOrDefault(ZoneId.systemDefault())
    val groups = data.slots.groupBy { slotDay(it, zone) }
    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Spacer(Modifier.height(2.dp)) }
        item {
            StatusCard(
                title = "Times in ${zone.id}",
                description = "Host timezone: ${data.hostTimezone}. Availability covers the next 14 days.",
                tone = StatusTone.Default,
            )
        }
        item {
            StatusCard(
                title = "Browsing only on Android",
                description = "Slot checkout remains on hold until the Play billing policy and production settlement gate are resolved.",
                tone = StatusTone.Warning,
            )
        }
        item {
            PirateButton(
                text = "Refresh availability",
                onClick = onRefresh,
                loading = loading,
                variant = ButtonVariant.Outline,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (groups.isEmpty()) {
            item {
                StatusCard(
                    title = "No open slots",
                    description = "This host has no available sessions in the next two weeks.",
                    tone = StatusTone.Default,
                )
            }
        }
        groups.forEach { (day, slots) ->
            item {
                Text(day, style = MaterialTheme.typography.titleMedium, color = PirateTokens.colors.textPrimary)
            }
            items(slots.size) { index -> BookingSlotCard(slots[index], zone) }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun BookingSlotCard(slot: ResolvedBookingSlot, zone: ZoneId) {
    val start = parseSlotInstant(slot.startUtc)?.atZone(zone)
    val end = parseSlotInstant(slot.endUtc)?.atZone(zone)
    val time = if (start != null && end != null) {
        "${start.format(DateTimeFormatter.ofPattern("h:mm a"))} – ${end.format(DateTimeFormatter.ofPattern("h:mm a"))}"
    } else {
        "Time unavailable"
    }
    androidx.compose.material3.Surface(
        color = PirateTokens.colors.surfaceSubtle,
        shape = RoundedCornerShape(PirateTokens.radius.md),
        border = BorderStroke(1.dp, PirateTokens.colors.borderSoft),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(time, style = MaterialTheme.typography.titleSmall, color = PirateTokens.colors.textPrimary)
                Text(
                    text = if (slot.available) "Open" else "Unavailable",
                    style = MaterialTheme.typography.bodySmall,
                    color = PirateTokens.colors.textSecondary,
                )
            }
            Text(
                text = "${slot.priceCents / 100}.${(slot.priceCents % 100).toString().padStart(2, '0')} USDC",
                style = MaterialTheme.typography.labelLarge,
                color = PirateTokens.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun parseSlotInstant(value: String): Instant? = runCatching { Instant.parse(value) }.getOrNull()

private fun slotDay(slot: ResolvedBookingSlot, zone: ZoneId): String =
    parseSlotInstant(slot.startUtc)
        ?.atZone(zone)
        ?.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
        ?: "Date unavailable"
