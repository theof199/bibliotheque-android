package fr.mediatheque.journal.ui.films

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.reactions.Reactions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les règles de « Mes films · le hall » (décisions du propriétaire des 24 et 25 septembre 2026) :
 * recherche, notes cochées, réactions, tri, et les libellés des trois puces. Fonctions pures de
 * `FilmsEtats.kt`, jumelles de `RealisateurEtatsTest.kt` — pas de réseau, pas de `ViewModel`.
 */
class FilmsEtatsTest {

    private fun film(
        id: String,
        titre: String = "Un film",
        realisateur: String? = null,
        date: String = "2026-09-01",
        note: Int? = null,
        reactions: List<String> = emptyList(),
    ): JournalItem = FakeJournalApi.item("m-$id", date, note, reactions, null, id = id, title = titre, director = realisateur)

    private val chihiro = film("chihiro", "Le Voyage de Chihiro", "Hayao Miyazaki", "2026-09-10", 9, listOf("adore", "visuel"))
    private val leon = film("leon", "Léon", "Luc Besson", "2026-08-02", 7, listOf("stressant", Reactions.EN_SALLE))
    private val totoro = film("totoro", "Mon voisin Totoro", "Hayao Miyazaki", "2026-07-15", null, listOf("adore"))
    private val tous = listOf(chihiro, leon, totoro)

    private fun ids(items: List<JournalItem>) = items.map { it.entry.id }

    // --- Texte -------------------------------------------------------------------------------

    // Le titre suffit. Mutation : ne chercher que dans le réalisateur casse cette assertion.
    @Test
    fun `le texte cherche dans le titre`() {
        assertEquals(listOf("totoro"), ids(appliquerFiltres(tous, FiltresFilms(texte = "totoro"))))
    }

    // « miya » trouve les deux Miyazaki par le réalisateur, et seulement eux. Mutation : ne
    // chercher que dans le titre casse cette assertion.
    @Test
    fun `le texte cherche aussi dans le realisateur`() {
        assertEquals(listOf("chihiro", "totoro"), ids(appliquerFiltres(tous, FiltresFilms(texte = "miya"))))
    }

    // « leon » trouve « Léon », « LÉON » aussi, et « Chihiro » trouve « chihiro ». Mutation : oublier
    // de retirer les accents ou de mettre en minuscules casse l'une de ces assertions.
    @Test
    fun `le texte ignore la casse et les accents`() {
        assertEquals(listOf("leon"), ids(appliquerFiltres(tous, FiltresFilms(texte = "leon"))))
        assertEquals(listOf("leon"), ids(appliquerFiltres(tous, FiltresFilms(texte = "LÉON"))))
        assertEquals(listOf("chihiro"), ids(appliquerFiltres(tous, FiltresFilms(texte = "CHIHIRO"))))
    }

    // Les espaces tapés autour ne comptent pas. Mutation : ne pas `trim()` casse cette assertion.
    @Test
    fun `le texte ignore les espaces autour`() {
        assertEquals(listOf("leon"), ids(appliquerFiltres(tous, FiltresFilms(texte = "  besson "))))
    }

    // Un texte vide ou blanc ne filtre rien. Mutation : filtrer sur « » (tout réalisateur nul
    // écarté, par exemple) casse cette assertion.
    @Test
    fun `un texte vide ou blanc garde tout`() {
        val sansRealisateur = film("anonyme", "Sans nom", null, "2026-06-01")
        val liste = tous + sansRealisateur
        assertEquals(4, appliquerFiltres(liste, FiltresFilms(texte = "")).size)
        assertEquals(4, appliquerFiltres(liste, FiltresFilms(texte = "   ")).size)
    }

    // --- Notes -------------------------------------------------------------------------------

    // Une note cochée garde exactement cette note, pas celles au-dessus (décision du propriétaire du
    // 25 septembre 2026). Mutation : revenir à un seuil `>=` garderait Chihiro (9) avec 7 coché et
    // casse cette assertion.
    @Test
    fun `une note cochee garde exactement cette note`() {
        assertEquals(listOf("leon"), ids(appliquerFiltres(tous, FiltresFilms(notes = setOf(7)))))
        assertEquals(listOf("chihiro"), ids(appliquerFiltres(tous, FiltresFilms(notes = setOf(9)))))
        assertEquals(emptyList<String>(), ids(appliquerFiltres(tous, FiltresFilms(notes = setOf(8)))))
    }

    // Deux notes cochées : l'une ou l'autre (ou), pas les deux à la fois. Mutation : un « et »
    // (toutes les notes) ne garderait rien et casse cette assertion.
    @Test
    fun `deux notes cochees gardent l une ou l autre`() {
        assertEquals(listOf("chihiro", "leon"), ids(appliquerFiltres(tous, FiltresFilms(notes = setOf(7, 9)))))
        assertEquals(listOf("leon"), ids(appliquerFiltres(tous, FiltresFilms(notes = setOf(4, 7)))))
    }

