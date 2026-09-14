package fr.mediatheque.journal.reactions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReactionsTest {
    private val forme = Regex("^[a-z0-9_]{1,32}$")

    @Test fun `les cles sont uniques`() = assertEquals(Reactions.all.size, Reactions.all.map { it.key }.toSet().size)
    @Test fun `les cles ont la forme que le back exige`() = Reactions.all.forEach { assertTrue(it.key, forme.matches(it.key)) }
    @Test fun `emoji et phrase ne sont jamais vides`() = Reactions.all.forEach {
        assertTrue(it.key, it.emoji.isNotBlank()); assertTrue(it.key, it.phrase.isNotBlank())
    }
    @Test fun `une cle hors catalogue se rend comme sa cle nue`() {
        assertEquals("disparue", Reactions.label("disparue"))
        assertEquals("disparue", Reactions.emoji("disparue"))
        assertEquals("disparue", Reactions.phrase("disparue"))
    }
    @Test fun `une cle du catalogue se rend avec son emoji et sa phrase`() =
        assertEquals("❤️ J’ai adoré", Reactions.label("adore"))

    @Test fun `phrase rend la phrase seule, sans l emoji`() = assertEquals("J’ai adoré", Reactions.phrase("adore"))

    @Test fun `ordered rend le catalogue d abord, puis les cles orphelines`() =
        assertEquals(listOf("adore", "sympa", "disparue"), Reactions.ordered(setOf("sympa", "disparue", "adore")))

    // Au ciné (brief du 14 septembre 2026) filtre le journal par `?reaction=en_salle` : la
    // constante et l'entrée du catalogue doivent rester la même clé.
    @Test fun `en_salle est au catalogue, avec un clap`() {
        assertEquals("en_salle", Reactions.EN_SALLE)
        assertEquals("🎬 En salle", Reactions.label(Reactions.EN_SALLE))
    }
}
