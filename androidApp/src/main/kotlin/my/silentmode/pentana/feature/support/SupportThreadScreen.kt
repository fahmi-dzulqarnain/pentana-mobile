package my.silentmode.pentana.feature.support

import android.graphics.BitmapFactory
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import my.silentmode.pentana.core.dateTimeMedium
import my.silentmode.pentana.core.relativeTimeFrom
import my.silentmode.pentana.shared.model.SupportMessageDto
import my.silentmode.pentana.shared.model.SupportTicketDetailDto
import my.silentmode.pentana.shared.presentation.SupportImage
import my.silentmode.pentana.shared.presentation.SupportStatus
import my.silentmode.pentana.shared.presentation.SupportThreadUiState
import my.silentmode.pentana.shared.presentation.closedNote
import my.silentmode.pentana.shared.presentation.supportHeaderLabel
import my.silentmode.pentana.shared.presentation.supportStatus
import my.silentmode.pentana.shared.presentation.supportTypeLabel
import my.silentmode.pentana.ui.appContainer
import my.silentmode.pentana.ui.components.ErrorState
import my.silentmode.pentana.ui.components.LoadingState
import my.silentmode.pentana.ui.theme.LocalPentanaColors

/** One question's conversation: the member's messages on the right, the committee's on the left. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportThreadScreen(ticketId: Long, onBack: () -> Unit) {
    val container = appContainer()
    // Keyed per ticket: the default key would hand a second ticket the first one's store.
    val vm: SupportThreadViewModel = viewModel(
        key = "support-thread-$ticketId",
        factory = viewModelFactory { initializer { SupportThreadViewModel(container.support, ticketId) } },
    )
    val state by vm.store.state.collectAsStateWithLifecycle()
    val inFlight by vm.store.inFlight.collectAsStateWithLifecycle()
    val actionError by vm.store.actionError.collectAsStateWithLifecycle()
    val images by vm.store.images.collectAsStateWithLifecycle()
    var draft by rememberSaveable { mutableStateOf("") }
    var confirmResolve by remember { mutableStateOf(false) }

    val refreshing by vm.store.refreshing.collectAsStateWithLifecycle()

    BackHandler(onBack = onBack)
    // The ViewModel outlives the screen (no NavHost), so reopening a ticket must fetch what changed since.
    LaunchedEffect(ticketId) { vm.refresh() }

    val detail = (state as? SupportThreadUiState.Content)?.detail
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(detail?.subject ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
        bottomBar = {
            if (detail != null) {
                val sending = ticketId in inFlight
                Composer(
                    draft = draft,
                    onDraft = { draft = it },
                    closed = supportStatus(detail.status) == SupportStatus.CLOSED,
                    sending = sending,
                    onSend = { vm.store.reply(draft); draft = "" },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface,
    ) { pad ->
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize().padding(pad)) {
            when (val uiState = state) {
                is SupportThreadUiState.Loading -> LoadingState()
                is SupportThreadUiState.Error -> ErrorState(uiState.message, vm.store::load)
                is SupportThreadUiState.Content -> Thread(
                    detail = uiState.detail,
                    images = images.associate { it.messageId to it.image },
                    resolving = -ticketId in inFlight,
                    onLoadImage = { id, url -> vm.store.loadImage(id, url) },
                    onResolve = { confirmResolve = true },
                )
            }
        }
    }

    if (confirmResolve) {
        AlertDialog(
            onDismissRequest = { confirmResolve = false },
            title = { Text("Mark this question as resolved?") },
            text = { Text("You can still reply later to reopen it.") },
            confirmButton = { TextButton(onClick = { confirmResolve = false; vm.store.resolve() }) { Text("Mark as resolved") } },
            dismissButton = { TextButton(onClick = { confirmResolve = false }) { Text("Cancel") } },
        )
    }
    actionError?.let { message ->
        AlertDialog(
            onDismissRequest = vm.store::dismissActionError,
            title = { Text("Something went wrong") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = vm.store::dismissActionError) { Text("OK") } },
        )
    }
}

@Composable
private fun Thread(
    detail: SupportTicketDetailDto,
    images: Map<Long, SupportImage>,
    resolving: Boolean,
    onLoadImage: (Long, String) -> Unit,
    onResolve: () -> Unit,
) {
    val status = supportStatus(detail.status)
    val listState = rememberLazyListState()
    LaunchedEffect(detail.messages.size) {
        if (detail.messages.isNotEmpty()) listState.animateScrollToItem(detail.messages.size)
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
        item {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
                SupportStatusChip(status, supportHeaderLabel(status))
                Text(
                    supportTypeLabel(detail.type),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(8.dp)).padding(horizontal = 11.dp, vertical = 5.dp),
                )
            }
        }
        items(detail.messages, key = { it.id }) { message ->
            Bubble(message, images[message.id]) { url -> onLoadImage(message.id, url) }
        }
        item {
            val note = closedNote(detail)
            if (note != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 12.dp).clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = LocalPentanaColors.current.ok.color, modifier = Modifier.size(18.dp))
                    val date = dateTimeMedium(detail.closedAt)
                    Text(if (date.isBlank()) note else "$note · $date", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                Box(Modifier.fillMaxWidth().padding(top = 14.dp), contentAlignment = Alignment.Center) {
                    OutlinedButton(onClick = onResolve, enabled = !resolving, shape = RoundedCornerShape(24.dp)) {
                        Icon(Icons.Filled.Check, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("Mark as resolved")
                    }
                }
            }
        }
    }
}

@Composable
private fun Bubble(message: SupportMessageDto, image: SupportImage?, loadImage: (String) -> Unit) {
    val mine = message.isMine
    val who = if (mine) "You" else "Committee"
    val whenText = relativeTimeFrom(message.createdAt)
    Column(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
        Text(if (whenText.isBlank()) who else "$who · $whenText", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 6.dp))
        Spacer(Modifier.height(3.dp))
        val shape = if (mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
        Text(
            message.body,
            style = MaterialTheme.typography.bodyLarge,
            color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 300.dp).clip(shape)
                .background(if (mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 13.dp, vertical = 10.dp),
        )
        message.attachmentUrl?.let { url ->
            LaunchedEffect(url) { loadImage(url) }
            Spacer(Modifier.height(6.dp))
            Screenshot(image)
        }
    }
}

@Composable
private fun Screenshot(image: SupportImage?) {
    val shape = RoundedCornerShape(12.dp)
    val bitmap = remember(image) {
        (image as? SupportImage.Loaded)?.let { BitmapFactory.decodeByteArray(it.bytes, 0, it.bytes.size)?.asImageBitmap() }
    }
    when {
        bitmap != null -> Image(
            bitmap = bitmap,
            contentDescription = "Screenshot",
            contentScale = ContentScale.Fit,
            modifier = Modifier.widthIn(max = 220.dp).heightIn(max = 260.dp).clip(shape),
        )
        image is SupportImage.Failed || image is SupportImage.Loaded -> Row(
            Modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.BrokenImage, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Screenshot unavailable", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        else -> Box(Modifier.size(180.dp, 110.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun Composer(draft: String, onDraft: (String) -> Unit, closed: Boolean, sending: Boolean, onSend: () -> Unit) {
    val canSend = draft.isNotBlank() && !sending
    Row(
        // Clear of the gesture bar, and above the keyboard while typing.
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).navigationBarsPadding().imePadding()
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = draft,
            onValueChange = onDraft,
            placeholder = { Text(if (closed) "Reply to reopen…" else "Reply…") },
            maxLines = 5,
            shape = RoundedCornerShape(24.dp),
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            ),
            modifier = Modifier.weight(1f),
        )
        FilledIconButton(onClick = onSend, enabled = canSend, modifier = Modifier.size(48.dp), shape = CircleShape) {
            if (sending) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Send reply")
        }
    }
}
