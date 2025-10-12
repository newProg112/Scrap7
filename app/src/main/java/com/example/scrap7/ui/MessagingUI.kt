package com.example.scrap7.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.scrap7.MessagingViewModel
import com.example.scrap7.model.ChatMessage
import kotlinx.coroutines.launch

@Composable
fun MessagingPanel(
    tripId: String,
    myUserId: String,
    visible: Boolean,
    tripStatus: String,
    modifier: Modifier = Modifier,
    vm: MessagingViewModel = viewModel(),
    onClose: () -> Unit
) {
    // Start listening whenever tripId/user changes
    LaunchedEffect(tripId, myUserId) { vm.bind(tripId, myUserId) }
    DisposableEffect(tripId, myUserId) { onDispose { vm.unbind() } }

    val messages by vm.messages.collectAsState()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val lastMessageId = messages.lastOrNull()?.id

    // Are we already near the bottom? (don’t yank if user scrolled up)
    val atBottom by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            // “near bottom” = last item is visible or 1 away
            total == 0 || lastVisible >= total - 2
        }
    }

    // When panel becomes visible (e.g., re-open), jump to bottom once layout is ready
    LaunchedEffect(visible, tripId) {
        if (visible) {
            // wait for compose/layout to settle
            withFrameNanos { }
            withFrameNanos { } // extra frame for good measure
            val last = messages.lastIndex.coerceAtLeast(0)
            if (last >= 0) listState.animateScrollToItem(last)
        }
    }

    // When new messages arrive, wait one frame so LazyColumn is laid out, then scroll
    LaunchedEffect(messages.size, atBottom) {
        if (messages.isNotEmpty() && atBottom) {
            withFrameNanos { /* wait for layout */ }
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    // A) When status flips (e.g., rider goes to in_progress), force jump to bottom if panel is visible
    LaunchedEffect(visible, tripStatus) {
        if (visible && tripStatus == "in_progress") {
            withFrameNanos { }
            withFrameNanos { } // small safety
            val last = messages.lastIndex.coerceAtLeast(0)
            if (last >= 0) listState.scrollToItem(last)
        }
    }

    // B) While in_progress, always follow the newest message (even if not at bottom)
    LaunchedEffect(lastMessageId, visible, tripStatus) {
        if (visible && tripStatus == "in_progress" && lastMessageId != null) {
            withFrameNanos { }
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    Column(modifier) {
        // Header with Close
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text("Chat", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text("Close") }
        }
        Divider()

        MessagesList(
            messages = messages,
            myUserId = myUserId,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            listState = listState
        )

        Divider()

        MessageComposer(
            onSend = { text -> vm.send(text) },
            modifier = Modifier.fillMaxWidth(),
            listState = listState
        )
    }
}

@Composable
private fun MessagesList(
    messages: List<ChatMessage>,
    myUserId: String,
    modifier: Modifier = Modifier,
    listState: LazyListState
) {
    LazyColumn(
        state = listState,
        modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 8.dp)
    ) {
        if (messages.isEmpty()) {
            item {
                Text(
                    "No messages yet",
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            items(messages, key = { it.id }) { m ->
                val mine = m.senderId == myUserId
                val bubbleColor =
                    if (mine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = bubbleColor,
                        shape = MaterialTheme.shapes.large,
                        tonalElevation = 1.dp,
                        modifier = Modifier.padding(vertical = 4.dp).widthIn(max = 320.dp)
                    ) {
                        Text(
                            text = m.text,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageComposer(
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    listState: LazyListState? = null  // so the composer can scroll after sending
) {
    val scope = rememberCoroutineScope()
    var text by remember { mutableStateOf("") }

    Row(
        modifier = modifier.padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f).padding(end = 8.dp),
            placeholder = { Text("Message…") },
            singleLine = true
        )
        Button(
            onClick = {
                val t = text.trim()
                if (t.isNotEmpty()) {
                    onSend(t)
                    text = ""
                    // nudge to bottom after send
                    listState?.let { s ->
                        scope.launch {
                            val last = (s.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                            s.animateScrollToItem(last)
                        }
                    }
                }
            }
        ) { Text("Send") }
    }
}