package fr.mediatheque.journal.search

import android.content.Context

/**
 * Les dix dernières recherches (point 6 de la revue du 24 septembre 2026, « avant la saisie ») :
 * conservées localement, jamais envoyées au back — un texte de recherche n'a rien à faire dans le
 * journal de quelqu'un d'autre.
 */
interface RecentSearchesStore {
    fun read(): List<String>
    fun write(value: List<String>)
}

/** Le magasin des tests — jumeau d'`InMemorySessionStore`. */
class InMemoryRecentSearchesStore : RecentSearchesStore {
    private var value: List<String> = emptyList()
    override fun read(): List<String> = value
    override fun write(value: List<String>) { this.value = value }
}

/** Une requête par ligne dans une seule préférence : pas de recherche vide, jamais de doublon. */
class PreferencesRecentSearchesStore(context: Context) : RecentSearchesStore {
    private val prefs = context.getSharedPreferences("recherches_recentes", Context.MODE_PRIVATE)

    override fun read(): List<String> =
        prefs.getString(KEY, null)?.split(SEPARATEUR)?.filter { it.isNotEmpty() } ?: emptyList()

    override fun write(value: List<String>) {
        prefs.edit().putString(KEY, value.joinToString(SEPARATEUR)).apply()
    }

    private companion object {
        const val KEY = "recherches"
        // Un caractère qu'aucune recherche ne tape au clavier — plus sûr qu'une virgule ou un
        // saut de ligne, qu'un titre de film pourrait légitimement porter.
        const val SEPARATEUR = "\u0000"
    }
}

/**
 * [requete] en tête, sans doublon (insensible à la casse — « chihiro » et « Chihiro » sont la même
 * recherche), plafonné à [max] — fonction pure, testée en JVM (`RecentSearchesTest.kt`). Une
 * requête vide ou blanche ne s'ajoute pas.
 */
fun ajouterRechercheRecente(existantes: List<String>, requete: String, max: Int = 10): List<String> {
    val nettoyee = requete.trim()
    if (nettoyee.isEmpty()) return existantes
    val sansDoublon = existantes.filterNot { it.equals(nettoyee, ignoreCase = true) }
    return (listOf(nettoyee) + sansDoublon).take(max)
}
