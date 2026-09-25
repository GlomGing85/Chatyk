package com.example.messenger

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun LoginScreen(vm: ChatViewModel, settings: () -> Unit) {
    var nickname by rememberSaveable { mutableStateOf(vm.myName) }
    var room by rememberSaveable { mutableStateOf(vm.roomCode) }
    val focus = LocalFocusManager.current
    val valid = nickname.isNotBlank() && room.isNotBlank()
    val enter = { if (valid) { focus.clearFocus(); vm.start(nickname, room) } }
    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            SymbolButton(R.drawable.symbol_settings, "Налаштування", settings)
        }
        Spacer(Modifier.height(24.dp))
        StateSymbol(R.drawable.symbol_sms)
        Spacer(Modifier.height(32.dp))
        Text("Чатик", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Простий месенджер без реєстрації", style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        OutlinedTextField(nickname, { nickname = it.take(MAX_NAME_LENGTH) }, Modifier.fillMaxWidth(),
            label = { Text("Твій нікнейм") }, leadingIcon = { Symbol(R.drawable.symbol_person) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            supportingText = { Text("До $MAX_NAME_LENGTH символів") })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(room, { room = normalizeRoomCode(it) }, Modifier.fillMaxWidth(),
            label = { Text("Код кімнати") }, leadingIcon = { Symbol(R.drawable.symbol_abc) },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { enter() }))
        Spacer(Modifier.height(16.dp))
        Text("Придумай код кімнати і поділися ним із другом — ви потрапите в одну переписку.",
            Modifier.fillMaxWidth(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(24.dp))
        ActionButton("Увійти до кімнати", enter, Modifier.fillMaxWidth(), enabled = valid, icon = R.drawable.symbol_login)
        Spacer(Modifier.height(40.dp))
        Text("База даних: ${vm.dbUrl}", Modifier.fillMaxWidth().padding(bottom = 16.dp),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectingScreen() {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(56.dp))
        Text("Будь ласка, зачекайте…", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text("Це може тривати кілька секунд", color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            LoadingIndicator(Modifier.size(96.dp))
        }
        Text("Назад — скасувати підключення", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 24.dp))
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ConnectionFailedScreen(vm: ChatViewModel) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(24.dp))
        StateSymbol(R.drawable.symbol_sentiment_dissatisfied)
        Text("З’єднання не вдалося", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Text("Перевірте підключення до мережі та спробуйте ще раз.",
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Surface(Modifier.fillMaxWidth().heightIn(min = 240.dp), shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Код помилки:", Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                SelectionContainer { Text(vm.error.orEmpty(), style = MaterialTheme.typography.bodyMedium) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            ActionButton("На головну", vm::backHome, Modifier.weight(1f), shape = ButtonGroupDefaults.connectedLeadingButtonShape)
            ActionButton("Спробувати ще", vm::retry, Modifier.weight(1f), tonal = true, shape = ButtonGroupDefaults.connectedTrailingButtonShape)
        }
    }
}
