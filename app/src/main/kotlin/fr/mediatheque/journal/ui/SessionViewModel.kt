package fr.mediatheque.journal.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.SessionCookieJar
import fr.mediatheque.journal.api.dto.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed interface SessionState {
    data object Checking : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
    data class Unreachable(val message: String) : SessionState
}

/**
 * Qui je suis. Au lancement : pas de cookie, donc pas de session, sans
 * déranger le back ; un cookie, donc `GET /auth/me`, qui tranche. Un `401`
 * n'importe où plus tard ramène ici par `expire()`.
 */
class SessionViewModel(private val api: JournalApi, private val cookieJar: SessionCookieJar) : ViewModel() {
    private val _state = MutableStateFlow<SessionState>(SessionState.Checking)
    val state: StateFlow<SessionState> = _state

    init { check() }

    fun retry() = check()

    fun signedIn(user: User) {
        _state.value = SessionState.SignedIn(user)
    }

    /** Un `401` reçu ailleurs : la session n'existe plus, le cookie non plus. */
    fun expire() {
        cookieJar.clear()
        _state.value = SessionState.SignedOut
    }

    fun signOut() {
        viewModelScope.launch {
            runCatching { api.logout() } // le cookie part de toute façon
            expire()
        }
    }

    private fun check() {
        if (!cookieJar.hasSession) {
            _state.value = SessionState.SignedOut
            return
        }
        _state.value = SessionState.Checking
        viewModelScope.launch {
            try {
                _state.value = SessionState.SignedIn(api.me())
            } catch (e: ApiError) {
                if (e.isUnauthenticated) expire() else _state.value = SessionState.Unreachable(e.message ?: ApiError.NETWORK_MESSAGE)
            }
        }
    }
}
