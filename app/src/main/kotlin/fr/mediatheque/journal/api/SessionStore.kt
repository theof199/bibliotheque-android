package fr.mediatheque.journal.api

import android.content.Context

/** Où le cookie de session dort entre deux lancements. Synchrone : OkHttp l'appelle depuis ses threads. */
interface SessionStore {
    fun read(): String?
    fun write(value: String?)
}

class InMemorySessionStore : SessionStore {
    private var value: String? = null
    override fun read(): String? = value
    override fun write(value: String?) { this.value = value }
}

class PreferencesSessionStore(context: Context) : SessionStore {
    private val prefs = context.getSharedPreferences("session", Context.MODE_PRIVATE)
    override fun read(): String? = prefs.getString(KEY, null)
    override fun write(value: String?) {
        prefs.edit().apply { if (value == null) remove(KEY) else putString(KEY, value) }.apply()
    }

    private companion object { const val KEY = "cookie" }
}
