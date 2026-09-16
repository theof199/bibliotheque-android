package fr.mediatheque.journal.ui.frise

import androidx.compose.ui.graphics.Color
import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.ui.theme.Corail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `construireFrise`, `construireDecennies` et `couleurDeCase` (`FriseViewModel.kt`) — fonctions
 * pures du chantier « La Frise et Ensuite » (15 septembre 2026), puis « Le calendrier du siècle »
 * (16 septembre 2026), testées sans réseau ni `ViewModel`.
 */
class FriseTest {
    // Tous vus en 2026 : la frise range par annee de SORTIE du film (`media.year`), jamais par
    // date de visionnage. Le 15 septembre 2026, la release montrait une seule ligne « 2026 »
    // avec 51 films : ce qui suit interdit ce retour.
    private fun vu(annee: Int?, externalId: String, titre: String = "Un film") =
        FakeJournalApi.item("m-$externalId", "2026-09-0${externalId.last()}", null, emptyList(), null, externalId = externalId, year = annee, title = titre)

    private fun aVoir(tmdbId: Int, titre: String, annee: Int?, demandeLe: String = "2026-09-10T20:12:00.000Z") =
        PlexFilm(tmdb_id = tmdbId, title = titre, year = annee, demande_le = demandeLe)

    // Un film du Plex déjà journalisé (tmdb_id == media.external_id) n'est plus « à voir ».
    // Mutation : comparer par titre plutôt que par tmdb_id, ou ne jamais filtrer, fait échouer
    // l'assertion sur la taille de `aVoir`.
    @Test
    fun `un film du plex deja dans le journal est ecarte, par tmdb_id`() {
        val journal = listOf(vu(2010, "27205"))
        val plex = PlexResponse(
            configure = true,
            films = listOf(aVoir(27205, "Inception", 2010), aVoir(912649, "Les Gardiens de la nuit", 2026)),
        )

        val frise = construireFrise(journal, plex)

        val annee2026 = frise.annees.first { it.annee == 2026 }
        assertEquals(listOf(912649), annee2026.aVoir.map { it.tmdb_id })
        // La ligne 2010 porte le vu (Inception, sorti en 2010) et aucun à-voir : le seul
        // candidat du Plex pour cette année est celui qu'on vient d'écarter.
        val annee2010 = frise.annees.first { it.annee == 2010 }
        assertEquals(1, annee2010.vus.size)
        assertEquals(emptyList<Int>(), annee2010.aVoir.map { it.tmdb_id })
    }

    // Les années se suivent en ordre croissant, « Sans année » toujours en dernier — jamais
    // trié parmi les autres. Mutation : trier par ordre d'apparition, ou mettre « Sans année »
    // en tête, fait échouer l'égalité de séquence.
    @Test
    fun `les annees sont triees croissant, sans annee toujours en fin`() {
        val journal = listOf(vu(2024, "1"), vu(2020, "2"), vu(null, "3"))
        val plex = PlexResponse(configure = true, films = listOf(aVoir(4, "Film 2022", 2022)))

        val frise = construireFrise(journal, plex)

        assertEquals(listOf(2020, 2022, 2024, null), frise.annees.map { it.annee })
    }

    // Une année sans le moindre vu ni le moindre à-voir n'apparaît pas : pas de trou comblé
    // dans la frise. Mutation : générer toutes les années entre le minimum et le maximum ferait
    // apparaître 2021 et 2023 ici.
    @Test
    fun `une annee sans rien n apparait pas dans la liste`() {
        val journal = listOf(vu(2020, "1"), vu(2024, "2"))
        val frise = construireFrise(journal, PlexResponse(configure = true))

        assertEquals(listOf(2020, 2024), frise.annees.map { it.annee })
    }

    // Un vu sans année de sortie et un à-voir sans année rejoignent le même groupe « Sans
    // année ». Mutation : les répartir dans deux groupes nuls distincts, ou les faire
    // disparaître, casse le compte de l'un ou l'autre.
    @Test
    fun `les vus et les a-voir sans annee rejoignent le meme groupe Sans annee`() {
        val journal = listOf(vu(null, "1"))
        val plex = PlexResponse(configure = true, films = listOf(aVoir(2, "Film sans annee", null)))

        val frise = construireFrise(journal, plex)

        val sansAnnee = frise.annees.last()
        assertEquals(null, sansAnnee.annee)
        assertEquals(1, sansAnnee.vus.size)
        assertEquals(1, sansAnnee.aVoir.size)
    }

