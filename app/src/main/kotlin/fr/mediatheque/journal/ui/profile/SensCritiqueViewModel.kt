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
                is SignInOutcome.Refused -> {
                    val (message, retryable) = messageForRefus(outcome.code)
                    _ui.value = SensCritiqueUi(error = message, retryable = retryable)
                }
                SignInOutcome.Unreachable -> _ui.value = SensCritiqueUi(error = "SensCritique est injoignable.", retryable = true)
            }
        }
    }

    /** Efface tout, file comprise (brief §1). */
    fun disconnect() {
        store.clear()
        _ui.value = SensCritiqueUi()
    }

    /**
     * Efface les champs saisis (mineur a de la revue du 14 septembre 2026) — jumeau de
     * `LoginViewModel.reset()`, mais appelé par `Root` à la *sortie* de l'écran plutôt qu'à
     * l'entrée : `connectedPseudo`, lui, reste utile hors visite (la ligne « SensCritique » du
     * profil le lit). Un email ou un mot de passe qui survivrait à la sortie de l'écran
     * réapparaîtrait à la prochaine visite, ce `ViewModel` étant indexé sur l'Activité.
     */
    fun clearCredentials() {
        email = ""
        password = ""
        _ui.update { it.copy(busy = false, error = null, retryable = false) }
    }
}

/**
 * Le message affiché pour un `SignInOutcome.Refused(code)`, et si « Réessayer » a un sens (revue du
 * 14 septembre 2026, point 2). Le premier essai réel a montré `EMAIL_NOT_FOUND` (Firebase renvoie
 * les codes classiques sur ce projet) : les cinq codes connus ont chacun leur phrase, tout autre
 * code lisible se lit tel quel, et un `code` nul (corps de refus illisible) se replie sur le
 * message générique d'injoignabilité — même phrase qu'un 5xx ou une panne réseau
 * (`SignInOutcome.Unreachable`), la distinction ne changerait rien à l'écran.
 */
internal fun messageForRefus(code: String?): Pair<String, Boolean> = when (code) {
    "EMAIL_NOT_FOUND" -> "Aucun compte SensCritique avec cet e-mail." to false
    "INVALID_PASSWORD", "INVALID_LOGIN_CREDENTIALS" -> "Identifiants refusés." to false
    "USER_DISABLED" -> "Ce compte SensCritique est désactivé." to false
    "TOO_MANY_ATTEMPTS_TRY_LATER" -> "Trop d’essais, réessaie plus tard." to true
    null -> "SensCritique est injoignable." to true
    else -> "SensCritique a refusé la connexion ($code)." to false
}
