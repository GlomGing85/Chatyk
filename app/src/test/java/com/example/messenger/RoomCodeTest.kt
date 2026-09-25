package com.example.messenger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Local JVM tests only: do not initialize Firebase or contact a real database. */
class RoomCodeTest {
    @Test fun sameRoomForDifferentCase() {
        assertEquals("kvity-2026", normalizeRoomCode("Kvity 2026"))
    }
    @Test fun firebasePathCharactersAreRemoved() {
        val code = normalizeRoomCode("A.B#C\$D[E]/F")
        assertEquals("abcdef", code)
        assertFalse(code.any { it in ".#\$[]/" })
    }
    @Test fun roomLengthIsBounded() {
        assertEquals(MAX_ROOM_LENGTH, normalizeRoomCode("a".repeat(100)).length)
    }
    @Test fun ukrainianRoomCodesStillWork() {
        assertEquals("друзі-2026", normalizeRoomCode("Друзі 2026"))
    }
    @Test fun emptyInputIsEmpty() {
        assertEquals("", normalizeRoomCode(""))
        assertEquals("", normalizeRoomCode(".#\$[]/"))
    }
    @Test fun normalizationIsIdempotent() {
        val code = normalizeRoomCode("Моя Кімната! 123")
        assertEquals(code, normalizeRoomCode(code))
    }
}
