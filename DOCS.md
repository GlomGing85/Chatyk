# 📘 DOCS — документація «Чатика»

Тут зібрана детальна технічна інструкція: як запустити, зібрати, оновити та
випускати нові версії додатку. Короткий огляд — у [README.md](./README.md).

---

## 1. Що це і як працює

«Чатик» — простий Android-месенджер без реєстрації:

- Користувач вводить **нікнейм** і **код кімнати**;
- Однаковий код кімнати = спільна переписка;
- Повідомлення зберігаються в **Firebase Realtime Database** і приходять у реальному часі.

Архітектура:

| Шар | Файл | Роль |
|-----|------|------|
| UI (екрани) | `LoginScreen.kt`, `ChatScreen.kt` | Вхід, стрічка повідомлень, статус «у мережі» |
| Логіка | `ChatViewModel.kt` | Firebase Auth, слухачі бази, надсилання |
| Точка входу | `MainActivity.kt` | Тема + вибір екрану |
| Збірка | `.github/workflows/build.yml` | Авто-збірка APK у GitHub Actions |

Дані в базі виглядають так:

```
rooms/
└── kvity-2026/              ← код кімнати
    ├── messages/{id}/       ← повідомлення
    │     ├── uid            ← хто написав
    │     ├── name           ← ім'я автора
    │     ├── text           ← текст
    │     └── ts             ← час (мілісекунди)
    └── presence/{uid}/      ← хто онлайн
          ├── name
          └── lastSeen
```

---

## 2. Збірка з нуля (як усе налаштувати)

Весь процес можна зробити **лише з телефону** — жоден крок не потребує комп'ютера.

### Крок 1. GitHub-репозиторій

1. Створи безкоштовний акаунт на [github.com](https://github.com).
2. **New repository** → назва `Chatyk` → **Public** (для приватних репозиторіїв
   безкоштовних хвилин збірки менше).
3. НЕ відмічай «Add a README file» — має бути порожнім.

### Крок 2. Firebase (безкоштовний backend)

> ⚠️ **Порядок важливий**: спочатку створюємо базу даних, і лише **потім**
> завантажуємо `google-services.json`. Інакше у файлі не буде `firebase_url`,
> і додаток не знатиме адреси бази.

1. [console.firebase.google.com](https://console.firebase.google.com) →
   **Створити проєкт** (Google Analytics можна вимкнути).
2. **Build → Realtime Database → Create database**:
   - регіон **`europe-west1`** (Бельгія — ближче до України);
   - режим безпеки **Start in production mode**.
3. Вкладка **Rules** → встав правила та **Publish**:

   ```json
   {
     "rules": {
       ".read": "auth != null",
       ".write": "auth != null"
     }
   }
   ```

4. **Build → Authentication → Get started → Sign-in method** → увімкни **Anonymous** → Save.
5. На головній сторінці проєкту **+ → Android**:
   - **Android package name:** `com.example.messenger` (точно, з крапками);
   - Register app.
6. **Download google-services.json** — тепер, коли база існує, у файлі буде
   `firebase_url`. Цей файл кладемо в репозиторій у папку `app/`.
7. Перевірка: у файлі має бути рядок типу
   `"firebase_url": "https://…-default-rtdb.europe-west1.firebasedatabase.app"`.

### Крок 3. Завантажити код у репозиторій

**Варіант А — Termux (рекомендовано):**

```bash
pkg install -y git gh           # якщо ще не встановлено
gh auth login                    # увійти у свій GitHub-акаунт

cd ~
git clone https://github.com/ТВІЙ_ЛОГІН/Chatyk.git
cd Chatyk

# покласти google-services.json у папку app/
cp ~/storage/downloads/google-services.json app/google-services.json

git add -A
git commit -m "init: проект та конфігурація"
git push
```

**Варіант Б — github.dev:**

1. Відкрий репозиторій, у адресному рядку заміни `github.com` → `github.dev`.
2. **Explorer → Upload Files...** — завантаж усі файли проєкту (папки `app/`,
   `.github/`, `gradle/` тощо) + `google-services.json` у `app/`.
3. Commit.

### Крок 4. Збірка APK

GitHub Actions запускається автоматично після кожного push у `main`:

1. Вкладка **Actions** → запуск «Збірка APK» → дочекайся зелених галочок ✅ (5–10 хв).
2. Внизу блок **Artifacts** → **chatyk-apk** → розпакуй → файл `app-debug.apk`.

Звідти ж можна перезапустити збірку вручну: **Actions → Збірка APK → Run workflow**.

### Крок 5. Встановлення

1. Відкрий `app-debug.apk` на телефоні (файловий менеджер).
2. Дозволь установку з «невідомих джерел» за запитом.
3. Так само на другому телефоні. Введи однаковий код кімнати — спілкуйся! 💬

---

## 3. Випуск нових версій

### Версія у коді

`app/build.gradle.kts`:

```kotlin
defaultConfig {
    versionCode = 2          // ⬆️ ЗБІЛЬШУЙ при кожній версії (1, 2, 3, …)
    versionName = "0.0.3"    // етикетка, яку бачить користувач
}
```

- `versionCode` — ціле число **для Android**: додаток оновлюється лише якщо воно зросло.
- `versionName` — будь-яка текстова назва, що показується в налаштуваннях.

### Створити реліз (Termux)

```bash
cd ~/Chatyk

# 1) завантажити APK останньої збірки з хмари
gh run download --name chatyk-apk

# 2) створити реліз і прикріпити APK
gh release create v0.0.3 \
  --title "Чатик 0.0.3" \
  --notes "Опис змін..." \
  app-debug.apk
```

Оновити існуючий реліз: `gh release upload v0.0.3 app-debug.apk --clobber`.

---

## 4. Діагностика

| Симптом | Причина | Рішення |
|---------|---------|---------|
| «google-services.json is missing» | файл не в репо | поклади його у `app/` і закоміть |
| Чат «працює», але нічого не приходить | немає `firebase_url` у json | завантаж json **після** створення бази |
| Банер «Permission denied» | правила БД не опубліковані | Крок 2, пункт 3 → Publish |
| «Анонімний вхід не працює» | Anonymous вимкнено | Крок 2, пункт 4 |
| Помилка push: «rejected, non-fast-forward» | історії розійшлися | `git pull --rebase origin main && git push` |
| Зникли комміти після pull | було використано `--force` | відновлення з `git reflog` |

Додаток сам показує діагностику:

- на екрані входу внизу — **адреса бази даних**, до якої реально підключений додаток;
- у чаті — **червоний банер** з текстом помилки Firebase, якщо вона є.

---

## 5. Безпека та використання власної бази

- Код додатку відкритий (MIT) — можна використовувати, модифікувати,
  публікувати під своїм ім'ям.
- **Заборонено** використовувати авторську Firebase-базу: щоб зробити «свій»
  Чатик, створи власний Firebase-проєкт (Крок 2) і поклади свій
  `google-services.json`.
- Повідомлення **не шифруються**. Будь-хто, хто знає код кімнати, може
  прочитати переписку. Не надсилай секрети.

---

*Документація — частина проєкту [Chatyk](https://github.com/GlomGing85/Chatyk).*
