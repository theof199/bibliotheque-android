package fr.mediatheque.journal.ui.frise

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les mondes du Voyage (brief du 16 septembre 2026, phase 2) : quelle décennie porte quelle
 * année, et quel numéro de chapitre. Fonctions pures, sans réseau ni Compose.
 */
class MondesTest {

    // La bascule se fait au millésime rond. Mutation : `(annee - 1890 + 1) / 10`, ou un
    // `annee / 10 * 10 - 10`, décale la frontière d'un an et fait échouer 1899 ou 1900.
    @Test
    fun `mondeDe bascule entre 1899 et 1900, pas avant, pas apres`() {
        assertEquals(1890, mondeDe(1890).decennie)
        assertEquals(1890, mondeDe(1895).decennie)
        assertEquals(1890, mondeDe(1899).decennie)
        assertEquals(1900, mondeDe(1900).decennie)
        assertEquals(1900, mondeDe(1901).decennie)
    }

    // Une année antérieure au premier monde rejoint les origines plutôt que de sortir de la
    // liste. Mutation : retirer le `coerceIn` fait lever un `IndexOutOfBoundsException` ici —
    // `(1888 - 1890).floorDiv(10)` vaut -1, pas 0 (c'est bien `floorDiv`, pas `/`, qui tronque
    // vers zéro et masquerait le défaut jusqu'à 1880).
    @Test
    fun `mondeDe ramene une annee d'avant 1890 aux origines`() {
        assertEquals(1890, mondeDe(1888).decennie)
        assertEquals(1890, mondeDe(1875).decennie)
        assertEquals("Les origines", mondeDe(1875).nom)
    }

    // Et au-delà du dernier monde, la dernière décennie — un film de 2031 ne sort pas de la carte.
    @Test
    fun `mondeDe ramene une annee d'apres le dernier monde au dernier`() {
        assertEquals(2020, mondeDe(2026).decennie)
        assertEquals(2020, mondeDe(2031).decennie)
    }

    // Les quatorze mondes se suivent de dix en dix, sans trou ni doublon : c'est ce que
    // `mondeDe` suppose pour indexer par soustraction.
    @Test
    fun `les mondes couvrent 1890 a 2020, de dix en dix`() {
        assertEquals((1890..2020 step 10).toList(), MONDES.map { it.decennie })
        assertEquals(MONDES.size, MONDES.map { it.titreVoyageur }.distinct().size)
    }

    // Le chapitre commence à I, pas à zéro ni à II. Mutation : `index` au lieu de `index + 1`
    // rend "" pour le premier monde ; `index + 2` rend "II".
    @Test
    fun `chapitreRomain part de I et compte en romain`() {
        assertEquals("I", chapitreRomain(0))
        assertEquals("II", chapitreRomain(1))
        assertEquals("IV", chapitreRomain(3))
        assertEquals("V", chapitreRomain(4))
        assertEquals("IX", chapitreRomain(8))
        assertEquals("X", chapitreRomain(9))
        assertEquals("XIV", chapitreRomain(13))
        // Une quinzième décennie sortira « XV » sans qu'on retouche la fonction.
        assertEquals("XV", chapitreRomain(14))
    }

    @Test
    fun `chapitreDe nomme le chapitre et son monde`() {
        assertEquals("Chapitre I · Les origines", chapitreDe(1895))
        assertEquals("Chapitre II · La féerie", chapitreDe(1902))
    }

    // Delta de Léon du 25 septembre 2026 (« pavillon par pavillon », §C, §E, §F) : chaque monde
    // porte désormais un format de photogramme, un slogan de marquise et une opacité de grain.
    // Mutation : mélanger deux décennies (le format de 1980 posé sur 1990, par exemple) ferait
    // passer ce test malgré un mauvais monde — chaque assertion cible sa décennie par nom.
    @Test
    fun `chaque monde porte son format, son slogan et son grain`() {
        val annees1890 = MONDES.first { it.decennie == 1890 }
        assertEquals(null, annees1890.slogan)
        assertEquals(0.09f, annees1890.grain)
        assertEquals(TraitementImage.SEPIA, annees1890.format.traitement)

        val annees1930 = MONDES.first { it.decennie == 1930 }
        assertEquals("100 % parlant · chantant", annees1930.slogan)
        assertEquals(0.07f, annees1930.grain)
        assertEquals(TraitementImage.BANDE_SON, annees1930.format.traitement)

        val annees1980 = MONDES.first { it.decennie == 1980 }
        assertEquals("VHS et synthés", annees1980.slogan)
        assertEquals(0f, annees1980.grain)
        assertEquals(Color(0xFFFF4FD8), annees1980.format.bord)
    }

    // Le grain disparaît à partir de 1980 (§F) : aucun des sept derniers mondes n'en porte plus.
    // Mutation : oublier une décennie dans la plage ferait rester un grain fantôme après 1980.
    @Test
    fun `le grain disparait a partir de 1980`() {
        MONDES.filter { it.decennie >= 1980 }.forEach { monde ->
            assertEquals("grain de ${monde.decennie}", 0f, monde.grain)
        }
        MONDES.filter { it.decennie < 1980 }.forEach { monde ->
            assertTrue("grain de ${monde.decennie}", monde.grain > 0f)
        }
    }

