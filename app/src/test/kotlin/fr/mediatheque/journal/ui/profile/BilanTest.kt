package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.FakeJournalApi.Companion.filmDe
import fr.mediatheque.journal.FakeJournalApi.Companion.item
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.suivis.EntiteSuivie
import fr.mediatheque.journal.ui.suivis.EtatFilmographie
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Le Bilan du profil (brief du 15 septembre 2026) : fonctions pures, sans
 * réseau ni `ViewModel`. `bilanJournal` est éprouvée contre un journal écrit
 * à la main ; `bilanSuivi` est **la même fonction pour les réalisateurs et
 * les sagas** (généralisation du même brief) — un seul jeu de tests lui
 * suffit, elle ne sait pas d'où viennent ses entités.
 */
class BilanTest {
    // ---------------------------------------------------------------------
    // bilanJournal
    // ---------------------------------------------------------------------

    // Un même film revu deux fois (rewatch) ne compte qu'une fois — comme les deux chiffres du
    // haut du profil (`GET /stats`), avec lesquels le Bilan doit s'accorder. Mutation : compter
    // les entrées de journal au lieu des `media_id` distincts donnerait 3 au lieu de 2.
    @Test
    fun `bilanJournal compte les films vus, distincts`() {
        val journal = listOf(
            item("inception", "2024-11-20", 9, emptyList(), null, id = "e-1"),
            item("inception", "2019-03-02", 6, emptyList(), null, id = "e-2"),
            item("interstellar", "2025-01-04", 7, emptyList(), null, id = "e-3"),
        )

        assertEquals(2, bilanJournal(journal, anneeCourante = 2026).filmsVus)
    }

    // « Cette année » compare l'année de `finished_at`, pas celle de sortie du film — revoir un
    // vieux film cette année le compte, voir un film récent l'an dernier ne le compte pas.
    // Mutation : comparer `media.year` à la place ferait sortir 0 (aucun des deux films n'est
    // sorti en 2026).
    @Test
    fun `bilanJournal compte a part les films vus cette annee`() {
        val journal = listOf(
            item("inception", "2026-02-10", 9, emptyList(), null, id = "e-1", year = 2010),
            item("interstellar", "2019-01-04", 7, emptyList(), null, id = "e-2", year = 2014),
        )

        val bilan = bilanJournal(journal, anneeCourante = 2026)
        assertEquals(2, bilan.filmsVus)
        assertEquals(1, bilan.filmsVusCetteAnnee)
    }

    // La réaction `en_salle` (§ « Au ciné ») décide d'une séance, pas le nombre d'entrées — un
    // visionnage sans cette réaction n'en est pas une. Mutation : compter toutes les entrées
    // donnerait 3 au lieu de 2 ; oublier le filtre « cette année » donnerait 2 au lieu de 1.
    @Test
    fun `bilanJournal compte les seances en salle, et celles de cette annee`() {
        val journal = listOf(
            item("a", "2026-03-01", null, listOf(Reactions.EN_SALLE), null, id = "e-1"),
            item("b", "2024-06-01", null, listOf(Reactions.EN_SALLE), null, id = "e-2"),
            item("c", "2026-04-01", null, emptyList(), null, id = "e-3"),
        )

        val bilan = bilanJournal(journal, anneeCourante = 2026)
        assertEquals(2, bilan.seancesEnSalle)
        assertEquals(1, bilan.seancesEnSalleCetteAnnee)
    }

    // La note moyenne ne porte que sur les films notés — un visionnage sans note ne doit pas
    // tirer la moyenne vers le bas comme s'il valait zéro. Une décimale, arrondie (8,25 → 8,3, pas
    // 8,2). Mutation : inclure les notes nulles comme des zéros donnerait 4,67 au lieu de 7 ; ne
    // pas arrondir laisserait 7.333333…
    @Test
    fun `bilanJournal ne moyenne que les films notes, une decimale`() {
        val journal = listOf(
            item("a", "2026-01-01", 6, emptyList(), null, id = "e-1"),
            item("b", "2026-01-02", 8, emptyList(), null, id = "e-2"),
            item("c", "2026-01-03", null, emptyList(), null, id = "e-3"),
        )

        assertEquals(7.0, bilanJournal(journal, anneeCourante = 2026).noteMoyenne)
    }

