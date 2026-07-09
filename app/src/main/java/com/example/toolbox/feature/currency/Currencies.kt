package com.example.toolbox.feature.currency

data class Currency(val code: String, val name: String)

val COMMON_CURRENCIES = listOf(
    Currency("USD", "美元"),
    Currency("CNY", "人民币"),
    Currency("EUR", "欧元"),
    Currency("JPY", "日元"),
    Currency("GBP", "英镑"),
    Currency("HKD", "港币"),
    Currency("KRW", "韩元"),
    Currency("AUD", "澳元"),
    Currency("CAD", "加元"),
    Currency("SGD", "新加坡元"),
    Currency("CHF", "瑞士法郎"),
    Currency("THB", "泰铢"),
)
