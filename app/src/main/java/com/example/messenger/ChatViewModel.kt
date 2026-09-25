package com.example.messenger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.Query
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import java.util.TreeMap
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

// --- Обмеження. Такі самі перевірки є в правилах бази (database.rules.json) ---

/** Скільки останніх повідомлень кімнати завантажувати */
const val MESSAGE_LIMIT = 300

/** Максимальна довжина одного повідомлення */
const val MAX_MESSAGE_LENGTH = 2000

/** Максимальна довжина нікнейму */
const val MAX_NAME_LENGTH = 24

/** Максимальна довжина коду кімнати */
const val MAX_ROOM_LENGTH = 32

/** Як часто оновлюємо "lastSeen", поки додаток відкритий (мс) */
private const val PING_INTERVAL_MS = 15_000L

/** Одне повідомлення в чаті */
data class Message(
    val id: String = "",              // унікальний id у Firebase
    val uid: String = "",             // хто написав
    val name: String = "",            // ім'я автора
    val text: String = "",            // текст
    val ts: Long = 0L,                // час (мілісекунди)
    val reactions: Map<String, String> = emptyMap() // uid -> емодзі (реакції)
)

/** Інформація про користувача в кімнаті (для статусу "онлайн") */
data class PresenceUser(
    val uid: String = "",
    val name: String = "",
    val lastSeen: Long = 0L
)

/**
 * Код кімнати: лише малі літери, цифри та дефіс (пробіл → дефіс).
 * Тож "Kvity 2026" і "kvity-2026" — це одна й та сама кімната.
 */
fun normalizeRoomCode(raw: String): String =
    raw.lowercase()
        .replace(' ', '-')
        .filter { it.isLetterOrDigit() || it == '-' }
        .take(MAX_ROOM_LENGTH)

/**
 * Вся логіка месенджера: вхід, підписка на кімнату,
 * надсилання повідомлень і статус "онлайн".
 *
 * Дані зберігаються в Firebase Realtime Database:
 *   rooms/{код_кімнати}/messages/{id}   — повідомлення
 *   rooms/{код_кімнати}/presence/{uid}  — хто онлайн
 */