    // Un film sans note est écarté dès qu'une note est cochée, et gardé sinon. Mutation : garder
    // les films sans note casse la première assertion.
    @Test
    fun `un film sans note est ecarte des qu une note est cochee`() {
        val tout = (1..10).toSet()
        assertEquals(listOf("chihiro", "leon"), ids(appliquerFiltres(tous, FiltresFilms(notes = tout))))
        assertEquals(listOf("chihiro", "leon", "totoro"), ids(appliquerFiltres(tous, FiltresFilms())))
    }

    // --- Réactions ---------------------------------------------------------------------------

    // Toutes les réactions cochées, pas l'une d'elles. Mutation : un « ou » (`any`) garderait
    // Totoro avec « adore » seul et casse cette assertion.
    @Test
    fun `les reactions cochees doivent toutes etre presentes`() {
        assertEquals(listOf("chihiro", "totoro"), ids(appliquerFiltres(tous, FiltresFilms(reactions = setOf("adore")))))
        assertEquals(listOf("chihiro"), ids(appliquerFiltres(tous, FiltresFilms(reactions = setOf("adore", "visuel")))))
        assertEquals(emptyList<String>(), ids(appliquerFiltres(tous, FiltresFilms(reactions = setOf("adore", "stressant")))))
    }

    // Les trois filtres se cumulent. Mutation : n'appliquer que le premier filtre casse cette
    // assertion.
    @Test
    fun `texte, note et reactions se cumulent`() {
        val filtres = FiltresFilms(texte = "miyazaki", notes = setOf(9, 5), reactions = setOf("adore"))
        assertEquals(listOf("chihiro"), ids(appliquerFiltres(tous, filtres)))
    }

    // --- Tri ---------------------------------------------------------------------------------

    // Par défaut, récents d'abord, quel que soit l'ordre d'arrivée. Mutation : rendre la liste
    // telle quelle casse cette assertion.
    @Test
    fun `le tri par defaut met les plus recents d abord`() {
        assertEquals(listOf("chihiro", "leon", "totoro"), ids(appliquerFiltres(listOf(totoro, chihiro, leon), FiltresFilms())))
    }

    // Anciens d'abord. Mutation : inverser les deux branches de date casse cette assertion.
    @Test
    fun `le tri par date croissante met les plus anciens d abord`() {
        assertEquals(listOf("totoro", "leon", "chihiro"), ids(appliquerFiltres(tous, FiltresFilms(tri = TriFilms.DATE_ASC))))
    }

    // Deux visionnages du même jour gardent leur ordre d'arrivée, dans les deux sens. Mutation : un
    // tri instable, ou un départage sur autre chose (le titre, l'id), casse cette assertion.
    @Test
    fun `le tri par date est stable sur un meme jour`() {
        val a = film("a", "Zodiac", date = "2026-09-05")
        val b = film("b", "Alien", date = "2026-09-05")
        val c = film("c", "Heat", date = "2026-09-04")
        assertEquals(listOf("a", "b", "c"), ids(appliquerFiltres(listOf(a, b, c), FiltresFilms(tri = TriFilms.DATE_DESC))))
        assertEquals(listOf("c", "a", "b"), ids(appliquerFiltres(listOf(a, b, c), FiltresFilms(tri = TriFilms.DATE_ASC))))
    }

    // Par note décroissante, les films sans note en dernier (même en tête d'arrivée), une égalité de
    // note départagée par la date, récents d'abord. Mutation : placer `null` comme un 0 en tête
    // d'un tri croissant, ou oublier le départage par date, casse cette assertion.
    @Test
    fun `le tri par note met les sans note en dernier et departage par la date`() {
        val sansNoteRecent = film("sans-note", date = "2026-09-20", note = null)
        val huitAncien = film("huit-ancien", date = "2026-01-01", note = 8)
        val huitRecent = film("huit-recent", date = "2026-05-01", note = 8)
        val dix = film("dix", date = "2025-12-31", note = 10)
        val deux = film("deux", date = "2026-09-01", note = 2)
        val liste = listOf(sansNoteRecent, huitAncien, deux, huitRecent, dix)
        assertEquals(
            listOf("dix", "huit-recent", "huit-ancien", "deux", "sans-note"),
            ids(appliquerFiltres(liste, FiltresFilms(tri = TriFilms.NOTE_DESC))),
        )
    }

    // Même note, même jour : l'ordre d'arrivée reste. Mutation : un tri instable casse cette
    // assertion.
    @Test
    fun `le tri par note est stable a note et date egales`() {
        val a = film("a", date = "2026-09-05", note = 6)
        val b = film("b", date = "2026-09-05", note = 6)
        assertEquals(listOf("a", "b"), ids(appliquerFiltres(listOf(a, b), FiltresFilms(tri = TriFilms.NOTE_DESC))))
        assertEquals(listOf("b", "a"), ids(appliquerFiltres(listOf(b, a), FiltresFilms(tri = TriFilms.NOTE_DESC))))
    }

