package com.example.toolbox.core.theme

enum class ThemeMode(val label: String) {
    SYSTEM("跟随系统"),
    LIGHT("浅色"),
    DARK("深色");

    companion object {
        fun fromOrdinal(o: Int) = entries.getOrElse(o) { SYSTEM }
    }
}
