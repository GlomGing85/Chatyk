package com.example.messenger

/** Compact spacing only; never scales text or the user's accessibility font size. */
internal fun isCompactChat(widthDp: Int, heightDp: Int): Boolean =
    widthDp < 400 || heightDp < 720
