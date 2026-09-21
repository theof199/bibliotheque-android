package fr.mediatheque.journal.ui.frise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La séance d'une année (brief du 21 septembre 2026, « la séance ») : les candidats de
 * remplacement, le corps envoyé, l'état de la zone séance, la séance la plus récente et les
 * séances passées. Fonctions pures, sans réseau ni `ViewModel`.
 */
class SeanceEtatsTest {

    private fun bobine(tmdbId: Int, etat: String) = BobineUi(tmdbId, "Bobine $tmdbId", 9, null, null, etat)

    private fun filmDeSalle(id: String, tmdbId: Int, etat: String, programme: ProgrammeUi? = null) = FilmSalleUi(
        id = id,
        rang = 1,
        tmdbId = tmdbId,
        title = "Film $tmdbId",
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

    private fun salle(nom: String, vararg films: FilmSalleUi) =
        SalleUi("s-$nom", 1, nom, "Ce qu'il ne fallait pas manquer", null, false, false, films.toList())

    private fun seanceFilm(filmId: String = "f-long", tmdbId: Int = 1, etat: String = "a_demander", plexUrl: String? = null, bobine: SeanceBobineUi? = null) =
        SeanceFilmUi(filmId, tmdbId, "Film $tmdbId", null, "Les essentiels", etat, plexUrl, bobine)

    private fun seance(id: String = "sc-1", rang: Int = 1, statut: String = "proposee", court: SeanceFilmUi? = null) =
        SeanceUi(id, rang, statut, "2026-09-21T22:00:00.000Z", "Une anecdote.", seanceFilm(), court)

    // Jamais un programme, jamais un vu ni un introuvable (décision 3 du brief).
    // Mutation : retirer un des deux filtres d'état laisserait passer un film déjà vu ou introuvable.
    @Test
    fun `candidatsSeanceLong ecarte les programmes, les vus et les introuvables`() {
        val ordinaire = filmDeSalle("f1", 1, "a_demander")
        val vu = filmDeSalle("f2", 2, "vu")
        val introuvable = filmDeSalle("f3", 3, "introuvable")
        val programme = filmDeSalle("f4", 4, "a_demander", ProgrammeUi(9, listOf(bobine(41, "a_demander"))))

        val groupes = candidatsSeanceLong(listOf(salle("Les essentiels", ordinaire, vu, introuvable, programme)))

        assertEquals(listOf(1), groupes.single().candidats.map { it.tmdbId })
    }

    // Groupés par salle, une salle sans candidat n'apparaît pas.
    @Test
    fun `candidatsSeanceLong groupe par salle et omet les salles sans candidat`() {
        val avecCandidat = filmDeSalle("f1", 1, "a_demander")
        val toutVu = filmDeSalle("f2", 2, "vu")

        val groupes = candidatsSeanceLong(listOf(salle("Salle A", avecCandidat), salle("Salle B", toutVu)))

        assertEquals(listOf("Salle A"), groupes.map { it.salle })
    }

    // Plex d'abord, puis demandé, puis à demander (décision 3).
    // Mutation : trier par tmdbId ou par ordre d'entrée plutôt que par état ferait échouer cet ordre précis.
    @Test
    fun `candidatsSeanceLong trie Plex puis demande puis a demander`() {
        val aDemander = filmDeSalle("f1", 1, "a_demander")
        val surLePlex = filmDeSalle("f2", 2, "sur_le_plex")
        val demande = filmDeSalle("f3", 3, "demande")

        val groupes = candidatsSeanceLong(listOf(salle("Salle A", aDemander, surLePlex, demande)))

        assertEquals(listOf(2, 3, 1), groupes.single().candidats.map { it.tmdbId })
    }

    // Les programmes et leurs bobines non vus, jamais les vus.
    @Test
    fun `candidatsSeanceCourt garde les programmes et bobines non vus`() {
        val programme = ProgrammeUi(9, listOf(bobine(11, "vu"), bobine(12, "a_demander"), bobine(13, "sur_le_plex")))
        val filmProgramme = filmDeSalle("f-prog", 10, "a_demander", programme)

        val groupes = candidatsSeanceCourt(listOf(salle("Salle A", filmProgramme)))
        val candidats = groupes.single().candidats

        // Le programme lui-même (10), puis ses bobines non vues, dans l'ordre du programme (12 et 13) — jamais la bobine vue (11).
        assertEquals(listOf(10, 12, 13), candidats.map { it.tmdbId })
        assertTrue(candidats[0] is CandidatSeance.Film)
        assertTrue(candidats[1] is CandidatSeance.Bobine)
    }

    // Un programme entièrement vu n'est plus un candidat (ni lui, ni aucune bobine puisqu'aucune ne reste non vue).
    @Test
    fun `candidatsSeanceCourt ecarte un programme entierement vu`() {
        val programme = ProgrammeUi(9, listOf(bobine(11, "vu"), bobine(12, "vu")))
        val filmProgramme = filmDeSalle("f-prog", 10, "sur_le_plex", programme)

        val groupes = candidatsSeanceCourt(listOf(salle("Salle A", filmProgramme)))

        assertTrue(groupes.isEmpty())
    }

    // Jamais un introuvable non plus, comme le long (corrigé le 21 septembre 2026 : le brief disait
    // à tort de le garder ; le back refuse un introuvable en 400, programme comme bobine) — chacun
    // sur son propre état, comme la ligne du programme et chaque bobine ont chacune le leur (marquer
    // un programme introuvable, via son propre `tmdb_id`, ne change rien à l'état de ses bobines).
    // Mutation : retirer le filtre sur « introuvable » (programme ou bobine) ferait réapparaître un
    // candidat que le back rejetterait.
    @Test
    fun `candidatsSeanceCourt ecarte aussi les introuvables, programme et bobines, chacun sur son propre etat`() {
        val programmeAvecBobineIntrouvable = ProgrammeUi(9, listOf(bobine(11, "vu"), bobine(12, "a_demander"), bobine(13, "introuvable")))
        val filmProgramme = filmDeSalle("f-prog", 10, "a_demander", programmeAvecBobineIntrouvable)
        val programmeIntrouvable = ProgrammeUi(9, listOf(bobine(21, "a_demander")))
        val filmIntrouvable = filmDeSalle("f-prog2", 20, "introuvable", programmeIntrouvable)

        val groupes = candidatsSeanceCourt(listOf(salle("Salle A", filmProgramme, filmIntrouvable)))
        val candidats = groupes.single().candidats

        // Le programme 10 (a_demander) et sa bobine 12 (jamais la bobine introuvable 13) ; le
        // programme 20 est lui-même introuvable — sa ligne de programme disparaît — mais sa bobine
        // 21 garde son propre état (a_demander) et reste un candidat.
        assertEquals(listOf(10, 12, 21), candidats.map { it.tmdbId })
    }

    // Le corps envoyé : `film_id` seul pour un film ou un programme, `+ bobine_tmdb_id` pour une bobine.
    // Mutation : envoyer `bobine_tmdb_id` pour un film serait rejeté en 400 par le back.
    @Test
    fun `corpsRemplacementSeance envoie film_id seul pour un film, + bobine_tmdb_id pour une bobine`() {
        val corpsFilm = corpsRemplacementSeance("long", CandidatSeance.Film("f1", 1, "Un film", null, "a_demander"))
        assertEquals("long", corpsFilm.morceau)
        assertEquals("f1", corpsFilm.film_id)
        assertNull(corpsFilm.bobine_tmdb_id)

        val corpsBobine = corpsRemplacementSeance("court", CandidatSeance.Bobine("f2", 22, "Une bobine", null, "a_demander"))
        assertEquals("court", corpsBobine.morceau)
        assertEquals("f2", corpsBobine.film_id)
        assertEquals(22, corpsBobine.bobine_tmdb_id)
    }

    // La plus récente : le rang le plus élevé, pas le dernier de la liste ni le premier.
    @Test
    fun `seanceRecente rend celle du rang le plus eleve`() {
        val s1 = seance("sc-1", rang = 1)
        val s3 = seance("sc-3", rang = 3)
        val s2 = seance("sc-2", rang = 2)

        assertEquals(s3, seanceRecente(listOf(s1, s3, s2)))
        assertNull(seanceRecente(emptyList()))
    }

    // « Séances passées » : prises ou ignorées, jamais la plus récente même si elle l'est aussi.
    // Mutation : ne pas exclure la plus récente la ferait apparaître deux fois (carte + « passées »).
    @Test
    fun `seancesPassees exclut la plus recente et les proposees, garde prise et ignoree`() {
        val recentePrise = seance("sc-3", rang = 3, statut = "prise")
        val ancienneIgnoree = seance("sc-1", rang = 1, statut = "ignoree")
        val ancienneProposee = seance("sc-2", rang = 2, statut = "proposee")

        val passees = seancesPassees(listOf(ancienneIgnoree, ancienneProposee, recentePrise))

        assertEquals(listOf("sc-1"), passees.map { it.id })
    }

    // L'état de la zone séance : bouton (rien, ou la plus récente ignorée), en cours (prime sur
    // tout), carte proposée, carte prise (décision 1-2, corrigée le 21 septembre 2026 : « ignorer »
    // n'est pas terminal — la carte disparaît et le bouton revient aussitôt, comme s'il n'y avait
    // pas de séance).
    // Mutation : ne pas faire primer `seanceEnCours` afficherait la carte d'une séance déjà
    // dépassée pendant qu'une nouvelle composition est en vol.
    @Test
    fun `etatZoneSeance suit seanceEnCours puis le statut de la plus recente`() {
        assertEquals(EtatZoneSeance.BOUTON, etatZoneSeance(seanceEnCours = false, seances = emptyList()))
        assertEquals(EtatZoneSeance.EN_COURS, etatZoneSeance(seanceEnCours = true, seances = emptyList()))
        assertEquals(EtatZoneSeance.EN_COURS, etatZoneSeance(seanceEnCours = true, seances = listOf(seance(statut = "proposee"))))
        assertEquals(EtatZoneSeance.CARTE_PROPOSEE, etatZoneSeance(seanceEnCours = false, seances = listOf(seance(statut = "proposee"))))
        assertEquals(EtatZoneSeance.CARTE_PRISE, etatZoneSeance(seanceEnCours = false, seances = listOf(seance(statut = "prise"))))
    }

    // « Ignorer » n'est pas terminal : le bouton « Composer une séance » revient, exactement comme
    // s'il n'y avait jamais eu de séance — jamais « rien » (mutation prouvée : remettre `RIEN` sur
    // le cas « ignoree » fait échouer cette assertion).
    @Test
    fun `etatZoneSeance rend le bouton quand la plus recente est ignoree, comme si aucune n'existait`() {
        assertEquals(
            etatZoneSeance(seanceEnCours = false, seances = emptyList()),
            etatZoneSeance(seanceEnCours = false, seances = listOf(seance(statut = "ignoree"))),
        )
        assertEquals(EtatZoneSeance.BOUTON, etatZoneSeance(seanceEnCours = false, seances = listOf(seance(statut = "ignoree"))))
    }

    // Toujours un texte, même sur « à demander » (contrairement à `etiquetteEtatFilm`, qui rend
    // `null` pour laisser l'affiche en sépia parler d'elle-même sur l'étagère).
    @Test
    fun `etiquetteEtatSeanceFilm rend toujours un texte, y compris pour a demander`() {
        assertEquals("sur ton Plex", etiquetteEtatSeanceFilm("sur_le_plex"))
        assertEquals("demandé", etiquetteEtatSeanceFilm("demande"))
        assertEquals("à demander", etiquetteEtatSeanceFilm("a_demander"))
        assertEquals("introuvable", etiquetteEtatSeanceFilm("introuvable"))
    }
}
