package com.example.secondwork

import android.graphics.drawable.Drawable

data class ProcessInfoExtended(
    val pid: String,
    val name: String,
    val cpu: Double,       // <-- здесь Double
    val mem: Double,       // <-- здесь Double
    val storage: Double,   // <-- здесь Double
    val rom: String,
    val user: String,
    val packageName: String? = null,
    val icon: Drawable? = null
)


