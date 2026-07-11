package com.example.toolbox.feature.settings

import com.example.toolbox.data.local.entity.CountdownEntity
import com.example.toolbox.data.repository.PasswordExport

data class BackupPayload(
    val passwords: List<PasswordExport>,
    val countdowns: List<CountdownExport>,
)

data class CountdownExport(
    val title: String,
    val targetDate: Long,
    val colorTag: String,
    val isPinned: Boolean,
    val type: String = "countdown",
    val isLunar: Boolean = false,
    val lunarMonth: Int = 0,
    val lunarDay: Int = 0,
) {
    fun toEntity(): CountdownEntity = CountdownEntity(
        title = title,
        targetDate = targetDate,
        colorTag = colorTag,
        isPinned = isPinned,
        type = type,
        isLunar = isLunar,
        lunarMonth = lunarMonth,
        lunarDay = lunarDay,
    )

    companion object {
        fun fromEntity(e: CountdownEntity) = CountdownExport(
            title = e.title,
            targetDate = e.targetDate,
            colorTag = e.colorTag,
            isPinned = e.isPinned,
            type = e.type,
            isLunar = e.isLunar,
            lunarMonth = e.lunarMonth,
            lunarDay = e.lunarDay,
        )
    }
}
