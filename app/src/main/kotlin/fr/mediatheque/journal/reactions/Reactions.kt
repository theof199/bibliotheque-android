package fr.mediatheque.journal.reactions

data class Reaction(val key: String, val emoji: String, val phrase: String)

/**
 * Le catalogue. Le back n'en connaît que les clés (`^[a-z0-9_]{1,32}$`, douze
 * cochées au plus) ; emoji et phrase sont de la présentation, et se changent
 * sans déploiement du back. Une clé enregistrée puis retirée d'ici s'affiche
 * comme sa clé nue, jamais comme une erreur.
 */
object Reactions {
    val all: List<Reaction> = listOf(
        Reaction("adore", "❤️", "J’ai adoré"),
        Reaction("sympa", "👍", "Sympa"),
        Reaction("nul", "👎", "Nul"),
        Reaction("marre", "😂", "Je me suis marré"),
        Reaction("touche", "🥲", "Ça m’a touché"),
        Reaction("flippe", "😱", "Ça fait flipper"),
        Reaction("long", "🥱", "C’était long"),
        Reaction("visuel", "🤩", "Wahou, visuellement"),
        Reaction("stressant", "😬", "Stressant"),
        Reaction("reflechir", "🤔", "Ça fait réfléchir"),
        Reaction("confus", "🌀", "Trop confus"),
        Reaction("a_revoir", "🔁", "À revoir"),
    )

    /** Ce que le back accepte au plus par visionnage. */
    const val MAX_SELECTED = 12

    private val byKey = all.associateBy { it.key }

    fun label(key: String): String = byKey[key]?.let { "${it.emoji} ${it.phrase}" } ?: key
    fun emoji(key: String): String = byKey[key]?.emoji ?: key

    /**
     * La phrase seule, sans l'emoji — le `contentDescription` d'une réaction
     * (design §8 : « une réaction porte sa phrase, jamais son emoji seul »).
     */
    fun phrase(key: String): String = byKey[key]?.phrase ?: key

    /**
     * Les clés sélectionnées, dans l'ordre du catalogue puis les clés
     * orphelines (retirées du catalogue mais encore portées par un
     * visionnage). Un seul endroit pour cet ordre : `FormViewModel.reactions()`
     * et `patchBodyOf` s'en servent tous les deux, pour ne pas en tenir deux
     * qui divergeraient.
     */
    fun ordered(selected: Set<String>): List<String> {
        val known = all.map { it.key }
        return known.filter { it in selected } + (selected - known.toSet())
    }
}
