package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les pistes de salles (brief du 22 septembre 2026, « les pistes ») : fonctions pures de
 * `PisteEtats.kt`, sans réseau ni `ViewModel`, comme `PodiumEtatsTest.kt`/`CarnetEtatsTest.kt` à
 * côté.
 */
class PisteEtatsTest {

    private val muet = PisteUi("Le cinéma muet allemand", "Expressionnisme et ombres.")
    private val technicolor = PisteUi("Les débuts du technicolor", "La couleur commence à s’installer.")
    private val sovietique = PisteUi("Le cinéma soviétique du montage", "Eisenstein et ses héritiers.")

    // Mutation : retirer une autre piste que celle utilisée (ou toutes) ferait perdre des pistes
    // encore valables — seule celle dont le nom correspond doit disparaître.
    @Test
    fun `pistesApresUsage ne retire que la piste utilisee`() {
        val pistes = listOf(muet, technicolor, sovietique)

        val restantes = pistesApresUsage(pistes, "Les débuts du technicolor")

        assertEquals(listOf(muet, sovietique), restantes)
    }

    // Mutation : vider la liste (ou ne rien faire du tout) sans piste utilisée casserait soit le
    // cas « champ rempli à la main », soit le cas « piste touchée ».
    @Test
    fun `pistesApresUsage laisse la liste intacte sans piste utilisee`() {
        val pistes = listOf(muet, technicolor)

        val restantes = pistesApresUsage(pistes, null)

        assertEquals(pistes, restantes)
    }

    // Un nom qui ne correspond à aucune piste (piste déjà retirée entre-temps) ne doit rien
    // retirer d'autre.
    @Test
    fun `pistesApresUsage ignore un nom inconnu`() {
        val pistes = listOf(muet, technicolor)

        val restantes = pistesApresUsage(pistes, "Une piste disparue")

        assertEquals(pistes, restantes)
    }

    @Test
    fun `autresPistesVisible est vrai seulement sans aucune piste`() {
        assertTrue(autresPistesVisible(emptyList()))
        assertFalse(autresPistesVisible(listOf(muet)))
    }

    // Toucher une pastille remplit le champ avec son nom et la retient comme touchée — mutation :
    // ne pas retenir `pisteTouchee` ferait envoyer `piste = null` malgré la pastille touchée.
    @Test
    fun `nouvelleSalleSuivant toucher une pastille remplit le texte et la retient`() {
        val etat = nouvelleSalleSuivant(NouvelleSalleEtat(), NouvelleSalleEvenement.ToucherPiste(muet))

        assertEquals("Le cinéma muet allemand", etat.texte)
        assertEquals("Le cinéma muet allemand", etat.pisteTouchee)
    }

    // Écrire sans avoir touché de pastille laisse `pisteTouchee` nulle — c'est ce qui fait que
    // « piste » part absente du corps de la demande.
    @Test
    fun `nouvelleSalleSuivant ecrire sans avoir touche de pastille laisse pisteTouchee nulle`() {
        val etat = nouvelleSalleSuivant(NouvelleSalleEtat(), NouvelleSalleEvenement.Ecrire("la comédie italienne"))

        assertEquals("la comédie italienne", etat.texte)
        assertNull(etat.pisteTouchee)
    }

    // Décision 1 du brief : retoucher le texte après avoir touché une pastille change le texte
    // affiché mais ne défait jamais `pisteTouchee` — mutation : la réinitialiser sur `Ecrire`
    // ferait perdre la piste dès la première frappe qui suit un tap.
    @Test
    fun `nouvelleSalleSuivant conserve la pastille touchee apres retouche du texte`() {
        var etat = NouvelleSalleEtat()
        etat = nouvelleSalleSuivant(etat, NouvelleSalleEvenement.ToucherPiste(muet))
        etat = nouvelleSalleSuivant(etat, NouvelleSalleEvenement.Ecrire("Le cinéma muet allemand, mais avec Chaplin"))

        assertEquals("Le cinéma muet allemand, mais avec Chaplin", etat.texte)
        assertEquals("Le cinéma muet allemand", etat.pisteTouchee)
    }

    // Toucher une seconde pastille remplace la première : c'est la dernière touchée qui compte,
    // jamais la première.
    @Test
    fun `nouvelleSalleSuivant toucher une autre pastille remplace la derniere touchee`() {
        var etat = NouvelleSalleEtat()
        etat = nouvelleSalleSuivant(etat, NouvelleSalleEvenement.ToucherPiste(muet))
        etat = nouvelleSalleSuivant(etat, NouvelleSalleEvenement.ToucherPiste(technicolor))

        assertEquals("Les débuts du technicolor", etat.texte)
        assertEquals("Les débuts du technicolor", etat.pisteTouchee)
    }
}
