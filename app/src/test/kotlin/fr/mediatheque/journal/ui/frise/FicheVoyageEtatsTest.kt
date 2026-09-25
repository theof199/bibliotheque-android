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

    // Les six boutons, un par cas qui les distingue.
    @Test
    fun `boutonsFicheVoyage montre Voir sur le Plex des qu'un lien existe, quel que soit l'etat`() {
        assertEquals(listOf(BoutonFicheVoyage.VOIR_SUR_LE_PLEX, BoutonFicheVoyage.METTRE_SUR_LE_PODIUM), boutonsFicheVoyage("vu", "https://app.plex.tv/x", entreeConnue = false))
        assertEquals(true, boutonsFicheVoyage("a_demander", "https://app.plex.tv/x", entreeConnue = false).contains(BoutonFicheVoyage.VOIR_SUR_LE_PLEX))
        assertEquals(false, boutonsFicheVoyage("a_demander", null, entreeConnue = false).contains(BoutonFicheVoyage.VOIR_SUR_LE_PLEX))
    }

    // « Mettre sur le podium » (décision 3 du brief du 21 septembre 2026) n'apparaît que sur un
    // film vu, jamais sur un état intermédiaire. Mutation : l'ajouter sans la garde `etat == "vu"`
    // proposerait le podium pour un film qu'on n'a pas encore vu.
    @Test
    fun `boutonsFicheVoyage ne propose Mettre sur le podium que sur un film vu`() {
        assertEquals(true, boutonsFicheVoyage("vu", null, entreeConnue = false).contains(BoutonFicheVoyage.METTRE_SUR_LE_PODIUM))
        assertEquals(false, boutonsFicheVoyage("a_demander", null, entreeConnue = false).contains(BoutonFicheVoyage.METTRE_SUR_LE_PODIUM))
        assertEquals(false, boutonsFicheVoyage("sur_le_plex", null, entreeConnue = false).contains(BoutonFicheVoyage.METTRE_SUR_LE_PODIUM))
        assertEquals(false, boutonsFicheVoyage("introuvable", null, entreeConnue = false).contains(BoutonFicheVoyage.METTRE_SUR_LE_PODIUM))
    }

    @Test
    fun `boutonsFicheVoyage cache Je l'ai vu une fois le film vu, seul cas ou il disparait`() {
        assertEquals(false, boutonsFicheVoyage("vu", null, entreeConnue = false).contains(BoutonFicheVoyage.JE_L_AI_VU))
        assertEquals(true, boutonsFicheVoyage("sur_le_plex", null, entreeConnue = false).contains(BoutonFicheVoyage.JE_L_AI_VU))
        assertEquals(true, boutonsFicheVoyage("a_demander", null, entreeConnue = false).contains(BoutonFicheVoyage.JE_L_AI_VU))
        assertEquals(true, boutonsFicheVoyage("introuvable", null, entreeConnue = false).contains(BoutonFicheVoyage.JE_L_AI_VU))
    }

    // Mutation : proposer « Demander » sur `demande` (déjà fait) redemanderait pour rien à Seerr.
    @Test
    fun `boutonsFicheVoyage ne propose Demander que sur a_demander, jamais sur demande`() {
        assertEquals(true, boutonsFicheVoyage("a_demander", null, entreeConnue = false).contains(BoutonFicheVoyage.DEMANDER))
        assertEquals(false, boutonsFicheVoyage("demande", null, entreeConnue = false).contains(BoutonFicheVoyage.DEMANDER))
        assertEquals(false, boutonsFicheVoyage("sur_le_plex", null, entreeConnue = false).contains(BoutonFicheVoyage.DEMANDER))
    }

    // Introuvable et son inverse sont mutuellement exclusifs, et aucun des deux ne sort sur un vu.
    @Test
    fun `boutonsFicheVoyage bascule entre marquer et retirer introuvable, jamais les deux`() {
        val surIntrouvable = boutonsFicheVoyage("introuvable", null, entreeConnue = false)
        assertEquals(true, surIntrouvable.contains(BoutonFicheVoyage.RETIRER_INTROUVABLE))
        assertEquals(false, surIntrouvable.contains(BoutonFicheVoyage.MARQUER_INTROUVABLE))

        val surADemander = boutonsFicheVoyage("a_demander", null, entreeConnue = false)
        assertEquals(true, surADemander.contains(BoutonFicheVoyage.MARQUER_INTROUVABLE))
        assertEquals(false, surADemander.contains(BoutonFicheVoyage.RETIRER_INTROUVABLE))

        val surVu = boutonsFicheVoyage("vu", null, entreeConnue = false)
        assertEquals(false, surVu.contains(BoutonFicheVoyage.MARQUER_INTROUVABLE))
        assertEquals(false, surVu.contains(BoutonFicheVoyage.RETIRER_INTROUVABLE))
    }

    // « Corriger » (« la fiche · trois visages », 25 septembre 2026) prend la tête de la pile d'un
    // film vu dont l'entrée est connue. Mutation : l'ajouter après « Voir sur le Plex » casse
    // l'ordre attendu.
    @Test
    fun `boutonsFicheVoyage met Corriger en tete sur un film vu dont l'entree est connue`() {
        assertEquals(
            listOf(BoutonFicheVoyage.CORRIGER, BoutonFicheVoyage.VOIR_SUR_LE_PLEX, BoutonFicheVoyage.METTRE_SUR_LE_PODIUM),
            boutonsFicheVoyage("vu", "https://app.plex.tv/x", entreeConnue = true),
        )
    }

    // Mutation : ignorer `entreeConnue` ferait sortir un « Corriger » qui n'aurait rien à ouvrir.
    @Test
    fun `boutonsFicheVoyage ne propose pas Corriger sans entree connue`() {
        assertEquals(false, boutonsFicheVoyage("vu", null, entreeConnue = false).contains(BoutonFicheVoyage.CORRIGER))
    }

    // Jamais avec « Je l'ai vu » : un film pas vu n'a rien à corriger. Mutation : ignorer `etat`
    // mettrait les deux boutons corail l'un sur l'autre.
    @Test
    fun `boutonsFicheVoyage ne propose pas Corriger sur un film pas vu`() {
        assertEquals(false, boutonsFicheVoyage("sur_le_plex", null, entreeConnue = true).contains(BoutonFicheVoyage.CORRIGER))
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

    // --- salleVientDeSeBoucler (habillage du 23 septembre 2026, geste 10) --------------------

    private fun film(id: String, etat: String) = FilmSalleUi(
        id = id,
        rang = 0,
        tmdbId = id.hashCode(),
        title = "Film $id",
        originalTitle = null,
        year = null,
        realisateur = "",
        raison = null,
        coverUrl = null,
        plexUrl = null,
        etat = etat,
        note = null,
        programme = null,
    )

    private fun salle(vararg etats: String) = SalleUi(
        id = "salle",
        rang = 0,
        nom = "Salle",
        raisonDEtre = "",
        cle = null,
        epuisee = false,
        fourneeEnCours = false,
        films = etats.mapIndexed { i, etat -> film("f$i", etat) },
    )

    // Le dernier film restant passe à vu : la salle se boucle. Mutation : comparer seulement
    // `apres` sans `avant` célébrerait à chaque relecture d'une salle déjà bouclée.
    @Test
    fun `salleVientDeSeBoucler quand le dernier film passe a vu`() {
        val avant = salle("vu", "a_demander")
        val apres = salle("vu", "vu")
        assertEquals(true, salleVientDeSeBoucler(avant, apres))
    }

    // Un introuvable compte comme acquis, au même titre qu'un vu. Mutation : compter un
    // introuvable comme non acquis (`etat == "vu"` seul, sans `|| etat == "introuvable"`) ferait
    // rougir ce test — la salle ne serait jamais dite bouclée ici.
    @Test
    fun `salleVientDeSeBoucler compte un introuvable comme acquis`() {
        val avant = salle("vu", "a_demander")
        val apres = salle("vu", "introuvable")
        assertEquals(true, salleVientDeSeBoucler(avant, apres))
    }

    // Une salle déjà bouclée avant le geste ne célèbre pas une seconde fois. Mutation : retirer
    // la garde `!etaitBouclee` la ferait fêter à chaque relecture d'une salle déjà complète.
    @Test
    fun `salleVientDeSeBoucler ne celebre pas une salle deja bouclee avant`() {
        val avant = salle("vu", "introuvable")
        val apres = salle("vu", "introuvable")
        assertEquals(false, salleVientDeSeBoucler(avant, apres))
    }

    // Une salle qui reste incomplète ne célèbre pas.
    @Test
    fun `salleVientDeSeBoucler ne celebre pas une salle qui reste incomplete`() {
        val avant = salle("a_demander", "a_demander")
        val apres = salle("vu", "a_demander")
        assertEquals(false, salleVientDeSeBoucler(avant, apres))
    }

    // Une salle tout juste apparue (`avant` nul, par exemple une fournée fraîchement ouverte) ne
    // célèbre jamais, même déjà complète (un seul film, déjà vu).
    @Test
    fun `salleVientDeSeBoucler ignore une salle sans etat anterieur connu`() {
        val apres = salle("vu")
        assertEquals(false, salleVientDeSeBoucler(null, apres))
    }
}
