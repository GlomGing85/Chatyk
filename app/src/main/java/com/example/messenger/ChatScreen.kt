package com.example.messenger

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Скільки мілісекунд "lastSeen" вважаємо користувача онлайн */
private const val ONLINE_WINDOW_MS = 45_000L
private val OnlineGreen = Color(0xFF6FCF97)

/**
 * Екран чату: стрічка повідомлень + поле для тексту.
 * Повідомлення з'являються в реальному часі.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(vm: ChatViewModel) {
    val myUid = vm.myUid ?: return
    val messages = vm.messages
    val presence = vm.presence

    var text by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // "Годинник": кожні 10 секунд оновлюємо час,
    // щоб перераховувати статус "онлайн"
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    val othersOnline = presence.values.count {
        it.uid != myUid && now - it.lastSeen < ONLINE_WINDOW_MS
    }

    // Чи список прикручений донизу (щоб вирішити: автоскрол чи кнопка "вниз")
    var nearBottom by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            last >= info.totalItemsCount - 2
        }.collect { nearBottom = it }
    }

    // Нове повідомлення → доскролити вниз (якщо ми і так внизу)
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && nearBottom) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text("Кімната: ${vm.roomCode}", fontWeight = FontWeight.SemiBold)
                            Text(
                                if (othersOnline > 0) "🟢 у мережі: $othersOnline" else "○ нікого немає",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (othersOnline > 0) OnlineGreen
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    },
                    actions = {
                        TextButton(onClick = { vm.exit() }) { Text("Вийти") }
                    }
                )
            },
            bottomBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .imePadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = { text = it },
                        placeholder = { Text("Повідомлення…") },
                        maxLines = 4,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            vm.send(text)
                            text = ""
                        },
                        enabled = text.isNotBlank(),
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text("➤", fontSize = 20.sp)
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Червоненький банер з помилкою бази даних (якщо є)
                vm.dbError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            err,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "👋 Поки порожньо. Напиши перше повідомлення!",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.id }) { m ->
                            MessageBubble(m, isMine = m.uid == myUid)
                        }
                    }
                }
            }
        }

        // Кнопка "доскролити вниз", якщо користувач піднявся вгору
        if (!nearBottom && messages.isNotEmpty()) {
            val scope = rememberCoroutineScope()
            FloatingActionButton(
                onClick = { scope.launch { listState.animateScrollToItem(messages.size - 1) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 88.dp)
            ) {
                Text("↓", fontSize = 20.sp)
            }
        }
    }
}

/** Один "бульбашковий" елемент повідомлення */
@Composable
private fun MessageBubble(m: Message, isMine: Boolean) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        if (!isMine) {
            Text(
                m.name,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Column(
            modifier = Modifier
                .padding(top = 2.dp)
                .widthIn(max = 300.dp)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isMine) 16.dp else 4.dp,
                        bottomEnd = if (isMine) 4.dp else 16.dp
                    )
                )
                .background(
                    if (isMine) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                m.text,
                color = if (isMine) Color.White else MaterialTheme.colorScheme.onSurface
            )
            Text(
                formatTime(m.ts),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) Color(0xFFDDD7FF)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

/** Форматування часу, наприклад 14:37 */
private fun formatTime(ts: Long): String =
    if (ts <= 0) ""
    else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
