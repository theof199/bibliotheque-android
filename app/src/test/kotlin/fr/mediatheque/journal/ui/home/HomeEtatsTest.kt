package fr.mediatheque.journal.ui.home

import fr.mediatheque.journal.api.dto.Carnet
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalMedia
import fr.mediatheque.journal.api.dto.LogEntry
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.suivis.EnCours
import fr.mediatheque.journal.ui.suivis.EntiteSuivie
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeEtatsTest {
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

    // « Accueil · la porte d'entrée » (planche de Léon validée le 25 septembre 2026) : le fronton.
    // Mutation : oublier `replaceFirstChar` rend « mercredi … », un autre `Locale` rend « Wednesday ».
    @Test fun `le jour en toutes lettres prend une capitale et le mois en francais`() {
        assertEquals("Jeudi 24 septembre", formatJour(LocalDate.of(2026, 9, 24)))
    }

    @Test fun `le premier du mois s ordinalise`() {
        assertEquals("Jeudi 1er octobre", formatJour(LocalDate.of(2026, 10, 1)))
    }

    // Le compte à droite du fronton. Mutation : un `if (thisYear >= 2)` oublié donne « 1 films ».
    @Test fun `le compte est absent tant que stats n a pas repondu`() {
        assertNull(compteAccueil(null))
    }

    @Test fun `zero film de l annee se dit aucun film encore`() {
        assertEquals("Aucun film encore", compteAccueil(0))
    }

    @Test fun `un film de l annee reste au singulier`() {
        assertEquals("1 film cette année", compteAccueil(1))
    }

    @Test fun `plusieurs films de l annee passent au pluriel`() {
        assertEquals("12 films cette année", compteAccueil(12))
    }

    @Test fun `la ligne du court commence par le plus et finit par court`() {
        assertEquals("+ Un chien andalou · court", ligneCourt("Un chien andalou"))
    }

    // La vitrine vide et le bouton rond caché. Mutation : oublier `endReached` ferait clignoter la
    // vitrine vide au premier chargement.
    private val entree = JournalItem(
        LogEntry(id = "e1", media_id = "m1", finished_at = "2026-09-24"),
        JournalMedia(id = "m1", title = "Metropolis"),
        Carnet(),
    )

    @Test fun `le journal est vide quand rien n est venu et la derniere page est lue`() {
        assertTrue(journalVide(emptyList(), endReached = true))
    }

    @Test fun `le journal n est pas vide tant qu une page est en route`() {
        assertFalse(journalVide(emptyList(), endReached = false))
    }

    @Test fun `le journal n est pas vide avec une entree`() {
        assertFalse(journalVide(listOf(entree), endReached = true))
    }
}
