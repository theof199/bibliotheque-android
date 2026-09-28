package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les crédits des images du Voyage (§I du delta de Léon, livraison 2 du 28 septembre 2026) :
 * treize entrées, une par fichier de `images/sources/` — les douze images de fond plus
 * `orlok-ombre.svg`, la même donnée que `LICENCES-IMAGES.md` à la racine.
 */
class CreditsImagesTest {

    // Treize fichiers dans `images/sources/` (`SOURCES.txt`), treize entrées ici — aucun oublié.
    // Mutation : retirer une entrée de `CREDITS_IMAGES` sans que ce test échoue laisserait une
    // image sans crédit dans l'appli.
    @Test
    fun `la liste couvre les treize fichiers de sources`() {
        assertEquals(13, CREDITS_IMAGES.size)
        assertEquals(CREDITS_IMAGES.size, CREDITS_IMAGES.map { it.fichier }.distinct().size)
    }

    // Cinq images « CC BY »/« CC BY-SA » (§I : elles imposent de garder la mention de licence,
    // contrairement au domaine public) plus le CC0 d'Orlok — chacune porte sa propre licence,
    // jamais « domaine public » à sa place. Mutation : une seule licence « domaine public » posée
    // par erreur sur une CC BY-SA perdrait l'attribution que sa licence impose.
    @Test
    fun `les images CC BY portent chacune leur licence, pas domaine public`() {
        val licencesAttribuables = CREDITS_IMAGES.filter { it.licence.startsWith("CC BY") }
        assertEquals(5, licencesAttribuables.size)
        assertEquals(5, licencesAttribuables.map { it.fichier }.distinct().size)
        licencesAttribuables.forEach { credit ->
            assertTrue("licence de ${credit.fichier}", credit.licence != "domaine public")
        }
    }

    // Les images de fond (hors Orlok, qui n'a pas de page Commons) couvrent les douze mondes
    // illustrés de `MONDES` — la même plage que `image != null` dans `MondesTest`.
    @Test
    fun `les images de fond couvrent les douze mondes illustres`() {
        val decenniesImages = MONDES.filter { it.image != null }.map { it.decennie }
        val decenniesCredits = CREDITS_IMAGES.filter { it.fichier != "orlok-ombre.svg" }.map { it.decennie }
        assertEquals(decenniesImages.sorted(), decenniesCredits.sorted())
    }
}
