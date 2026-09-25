package fr.mediatheque.journal.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `ressourceTabler` : le nom Tabler (même liste que `icones/tabler.txt`) vers son
 * `R.drawable.tabler_*`. Fonction pure, sans Compose.
 */
class IconeTablerTest {

    // Les douze icônes qui remplacent Icons.* (Material), les trois qui remplacent les
    // Text("✦") (geste 2), les trois de « Mes films · le hall » (24 septembre 2026), puis les
    // quatre enseignes de la barre du bas (25 septembre 2026) : chacune doit avoir sa ressource.
    // Mutation : retirer une entrée de `RESSOURCES_TABLER` fait rougir exactement la ligne qui la
    // nomme ici, aucune autre.
    @Test
    fun `chaque icone listee a une ressource`() {
        val noms = listOf(
            "arrow-left", "search", "check", "x", "plus", "chevron-right", "timeline", "map", "user", "movie",
            "home", "trash", "ticket", "cloud", "star-filled", "sparkles", "award",
            "arrows-sort", "pencil", "chevron-down",
            "building-pavilion", "route", "chair-director", "armchair",
        )
        noms.forEach { nom ->
            assertEquals(
                "icone sans ressource : $nom",
                true,
                ressourceTabler(nom) != null,
            )
        }
    }

    // Un nom qui ne vient pas de icones/tabler.txt ne doit pas rendre une icône au hasard.
    @Test
    fun `un nom inconnu n'a pas de ressource`() {
        assertNull(ressourceTabler("clapperboard"))
        assertNull(ressourceTabler(""))
    }
}
