package com.example.messenger

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatLayoutTest {
    @Test fun smallPortraitUsesCompactSpacing() { assertTrue(isCompactChat(360, 640)) }
    @Test fun narrowTallPhoneUsesCompactSpacing() { assertTrue(isCompactChat(360, 800)) }
    @Test fun shortWindowUsesCompactSpacing() { assertTrue(isCompactChat(412, 640)) }
    @Test fun landscapeUsesCompactSpacing() { assertTrue(isCompactChat(800, 360)) }
    @Test fun originalDesignSizeKeepsRegularSpacing() { assertFalse(isCompactChat(412, 892)) }
    @Test fun boundaryIsInclusiveForRegularSpacing() {
        assertFalse(isCompactChat(400, 720))
        assertTrue(isCompactChat(399, 720))
        assertTrue(isCompactChat(400, 719))
    }
}