    // `anneeEnCours` est la plus ancienne année qui a au moins un à-voir — pas la plus ancienne
    // année tout court, et pas celle qui en a le plus. Mutation : prendre la première année de
    // la liste sans regarder `aVoir` retournerait 2019 ; prendre la dernière retournerait 2026.
    @Test
    fun `anneeEnCours est la plus ancienne annee avec au moins un a-voir`() {
        val journal = listOf(vu(2019, "1"))
        val plex = PlexResponse(
            configure = true,
            films = listOf(aVoir(10, "Ancien a voir", 2021), aVoir(11, "Recent a voir", 2026)),
        )

        val frise = construireFrise(journal, plex)

        assertEquals(2021, frise.anneeEnCours)
    }

    // Sans le moindre à-voir, `anneeEnCours` est nul — « Tout vu jusqu'ici » côté écran.
    @Test
    fun `anneeEnCours est nul si rien n est a voir`() {
        val frise = construireFrise(listOf(vu(2020, "1")), PlexResponse(configure = true))
        assertNull(frise.anneeEnCours)
    }

    // `ensuite` est le plus ancien à-voir, toutes années confondues, année puis titre en cas
    // d'égalité. Mutation : trier par `demande_le` renverrait le film demandé en dernier ici,
    // puisque celui de 2021 a la date de demande la plus récente des deux.
    @Test
    fun `ensuite est le plus ancien a-voir, annee puis titre`() {
        val plex = PlexResponse(
            configure = true,
            films = listOf(
                aVoir(10, "Zebre", 2021, demandeLe = "2026-09-14T00:00:00.000Z"),
                aVoir(11, "Abricot", 2021, demandeLe = "2026-01-01T00:00:00.000Z"),
                aVoir(12, "Le plus recent", 2025),
            ),
        )

        val frise = construireFrise(emptyList(), plex)

        // Même année (2021) pour les deux premiers : le titre départage, "Abricot" avant "Zebre".
        assertEquals(11, frise.ensuite?.tmdb_id)
    }

    // Sans le moindre à-voir, `ensuite` est nul — la ligne « Ensuite » de l'accueil doit alors
    // disparaître, pas s'afficher vide.
    @Test
    fun `ensuite est nul si rien n est a voir`() {
        val frise = construireFrise(emptyList(), PlexResponse(configure = true))
        assertNull(frise.ensuite)
    }

    // `couleurDeCase` (calendrier de la Frise, 16 septembre 2026) : nulle pour 0 (l'appelant pose
    // alors le fond et le liseré par défaut), puis quatre paliers jusqu'au corail plein à 5 et
    // plus. Mutation : décaler une seule borne (`vus <= 4` en `vus < 4`, par exemple) fait
    // échouer l'assertion sur 4 sans toucher à celle sur 3, ou l'inverse.
    @Test
    fun `couleurDeCase suit ses quatre paliers, aux bornes`() {
        assertNull(couleurDeCase(0))
        assertEquals(Color(0xFF5A2E27), couleurDeCase(1))
        assertEquals(Color(0xFF93412F), couleurDeCase(2))
        assertEquals(Color(0xFFC9553E), couleurDeCase(3))
        assertEquals(Color(0xFFC9553E), couleurDeCase(4))
        assertEquals(Corail, couleurDeCase(5))
        assertEquals(Corail, couleurDeCase(12))
    }