    // --- Réactions en mots, au cinéma -------------------------------------------------------

    // Dans l'ordre du catalogue (« adore » avant « visuel » avant « a_revoir »), pas dans l'ordre
    // posé, et sans « En salle » : le ticket le dit déjà. Mutation : garder `en_salle`, ou l'ordre
    // posé, casse cette assertion.
    @Test
    fun `motsReactions suit le catalogue et laisse en_salle au ticket`() {
        val item = film("x", reactions = listOf("a_revoir", Reactions.EN_SALLE, "visuel", "adore"))
        assertEquals("J’ai adoré · Wahou, visuellement · À revoir", motsReactions(item))
    }

    // Rien, ou seulement « En salle » : chaîne vide. Mutation : rendre « En salle » casse cette
    // assertion.
    @Test
    fun `motsReactions est vide sans autre reaction que en_salle`() {
        assertEquals("", motsReactions(film("x")))
        assertEquals("", motsReactions(film("y", reactions = listOf(Reactions.EN_SALLE))))
    }

    // Le ticket suit la seule réaction `en_salle`. Mutation : l'inverser casse ces assertions.
    @Test
    fun `auCinema suit la reaction en_salle`() {
        assertTrue(auCinema(leon))
        assertFalse(auCinema(chihiro))
    }

    // --- Puces -------------------------------------------------------------------------------

    @Test
    fun `la puce Date dit l ordre choisi`() {
        assertEquals("Date, récents d’abord", libellePuceDate(FiltresFilms()))
        assertEquals("Date, anciens d’abord", libellePuceDate(FiltresFilms(tri = TriFilms.DATE_ASC)))
        assertEquals("Date, récents d’abord", libellePuceDate(FiltresFilms(tri = TriFilms.NOTE_DESC)))
    }

    // Les combinaisons du tri par note et des notes cochées (décisions du propriétaire des 24 et 25
    // septembre 2026 : une seule puce pour les deux, qui compte les notes comme la puce Réaction
    // compte les réactions). Mutation : afficher la note plutôt que le compte casse cette assertion.
    @Test
    fun `la puce Note dit le tri et le nombre de notes cochees`() {
        assertEquals("Note", libellePuceNote(FiltresFilms()))
        assertEquals("Note · 1", libellePuceNote(FiltresFilms(notes = setOf(8))))
        assertEquals("Note · 2", libellePuceNote(FiltresFilms(notes = setOf(4, 7))))
        assertEquals("Note, tri", libellePuceNote(FiltresFilms(tri = TriFilms.NOTE_DESC)))
        assertEquals("Note, tri · 2", libellePuceNote(FiltresFilms(tri = TriFilms.NOTE_DESC, notes = setOf(4, 7))))
    }

    @Test
    fun `la puce Reaction compte les reactions cochees`() {
        assertEquals("Réaction", libellePuceReaction(FiltresFilms()))
        assertEquals("Réaction · 2", libellePuceReaction(FiltresFilms(reactions = setOf("adore", "nul"))))
    }

    // --- actifs ------------------------------------------------------------------------------

    // Chaque écart au défaut rend les filtres actifs (l'écran charge alors tout le journal) ; un
    // texte blanc, qui ne filtre rien, non. Mutation : oublier l'un des quatre champs casse l'une de
    // ces assertions.
    @Test
    fun `actifs des qu un choix s ecarte du defaut`() {
        assertFalse(FiltresFilms().actifs)
        assertFalse(FiltresFilms(texte = "   ").actifs)
        assertTrue(FiltresFilms(texte = "miya").actifs)
        assertTrue(FiltresFilms(tri = TriFilms.DATE_ASC).actifs)
        assertTrue(FiltresFilms(tri = TriFilms.NOTE_DESC).actifs)
        assertTrue(FiltresFilms(notes = setOf(1)).actifs)
        assertTrue(FiltresFilms(reactions = setOf("adore")).actifs)
    }

    // Le compte de l'en-tête : les deux chiffres, le total seul, ou rien du tout tant que
    // `GET /stats` n'a pas répondu. Mutation : afficher « 0 films » à la place de rien, ou oublier le
    // singulier, casse l'une de ces assertions.
    @Test
    fun `le compte de l en-tete`() {
        assertEquals("87 films · 12 cette année", compteEnTete(87, 12))
        assertEquals("87 films", compteEnTete(87, null))
        assertEquals("1 film · 1 cette année", compteEnTete(1, 1))
        assertEquals("0 film · 0 cette année", compteEnTete(0, 0))
        assertEquals(null, compteEnTete(null, 12))
        assertEquals(null, compteEnTete(null, null))
    }
}
