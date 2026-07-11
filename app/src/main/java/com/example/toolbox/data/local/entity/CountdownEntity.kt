package com.example.toolbox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "countdowns")
data class CountdownEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val targetDate: Long,      // epoch millis, normalized to 00:00 of the target day
    val type: String = "countdown",  // "countdown" | "anniversary" | "birthday"
    val colorTag: String = "#4F7CFF",
    val isPinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val isLunar: Boolean = false,   // true if the date is in lunar calendar
    val lunarMonth: Int = 0,         // lunar month (1-12), 0 if not lunar
    val lunarDay: Int = 0,           // lunar day (1-30), 0 if not lunar
)
