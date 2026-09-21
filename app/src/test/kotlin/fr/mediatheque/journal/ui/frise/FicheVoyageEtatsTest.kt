package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La fiche d'un film du Voyage (brief du 21 septembre 2026, « l'année en étages ») : l'état d'un
 * film et d'un programme, ses boutons, le lien Plex, l'étiquette de l'étagère. Fonctions pures,
 * sans réseau ni `ViewModel`.
 */
class FicheVoyageEtatsTest {

    private fun bobine(tmdbId: Int, etat: String) = BobineUi(tmdbId, "Bobine $tmdbId", 9, null, null, etat)

    // Un film sans bobine garde son propre état. Mutation : rendre toujours « vu » sur une liste
    // vide ferait passer un film à demander pour vu.
    @Test
    fun `etatFilmVoyage sans bobine garde l'etat propre du film`() {
        assertEquals("a_demander", etatFilmVoyage("a_demander", emptyList()))
        assertEquals("vu", etatFilmVoyage("vu", emptyList()))
    }

    // Un programme n'est vu que quand **toutes** ses bobines le sont (spec du 19 septembre 2026,
    // §3). Mutation : `any` au lieu de `all` rendrait « vu » dès la première bobine vue.
    @Test
    fun `etatFilmVoyage sur un programme n'est vu que si toutes les bobines le sont`() {
        val toutesVues = listOf(bobine(1, "vu"), bobine(2, "vu"))
        val uneRestante = listOf(bobine(1, "vu"), bobine(2, "sur_le_plex"))

        assertEquals("vu", etatFilmVoyage("sur_le_plex", toutesVues))
        // L'état propre du programme (pas « vu ») ressort tant qu'une bobine manque, quel qu'il soit.
        assertEquals("sur_le_plex", etatFilmVoyage("sur_le_plex", uneRestante))
    }

    // Les cinq boutons, un par cas qui les distingue.
    @Test
    fun `boutonsFicheVoyage montre Voir sur le Plex des qu'un lien existe, quel que soit l'etat`() {
        assertEquals(listOf(BoutonFicheVoyage.VOIR_SUR_LE_PLEX), boutonsFicheVoyage("vu", "https://app.plex.tv/x"))
        assertEquals(true, boutonsFicheVoyage("a_demander", "https://app.plex.tv/x").contains(BoutonFicheVoyage.VOIR_SUR_LE_PLEX))
        assertEquals(false, boutonsFicheVoyage("a_demander", null).contains(BoutonFicheVoyage.VOIR_SUR_LE_PLEX))
    }

    @Test
    fun `boutonsFicheVoyage cache Je l'ai vu une fois le film vu, seul cas ou il disparait`() {
        assertEquals(false, boutonsFicheVoyage("vu", null).contains(BoutonFicheVoyage.JE_L_AI_VU))
        assertEquals(true, boutonsFicheVoyage("sur_le_plex", null).contains(BoutonFicheVoyage.JE_L_AI_VU))
        assertEquals(true, boutonsFicheVoyage("a_demander", null).contains(BoutonFicheVoyage.JE_L_AI_VU))
        assertEquals(true, boutonsFicheVoyage("introuvable", null).contains(BoutonFicheVoyage.JE_L_AI_VU))
    }

    // Mutation : proposer « Demander » sur `demande` (déjà fait) redemanderait pour rien à Seerr.
    @Test
    fun `boutonsFicheVoyage ne propose Demander que sur a_demander, jamais sur demande`() {
        assertEquals(true, boutonsFicheVoyage("a_demander", null).contains(BoutonFicheVoyage.DEMANDER))
        assertEquals(false, boutonsFicheVoyage("demande", null).contains(BoutonFicheVoyage.DEMANDER))
        assertEquals(false, boutonsFicheVoyage("sur_le_plex", null).contains(BoutonFicheVoyage.DEMANDER))
    }

    // Introuvable et son inverse sont mutuellement exclusifs, et aucun des deux ne sort sur un vu.
    @Test
    fun `boutonsFicheVoyage bascule entre marquer et retirer introuvable, jamais les deux`() {
        val surIntrouvable = boutonsFicheVoyage("introuvable", null)
        assertEquals(true, surIntrouvable.contains(BoutonFicheVoyage.RETIRER_INTROUVABLE))
        assertEquals(false, surIntrouvable.contains(BoutonFicheVoyage.MARQUER_INTROUVABLE))

        val surADemander = boutonsFicheVoyage("a_demander", null)
        assertEquals(true, surADemander.contains(BoutonFicheVoyage.MARQUER_INTROUVABLE))
        assertEquals(false, surADemander.contains(BoutonFicheVoyage.RETIRER_INTROUVABLE))

        val surVu = boutonsFicheVoyage("vu", null)
        assertEquals(false, surVu.contains(BoutonFicheVoyage.MARQUER_INTROUVABLE))
        assertEquals(false, surVu.contains(BoutonFicheVoyage.RETIRER_INTROUVABLE))
    }

    // Le lien Plex choisi : `plex://` avant le web, rien si nul. Mutation : inverser les deux
    // branches ferait ouvrir l'appli Plex sur un lien web, et réciproquement.
    @Test
    fun `lienPlexVoyage essaie plex avant le web, rien si nul`() {
        assertEquals(LienPlex.App("plex://preplay/?metadataKey=1"), lienPlexVoyage("plex://preplay/?metadataKey=1"))
        assertEquals(LienPlex.Web("https://app.plex.tv/desktop#!/details"), lienPlexVoyage("https://app.plex.tv/desktop#!/details"))
        assertNull(lienPlexVoyage(null))
        assertNull(lienPlexVoyage(""))
    }

    // Jamais les trois à la fois : une salle qui se remplit prime sur épuisée, qui prime sur « en
    // voir plus ». Mutation : tester `epuisee` avant `fourneeEnCours` ferait dire « épuisée » sur
    // une salle qu'on vient justement de relancer.
    @Test
    fun `etiquetteEtagere priorise se remplit, puis epuisee, puis en voir plus`() {
        assertEquals("La salle se remplit…", etiquetteEtagere(epuisee = true, fourneeEnCours = true))
        assertEquals("La salle se remplit…", etiquetteEtagere(epuisee = false, fourneeEnCours = true))
        assertEquals("Salle épuisée", etiquetteEtagere(epuisee = true, fourneeEnCours = false))
        assertEquals("En voir plus", etiquetteEtagere(epuisee = false, fourneeEnCours = false))
    }
}