    @Test
    fun `bilanJournal arrondit la note moyenne a une decimale`() {
        val journal = listOf(
            item("a", "2026-01-01", 8, emptyList(), null, id = "e-1"),
            item("b", "2026-01-02", 9, emptyList(), null, id = "e-2"),
            item("c", "2026-01-03", 8, emptyList(), null, id = "e-3"),
        )
        // (8 + 9 + 8) / 3 = 8,333… → 8,3.
        assertEquals(8.3, bilanJournal(journal, anneeCourante = 2026).noteMoyenne)
    }

    // Nul si aucun film n'est noté — jamais zéro, qui serait une moyenne, pas une absence de
    // notes.
    @Test
    fun `bilanJournal rend une note moyenne nulle sans aucune note`() {
        val journal = listOf(item("a", "2026-01-01", null, emptyList(), null, id = "e-1"))
        assertNull(bilanJournal(journal, anneeCourante = 2026).noteMoyenne)
    }

    // L'exemple du brief, à la lettre : « 1920 → 2020, 9 décennies sur 11 ». Neuf décennies avec
    // au moins un film, deux trous (2000 et 2010) dans la fourchette 1920–2020. Mutation : ne pas
    // compter les décennies vides dans `total` donnerait 9 sur 9 ; les compter dans `couvertes`
    // donnerait 11 sur 11.
    @Test
    fun `decenniesCouvertes rend l exemple du brief`() {
        val annees = listOf(1925, 1938, 1942, 1957, 1963, 1974, 1988, 1991, 2024)
        val decennies = decenniesCouvertes(annees)

        assertEquals(1920, decennies.premiere)
        assertEquals(2020, decennies.derniere)
        assertEquals(9, decennies.couvertes)
        assertEquals(11, decennies.total)
    }

    // Une seule année : une seule décennie, couvrant tout le total — pas de division par zéro, ni
    // de span à côté.
    @Test
    fun `decenniesCouvertes avec une seule annee rend une seule decennie`() {
        val decennies = decenniesCouvertes(listOf(2015))
        assertEquals(2010, decennies.premiere)
        assertEquals(2010, decennies.derniere)
        assertEquals(1, decennies.couvertes)
        assertEquals(1, decennies.total)
    }

    // `bilanJournal` délègue à `decenniesCouvertes` sur les années connues, et rend nul sans
    // aucune. Mutation : oublier de filtrer les films sans année ferait planter sur `min()`
    // d'une liste vide dès qu'un seul film en a une.
    @Test
    fun `bilanJournal rend les decennies nulles sans aucune annee connue`() {
        val journal = listOf(item("a", "2026-01-01", null, emptyList(), null, id = "e-1", year = null))
        assertNull(bilanJournal(journal, anneeCourante = 2026).decennies)
    }

    // Le plus ancien film vu, par année de sortie — pas par date de visionnage. Mutation : trier
    // par `finished_at` au lieu de `media.year` élirait « Le Voyage de Chihiro » (vu en premier)
    // au lieu de « Metropolis » (sorti le premier).
    @Test
    fun `bilanJournal rend le plus ancien film vu, par annee de sortie`() {
        val journal = listOf(
            item("chihiro", "2020-01-01", null, emptyList(), null, id = "e-1", title = "Le Voyage de Chihiro", year = 2001),
            item("metropolis", "2025-06-01", null, emptyList(), null, id = "e-2", title = "Metropolis", year = 1927),
        )

        val plusAncien = bilanJournal(journal, anneeCourante = 2026).plusAncien
        assertEquals("Metropolis", plusAncien?.titre)
        assertEquals(1927, plusAncien?.annee)
    }

