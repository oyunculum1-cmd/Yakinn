package com.yakin.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.items

@Composable
fun StickerPanel(stickers: StickerPack, onSelected: (String) -> Unit) {
    var showPicker = remember { mutableStateOf(false) }
    
    IconButton({ showPicker.value = !showPicker.value }) {
        Icon(Icons.Default.EmojiEmotions, null, tint = Color(0xFF14B8A6))
    }
    
    if (showPicker.value) {
        AlertDialog(
            onDismissRequest = { showPicker.value = false },
            title = { Text("Sticker Seç") },
            text = {
                LazyColumn {
                    items(stickers.stickers) { s ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onSelected(s.emoji)
                                    showPicker.value = false
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(s.emoji, fontSize = 32.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton({ showPicker.value = false }) { Text("Kapat") } }
        )
    }
}