    // Une décennie sans le moindre vu ni à-voir, entre deux décennies peuplées, apparaît quand
    // même dans `construireDecennies` — à la différence de `construireFrise`, qui omet les années
    // sans rien : le calendrier a besoin d'une ligne par décennie, sans trou. Mutation : sauter
    // les décennies vides (ne garder que celles qui ont des années) ferait disparaître 2010 de la
    // séquence, et raccourcirait sa liste de dix années à zéro.
    @Test
    fun `une decennie sans rien entre deux decennies peuplees apparait quand meme`() {
        val journal = listOf(vu(2005, "1"))
        val plex = PlexResponse(configure = true, films = listOf(aVoir(9, "Proche", 2024)))
        val frise = construireFrise(journal, plex)

        val decennies = construireDecennies(frise, anneeActuelle = 2026)

        assertEquals(listOf(2000, 2010, 2020), decennies.map { it.decennie })
        val decennie2010 = decennies.first { it.decennie == 2010 }
        assertEquals(0, decennie2010.vus)
        assertEquals(0, decennie2010.aVoir)
        assertEquals(emptyList<FilmDecennie>(), decennie2010.films)
        assertEquals(10, decennie2010.annees.size)
        assertTrue(decennie2010.annees.all { it.vus == 0 && it.aVoir == 0 })
    }

    // Une année postérieure à `anneeActuelle` (un à-voir du Plex pour un film pas encore sorti,
    // par exemple) n'entre dans aucun compte ni dans l'étagère de sa décennie — le calendrier la
    // laisse invisible (le constat, point 1). Mutation : omettre le filtre sur `anneeActuelle`
    // ferait remonter le film de 2027 dans `films` et porterait `aVoir` de la décennie à 1.
    @Test
    fun `une annee posterieure a l annee actuelle n entre dans aucun compte`() {
        val journal = listOf(vu(2020, "1"))
        val plex = PlexResponse(configure = true, films = listOf(aVoir(2, "Film futur", 2027)))
        val frise = construireFrise(journal, plex)

        val decennie2020 = construireDecennies(frise, anneeActuelle = 2026).first { it.decennie == 2020 }

        assertEquals(1, decennie2020.vus)
        assertEquals(0, decennie2020.aVoir)
        assertTrue(decennie2020.films.none { it.annee == 2027 })
        val annee2027 = decennie2020.annees.first { it.annee == 2027 }
        assertEquals(0, annee2027.vus)
        assertEquals(0, annee2027.aVoir)
    }

    // L'étagère d'une décennie (`DecennieFrise.films`) trie ses films par année puis par titre, vus et
    // à-voir mélangés. Mutation : trier par année seule laisserait « Citizen Kane » (un vu, groupé
    // avant les à-voir de son année dans `construireFrise`) devant « Abricot » en 1941, au lieu de
    // l'inverse.
    @Test
    fun `les films de la decennie sont ordonnes par annee puis par titre`() {
        val journal = listOf(vu(1942, "1", titre = "Casablanca"), vu(1941, "2", titre = "Citizen Kane"))
        val plex = PlexResponse(
            configure = true,
            films = listOf(aVoir(3, "Assurance sur la mort", 1944), aVoir(4, "Abricot", 1941)),
        )
        val frise = construireFrise(journal, plex)

        val decennie1940 = construireDecennies(frise, anneeActuelle = 2026).first { it.decennie == 1940 }

        assertEquals(
            listOf("Abricot" to 1941, "Citizen Kane" to 1941, "Casablanca" to 1942, "Assurance sur la mort" to 1944),
            decennie1940.films.map { it.titre to it.annee },
        )
    }

    // `vus` et `aVoir` d'une décennie sont la somme de ceux de ses années. Mutation : ne compter
    // qu'une seule année de la décennie (la première rencontrée, par exemple) ferait retomber ces
    // deux sommes à des valeurs inférieures aux totaux réels.
    @Test
    fun `vus et a voir de la decennie sont la somme de ses annees`() {
        val journal = listOf(vu(1941, "1"), vu(1941, "2"), vu(1943, "3"))
        val plex = PlexResponse(
            configure = true,
            films = listOf(aVoir(10, "A", 1941), aVoir(11, "B", 1945), aVoir(12, "C", 1945)),
        )
        val frise = construireFrise(journal, plex)

        val decennie1940 = construireDecennies(frise, anneeActuelle = 2026).first { it.decennie == 1940 }

        assertEquals(3, decennie1940.vus)
        assertEquals(3, decennie1940.aVoir)
    }
}