    // Un journal vide : tout à zéro ou nul, jamais une exception.
    @Test
    fun `bilanJournal ne casse pas sur un journal vide`() {
        val bilan = bilanJournal(emptyList(), anneeCourante = 2026)
        assertEquals(0, bilan.filmsVus)
        assertEquals(0, bilan.filmsVusCetteAnnee)
        assertEquals(0, bilan.seancesEnSalle)
        assertNull(bilan.noteMoyenne)
        assertNull(bilan.decennies)
        assertNull(bilan.plusAncien)
    }

    // ---------------------------------------------------------------------
    // filmographieTerminee / bilanSuivi — génériques aux deux sources
    // ---------------------------------------------------------------------

    // La règle du brief, à la lettre : « un réalisateur avec un introuvable non vu et tout le
    // reste vu compte terminé ». Mutation : exiger `vu != null` sur tous les films, introuvables
    // compris, ferait échouer cette assertion précise.
    @Test
    fun `filmographieTerminee compte un introuvable non vu comme termine`() {
        val films = listOf(
            filmDe(1, "Vu", 2000, entryId = "e-1"),
            filmDe(2, "Introuvable jamais vu", 2001, introuvable = true),
        )
        assertTrue(filmographieTerminee(films))
    }

    // À l'inverse, un seul film ni vu ni introuvable suffit à ne pas être terminé.
    @Test
    fun `filmographieTerminee est fausse des qu un film retrouvable n est pas vu`() {
        val films = listOf(filmDe(1, "Vu", 2000, entryId = "e-1"), filmDe(2, "Pas vu", 2001))
        assertEquals(false, filmographieTerminee(films))
    }

    // Une filmographie vide est terminée : rien n'y reste à voir. Mutation : `films.isNotEmpty()
    // && films.all { ... }` la ferait échouer.
    @Test
    fun `filmographieTerminee est vraie pour une filmographie vide`() {
        assertTrue(filmographieTerminee(emptyList()))
    }

    // `bilanSuivi` : le compte total, et les terminés parmi ceux dont la filmographie a répondu.
    // Une en attente ne compte ni comme terminée, ni comme non terminée : elle est simplement
    // ignorée du numérateur, sans faire planter la fonction. Mutation : caster sans `as?` lèverait
    // sur l'entrée `EnAttente` ; compter les non-`Pret` comme terminées ferait 3 au lieu de 2.
    @Test
    fun `bilanSuivi compte les suivis et les termines, les non pretes exclues du numerateur`() {
        val nolan = EntiteSuivie(525, "Christopher Nolan", null)
        val kubrick = EntiteSuivie(240, "Stanley Kubrick", null)
        val varda = EntiteSuivie(1234, "Agnès Varda", null)

        val filmographies = mapOf(
            nolan.tmdbId to EtatFilmographie.Pret(listOf(filmDe(1, "Vu", 2000, entryId = "e-1"))),
            kubrick.tmdbId to EtatFilmographie.Pret(listOf(filmDe(2, "Pas vu", 2001))),
            varda.tmdbId to EtatFilmographie.EnAttente,
        )

        val bilan = bilanSuivi(listOf(nolan, kubrick, varda), filmographies)
        assertEquals(3, bilan.suivis)
        assertEquals(1, bilan.termines)
    }

    // La généralisation, éprouvée avec des sagas (brief du 15 septembre 2026) : `bilanSuivi` ne
    // sait pas d'où viennent ses entités, la même assertion vaut pour l'une ou l'autre source.
    @Test
    fun `bilanSuivi fonctionne a l identique pour des sagas`() {
        val alien = EntiteSuivie(8091, "Alien (Saga)", null)
        val godzilla = EntiteSuivie(9946, "Godzilla (Saga)", null)

        val filmographies = mapOf(
            alien.tmdbId to EtatFilmographie.Pret(
                listOf(filmDe(348, "Alien", 1979, entryId = "e-1"), filmDe(679, "Aliens", 1986, entryId = "e-2")),
            ),
            godzilla.tmdbId to EtatFilmographie.Pret(listOf(filmDe(1, "Godzilla", 1954))),
        )

        val bilan = bilanSuivi(listOf(alien, godzilla), filmographies)
        assertEquals(2, bilan.suivis)
        assertEquals(1, bilan.termines)
    }
}
