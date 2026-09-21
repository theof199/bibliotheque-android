package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.AnneeVoyage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La carte du Voyage (brief du 21 septembre 2026, « l'année en étages ») : la récompense d'une
 * année (étape 5, pas encore livrée), la frontière qui avance, les tampons du passeport (étape 3).
 * Fonctions pures, sans réseau ni `ViewModel`.
 */
class VoyageCarteTest {

    private fun vu(annee: Int?, id: String, date: String, titre: String = "Un film") =
        FakeJournalApi.item("m-$id", date, null, emptyList(), null, id = "e-$id", title = titre, year = annee)

    // Les trois paliers, à leurs bornes (étape 5, à venir). Mutation : `manquants < 2` au lieu de
    // `<= 2` fait passer deux introuvables de Lion à Ours ; `manquants <= 1` idem ; `manquants ==
    // 0 -> LION` casse la première.
    @Test
    fun `recompense donne la Palme sans manquant, le Lion a un ou deux, l'Ours au-dela`() {
        assertEquals(Recompense.PALME, recompense(essentielsTotal = 5, essentielsFaits = 5))
        assertEquals(Recompense.LION, recompense(essentielsTotal = 5, essentielsFaits = 4))
        assertEquals(Recompense.LION, recompense(essentielsTotal = 5, essentielsFaits = 3))
        assertEquals(Recompense.OURS, recompense(essentielsTotal = 5, essentielsFaits = 2))
        assertEquals(Recompense.OURS, recompense(essentielsTotal = 5, essentielsFaits = 0))
    }

    // Une année sans essentiel connu n'est pas une année ratée : rien ne manque, donc la Palme.
    @Test
    fun `recompense sans essentiel du tout reste la Palme`() {
        assertEquals(Recompense.PALME, recompense(essentielsTotal = 0, essentielsFaits = 0))
    }

    // Le pluriel, et l'ordre Palme puis Lion puis Ours — jamais l'ordre d'arrivée des années.
    @Test
    fun `phraseRecompenses accorde le pluriel et garde l'ordre des festivals`() {
        val phrase = phraseRecompenses(listOf(Recompense.OURS, Recompense.PALME, Recompense.PALME))
        assertEquals("2 Palmes · 1 Ours", phrase)
    }

    // Vide à cette étape (le brief du 21 septembre 2026 : le back ne sert encore aucune
    // récompense) — la ligne du HUD ne s'affiche donc jamais. Mutation : rendre autre chose qu'une
    // chaîne vide sur une liste vide ferait apparaître « 0 Palme » au HUD.
    @Test
    fun `phraseRecompenses est vide sans aucune recompense`() {
        assertEquals("", phraseRecompenses(emptyList()))
    }

    // Le photogramme de la carte (brief du 21 septembre 2026, « le podium ») : l'affiche du n°1 du
    // podium prime sur le dernier film vu, jamais l'inverse. Mutation : inverser l'ordre du `?:`
    // ferait retomber sur le dernier vu même quand un podium est posé.
    @Test
    fun `afficheAnnee prend l'affiche du podium avant le dernier vu, le dernier vu si nulle`() {
        assertEquals("https://podium", afficheAnnee("https://podium", "https://dernier-vu"))
        assertEquals("https://dernier-vu", afficheAnnee(null, "https://dernier-vu"))
        assertNull(afficheAnnee(null, null))
    }

    // C'est l'année **quittée** qui est bouclée, pas la nouvelle année en cours. Mutation :
    // `anneeBouclee = apres` ferait annoncer « 1899 dans la boîte ! » alors qu'on vient d'y entrer.
    @Test
    fun `detecterFrontiereAvancee boucle l'annee quittee, sans decennie dans le meme monde`() {
        val avancee = detecterFrontiereAvancee(avant = 1898, apres = 1899)

        assertEquals(1898, avancee?.anneeBouclee)
        assertNull(avancee?.decennieBouclee)
    }

    // Et la décennie quittée quand l'année en cours change de monde. Mutation : comparer
    // `decennieApres` à elle-même, ou rendre toujours la décennie, allumerait la marquise et
    // lancerait le générique à chaque année.
    @Test
    fun `detecterFrontiereAvancee boucle la decennie quand l'annee en cours change de monde`() {
        val avancee = detecterFrontiereAvancee(avant = 1899, apres = 1900)

        assertEquals(1899, avancee?.anneeBouclee)
        assertEquals(1890, avancee?.decennieBouclee)
    }

    // Un premier chargement (rien de mémorisé), une année en cours qui ne bouge pas, ou qui recule
    // (une réponse en retard) ne bouclent rien : la snackbar et le générique ne se jouent pas tout
    // seuls à l'ouverture de l'écran.
    @Test
    fun `detecterFrontiereAvancee ne boucle rien sans avancee reelle`() {
        assertNull(detecterFrontiereAvancee(avant = null, apres = 1899))
        assertNull(detecterFrontiereAvancee(avant = 1899, apres = null))
        assertNull(detecterFrontiereAvancee(avant = 1899, apres = 1899))
        assertNull(detecterFrontiereAvancee(avant = 1900, apres = 1899))
    }

    // Vide à cette étape (brief du 21 septembre 2026) : une décennie ne se boucle qu'avec un Ours
    // par année et le ticket suivant utilisé (spec du 19 septembre 2026, §6), ni l'un ni l'autre
    // n'existant encore — même avec des années déjà connues de `/me/voyage` et un journal peuplé.
    // Mutation : tamponner quoi que ce soit ici ferait apparaître un tampon que le back n'a pas
    // encore gagné.
    @Test
    fun `tamponsPasseport ne tamponne jamais rien a cette etape`() {
        val voyage = VoyageUi(
            parAnnee = listOf(
                AnneeVoyage(1895, "ouverte", visitee = true, profondeur = 4),
                AnneeVoyage(1896, "ouverte", visitee = true, profondeur = 3),
                AnneeVoyage(1897, "en_cours", visitee = true, profondeur = 1),
            ).associateBy { it.annee },
        )
        val journal = listOf(vu(1895, "a", "2026-02-11"), vu(1896, "b", "2026-03-20"))

        assertTrue(tamponsPasseport(voyage, journal).isEmpty())
        assertTrue(tamponsPasseport(VoyageUi(), emptyList()).isEmpty())
    }

    // Le tri du portefeuille (décision 3 du brief du 21 septembre 2026, « le ticket ») : les non
    // utilisés d'abord (par année), les compostés ensuite (par date d'utilisation). Mutation :
    // inverser les deux groupes, ou trier les compostés par année plutôt que par date, ferait
    // remonter un vieux ticket composté devant un ticket qui attend encore.
    @Test
    fun `trierPortefeuille met les non utilises d'abord, par annee, puis les compostes par date`() {
        val nonUtilise1943 = TicketPortefeuilleUi(1943, "Motif 1943", utiliseLe = null)
        val nonUtilise1942 = TicketPortefeuilleUi(1942, "Motif 1942", utiliseLe = null)
        val composteRecent = TicketPortefeuilleUi(1938, "Motif 1938", utiliseLe = "2026-09-10T10:00:00.000Z")
        val composteAncien = TicketPortefeuilleUi(1935, "Motif 1935", utiliseLe = "2026-06-04T10:00:00.000Z")

        val tries = trierPortefeuille(listOf(composteRecent, nonUtilise1943, composteAncien, nonUtilise1942))

        assertEquals(listOf(nonUtilise1942, nonUtilise1943, composteAncien, composteRecent), tries)
    }

    @Test
    fun `trierPortefeuille sur une liste vide reste vide`() {
        assertTrue(trierPortefeuille(emptyList()).isEmpty())
    }
}
