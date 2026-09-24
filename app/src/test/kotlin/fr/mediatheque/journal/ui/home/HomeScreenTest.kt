package fr.mediatheque.journal.ui.home

import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.suivis.EnCours
import fr.mediatheque.journal.ui.suivis.EntiteSuivie
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeScreenTest {
    private val film = PlexFilm(tmdb_id = 1, title = "Metropolis", demande_le = "2026-09-01")
    private val realisateur = EnCours(
        SourceSuivi.REALISATEURS,
        EntiteSuivie(2, "Fritz Lang", null),
        FilmSuivi(tmdb_id = 3, title = "M le maudit"),
    )
    private val saga = EnCours(
        SourceSuivi.SAGAS,
        EntiteSuivie(4, "Alien", null),
        FilmSuivi(tmdb_id = 5, title = "Alien"),
    )

    // Revue du 24 septembre 2026, point 5, « le carrousel Ensuite ». Mutation : ne pas filtrer les
    // `null` (les glisser tels quels dans la liste) casse toutes les assertions de taille ; inverser
    // l'ordre (saga avant Plex) casse la troisième assertion.
    @Test fun `rien de propose, aucune carte`() {
        assertEquals(emptyList<CarteEnsuite>(), cartesEnsuite(null, null, null))
    }

    @Test fun `une seule source, une seule carte`() {
        assertEquals(listOf(CarteEnsuite.Plex(film)), cartesEnsuite(film, null, null))
        assertEquals(listOf(CarteEnsuite.Realisateur(realisateur)), cartesEnsuite(null, realisateur, null))
        assertEquals(listOf(CarteEnsuite.Saga(saga)), cartesEnsuite(null, null, saga))
    }

    @Test fun `les trois sources, dans l ordre Plex puis realisateur puis saga`() {
        assertEquals(
            listOf(CarteEnsuite.Plex(film), CarteEnsuite.Realisateur(realisateur), CarteEnsuite.Saga(saga)),
            cartesEnsuite(film, realisateur, saga),
        )
    }
}
