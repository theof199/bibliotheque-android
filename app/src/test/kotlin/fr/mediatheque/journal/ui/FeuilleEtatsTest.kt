package fr.mediatheque.journal.ui

import org.junit.Assert.assertEquals
import org.junit.Test

// « La feuille de lecture · le chroniqueur », planche de Léon du 25 septembre 2026.
class FeuilleEtatsTest {
    @Test fun `une ligne vide separe deux paragraphes`() =
        assertEquals(listOf("Premier.", "Second."), paragraphes("Premier.\n\nSecond."))

    // Mutation : couper sur une seule ligne vide exactement laisserait un paragraphe vide au milieu.
    @Test fun `plusieurs lignes vides ne font qu une separation`() =
        assertEquals(listOf("Premier.", "Second."), paragraphes("Premier.\n\n\n  \n\nSecond."))

    @Test fun `les espaces de bord et les lignes vides des extremites disparaissent`() =
        assertEquals(listOf("Premier.", "Second."), paragraphes("\n\n  Premier.   \n \nSecond.  \n\n"))

    @Test fun `un texte sans ligne vide reste un seul paragraphe, retours a la ligne compris`() =
        assertEquals(listOf("Une ligne\npuis une autre."), paragraphes("Une ligne\npuis une autre."))

    @Test fun `un texte vide ne donne aucun paragraphe`() = assertEquals(emptyList<String>(), paragraphes("   \n\n "))

    @Test fun `un texte plus court que la limite se tape en entier`() = assertEquals(5, partieTapee("court", limite = 10))

    // Mutation : s'arrêter pile à la limite couperait « chroniqueur » en « chron » + « iqueur ».
    @Test fun `une limite au milieu d un mot va jusqu a la fin du mot`() =
        assertEquals(14, partieTapee("le chroniqueur écrit", limite = 5))

    @Test fun `une limite tombee sur une espace s arrete la`() = assertEquals(14, partieTapee("le chroniqueur écrit", limite = 14))

    @Test fun `une limite dans le dernier mot va jusqu au bout du texte`() = assertEquals(20, partieTapee("le chroniqueur écrit", limite = 17))

    @Test fun `tapes donne le debut du texte`() = assertEquals("Le ch", tapes("Le chroniqueur", 5))

    @Test fun `tapes ne deborde ni au dela du texte ni en dessous de zero`() {
        assertEquals("Le", tapes("Le", 40))
        assertEquals("", tapes("Le", -1))
    }
}
