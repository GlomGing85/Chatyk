#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
#  Чатик — налаштування ПОСТІЙНОГО ключа підпису APK (запускати в Termux)
#
#  Навіщо: без постійного ключа GitHub Actions щоразу підписує APK новим
#  тимчасовим ключем, і Android відмовляється ставити оновлення поверх
#  старої версії («Додаток не встановлено»).
#
#  Що робить скрипт:
#    1) створює ключ ~/chatyk-keys/chatyk-release.jks (або бере наявний);
#    2) кладе його в СЕКРЕТИ GitHub-репозиторію (KEYSTORE_BASE64,
#       KEYSTORE_PASSWORD, KEY_ALIAS) — у код ключ НЕ потрапляє;
#    3) допомагає зробити резервну копію.
#
#  Запуск (з папки репозиторію):   bash scripts/setup-signing.sh
#  Скрипт можна запускати повторно — наявний ключ він не перезапише.
# ─────────────────────────────────────────────────────────────────────────────
set -eu

KEY_DIR="${CHATYK_KEY_DIR:-$HOME/chatyk-keys}"
KEYSTORE="$KEY_DIR/chatyk-release.jks"
PASS_FILE="$KEY_DIR/password.txt"
ALIAS="chatyk"

say()  { printf '%s\n' "$*"; }
fail() { printf '❌ %s\n' "$*" >&2; exit 1; }

# ── 0. Перевірки ────────────────────────────────────────────────────────────
command -v gh >/dev/null 2>&1 || fail "Немає gh. Встанови: pkg install gh"
gh auth status >/dev/null 2>&1 || fail "gh не авторизований. Виконай: gh auth login"
REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner 2>/dev/null)" \
  || fail "Не бачу GitHub-репозиторій. Запусти скрипт із папки проєкту: cd ~/Chatyk"

if ! command -v keytool >/dev/null 2>&1; then
  if command -v pkg >/dev/null 2>&1; then
    say "☕ Встановлюю Java (потрібна лише для створення ключа)…"
    pkg install -y openjdk-17
  fi
  command -v keytool >/dev/null 2>&1 \
    || fail "Немає keytool. Встанови Java 17 (у Termux: pkg install openjdk-17)"
fi

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

# ── 1. Ключ ─────────────────────────────────────────────────────────────────
if [ -f "$KEYSTORE" ]; then
  say "🔑 Знайдено наявний ключ: $KEYSTORE"
  if [ -f "$PASS_FILE" ]; then
    PASS="$(cat "$PASS_FILE")"
  else
    read -r -s -p "Введи пароль від ключа: " PASS
    echo
  fi
else
  say "🔑 Створюю новий ключ підпису…"
  PASS="$(head -c 48 /dev/urandom | base64 | tr -dc 'A-Za-z0-9' | cut -c1-24)"
  keytool -genkeypair -noprompt \
    -keystore "$KEYSTORE" -storetype PKCS12 -storepass "$PASS" \
    -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Chatyk, O=Chatyk, C=UA" >/dev/null 2>&1
  printf '%s' "$PASS" > "$PASS_FILE"
  chmod 600 "$KEYSTORE" "$PASS_FILE"
  say "✅ Ключ створено: $KEYSTORE"
fi

# Перевіряємо, що пароль справді підходить до ключа
keytool -list -keystore "$KEYSTORE" -storepass "$PASS" -alias "$ALIAS" >/dev/null 2>&1 \
  || fail "Пароль не підходить до ключа (або в ньому немає alias '$ALIAS')"

# ── 2. Секрети GitHub ───────────────────────────────────────────────────────
say "☁️  Зберігаю секрети в репозиторії $REPO…"
base64 < "$KEYSTORE" | tr -d '\n' | gh secret set KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASS"  | gh secret set KEYSTORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" | gh secret set KEY_ALIAS --repo "$REPO"
say "✅ Секрети KEYSTORE_BASE64, KEYSTORE_PASSWORD і KEY_ALIAS збережено"

# ── 3. Відбиток і резервна копія ────────────────────────────────────────────
FP="$(keytool -list -v -keystore "$KEYSTORE" -storepass "$PASS" -alias "$ALIAS" 2>/dev/null \
      | grep -m1 'SHA256:' | sed 's/.*SHA256: *//')"
say ""
say "🔏 Відбиток ключа (SHA-256):"
say "   $FP"
say "   Такий самий відбиток буде у звіті кожної збірки в Actions."

if [ -d "$HOME/storage/downloads" ]; then
  say ""
  read -r -p "💾 Покласти резервну копію ключа в «Завантаження»? [Т/н] " ANSWER || ANSWER=""
  case "$ANSWER" in
    [НнNn]*) say "   Добре. Ключ лежить тут: $KEY_DIR" ;;
    *)
      cp "$KEYSTORE"  "$HOME/storage/downloads/chatyk-release.jks"
      cp "$PASS_FILE" "$HOME/storage/downloads/chatyk-key-password.txt"
      say "   Готово: chatyk-release.jks і chatyk-key-password.txt у «Завантаженнях»."
      say "   ➜ Перенеси їх у надійне місце (наприклад, приватна папка на Google Диску),"
      say "     а із «Завантажень» потім видали."
      ;;
  esac
fi

say ""
say "⚠️  НЕ ВТРАЧАЙ ключ і пароль і НІКОЛИ не клади їх у репозиторій!"
say "    Без них наступні версії знову доведеться ставити з видаленням старої."
say ""
say "🎉 Готово! Тепер: git push (або Actions → «Збірка APK» → Run workflow)."
