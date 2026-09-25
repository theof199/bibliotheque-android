package fr.mediatheque.journal.ui.cinema

import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.SortieCinemaFilm
import fr.mediatheque.journal.api.dto.SortieFilm
import fr.mediatheque.journal.ui.suivis.EntiteSuivie
import fr.mediatheque.journal.ui.suivis.EtatFilmographie
import fr.mediatheque.journal.ui.suivis.SuiviState
import fr.mediatheque.journal.ui.suivis.SuivisUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** « Au ciné · le guichet » (25 septembre 2026) : les repères du sceau or, tirés des Suivis déjà chargés. */
class AuCineEtatsTest {
    private fun realisateur(nom: String) = EntiteSuivie(tmdbId = nom.hashCode(), nom = nom, imageUrl = null)

    private fun saga(tmdbId: Int) = EntiteSuivie(tmdbId = tmdbId, nom = "Saga $tmdbId", imageUrl = null)

    private fun filmSuivi(tmdbId: Int) = FilmSuivi(tmdb_id = tmdbId, title = "Film $tmdbId")

    private fun tuileEnCours(tmdbId: Int?, vararg realisateurs: String) =
        SortieCinemaFilm(tmdb_id = tmdbId, allocine_id = 1, title = "Tuile", directors = realisateurs.toList())

    // Allociné et TMDB n'écrivent pas toujours un nom pareil : l'accent ou la casse ne doivent pas
    // faire manquer le sceau. Mutation : comparer les noms bruts casse cette assertion.
    @Test
    fun `un realisateur suivi se reconnait par son nom, sans accents ni casse`() {
        val reperes = reperesSuivis(SuivisUi(realisateurs = SuiviState(entites = listOf(realisateur("Céline Sciamma")))))

        assertEquals(MarqueSuivi.REALISATEUR, reperes.marque(tuileEnCours(42, "CELINE SCIAMMA")))
    }

    @Test
    fun `un film d une saga suivie se reconnait par son tmdb_id sur la grille en cours`() {
        val reperes = ReperesSuivis(filmsDeSagas = setOf(120))

        assertEquals(MarqueSuivi.SAGA, reperes.marque(tuileEnCours(120, "Peter Jackson")))
    }

    @Test
    fun `un film d une saga suivie se reconnait par son tmdb_id sur la semaine prochaine`() {
        val reperes = ReperesSuivis(filmsDeSagas = setOf(120))

        assertEquals(MarqueSuivi.SAGA, reperes.marque(SortieFilm(120, "La Communauté de l'anneau")))
        assertNull(reperes.marque(SortieFilm(121, "Un autre film")))
    }

    // Décision du 25 septembre 2026 : quand les deux sont vrais, le réalisateur l'emporte.
    @Test
    fun `le realisateur suivi l emporte sur la saga suivie`() {
        val reperes = ReperesSuivis(realisateurs = setOf("peter jackson"), filmsDeSagas = setOf(120))

        assertEquals(MarqueSuivi.REALISATEUR, reperes.marque(tuileEnCours(120, "Peter Jackson")))
    }

    // Une tuile sans tmdb_id n'est pas touchable : ni coche ni sceau, même d'un réalisateur suivi.
    @Test
    fun `une tuile sans tmdb_id ne porte aucune marque`() {
        val reperes = ReperesSuivis(realisateurs = setOf("peter jackson"))

        assertNull(reperes.marque(tuileEnCours(null, "Peter Jackson")))
    }

    // Seules les filmographies arrivées comptent. Mutation : prendre toutes les sagas suivies sans
    // regarder l'état de leur filmographie casse cette assertion.
    @Test
    fun `une filmographie en attente ou indisponible ne contribue aucun film`() {
        val ui = SuivisUi(
            sagas = SuiviState(
                entites = listOf(saga(1), saga(2), saga(3)),
                filmographies = mapOf(
                    1 to EtatFilmographie.Pret(listOf(filmSuivi(10), filmSuivi(11))),
                    2 to EtatFilmographie.EnAttente,
                    3 to EtatFilmographie.Indisponible,
                ),
            ),
        )

        assertEquals(setOf(10, 11), reperesSuivis(ui).filmsDeSagas)
    }

    @Test
    fun `des suivis vides donnent des reperes vides`() {
        assertEquals(ReperesSuivis(), reperesSuivis(SuivisUi()))
    }
}
