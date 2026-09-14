package fr.mediatheque.journal.senscritique

import android.util.Log

const val TAG_SENSCRITIQUE = "SensCritique"

/**
 * Le seul point d'entrée vers `Log.d` pour tout le paquet `senscritique` — remplaçable par un faux
 * en test (revue du 14 septembre 2026, critique 1) : c'est ce qui permet d'affirmer, par une
 * assertion sur la chaîne construite, qu'aucun jeton ni corps de réponse d'authentification ne
 * l'atteint jamais, plutôt que de l'espérer en relisant le code.
 */
fun interface SensCritiqueLogger {
    fun d(message: String)
}

/** L'implémentation réelle, `android.util.Log` — celle qu'`AppContainer` câble. */
val AndroidSensCritiqueLogger = SensCritiqueLogger { message -> Log.d(TAG_SENSCRITIQUE, message) }
