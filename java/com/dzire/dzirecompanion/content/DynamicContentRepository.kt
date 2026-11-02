package com.dzire.dzirecompanion.content

import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Remote editorial content. Firestore snapshot listeners make changes visible
 * while the app is open, without requiring an APK update.
 */
class DynamicContentRepository(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun observeQuotes(tags: Set<String>, premium: Boolean): Flow<DynamicContent?> =
        observeBest("content_quotes", tags, premium)

    fun observeAdvice(tags: Set<String>, premium: Boolean): Flow<DynamicContent?> =
        observeBest("content_advice", tags, premium, preferHighestPriority = true)

    suspend fun getQuotes(tags: Set<String> = emptySet(), premium: Boolean? = null): List<DynamicContent> =
        getCollection("content_quotes", tags, premium)

    suspend fun getAdvice(tags: Set<String> = emptySet(), premium: Boolean? = null): List<DynamicContent> =
        getCollection("content_advice", tags, premium)

    private fun observeBest(
        collection: String,
        tags: Set<String>,
        premium: Boolean,
        preferHighestPriority: Boolean = false
    ): Flow<DynamicContent?> = callbackFlow {
        val registration = firestore.collection(collection)
            .whereEqualTo("active", true)
            .addSnapshotListener { snapshot, error ->
                if (error != null || snapshot == null) {
                    trySend(null)
                    return@addSnapshotListener
                }
                val now = System.currentTimeMillis()
                val candidates = snapshot.documents.mapNotNull { doc ->
                    doc.toObject(DynamicContent::class.java)?.copy(id = doc.id)
                }.filter { it.isCurrentlyActive(now) }
                    .filter { it.premium == premium }
                    .filter { tags.isEmpty() || it.tags.any { tag -> tags.contains(tag.uppercase()) } }
                    .sortedByDescending { it.priority }

                val selected = if (preferHighestPriority) candidates.firstOrNull() else candidates.randomOrNull()
                    ?: run {
                        val fallback = snapshot.documents.mapNotNull { doc ->
                            doc.toObject(DynamicContent::class.java)?.copy(id = doc.id)
                        }.filter { it.isCurrentlyActive(now) && it.premium == premium }
                            .sortedByDescending { it.priority }
                        if (preferHighestPriority) fallback.firstOrNull() else fallback.randomOrNull()
                    }
                trySend(selected)
            }
        awaitClose { registration.remove() }
    }

    private suspend fun getCollection(
        collection: String,
        tags: Set<String>,
        premium: Boolean?
    ): List<DynamicContent> {
        val snapshot = firestore.collection(collection).whereEqualTo("active", true).get().await()
        return snapshot.documents.mapNotNull { doc ->
            doc.toObject(DynamicContent::class.java)?.copy(id = doc.id)
        }.filter { it.isCurrentlyActive() }
            .filter { premium == null || it.premium == premium }
            .filter { tags.isEmpty() || it.tags.any { tag -> tags.contains(tag.uppercase()) } }
            .sortedByDescending { it.priority }
    }
}
