package com.yakin.app

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import java.io.File

data class Sticker(val id: String, val emoji: String)
data class ContactRequest(val number: String, val name: String, val status: Int)

class StickerPack(ctx: Context) {
    val stickers = mutableStateListOf(
        Sticker("1", "😀"),
        Sticker("2", "😂"),
        Sticker("3", "❤️"),
        Sticker("4", "👍"),
        Sticker("5", "🔥"),
        Sticker("6", "💯"),
        Sticker("7", "🎉"),
        Sticker("8", "🚀")
    )
}
