package fr.mediatheque.journal.ui

import fr.mediatheque.journal.ui.frise.Recompense
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `Emblemes` : le nom de ressource attendu pour chaque emblème, et la récompense que dessine son
 * substitut vectoriel. Fonctions pures, sans Compose.
 */
class EmblemesTest {

    // Les cinq emblèmes ont chacun un nom de ressource, et deux emblèmes distincts n'en partagent
    // jamais un. Mutation : faire pointer PALME sur "embleme_lion" romprait l'unicité sans faire
    // rougir la première assertion seule.
    @Test
    fun `chaque embleme a un nom de ressource, tous distincts`() {
        val noms = EmblemeType.entries.map { Emblemes.nomRessource(it) }
        noms.forEach { nom -> assertNotNull(nom) }
        assertEquals(EmblemeType.entries.size, noms.distinct().size)
    }

    // Chaque récompense a son substitut (`glypheRecompense`) ; les deux tampons dessinent le
    // leur, propre, sans passer par une récompense. Mutation : `PASSEPORT -> Recompense.OURS`
    // ferait dessiner un ours à la place du double cercle.
    @Test
    fun `chaque recompense a son substitut, les tampons n'en ont pas`() {
        assertEquals(Recompense.OURS, Emblemes.recompenseDe(EmblemeType.OURS))
        assertEquals(Recompense.LION, Emblemes.recompenseDe(EmblemeType.LION))
        assertEquals(Recompense.PALME, Emblemes.recompenseDe(EmblemeType.PALME))
        assertNull(Emblemes.recompenseDe(EmblemeType.PASSEPORT))
        assertNull(Emblemes.recompenseDe(EmblemeType.PERDU))
    }

    // `typeDe` est l'inverse de `recompenseDe`, pour les appelants qui n'ont qu'un `Recompense`
    // (la carte, la boîte, le cartouche de l'année). Mutation : `LION -> EmblemeType.OURS` casse
    // le aller-retour sans casser le test au-dessus, qui ne regarde que l'autre sens.
    @Test
    fun `typeDe fait l'aller-retour avec recompenseDe`() {
        Recompense.entries.forEach { recompense ->
            assertEquals(recompense, Emblemes.recompenseDe(Emblemes.typeDe(recompense)))
        }
    }
}
