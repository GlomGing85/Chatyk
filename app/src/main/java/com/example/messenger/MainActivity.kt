package com.example.messenger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.viewModels
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

class MainActivity : ComponentActivity() {
    private val vm: ChatViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(ChatykColors.surface.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(ChatykColors.surface.toArgb())
        )
        setContent { MessengerTheme { ChatykApp(vm) } }
    }
    override fun onStart() { super.onStart(); vm.onAppForeground() }
    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) vm.onAppBackground()
    }
}

val ChatykColors = darkColorScheme(
    primary = Color(0xFFD2BCFC), onPrimary = Color(0xFF32226F),
    primaryContainer = Color(0xFF4C3889), onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCDC1E1), onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4B425D), onSecondaryContainer = Color(0xFFE9DDFD),
    tertiary = Color(0xFFEFB8C8), onTertiary = Color(0xFF4F2532),
    tertiaryContainer = Color(0xFF6C3644), onTertiaryContainer = Color(0xFFFDDAE1),
    background = Color(0xFF141317), onBackground = Color(0xFFE4E1E7),
    surface = Color(0xFF141317), surfaceContainerLow = Color(0xFF1C1B1F),
    surfaceContainer = Color(0xFF201F23), surfaceContainerHigh = Color(0xFF2B292D),
    surfaceContainerHighest = Color(0xFF363438), surfaceVariant = Color(0xFF363438),
    onSurface = Color(0xFFE4E1E7), onSurfaceVariant = Color(0xFFC9C4D1),
    outline = Color(0xFF938F9B), outlineVariant = Color(0xFF494550),
    inverseSurface = Color(0xFFE4E1E7), inverseOnSurface = Color(0xFF313034),
    inversePrimary = Color(0xFF6750A4),
    error = Color(0xFFF2B8B5), onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18), onErrorContainer = Color(0xFFF9DEDC)
)

// Bundled Roboto keeps the same typography even on phones with a different system font.
private val ChatykFont = FontFamily(
    Font(R.font.roboto_regular, FontWeight.Normal),
    Font(R.font.roboto_medium, FontWeight.Medium),
    Font(R.font.roboto_bold, FontWeight.Bold)
)
private val ChatykTypography = with(Typography()) {
    Typography(
        displayLarge = displayLarge.copy(fontFamily = ChatykFont),
        displayMedium = displayMedium.copy(fontFamily = ChatykFont),
        displaySmall = displaySmall.copy(fontFamily = ChatykFont),
        headlineLarge = headlineLarge.copy(fontFamily = ChatykFont),
        headlineMedium = headlineMedium.copy(fontFamily = ChatykFont),
        headlineSmall = headlineSmall.copy(fontFamily = ChatykFont),
        titleLarge = titleLarge.copy(fontFamily = ChatykFont),
        titleMedium = titleMedium.copy(fontFamily = ChatykFont),
        titleSmall = titleSmall.copy(fontFamily = ChatykFont),
        bodyLarge = bodyLarge.copy(fontFamily = ChatykFont),
        bodyMedium = bodyMedium.copy(fontFamily = ChatykFont),
        bodySmall = bodySmall.copy(fontFamily = ChatykFont),
        labelLarge = labelLarge.copy(fontFamily = ChatykFont),
        labelMedium = labelMedium.copy(fontFamily = ChatykFont),
        labelSmall = labelSmall.copy(fontFamily = ChatykFont)
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MessengerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = ChatykColors, typography = ChatykTypography, motionScheme = MotionScheme.expressive(), content = content)
}

@Composable
private fun ChatykApp(vm: ChatViewModel) {
    var settings by rememberSaveable { mutableStateOf(false) }
    val screen = when { vm.started -> "chat"; vm.busy -> "connecting"; vm.error != null -> "failed"; else -> "home" }
    BackHandler(screen != "home") { vm.navigateBack() }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        AnimatedContent(
            targetState = screen,
            transitionSpec = {
                if (targetState == "home" && vm.reverseNavigation) {
                    (scaleIn(initialScale = 0.96f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.9f) + fadeOut())
                } else if (targetState == "home") {
                    (slideInHorizontally(spring(dampingRatio = 0.85f)) { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it / 3 } + fadeOut())
                } else {
                    (scaleIn(spring(dampingRatio = 0.8f), initialScale = 0.9f) + fadeIn()) togetherWith
                        (scaleOut(targetScale = 0.96f) + fadeOut())
                }
            }, label = "navigation"
        ) { destination ->
            when (destination) {
                "home" -> LoginScreen(vm) { settings = true }
                "connecting" -> ConnectingScreen()
                "failed" -> ConnectionFailedScreen(vm)
                "chat" -> ChatScreen(vm) { settings = true }
            }
        }
        if (settings) SettingsSheet(vm) { settings = false }
    }
}
