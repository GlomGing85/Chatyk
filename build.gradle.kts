// Кореневий файл проєкту: тут лише описуємо версії інструментів.
// Самі модулі (папка app/) мають власний build.gradle.kts.
plugins {
    id("com.android.application") version "9.1.0" apply false
    id("org.jetbrains.kotlin.android") version "2.2.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.20" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
