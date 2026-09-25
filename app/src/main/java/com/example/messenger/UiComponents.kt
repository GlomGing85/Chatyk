package com.example.messenger

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription

@Composable
fun Symbol(id: Int, description: String? = null, modifier: Modifier = Modifier.size(24.dp)) {
    Icon(painterResource(id), description, modifier)
}

@Composable
fun pressSource(): Pair<MutableInteractionSource, Float> {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, spring(dampingRatio = 0.65f), label = "press")
    return source to scale
}

@Composable
fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                 tonal: Boolean = false, enabled: Boolean = true, icon: Int? = null,
                 shape: Shape = CircleShape, description: String? = null) {
    val (source, scale) = pressSource()
    Button(onClick, modifier.heightIn(min = 56.dp).scale(scale).semantics { if (description != null) contentDescription = description }, enabled = enabled,
        interactionSource = source, shape = shape,
        contentPadding = if (label.isEmpty()) PaddingValues(0.dp) else ButtonDefaults.ContentPadding,
        colors = if (tonal) ButtonDefaults.filledTonalButtonColors() else ButtonDefaults.buttonColors()) {
        if (icon != null) { Symbol(icon); if (label.isNotEmpty()) Spacer(Modifier.width(8.dp)) }
        if (label.isNotEmpty()) Text(label)
    }
}

@Composable
fun SymbolButton(id: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier,
                 filled: Boolean = false, enabled: Boolean = true) {
    val (source, scale) = pressSource()
    if (filled) FilledIconButton(onClick, modifier.size(56.dp).scale(scale), enabled = enabled, interactionSource = source) {
        Symbol(id, description)
    } else IconButton(onClick, modifier.size(48.dp).scale(scale), enabled = enabled, interactionSource = source) {
        Symbol(id, description)
    }
}

// These large symbols illustrate a state, not an action: intentionally not clickable.
@Composable
fun StateSymbol(id: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(136.dp)) {
        Box(contentAlignment = Alignment.Center) { Symbol(id, modifier = Modifier.size(64.dp)) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: ChatViewModel, dismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, shape = RoundedCornerShape(topStart = 40.dp, topEnd = 40.dp)) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Налаштування", style = MaterialTheme.typography.headlineSmall)
            Text("Чатик ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Час повідомлень", Modifier.weight(1f))
                val (source, scale) = pressSource()
                Switch(checked = vm.showTimes, onCheckedChange = vm::updateShowTimes,
                    interactionSource = source, modifier = Modifier.scale(scale).semantics { contentDescription = "Показувати час повідомлень" })
            }
            Text("Оформлення: темне • Purple", style = MaterialTheme.typography.bodyMedium)
            Text("Без реєстрації — але не секретний чат. Повідомлення зберігаються у Firebase без наскрізного шифрування. Кожен, хто знає код кімнати, може приєднатися.",
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("База даних: ${vm.dbUrl}", style = MaterialTheme.typography.bodySmall)
            ActionButton("Готово", dismiss, Modifier.fillMaxWidth())
        }
    }
}
