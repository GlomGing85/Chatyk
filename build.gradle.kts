// Кореневий файл проєкту: тут лише описуємо версії інструментів.
// Самі модулі (папка app/) мають власний build.gradle.kts.
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
}
