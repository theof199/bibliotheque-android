package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * La marquise d'un monde (lot 1 du brief du 28 septembre 2026, « le voyage se sent progresser » :
 * « chaque marquise se dit décennie en cours ») — `libelleMarquise` (son accessibilité) et
 * `sousTitreMarquise` (son texte visible), fonctions pures, sans réseau ni `ViewModel`.
 */
class MarquiseTest {

    @Test
    fun `libelleMarquise dit le titre de voyageur d'une decennie bouclee`() {
        // Mutation : tester `enCours` avant `bouclee` ferait dire « décennie en cours » à une
        // décennie bouclée qui se trouve être celle de l'année en cours (cas impossible en
        // pratique, mais l'ordre des branches doit rester celui-là).
        assertEquals("Les origines", libelleMarquise(bouclee = true, enCours = true, titreVoyageur = "Les origines"))
        assertEquals("Les origines", libelleMarquise(bouclee = true, enCours = false, titreVoyageur = "Les origines"))
    }

    @Test
    fun `libelleMarquise dit decennie en cours seulement pour la vraie decennie en cours`() {
        // Mutation : rendre "décennie en cours" par défaut (sans regarder `enCours`) — le bug du
        // 28 septembre 2026 — ferait échouer la deuxième assertion.
        assertEquals("décennie en cours", libelleMarquise(bouclee = false, enCours = true, titreVoyageur = "Les origines"))
        assertEquals("à venir", libelleMarquise(bouclee = false, enCours = false, titreVoyageur = "Les origines"))
    }

    @Test
    fun `sousTitreMarquise prefere le slogan de la decennie, meme hors decennie en cours`() {
        // Mutation : faire primer `enCours` sur un slogan non nul ferait échouer les deux
        // assertions — le slogan est une tagline, pas une prétention à être « en cours ».
        assertEquals("Méliès et les forains", sousTitreMarquise(bouclee = false, enCours = false, titreVoyageur = "La Féerie", slogan = "Méliès et les forains"))
        assertEquals("Méliès et les forains", sousTitreMarquise(bouclee = false, enCours = true, titreVoyageur = "La Féerie", slogan = "Méliès et les forains"))
    }

    @Test
    fun `sousTitreMarquise replie sur en cours de tournage ou a tourner sans slogan`() {
        // Mutation : rendre "en cours de tournage" par défaut (sans regarder `enCours`) laisserait
        // 1890 se dire « en cours de tournage » même après que l'année en cours l'a quittée sans
        // la boucler — le bug que ce test interdit.
        assertEquals("en cours de tournage", sousTitreMarquise(bouclee = false, enCours = true, titreVoyageur = "Les origines", slogan = null))
        assertEquals("à tourner", sousTitreMarquise(bouclee = false, enCours = false, titreVoyageur = "Les origines", slogan = null))
    }

    @Test
    fun `sousTitreMarquise dit le titre de voyageur d'une decennie bouclee, meme sans slogan`() {
        assertEquals("Les origines", sousTitreMarquise(bouclee = true, enCours = false, titreVoyageur = "Les origines", slogan = null))
    }
}