class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val preferences = application.getSharedPreferences("chatyk", 0)
    var showTimes by mutableStateOf(preferences.getBoolean("showTimes", true))
        private set
    fun updateShowTimes(value: Boolean) {
        showTimes = value
        preferences.edit().putBoolean("showTimes", value).apply()
    }
    fun draft(): String = preferences.getString("draft:$roomCode", "").orEmpty()
    fun saveDraft(value: String) { preferences.edit().putString("draft:$roomCode", value).apply() }
    private var connectionJob: Job? = null
    private var connectionAttempt = 0L
    var reverseNavigation by mutableStateOf(false)
        private set
    fun navigateBack() { backHome(); reverseNavigation = true }
    fun backHome() {
        reverseNavigation = false
        connectionAttempt++
        connectionJob?.cancel()
        connectionJob = null
        busy = false
        error = null
        exit()
    }
    fun retry() = start(myName, roomCode)


    // --- Стан, який бачить інтерфейс (Compose перемальовується, коли він змінюється) ---

    var started by mutableStateOf(false)          // ми вже в чаті?
        private set
    var busy by mutableStateOf(false)             // йде вхід?
        private set
    var error by mutableStateOf<String?>(null)    // помилка входу, якщо є
        private set
    var dbError by mutableStateOf<String?>(null)  // помилка бази даних (видно в чаті)
        private set

    var myName by mutableStateOf(preferences.getString("name", "").orEmpty())
        private set
    var roomCode by mutableStateOf(preferences.getString("room", "").orEmpty())
        private set
    var myUid by mutableStateOf<String?>(null)
        private set

    var messages by mutableStateOf(listOf<Message>())
        private set
    var presence by mutableStateOf(mapOf<String, PresenceUser>())
        private set

    /**
     * Наскільки годинник сервера випереджає годинник телефона (мс).
     * Потрібно, щоб правильно рахувати «у мережі», навіть якщо час на телефоні збився.
     */
    var serverTimeOffset by mutableStateOf(0L)
        private set

    // --- Firebase ---

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance().also {
        // Configure once, before any reference; RTDB persists history and queued writes.
        if (!persistenceConfigured) {
            it.setPersistenceEnabled(true)
            persistenceConfigured = true
        }
    }
    companion object { private var persistenceConfigured = false }

    /** Адреса бази даних, до якої реально підключений додаток (для перевірки) */
    val dbUrl: String
        get() = runCatching { db.reference.toString() }.getOrDefault("?")

    /** Повідомлення кімнати, відсортовані за ключем (ключі push() ідуть за часом) */
    private val messageMap = TreeMap<String, Message>()

    private var messagesQuery: Query? = null
    private var messagesListener: ChildEventListener? = null
    private var presenceListRef: DatabaseReference? = null
    private var presenceListener: ValueEventListener? = null
    private var presenceRef: DatabaseReference? = null     // мій запис у presence
    private val connectedRef: DatabaseReference = db.getReference(".info/connected")
    private var connectedListener: ValueEventListener? = null
    private val offsetRef: DatabaseReference = db.getReference(".info/serverTimeOffset")
    private var offsetListener: ValueEventListener? = null
    private var pingJob: Job? = null

    /** Чи видно додаток на екрані (згорнутий додаток — не «в мережі») */
    private var inForeground = true

    /**
     * Вхід у кімнату: анонімний вхід у Firebase + підписка на кімнату.
     */
    fun start(nickname: String, room: String) {
        if (busy || started) return
        val code = normalizeRoomCode(room)
        if (code.isEmpty()) {
            error = "Введи код кімнати"
            return
        }
        if (nickname.isBlank()) return
        myName = nickname.trim().take(MAX_NAME_LENGTH)
        roomCode = code
        preferences.edit().putString("name", myName).putString("room", code).apply()
        reverseNavigation = false
        busy = true
        error = null
        dbError = null
        val attempt = ++connectionAttempt
        connectionJob = viewModelScope.launch {
            try {
                withTimeout(20_000) {
                    if (auth.currentUser == null) auth.signInAnonymously().await()
                    val uid = auth.currentUser?.uid ?: error("AUTH_NO_USER")
                    myUid = uid
                    awaitConnection()
                    val initial = db.getReference("rooms").child(code).child("messages")
                        .orderByKey().limitToLast(MESSAGE_LIMIT).get().await()
                    attachRoom(uid)
                    initial.children.forEach { putMessage(it) }
                    started = true
                }
            } catch (e: TimeoutCancellationException) {
                detachRoom()
                error = "CONNECTION_TIMEOUT: сервер не відповів за 20 секунд."
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                detachRoom()
                error = "${e.javaClass.simpleName}: ${e.localizedMessage ?: "Невідома помилка"}"
            } finally {
                if (attempt == connectionAttempt) busy = false
            }
        }
    }

    private suspend fun awaitConnection(): Unit = suspendCancellableCoroutine { continuation ->
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == true && continuation.isActive) {
                    connectedRef.removeEventListener(this)
                    continuation.resume(Unit)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                connectedRef.removeEventListener(this)
                if (continuation.isActive) continuation.resumeWithException(error.toException())
            }
        }
        connectedRef.addValueEventListener(listener)
        continuation.invokeOnCancellation { connectedRef.removeEventListener(listener) }
    }

    /** Підписатися на повідомлення та статус користувачів у кімнаті */
    private fun attachRoom(uid: String) {
        val roomRef = db.getReference("rooms").child(roomCode)
        messageMap.clear()
        messages = emptyList()

        // 1) Слухач повідомлень: останні MESSAGE_LIMIT повідомлень + усі нові.
        //    (Раніше завантажувалась УСЯ історія кімнати — великі кімнати гальмували
        //    і з'їдали безкоштовний ліміт трафіку Firebase.)
        val query = roomRef.child("messages").orderByKey().limitToLast(MESSAGE_LIMIT)
        messagesQuery = query
        messagesListener = query.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) =
                putMessage(snapshot)

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) =
                putMessage(snapshot)

            override fun onChildRemoved(snapshot: DataSnapshot) {
                val key = snapshot.key ?: return
                if (messageMap.remove(key) != null) publishMessages()
            }

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
            override fun onCancelled(error: DatabaseError) {
                dbError = "Помилка читання повідомлень: ${error.message}"
            }
        })

        // 2) Слухач "хто зараз у кімнаті"
        val presenceList = roomRef.child("presence")
        presenceListRef = presenceList
        presenceListener = presenceList.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, PresenceUser>()
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    map[key] = PresenceUser(
                        uid = key,
                        name = child.child("name").value as? String ?: "",
                        lastSeen = (child.child("lastSeen").value as? Number)?.toLong() ?: 0L
                    )
                }
                this@ChatViewModel.presence = map
            }

            override fun onCancelled(error: DatabaseError) {
                dbError = "Помилка статусу користувачів: ${error.message}"
            }
        })

        // 3) Мій статус "я в мережі". Слухаємо .info/connected: після КОЖНОГО
        //    (пере)підключення знову записуємо себе й реєструємо onDisconnect.
        //    Раніше після втрати інтернету запис відновлювався без імені,
        //    а автоприбирання при відключенні більше не спрацьовувало.
        presenceRef = presenceList.child(uid)
        connectedListener = connectedRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.value == true && inForeground) goOnline()
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })

        // 4) Різниця між годинниками сервера й телефона
        offsetListener = offsetRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                serverTimeOffset = (snapshot.value as? Number)?.toLong() ?: 0L
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })

        // 5) Раз на 15 секунд оновлюємо "lastSeen" (лише поки додаток на екрані)
        if (inForeground) startPing()
    }

    /** Записати себе в presence і попросити сервер прибрати запис, коли зв'язок зникне */
    private fun goOnline() {
        val ref = presenceRef ?: return
        ref.onDisconnect().removeValue()
        ref.setValue(mapOf("name" to myName, "lastSeen" to ServerValue.TIMESTAMP))
    }

    private fun startPing() {
        pingJob?.cancel()
        pingJob = viewModelScope.launch {
            while (true) {
                delay(PING_INTERVAL_MS)
                // updateChildren (а не лише lastSeen): якщо запис встигли прибрати,
                // він відновиться повністю — разом з ім'ям
                presenceRef?.updateChildren(
                    mapOf("name" to myName, "lastSeen" to ServerValue.TIMESTAMP)
                )
            }
        }
    }

    /** Додаток знову на екрані (викликає MainActivity) */
    fun onAppForeground() {
        if (inForeground) return
        inForeground = true
        if (started) {
            goOnline()
            startPing()
        }
    }

    /**
     * Додаток згорнули або вимкнули екран (викликає MainActivity).
     * Раніше в такому стані ми ще довго лишалися «у мережі» для інших.
     */
    fun onAppBackground() {
        if (!inForeground) return
        inForeground = false
        pingJob?.cancel()
        pingJob = null
        presenceRef?.removeValue()
    }

    /** Надіслати повідомлення в поточну кімнату */
    fun send(text: String) {
        val t = text.trim().take(MAX_MESSAGE_LENGTH)
        val uid = myUid ?: return
        if (t.isEmpty() || !started) return
        val data = mapOf(
            "uid" to uid,
            "name" to myName,
            "text" to t,
            "ts" to ServerValue.TIMESTAMP
        )
        messagesRef().push().setValue(data)
            .addOnFailureListener { e ->
                dbError = "Повідомлення не надіслано: ${e.message}"
            }
    }

    /**
     * Поставити/прибрати реакцію-емодзі на повідомленні.
     * Якщо ти вже поставив це емодзі — воно прибереться (toggle).
     *
     * Пишемо ЛИШЕ свій запис reactions/{мій uid}. Раніше перезаписувався весь
     * список реакцій, і одночасні реакції різних людей могли губитися.
     */
    fun toggleReaction(messageId: String, emoji: String) {
        val uid = myUid ?: return
        val msg = messageMap[messageId] ?: return
        val ref = messagesRef().child(messageId).child("reactions").child(uid)
        val task = if (msg.reactions[uid] == emoji) ref.removeValue() else ref.setValue(emoji)
        task.addOnFailureListener { e ->
            dbError = "Не вдалося оновити реакцію: ${e.message}"
        }
    }

    /** Сховати червоний банер з помилкою */
    fun clearDbError() {
        dbError = null
    }

    /** Вийти з кімнати та повернутися на екран входу */
    fun exit() {
        detachRoom()
        started = false
        messages = emptyList()
        presence = emptyMap()
        dbError = null
    }

    private fun messagesRef(): DatabaseReference =
        db.getReference("rooms").child(roomCode).child("messages")

    private fun detachRoom() {
        pingJob?.cancel()
        pingJob = null
        messagesListener?.let { l -> messagesQuery?.removeEventListener(l) }
        presenceListener?.let { l -> presenceListRef?.removeEventListener(l) }
        connectedListener?.let { connectedRef.removeEventListener(it) }
        offsetListener?.let { offsetRef.removeEventListener(it) }
        messagesQuery = null
        messagesListener = null
        presenceListRef = null
        presenceListener = null
        connectedListener = null
        offsetListener = null
        presenceRef?.let { ref ->
            ref.onDisconnect().cancel()
            ref.removeValue()
        }
        presenceRef = null
        messageMap.clear()
    }

    /** Додати або оновити повідомлення у списку */
    private fun putMessage(snapshot: DataSnapshot) {
        val m = snapshot.toMessage()
        if (m.id.isEmpty()) return
        messageMap[m.id] = m
        publishMessages()
    }

    private fun publishMessages() {
        messages = messageMap.values.toList()
    }

    /**
     * Перетворити дані з Firebase на об'єкт Message.
     * Безпечні перетворення (as?), щоб "криві" дані в базі не валили додаток.
     */
    private fun DataSnapshot.toMessage(): Message {
        val reactions = mutableMapOf<String, String>()
        child("reactions").children.forEach { r ->
            val who = r.key
            val emoji = r.value as? String
            if (!who.isNullOrEmpty() && !emoji.isNullOrEmpty()) reactions[who] = emoji
        }
        return Message(
            id = key ?: "",
            uid = child("uid").value as? String ?: "",
            name = child("name").value as? String ?: "",
            text = child("text").value as? String ?: "",
            ts = (child("ts").value as? Number)?.toLong() ?: 0L,
            reactions = reactions
        )
    }

    override fun onCleared() {
        detachRoom()
        super.onCleared()
    }
}
