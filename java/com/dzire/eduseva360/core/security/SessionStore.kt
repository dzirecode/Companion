package com.dzire.eduseva360.core.security

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.dzire.eduseva360.core.model.Session
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sessionDataStore by preferencesDataStore(name = "eduseva_session")

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val sessionKey = stringPreferencesKey("session")

    val session: Flow<Session?> = context.sessionDataStore.data.map { prefs ->
        prefs[sessionKey]?.let { runCatching { json.decodeFromString<Session>(it) }.getOrNull() }
    }

    suspend fun save(session: Session) {
        context.sessionDataStore.edit { it[sessionKey] = json.encodeToString(session) }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { it.remove(sessionKey) }
    }
}
