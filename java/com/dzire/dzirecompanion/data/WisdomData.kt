package com.dzire.dzirecompanion.data

/**
 * Legacy local model retained for compatibility. Remote content is now served
 * by Firebase/Firestore through DynamicContentRepository.
 */
data class WisdomQuote(
    val text: String,
    val author: String = "Unknown",
    val parameter: String = "GENERAL"
)
