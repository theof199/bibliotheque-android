package fr.mediatheque.journal.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.senscritique.SensCritiqueAuth
import fr.mediatheque.journal.senscritique.SensCritiqueAuthClient
import fr.mediatheque.journal.senscritique.SensCritiqueStore
import fr.mediatheque.journal.senscritique.SignInOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SensCritiqueUi(
    val connectedPseudo: String? = null,
    val busy: Boolean = false,
    val error: String? = null,
    /** « SensCritique est injoignable. » se rejoue par « Réessayer » ; « Identifiants refusés. » non. */
    val retryable: Boolean = false,
)

/**
 * La connexion SensCritique du profil (brief du 14 septembre 2026) — jumeau de `LoginViewModel`.
 * Le mot de passe ne survit jamais au-delà de `connect()` : seul `refreshToken` (et le pseudo)
 * partent dans `SensCritiqueStore`, chiffrés.
 */
class SensCritiqueViewModel(
    private val store: SensCritiqueStore,
    private val authClient: SensCritiqueAuthClient,
) : ViewModel() {
    var email by mutableStateOf("")
    var password by mutableStateOf("")

    private val _ui = MutableStateFlow(SensCritiqueUi(connectedPseudo = store.readAuth()?.pseudo))
    val ui: StateFlow<SensCritiqueUi> = _ui

    /** Rafraîchit l'état affiché depuis le magasin — utile si l'auto-rejeu de la file vient de déconnecter. */
    fun refresh() {
        _ui.update { it.copy(connectedPseudo = store.readAuth()?.pseudo) }
    }

    fun connect() {
        if (_ui.value.busy) return
        _ui.value = SensCritiqueUi(busy = true)
        viewModelScope.launch {
            when (val outcome = authClient.signIn(email.trim(), password)) {
                is SignInOutcome.Success -> {
                    store.writeAuth(SensCritiqueAuth(outcome.refreshToken, outcome.pseudo))
                    email = ""
                    password = ""
                    _ui.value = SensCritiqueUi(connectedPseudo = outcome.pseudo)
                }
                SignInOutcome.InvalidCredentials -> _ui.value = SensCritiqueUi(error = "Identifiants refusés.")
                SignInOutcome.Unreachable -> _ui.value = SensCritiqueUi(error = "SensCritique est injoignable.", retryable = true)
            }
        }
    }

    /** Efface tout, file comprise (brief §1). */
    fun disconnect() {
        store.clear()
        _ui.value = SensCritiqueUi()
    }
}
