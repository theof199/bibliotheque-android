package fr.mediatheque.journal.frise

import android.content.Context

/**
 * Les mondes du Voyage déjà présentés (delta de Léon du 25 septembre 2026, « pavillon par
 * pavillon », §D) : l'entrée d'un carton-titre ne se joue qu'une fois par monde, à la première
 * visite — persistée, pas seulement `rememberSaveable` (qui ne survivait qu'à une rotation
 * d'écran, jamais à un nouveau lancement de l'appli). Jumeau de `RecentSearchesStore`
 * (`search/RecentSearches.kt`) : locale, jamais envoyée au back, une décennie par ligne.
 *
 * Livraison 1 : le magasin est écrit dès qu'un monde entre dans le viewport (`VoyageScreen.kt`),
 * mais rien ne s'y déclenche encore — le carton reste rendu dans son état final (`EtatEntree
 * .Jouee`) quoi que le magasin en dise ; c'est la livraison 3 qui branchera l'animation d'entrée
 * dessus.
 */
interface MondesVisitesStore {
    fun lire(): Set<Int>
    fun marquer(decennie: Int)
}

/** Le magasin des tests — jumeau d'`InMemoryRecentSearchesStore`. */
class InMemoryMondesVisitesStore : MondesVisitesStore {
    private var value: Set<Int> = emptySet()
    override fun lire(): Set<Int> = value
    override fun marquer(decennie: Int) {
        value = value + decennie
    }
}

/** Une décennie par ligne dans une seule préférence, comme `PreferencesRecentSearchesStore`. */
class PreferencesMondesVisitesStore(context: Context) : MondesVisitesStore {
    private val prefs = context.getSharedPreferences("mondes_visites", Context.MODE_PRIVATE)

    override fun lire(): Set<Int> =
        prefs.getStringSet(KEY, null)?.mapNotNull { it.toIntOrNull() }?.toSet() ?: emptySet()

    override fun marquer(decennie: Int) {
        prefs.edit().putStringSet(KEY, (lire() + decennie).map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val KEY = "decennies"
    }
}
