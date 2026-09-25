package fr.mediatheque.journal.ui.suivis

import fr.mediatheque.journal.FakeJournalApi.Companion.filmDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Les règles pures de la liste des Suivis (rétrospectives et cycles, 25 septembre 2026). */
class SuivisEtatsTest {
    private val jeudi = LocalDate.parse("2026-09-25")

    // Mutation : accorder à `>= 1` écrirait « 1 rétrospectives » ; ne pas accorder du tout,
    // « 2 cycle ».
    @Test
    fun `compteSuivis accorde chaque nom`() {
        assertEquals("5 rétrospectives · 2 cycles", compteSuivis(5, 2))
        assertEquals("1 rétrospective · 0 cycle", compteSuivis(1, 0))
        assertEquals("0 rétrospective · 1 cycle", compteSuivis(0, 1))
    }

    // Le plus récent des visionnages, pas le premier de la liste ; `ajoute_le` tronqué au jour
    // quand rien n'est vu. Mutation : `minOrNull` rendrait « 2026-07-01 ».
    @Test
    fun `derniereActivite prend le visionnage le plus recent, sinon l ajout`() {
        val films = listOf(
            filmDe(1, "A", 1990, entryId = "e-1", finishedAt = "2026-07-01"),
            filmDe(2, "B", 1991, entryId = "e-2", finishedAt = "2026-09-20"),
            filmDe(3, "C", 1992),
        )
        assertEquals("2026-09-20", derniereActivite("2026-01-02T10:00:00.000Z", films))
        assertEquals("2026-01-02", derniereActivite("2026-01-02T10:00:00.000Z", listOf(filmDe(3, "C", 1992))))
        assertEquals("2026-01-02", derniereActivite("2026-01-02T10:00:00.000Z", null))
    }

    // Un visionnage d'hier passe devant un ajout d'avant-hier ; une filmographie pas encore là se
    // classe sur son ajout ; l'égalité garde l'ordre du back. Mutation : `sortedBy` (croissant)
    // inverserait tout ; ignorer `filmographies` classerait Kubrick sur son seul ajout, en dernier.
    @Test
    fun `trierParActivite, du plus recemment actif au plus ancien, stable`() {
        val kubrick = EntiteSuivie(240, "Stanley Kubrick", null, "2026-03-01T00:00:00.000Z")
        val nolan = EntiteSuivie(525, "Christopher Nolan", null, "2026-09-23T00:00:00.000Z")
        val miyazaki = EntiteSuivie(608, "Hayao Miyazaki", null, "2026-06-01T00:00:00.000Z")
        val varda = EntiteSuivie(700, "Agnès Varda", null, "2026-06-01T12:00:00.000Z")
        val filmographies = mapOf(
            240 to EtatFilmographie.Pret(listOf(filmDe(1, "Lolita", 1962, entryId = "e", finishedAt = "2026-09-24"))),
            525 to EtatFilmographie.EnAttente,
            608 to EtatFilmographie.Indisponible,
            700 to EtatFilmographie.Pret(emptyList()),
        )
        assertEquals(
            listOf("Stanley Kubrick", "Christopher Nolan", "Hayao Miyazaki", "Agnès Varda"),
            trierParActivite(listOf(miyazaki, varda, nolan, kubrick), filmographies).map { it.nom },
        )
    }

    // Même règle que `filmographieTerminee`, mais une liste vide n'est pas bouclée. Mutation :
    // oublier `isNotEmpty()` rendrait vrai le dernier cas.
    @Test
    fun `entiteBouclee, tout vu ou introuvable, jamais vide`() {
        assertTrue(entiteBouclee(listOf(filmDe(1, "A", 1990, entryId = "e"), filmDe(2, "B", 1991, introuvable = true))))
        assertFalse(entiteBouclee(listOf(filmDe(1, "A", 1990, entryId = "e"), filmDe(2, "B", 1991))))
        assertFalse(entiteBouclee(emptyList()))
    }

    // Les bouclées descendent en section, chacune des deux parts reste triée par activité ; une
    // filmographie en attente reste en cours.
    @Test
    fun `repartirSuivis met les bouclees a part`() {
        val a = EntiteSuivie(1, "A", null, "2026-01-01")
        val b = EntiteSuivie(2, "B", null, "2026-02-01")
        val c = EntiteSuivie(3, "C", null, "2026-03-01")
        val filmographies = mapOf(
            1 to EtatFilmographie.Pret(listOf(filmDe(1, "X", 1990, entryId = "e", finishedAt = "2026-09-01"))),
            2 to EtatFilmographie.Pret(listOf(filmDe(2, "Y", 1990))),
            3 to EtatFilmographie.EnAttente,
        )
        val (enCours, bouclees) = repartirSuivis(listOf(a, b, c), filmographies)
        assertEquals(listOf("C", "B"), enCours.map { it.nom })
        assertEquals(listOf("A"), bouclees.map { it.nom })
    }

    @Test
    fun `sousLigneCarte, vu recemment`() = assertEquals(
        "4 sur 12 · vu il y a 3 jours",
        sousLigneCarte(SourceSuivi.REALISATEURS, 4, 12, "2026-09-22", "2026-01-01T00:00:00.000Z", jeudi, bouclee = false),
    )

    @Test
    fun `sousLigneCarte, rien de vu, la date d ajout`() = assertEquals(
        "0 sur 12 · ajouté le 12 septembre 2026",
        sousLigneCarte(SourceSuivi.SAGAS, 0, 12, null, "2026-09-12T08:00:00.000Z", jeudi, bouclee = false),
    )

    // Accordé à la puce : une rétrospective est « bouclée », un cycle « bouclé ».
    @Test
    fun `sousLigneCarte, bouclee, datee du dernier visionnage`() {
        assertEquals(
            "6 sur 6 · bouclée le 2 août 2026",
            sousLigneCarte(SourceSuivi.REALISATEURS, 6, 6, "2026-08-02", "2026-01-01", jeudi, bouclee = true),
        )
        assertEquals(
            "3 sur 3 · bouclé le 2 août 2026",
            sousLigneCarte(SourceSuivi.SAGAS, 3, 3, "2026-08-02", "2026-01-01", jeudi, bouclee = true),
        )
        // Tout introuvable, rien de vu : datée de l'ajout.
        assertEquals(
            "0 sur 2 · bouclée le 1er janvier 2026",
            sousLigneCarte(SourceSuivi.REALISATEURS, 0, 2, null, "2026-01-01", jeudi, bouclee = true),
        )
    }

    @Test
    fun `sousLigneCarte, sans aucune date, le compte seul`() =
        assertEquals("0 sur 3", sousLigneCarte(SourceSuivi.SAGAS, 0, 3, null, "", jeudi, bouclee = false))
}
