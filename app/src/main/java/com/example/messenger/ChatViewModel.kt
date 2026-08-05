package com.example.messenger

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Одне повідомлення в чаті */
data class Message(
    val id: String = "",      // унікальний id у Firebase
    val uid: String = "",     // хто написав
    val name: String = "",    // ім'я автора
    val text: String = "",    // текст
    val ts: Long = 0L         // час (мілісекунди)
)

/** Інформація про користувача в кімнаті (для статусу "онлайн") */
data class PresenceUser(
    val uid: String = "",
    val name: String = "",
    val lastSeen: Long = 0L
)

/**
 * Вся логіка месенджера: вхід, підписка на кімнату,
 * надсилання повідомлень і статус "онлайн".
 *
 * Дані зберігаються в Firebase Realtime Database:
 *   rooms/{код_кімнати}/messages/{id}   — повідомлення
 *   rooms/{код_кімнати}/presence/{uid}  — хто онлайн
 */
class ChatViewModel : ViewModel() {

    // --- Стан, який бачить інтерфейс (Compose перемальовується, коли він змінюється) ---

    var started by mutableStateOf(false)          // ми вже в чаті?
        private set
    var busy by mutableStateOf(false)             // йде вхід?
        private set
    var error by mutableStateOf<String?>(null)    // помилка, якщо є
        private set

    var myName by mutableStateOf("")
        private set
    var roomCode by mutableStateOf("")
        private set
    var myUid by mutableStateOf<String?>(null)
        private set

    var messages by mutableStateOf(listOf<Message>())
        private set
    var presence by mutableStateOf(mapOf<String, PresenceUser>())
        private set

    // --- Firebase ---

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseDatabase.getInstance()

    private var messagesListener: ChildEventListener? = null
    private var presenceListener: ValueEventListener? = null
    private var presenceRef: DatabaseReference? = null
    private var pingJob: Job? = null

    /**
     * Вхід у кімнату: анонімний вхід у Firebase + підписка на кімнату.
     */
    fun start(nickname: String, room: String) {
        if (busy) return
        busy = true
        error = null
        viewModelScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                myUid = auth.currentUser?.uid
                    ?: throw IllegalStateException("Не вдалося увійти у Firebase")
                myName = nickname.trim().ifEmpty { "Анонім" }
                roomCode = room.trim().lowercase().replace(" ", "-")
                attachRoom(myUid!!)
                started = true
            } catch (e: Exception) {
                error = "Помилка входу: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    /** Підписатися на повідомлення та статус користувачів у кімнаті */
    private fun attachRoom(myUid: String) {
        val roomRef = db.getReference("rooms/$roomCode")

        // 1) Слухач повідомлень — спрацьовує щоразу, коли хтось щось написав
        messagesListener = roomRef.child("messages")
            .addChildEventListener(object : ChildEventListener {
                override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                    messages = (messages + snapshot.toMessage()).sortedBy { it.ts }
                }

                override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                    val m = snapshot.toMessage()
                    messages = messages.map { if (it.id == m.id) m else it }.sortedBy { it.ts }
                }

                override fun onChildRemoved(snapshot: DataSnapshot) {
                    messages = messages.filter { it.id != snapshot.key }
                }

                override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) = Unit
                override fun onCancelled(error: DatabaseError) = Unit
            })

        // 2) Слухач "хто зараз у кімнаті"
        val presence = roomRef.child("presence")
        presenceListener = presence.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val map = mutableMapOf<String, PresenceUser>()
                for (child in snapshot.children) {
                    map[child.key ?: ""] = PresenceUser(
                        uid = child.key ?: "",
                        name = child.child("name").getValue(String::class.java) ?: "",
                        lastSeen = child.child("lastSeen").getValue(Long::class.java) ?: 0L
                    )
                }
                this@ChatViewModel.presence = map
            }

            override fun onCancelled(error: DatabaseError) = Unit
        })

        // 3) Пишемо про себе: "я в кімнаті"
        presenceRef = presence.child(myUid)
        presenceRef!!.setValue(mapOf("name" to myName, "lastSeen" to ServerValue.TIMESTAMP))
        // Якщо додаток закриють — статус прибереться сам
        presenceRef!!.onDisconnect().removeValue()

        // 4) Раз на 15 секунд оновлюємо "lastSeen", щоб інші бачили нас онлайн
        pingJob = viewModelScope.launch {
            while (true) {
                delay(15_000)
                presenceRef?.child("lastSeen")?.setValue(ServerValue.TIMESTAMP)
            }
        }
    }

    /** Надіслати повідомлення в поточну кімнату */
    fun send(text: String) {
        val t = text.trim()
        val myUid = myUid ?: return
        if (t.isEmpty()) return
        val data = mapOf(
            "uid" to myUid,
            "name" to myName,
            "text" to t,
            "ts" to ServerValue.TIMESTAMP
        )
        db.getReference("rooms/$roomCode/messages").push().setValue(data)
    }

    /** Вийти з кімнати та повернутися на екран входу */
    fun exit() {
        detachRoom()
        started = false
        messages = emptyList()
        presence = emptyMap()
    }

    private fun detachRoom() {
        pingJob?.cancel()
        pingJob = null
        if (roomCode.isNotEmpty()) {
            messagesListener?.let {
                db.getReference("rooms/$roomCode/messages").removeEventListener(it)
            }
            presenceListener?.let {
                db.getReference("rooms/$roomCode/presence").removeEventListener(it)
            }
        }
        messagesListener = null
        presenceListener = null
        presenceRef?.removeValue()
        presenceRef = null
    }

    /** Перетворити дані з Firebase на об'єкт Message */
    private fun DataSnapshot.toMessage(): Message = Message(
        id = key ?: "",
        uid = child("uid").getValue(String::class.java) ?: "",
        name = child("name").getValue(String::class.java) ?: "",
        text = child("text").getValue(String::class.java) ?: "",
        ts = child("ts").getValue(Long::class.java) ?: 0L
    )

    override fun onCleared() {
        detachRoom()
        super.onCleared()
    }
}
