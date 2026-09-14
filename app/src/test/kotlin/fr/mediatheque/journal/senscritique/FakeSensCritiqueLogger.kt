package fr.mediatheque.journal.senscritique

/** Enregistre tout ce qui a été journalisé, pour prouver par assertion qu'aucun secret n'y passe. */
class FakeSensCritiqueLogger : SensCritiqueLogger {
    val lines = mutableListOf<String>()
    override fun d(message: String) {
        lines += message
    }
}
