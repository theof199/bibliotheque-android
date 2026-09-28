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
 *
 * Livraison 3 (28 septembre 2026) : l'entrée se branche enfin sur le magasin (`SectionMonde.kt`,
 * `etatEntreeCarton` de `VoyageCarte.kt`) — mais la livraison 1 marquait déjà chaque monde dès sa
 * seule visibilité, sans jamais jouer d'entrée. En gardant la clé `decennies`, tous les mondes
 * déjà visités se liraient `Jouee` dès le premier lancement après cette mise à jour, et le
 * propriétaire ne verrait alors aucune des entrées qui viennent d'être écrites. D'où la clé
 * versionnée `decennies_v2` : un magasin neuf, une fois — les anciennes lignes restent orphelines
 * dans les préférences, jamais relues, sans migration (comme `RecentSearchesStore`, qui n'en fait
 * pas non plus entre ses versions).
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
        // Livraison 3, 28 septembre 2026 : "decennies" -> "decennies_v2" — voir la note plus haut.
        const val KEY = "decennies_v2"
    }
}
