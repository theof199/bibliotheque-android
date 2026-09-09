package fr.mediatheque.journal.ui.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.User
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class LoginUi(
    val busy: Boolean = false,
    val error: String? = null,
    val pseudoInvalid: Boolean = false,
    val passwordInvalid: Boolean = false,
    /** Horodatage (ms) jusqu'auquel le bouton reste désactivé après un `429`. */
    val blockedUntilMillis: Long? = null,
    /** Vient de `ApiError.retryable` (décision 1) : un 401 ne l'est pas, une panne réseau l'est. */
    val retryable: Boolean = false,
)

class LoginViewModel(private val api: JournalApi, private val onSignedIn: (User) -> Unit) : ViewModel() {
    var pseudo by mutableStateOf("")
    var password by mutableStateOf("")

    private val _ui = MutableStateFlow(LoginUi())
    val ui: StateFlow<LoginUi> = _ui

    fun submit() {
        val pseudoInvalid = pseudo.isBlank()
        val passwordInvalid = password.isEmpty()
        if (pseudoInvalid || passwordInvalid) {
            _ui.value = _ui.value.copy(pseudoInvalid = pseudoInvalid, passwordInvalid = passwordInvalid)
            return
        }
        _ui.value = LoginUi(busy = true)
        viewModelScope.launch {
            try {
                val user = api.login(pseudo.trim(), password)
                _ui.value = LoginUi()
                onSignedIn(user)
            } catch (e: ApiError) {
                _ui.value = LoginUi(
                    error = e.message,
                    retryable = e.retryable,
                    blockedUntilMillis = e.retryAfterSeconds?.let { System.currentTimeMillis() + it * 1_000L },
                )
            }
        }
    }
}
