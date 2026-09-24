package fr.mediatheque.journal.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    // Revue du 24 septembre 2026, point 6, « dernières recherches ». Mutation : ne pas retirer le
    // doublon (une simple concaténation en tête) fait tomber la première assertion ; comparer avec
    // la casse plutôt que sans casse-la fait tomber la deuxième.
    @Test fun `une recherche deja presente remonte en tete, sans doublon`() {
        assertEquals(
            listOf("chihiro", "metropolis"),
            ajouterRechercheRecente(listOf("metropolis", "chihiro"), "chihiro"),
        )
        assertEquals(
            listOf("Chihiro", "metropolis"),
            ajouterRechercheRecente(listOf("metropolis", "chihiro"), "Chihiro"),
        )
    }

    @Test fun `une recherche vide ou blanche ne s ajoute pas`() {
        assertEquals(listOf("metropolis"), ajouterRechercheRecente(listOf("metropolis"), ""))
        assertEquals(listOf("metropolis"), ajouterRechercheRecente(listOf("metropolis"), "   "))
    }

    // Mutation : retirer le `.take(max)` fait grandir la liste sans fin.
    @Test fun `plafonnee a dix par defaut`() {
        val neuf = (1..9).map { "recherche $it" }
        assertEquals(10, ajouterRechercheRecente(neuf, "la dixieme").size)
        assertEquals(10, ajouterRechercheRecente(neuf + "la dixieme", "la onzieme").size)
    }

    @Test fun `le plafond se regle`() = assertEquals(2, ajouterRechercheRecente(listOf("a", "b"), "c", max = 2).size)
}