    // Les quatorze slogans sont distincts, sauf 1890 qui n'en a aucun (§E) — pas deux mondes qui
    // partagent la même phrase de marquise.
    @Test
    fun `les slogans sont distincts, sauf l'absence de celui de 1890`() {
        val slogans = MONDES.mapNotNull { it.slogan }
        assertEquals(MONDES.size - 1, slogans.size)
        assertEquals(slogans.size, slogans.distinct().size)
    }

    // Retouche du 28 septembre 2026 (§C du delta) : `matriceDe` — nulle pour les mondes récents,
    // sépia identité pour SEPIA, désaturée pour les traitements noir et blanc. Mutation : rendre
    // une matrice non nulle pour NUMERIQUE/STREAMING/AUJOURDHUI laisserait un filtre sur des
    // jaquettes qui doivent rester « propres et nettes ».
    @Test
    fun `matriceDe est nulle pour les mondes recents`() {
        assertNull(matriceDe(TraitementImage.NUMERIQUE))
        assertNull(matriceDe(TraitementImage.STREAMING))
        assertNull(matriceDe(TraitementImage.AUJOURDHUI))
    }

    // La matrice sépia à pleine intensité est celle des origines (1890), la référence de
    // `matriceSepia` — comparée valeur à valeur, `ColorMatrix` n'étant pas une `data class`.
    @Test
    fun `matriceDe rend le sepia classique pour SEPIA`() {
        val attendue = ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        val matrice = matriceDe(TraitementImage.SEPIA)
        assertEquals(attendue.values.toList(), matrice?.values?.toList())
    }

    // Les traitements noir et blanc désaturent complètement : la signature d'une saturation à 0
    // dans une `ColorMatrix` est que les trois lignes rouge/vert/bleu portent les mêmes
    // coefficients sur les colonnes rouge/vert/bleu (chaque canal de sortie devient la même
    // moyenne pondérée des trois entrées) — indépendant des poids de luminance exacts choisis par
    // l'implémentation de `setToSaturation`.
    @Test
    fun `matriceDe desature completement les traitements noir et blanc`() {
        listOf(TraitementImage.NOIR_ET_BLANC_DUR, TraitementImage.NB_GRANULEUX, TraitementImage.BANDE_SON_CONTRASTE).forEach { traitement ->
            val v = matriceDe(traitement)!!.values
            assertEquals("$traitement, colonne rouge", v[0], v[5], 0.001f)
            assertEquals("$traitement, colonne rouge", v[0], v[10], 0.001f)
            assertEquals("$traitement, colonne verte", v[1], v[6], 0.001f)
            assertEquals("$traitement, colonne verte", v[1], v[11], 0.001f)
            assertEquals("$traitement, colonne bleue", v[2], v[7], 0.001f)
            assertEquals("$traitement, colonne bleue", v[2], v[12], 0.001f)
        }
    }

    // Sans traitement noir et blanc, la matrice n'a pas cette signature — un garde-fou contre un
    // `matriceSaturation(0f)` posé partout par erreur.
    @Test
    fun `matriceDe ne desature pas SATURE`() {
        val v = matriceDe(TraitementImage.SATURE)!!.values
        assertTrue(kotlin.math.abs(v[0] - v[5]) > 0.01f || kotlin.math.abs(v[1] - v[6]) > 0.01f)
    }

    // Livraison 2 du delta de Léon (§H, 28 septembre 2026) : douze mondes sur quatorze portent une
    // image de fond, seuls 1890 et 1900 n'en ont aucune. Mutation : oublier une décennie dans la
    // plage 1910-2020 laisserait un fond manquant sans que rien ne le signale.
    @Test
    fun `chaque monde a une image sauf 1890 et 1900`() {
        MONDES.filter { it.decennie == 1890 || it.decennie == 1900 }.forEach { monde ->
            assertNull("image de ${monde.decennie}", monde.image)
        }
        MONDES.filter { it.decennie != 1890 && it.decennie != 1900 }.forEach { monde ->
            assertTrue("image de ${monde.decennie}", monde.image != null)
        }
    }

    // 1910 est la seule exception de §H : une affiche verticale, pas le cadrage Standard des onze
    // autres images.
    @Test
    fun `1910 porte le cadrage Affiche, les autres le cadrage Standard`() {
        val annees1910 = MONDES.first { it.decennie == 1910 }
        assertEquals(CadrageImage.Affiche, annees1910.image?.cadrage)

        MONDES.filter { it.image != null && it.decennie != 1910 }.forEach { monde ->
            assertEquals("cadrage de ${monde.decennie}", CadrageImage.Standard, monde.image?.cadrage)
        }
    }

    // `ImageDeMonde.matrice()` sans aucun filtre renseigné doit rester l'identité — sinon une image
    // qui ne demande ni grayscale, ni sepia, ni saturate, ni contraste, ni luminosité changée finirait
    // quand même teintée.
    @Test
    fun `matrice d'une image sans filtre est l'identite`() {
        val image = ImageDeMonde(0, CadrageImage.Standard, alpha = 1f)
        assertEquals(ColorMatrix().values.toList(), image.matrice().values.toList())
    }

    // La matrice d'une image en grayscale complet a la même signature de désaturation que les
    // traitements noir et blanc de `matriceDe`, ci-dessus.
    @Test
    fun `matrice d'une image grayscale desature completement`() {
        val v = ImageDeMonde(0, CadrageImage.Standard, alpha = 1f, grayscale = 1f).matrice().values
        assertEquals(v[0], v[5], 0.001f)
        assertEquals(v[0], v[10], 0.001f)
    }
}
