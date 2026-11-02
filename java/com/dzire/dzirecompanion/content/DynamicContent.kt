package com.dzire.dzirecompanion.content

data class DynamicContent(
    val id: String = "",
    val type: String = "QUOTE", // QUOTE or ADVICE
    val title: String = "",
    val text: String = "",
    val author: String = "",
    val tags: List<String> = emptyList(),
    val priority: Long = 0,
    val premium: Boolean = false,
    val active: Boolean = true,
    val activeFrom: Long? = null,
    val activeUntil: Long? = null
)

fun DynamicContent.isCurrentlyActive(now: Long = System.currentTimeMillis()): Boolean =
    active && (activeFrom == null || now >= activeFrom) && (activeUntil == null || now <= activeUntil)
