package fr.mediatheque.journal.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * `opacitePerforation` : quelle perforation est déjà allumée en or, le long de la bande — la
 * célébration d'une salle bouclée (geste 10) en allume une à une. Fonction pure, sans Compose.
 */
class OrnementsTest {

    // Sous le compte d'allumées, une perforation reste au grain de base (0,5). Mutation : renvoyer
    // 1f par défaut ferait croire toute la bande allumée avant la première célébration.
    @Test
    fun `une perforation au-dela du compte allumees reste au grain de base`() {
        assertEquals(0.5f, opacitePerforation(index = 3, allumees = 0), 0f)
        assertEquals(0.5f, opacitePerforation(index = 3, allumees = 2), 0f)
    }

    // À la frontière, l'index égal au compte n'est pas encore allumé : `allumees` compte des
    // perforations, pas un dernier index inclus. Mutation : `index <= allumees` allumerait une de
    // trop à chaque étape.
    @Test
    fun `l'index egal au compte allumees n'est pas encore allume`() {
        assertEquals(0.5f, opacitePerforation(index = 2, allumees = 2), 0f)
        assertEquals(1f, opacitePerforation(index = 1, allumees = 2), 0f)
        assertEquals(1f, opacitePerforation(index = 0, allumees = 2), 0f)
    }

    // Au repos, la bobine ne se dessine pas du tout : pas de place, pas de capteur de taps.
    // Mutation : renvoyer `visible = true` au repos fait rougir cette assertion (le defaut corrige
    // par ce correctif, l'indicateur restait affiche en permanence).
    @Test
    fun `au repos la bobine n'est pas visible`() {
        val presentation = presentationBobine(distanceFraction = 0f, isRefreshing = false)
        assertEquals(false, presentation.visible)
        assertEquals(0f, presentation.decalage, 0f)
        assertEquals(0f, presentation.opacite, 0f)
    }

    // Pendant le tirage, le decalage et l'opacite suivent distanceFraction telle quelle.
    // Mutation : plafonner sans coercition ou renvoyer une constante ferait rougir cette assertion
    // a mi-tirage.
    @Test
    fun `pendant le tirage le decalage et l'opacite suivent la distance`() {
        val presentation = presentationBobine(distanceFraction = 0.4f, isRefreshing = false)
        assertEquals(true, presentation.visible)
        assertEquals(0.4f, presentation.decalage, 0f)
        assertEquals(0.4f, presentation.opacite, 0f)
    }

    // Une distance au-dela de 1 (le tirage peut depasser le seuil) reste plafonnee a 1.
    // Mutation : retirer le coerceIn ferait rougir cette assertion des qu'on tire au-dela du seuil.
    @Test
    fun `une distance au-dela de 1 reste plafonnee`() {
        val presentation = presentationBobine(distanceFraction = 1.6f, isRefreshing = false)
        assertEquals(1f, presentation.decalage, 0f)
        assertEquals(1f, presentation.opacite, 0f)
    }

    // Pendant le chargement, elle reste a son decalage d'arrivee et tourne, meme si
    // distanceFraction est deja retombee a 0 (isRefreshing l'emporte).
    // Mutation : lire distanceFraction en priorite ferait rougir cette assertion.
    @Test
    fun `pendant le chargement elle reste a l'arrivee, meme distance retombee`() {
        val presentation = presentationBobine(distanceFraction = 0f, isRefreshing = true)
        assertEquals(true, presentation.visible)
        assertEquals(1f, presentation.decalage, 0f)
        assertEquals(1f, presentation.opacite, 0f)
    }
}
