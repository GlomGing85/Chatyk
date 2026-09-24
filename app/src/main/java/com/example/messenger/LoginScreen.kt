package com.example.messenger

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.random.Random

/**
 * Екран входу: нікнейм + код кімнати.
 * Однаковий код кімнати = спільна переписка.
 */
@Composable
fun LoginScreen(vm: ChatViewModel) {
    // rememberSaveable — поля не скидаються при повороті екрана.
    // Після виходу з кімнати підставляємо попередній нікнейм і код.
    var nickname by rememberSaveable { mutableStateOf(vm.myName.ifEmpty { randomNickname() }) }
    var room by rememberSaveable { mutableStateOf(vm.roomCode) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("💬", fontSize = 56.sp)
        Spacer(Modifier.height(8.dp))
        Text(
            "Чатик",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            "Простий месенджер без реєстрації",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { if (it.length <= MAX_NAME_LENGTH) nickname = it },
            label = { Text("Твій нікнейм") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = room,
            // лише малі літери, цифри й дефіс; пробіл сам стає дефісом
            onValueChange = { room = normalizeRoomCode(it) },
            label = { Text("Код кімнати") },
            placeholder = { Text("наприклад: kvity-2026") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "Придумай код кімнати і поділися ним з другом — ви потрапите в одну переписку.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = { vm.start(nickname, room) },
            enabled = !vm.busy && room.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
        ) {
            if (vm.busy) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Увійти до кімнати", style = MaterialTheme.typography.titleMedium)
            }
        }

        vm.error?.let { error ->
            Spacer(Modifier.height(16.dp))
            Text(
                error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "База даних: ${vm.dbUrl}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Кумедний нікнейм за замовчуванням, щоб не сидіти з порожнім полем */
private fun randomNickname(): String {
    val adjectives = listOf(
        "Веселий", "Сміливий", "Загадковий", "Швидкий",
        "Мудрий", "Яскравий", "Тихий", "Великий"
    )
    val animals = listOf(
        "Кіт", "Пес", "Лис", "Їжак", "Сова",
        "Панда", "Кенгуру", "Ведмідь", "Заєць", "Лев"
    )
    return "${adjectives.random()}_${animals.random()}_${Random.nextInt(10, 99)}"
}
