package com.example.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Точка входу в додаток.
 * Тут лише вирішуємо, який екран показати:
 * вхід (LoginScreen) чи чат (ChatScreen).
 */
class MainActivity : ComponentActivity() {

    /** Одна ViewModel на весь екран: переживає поворот екрана */
    private val vm: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MessengerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!vm.started) {
                        LoginScreen(vm)
                    } else {
                        ChatScreen(vm)
                    }
                }
            }
        }
    }

    // Статус «у мережі» — лише поки додаток видно на екрані
    override fun onStart() {
        super.onStart()
        vm.onAppForeground()
    }

    override fun onStop() {
        super.onStop()
        // При повороті екрана активність перестворюється — це не «пішов у фон»
        if (!isChangingConfigurations) vm.onAppBackground()
    }
}

/** Фірмовий фіолетовий колір додатку */
val ChatykPurple = Color(0xFF8A5CFF)

/**
 * Темна тема "Чатика". Трохи фіолетового відтінку, щоб було затишно :)
 */
@Composable
fun MessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = ChatykPurple,
            background = Color(0xFF12101A),
            surface = Color(0xFF1C1927),
            surfaceVariant = Color(0xFF2A2438),
            onSurface = Color(0xFFEDEAF6),
            onSurfaceVariant = Color(0xFFB8B0CC)
        ),
        content = content
    )
}
