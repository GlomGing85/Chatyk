package com.example.messenger

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.scale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Скільки мілісекунд "lastSeen" вважаємо користувача онлайн */
private const val ONLINE_WINDOW_MS = 45_000L

/** Емодзі, доступні для реакцій на повідомлення */
private val ReactionEmojis = listOf("👍", "❤️", "😂", "😮", "😢", "🔥")

/**
 * Екран чату: стрічка повідомлень + поле для тексту.
 * Повідомлення з'являються в реальному часі.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ChatScreen(vm: ChatViewModel, settings: () -> Unit) {
    val myUid = vm.myUid ?: return
    val messages = vm.messages
    val presence = vm.presence
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // rememberSaveable — щоб набраний текст не зникав при повороті екрана
    var text by rememberSaveable { mutableStateOf(vm.draft()) }
    var reactingId by rememberSaveable { mutableStateOf<String?>(null) } // яке повідомлення "відкрито" для реакцій
    val listState = rememberLazyListState()

    // Кнопка «Назад»: спершу закриває вибір реакції, а потім виводить із кімнати
    // (раніше «Назад» просто закривав додаток)
    BackHandler { vm.navigateBack() }
    BackHandler(enabled = reactingId != null) { reactingId = null }

    // "Годинник": кожні 10 секунд оновлюємо час,
    // щоб перераховувати статус "онлайн"
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(10_000)
            now = System.currentTimeMillis()
        }
    }

    // Рахуємо за часом СЕРВЕРА: годинник телефона може поспішати чи відставати
    val serverNow = now + vm.serverTimeOffset
    val othersOnline = presence.values.count {
        it.uid != myUid && serverNow - it.lastSeen < ONLINE_WINDOW_MS
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

    // Нове повідомлення → доскролити вниз, якщо ми і так унизу або це НАШЕ повідомлення.
    // Стежимо за id останнього повідомлення, а не за кількістю: коли в кімнаті вже
    // MESSAGE_LIMIT повідомлень, кількість не змінюється (найстаріше зникає зі списку).
    val lastMessage = messages.lastOrNull()
    LaunchedEffect(lastMessage?.id) {
        if (lastMessage != null && (nearBottom || lastMessage.uid == myUid)) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = { Text("Кімната: ${vm.roomCode}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = { SymbolButton(R.drawable.symbol_settings, "Налаштування", settings) }
                    )
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 2.dp, modifier = Modifier.weight(1f)) {
                            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Symbol(R.drawable.symbol_circle, modifier = Modifier.size(12.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (othersOnline > 0) "У мережі: $othersOnline" else "Нікого",
                                    style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                        ActionButton("", {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "Заходь у Чатик! Код кімнати: ${vm.roomCode}")
                            }
                            runCatching { context.startActivity(Intent.createChooser(intent, "Поділитися кодом")) }
                                .onFailure { android.widget.Toast.makeText(context, "Немає застосунку для поширення", android.widget.Toast.LENGTH_SHORT).show() }
                        }, icon = R.drawable.symbol_share, description = "Поділитися кодом",
                            modifier = Modifier.width(56.dp), shape = ButtonGroupDefaults.connectedLeadingButtonShape)
                        ActionButton("Вийти", vm::backHome, shape = ButtonGroupDefaults.connectedTrailingButtonShape)
                        }
                    }
                }
            },
            bottomBar = {
                Surface(Modifier.fillMaxWidth().navigationBarsPadding().imePadding(),
                    shape = RoundedCornerShape(48.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 88.dp).padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextField(
                            value = text,
                            onValueChange = { text = it.take(MAX_MESSAGE_LENGTH); vm.saveDraft(text) },
                            maxLines = 4,
                            placeholder = { Text("Повідомлення…") },
                            supportingText = if (text.length > MAX_MESSAGE_LENGTH - 200) {
                                { Text("${text.length} / $MAX_MESSAGE_LENGTH") }
                            } else null,
                            shape = RoundedCornerShape(28.dp),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0f),
                                unfocusedIndicatorColor = MaterialTheme.colorScheme.outline.copy(alpha = 0f)
                            ),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Повідомлення" }
                        )
                        SymbolButton(R.drawable.symbol_send, "Надіслати", {
                            vm.send(text)
                            text = ""
                            vm.saveDraft("")
                        }, filled = true, enabled = text.isNotBlank())
                    }
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // Червоненький банер з помилкою бази даних (якщо є). Тап — сховати.
                vm.dbError?.let { err ->
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier
                            .fillMaxWidth()
                    ) {
                        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(err, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer,
                                style = MaterialTheme.typography.bodySmall)
                            SymbolButton(R.drawable.symbol_close, "Сховати помилку", vm::clearDbError)
                        }
                    }
                }

                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                            StateSymbol(R.drawable.symbol_sentiment_excited)
                            Spacer(Modifier.height(24.dp))
                            Text("Будьте першим, хто напише!", style = MaterialTheme.typography.titleLarge,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(messages, key = { it.id }) { m ->
                            MessageBubble(
                                m = m,
                                isMine = m.uid == myUid,
                                showTime = vm.showTimes,
                                showPicker = reactingId == m.id,
                                onLongPress = { reactingId = m.id },
                                onPickEmoji = { emoji ->
                                    vm.toggleReaction(m.id, emoji)
                                    reactingId = null
                                },
                                onDismissPicker = { reactingId = null }
                            )
                        }
                    }
                }
            }
        }

        // Кнопка "доскролити вниз", якщо користувач піднявся вгору
        if (!nearBottom && messages.isNotEmpty()) {
            val (fabSource, fabScale) = pressSource()
            FloatingActionButton(
                interactionSource = fabSource,
                onClick = { scope.launch { listState.animateScrollToItem(messages.lastIndex) } },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding().imePadding().padding(end = 16.dp, bottom = 104.dp).scale(fabScale)
            ) {
                Symbol(R.drawable.symbol_arrow_downward, "До останнього повідомлення")
            }
        }
    }
}

/** Один "бульбашковий" елемент повідомлення (з реакціями) */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    m: Message,
    isMine: Boolean,
    showTime: Boolean,
    showPicker: Boolean,
    onLongPress: () -> Unit,
    onPickEmoji: (String) -> Unit,
    onDismissPicker: () -> Unit
) {
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

        // Рядок вибору емодзі (з'являється після довгого тапу)
        if (showPicker) {
            Row(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ReactionEmojis.forEach { emoji ->
                    val (source, scale) = pressSource()
                    TextButton(onClick = { onPickEmoji(emoji) }, modifier = Modifier.size(48.dp).scale(scale),
                        interactionSource = source, contentPadding = PaddingValues(0.dp)) {
                        Text(emoji, fontSize = 20.sp)
                    }
                }
                SymbolButton(R.drawable.symbol_close, "Закрити реакції", onDismissPicker)
            }
        }

        // Саме повідомлення
        val (bubbleSource, bubbleScale) = pressSource()
        Column(
            modifier = Modifier
                .padding(top = 2.dp)
                .widthIn(max = 300.dp).scale(bubbleScale)
                .clip(
                    RoundedCornerShape(
                        topStart = 32.dp,
                        topEnd = 32.dp,
                        bottomStart = if (isMine) 32.dp else 8.dp,
                        bottomEnd = if (isMine) 8.dp else 32.dp
                    )
                )
                .background(
                    if (isMine) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .combinedClickable(
                    interactionSource = bubbleSource, indication = ripple(),
                    onClickLabel = "Реакції", onLongClickLabel = "Реакції",
                    onClick = onLongPress,
                    onLongClick = onLongPress
                )
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                m.text,
                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
            )
            if (showTime) Text(
                formatTime(m.ts),
                style = MaterialTheme.typography.labelSmall,
                color = if (isMine) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }

        // Чіпси з реакціями, напр. "👍 2" "😂 1"
        if (m.reactions.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 2.dp).horizontalScroll(rememberScrollState())) {
                m.reactions.values.groupingBy { it }.eachCount().forEach { (emoji, count) ->
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text(
                            "$emoji $count",
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Форматування часу, наприклад 14:37 */
private fun formatTime(ts: Long): String =
    if (ts <= 0) ""
    else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(ts))
