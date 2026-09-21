package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le podium d'une année (brief du 21 septembre 2026, « le podium ») : les candidats à une marche,
 * le corps envoyé, les lignes des deux feuilles. Fonctions pures, sans réseau ni `ViewModel`.
 */
class PodiumEtatsTest {

    private fun vu(tmdbId: Int, annee: Int?, note: Int? = null, titre: String = "Film $tmdbId") =
        FakeJournalApi.item("m-$tmdbId", "2026-02-11", note, emptyList(), null, id = "e-$tmdbId", title = titre, year = annee, externalId = "$tmdbId")

    private fun bobine(tmdbId: Int, etat: String) = BobineUi(tmdbId, "Bobine $tmdbId", 9, null, null, etat)

    private fun filmDeSalle(id: String, tmdbId: Int, etat: String, programme: ProgrammeUi? = null) = FilmSalleUi(
        id = id,
        rang = 1,
        tmdbId = tmdbId,
        title = "Salle $tmdbId",
        originalTitle = null,
        year = 1941,
        realisateur = "Un réalisateur",
        raison = null,
        coverUrl = null,
        plexUrl = null,
        etat = etat,
        note = null,
        programme = programme,
    )

    private fun salle(vararg films: FilmSalleUi) =
        SalleUi("s1", 1, "Une salle", "Ce qu'il ne fallait pas manquer", null, false, false, films.toList())

    // Jamais un film d'une autre année (le back refuserait le `tmdb_id` en `400` sinon).
    // Mutation : retirer le filtre sur l'année ferait proposer un film de 1940 sur le podium de 1941.
    @Test
    fun `candidatsPodium ecarte un film d'une autre annee`() {
        val journal = listOf(vu(1, 1941), vu(2, 1940))

        val candidats = candidatsPodium(journal, 1941, emptyList())

        assertEquals(listOf(1), candidats.filterIsInstance<CandidatPodium.Film>().map { it.tmdbId })
    }

    // Jamais un programme partiel (une bobine qui manque encore).
    // Mutation : ne tester qu'une bobine (`any` au lieu de `all` dans `etatFilmVoyage`) proposerait
    // un programme encore incomplet.
    @Test
    fun `candidatsPodium ecarte un programme partiel, garde un programme entierement vu`() {
        val complet = filmDeSalle("prog-complet", 10, "sur_le_plex", ProgrammeUi(9, listOf(bobine(11, "vu"), bobine(12, "vu"))))
        val partiel = filmDeSalle("prog-partiel", 20, "sur_le_plex", ProgrammeUi(9, listOf(bobine(21, "vu"), bobine(22, "sur_le_plex"))))

        val candidats = candidatsPodium(emptyList(), 1941, listOf(salle(complet, partiel)))

        assertEquals(listOf("prog-complet"), candidats.filterIsInstance<CandidatPodium.Programme>().map { it.programmeId })
    }

    // Un film de salle ordinaire (sans bobines) n'est candidat que par le journal, jamais une
    // deuxième fois comme « programme ». Mutation : retirer le filtre `programme != null` doublerait
    // ce même film vu.
    @Test
    fun `candidatsPodium ne double pas un film de salle ordinaire deja compte par le journal`() {
        val ordinaire = filmDeSalle("f-ordinaire", 30, "vu")
        val journal = listOf(vu(30, 1941))

        val candidats = candidatsPodium(journal, 1941, listOf(salle(ordinaire)))

        assertEquals(1, candidats.size)
        assertTrue(candidats.single() is CandidatPodium.Film)
    }

    // Le corps envoyé : `tmdb_id` pour un film, `programme_id` pour un programme, jamais les deux.
    // Mutation : inverser les deux branches enverrait un `tmdb_id` pour un programme, rejeté en 400.
    @Test
    fun `corpsPodium envoie tmdb_id pour un film, programme_id pour un programme, jamais les deux`() {
        val corpsFilm = corpsPodium(CandidatPodium.Film(42, "Un film", null, null))
        assertEquals(42, corpsFilm.tmdb_id)
        assertNull(corpsFilm.programme_id)

        val corpsProgramme = corpsPodium(CandidatPodium.Programme("prog-1", "Un programme", null))
        assertNull(corpsProgramme.tmdb_id)
        assertEquals("prog-1", corpsProgramme.programme_id)
    }

    // « Retirer du podium » seulement si la marche est occupée (décision 2 du brief).
    // Mutation : l'ajouter systématiquement ferait proposer de vider une marche déjà vide.
    @Test
    fun `lignesFeuillePodium ne propose Retirer que si la marche est occupee`() {
        val candidat = CandidatPodium.Film(1, "Un film", null, null)

        val marcheVide = lignesFeuillePodium(1, null, listOf(candidat))
        assertTrue(marcheVide.none { it is LignePodiumFeuille.Retirer })

        val marcheOccupee = lignesFeuillePodium(1, PodiumMarcheUi(1, 1, null, "Un film", null), listOf(candidat))
        assertEquals(LignePodiumFeuille.Retirer(1), marcheOccupee.first())
    }

    // L'occupant actuel, et lui seul, coché. Mutation : comparer par titre plutôt que par
    // identifiant cocherait n'importe quel candidat qui porte le même titre.
    @Test
    fun `lignesFeuillePodium coche seulement le candidat qui occupe deja la marche`() {
        val occupantFilm = CandidatPodium.Film(1, "Occupant", null, null)
        val autreFilm = CandidatPodium.Film(2, "Occupant", null, null)
        val programme = CandidatPodium.Programme("prog-1", "Un programme", null)
        val marche = PodiumMarcheUi(1, 1, null, "Occupant", null)

        val lignes = lignesFeuillePodium(1, marche, listOf(occupantFilm, autreFilm, programme))
        val coches = lignes.filterIsInstance<LignePodiumFeuille.Candidat>().filter { it.estOccupant }.map { it.candidat }

        assertEquals(listOf(occupantFilm), coches)
    }

    // Les trois lignes de la feuille « Mettre sur le podium » : « libre » ou l'occupant, la marche
    // qui porte déjà ce film cochée. Mutation : comparer sans distinguer `tmdbId`/`programmeId`
    // cocherait la marche d'un programme de même identifiant numérique qu'un film.
    @Test
    fun `lignesChoixMarche dit libre ou l'occupant, coche la marche qui porte deja ce film`() {
        val podium = listOf(
            PodiumMarcheUi(1, 500, null, "Citizen Kane", null),
            null,
            PodiumMarcheUi(3, null, "prog-1", "Programme Lumiere", null),
        )

        val pourLeFilm500 = lignesChoixMarche(podium, tmdbId = 500, programmeId = null)
        assertEquals("Citizen Kane", pourLeFilm500[0].occupantActuel)
        assertTrue(pourLeFilm500[0].estCeFilm)
        assertNull(pourLeFilm500[1].occupantActuel)
        assertTrue(!pourLeFilm500[1].estCeFilm)
        assertTrue(!pourLeFilm500[2].estCeFilm)

        val pourUnAutreFilm = lignesChoixMarche(podium, tmdbId = 999, programmeId = null)
        assertTrue(pourUnAutreFilm.none { it.estCeFilm })

        val pourLeProgramme = lignesChoixMarche(podium, tmdbId = null, programmeId = "prog-1")
        assertTrue(pourLeProgramme[2].estCeFilm)
        assertTrue(!pourLeProgramme[0].estCeFilm)
    }
}
