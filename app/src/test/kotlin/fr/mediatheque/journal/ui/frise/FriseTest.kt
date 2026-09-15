package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.api.dto.PlexResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `construireFrise` (`FriseViewModel.kt`) — fonction pure du chantier « La
 * Frise et Ensuite » (15 septembre 2026), testée sans réseau ni `ViewModel`.
 */
class FriseTest {
    private fun vu(finishedAt: String, externalId: String) =
        FakeJournalApi.item("m-$externalId", finishedAt, null, emptyList(), null, externalId = externalId)

    private fun aVoir(tmdbId: Int, titre: String, annee: Int?, demandeLe: String = "2026-09-10T20:12:00.000Z") =
        PlexFilm(tmdb_id = tmdbId, title = titre, year = annee, demande_le = demandeLe)

    // Un film du Plex déjà journalisé (tmdb_id == media.external_id) n'est plus « à voir ».
    // Mutation : comparer par titre plutôt que par tmdb_id, ou ne jamais filtrer, fait échouer
    // l'assertion sur la taille de `aVoir`.
    @Test
    fun `un film du plex deja dans le journal est ecarte, par tmdb_id`() {
        val journal = listOf(vu("2026-01-10", "27205"))
        val plex = PlexResponse(
            configure = true,
            films = listOf(aVoir(27205, "Inception", 2010), aVoir(912649, "Les Gardiens de la nuit", 2026)),
        )

        val frise = construireFrise(journal, plex)

        val annee2026 = frise.annees.first { it.annee == 2026 }
        assertEquals(listOf(912649), annee2026.aVoir.map { it.tmdb_id })
        // Aucune ligne 2010 : le seul film candidat de cette année a été écarté, et l'année ne
        // porte par ailleurs aucun vu.
        assertEquals(null, frise.annees.find { it.annee == 2010 })
    }

    // Les années se suivent en ordre croissant, « Sans année » toujours en dernier — jamais
    // trié parmi les autres. Mutation : trier par ordre d'apparition, ou mettre « Sans année »
    // en tête, fait échouer l'égalité de séquence.
    @Test
    fun `les annees sont triees croissant, sans annee toujours en fin`() {
        val journal = listOf(vu("2024-03-01", "1"), vu("2020-03-01", "2"), vu("", "3"))
        val plex = PlexResponse(configure = true, films = listOf(aVoir(4, "Film 2022", 2022)))

        val frise = construireFrise(journal, plex)

        assertEquals(listOf(2020, 2022, 2024, null), frise.annees.map { it.annee })
    }

    // Une année sans le moindre vu ni le moindre à-voir n'apparaît pas : pas de trou comblé
    // dans la frise. Mutation : générer toutes les années entre le minimum et le maximum ferait
    // apparaître 2021 et 2023 ici.
    @Test
    fun `une annee sans rien n apparait pas dans la liste`() {
        val journal = listOf(vu("2020-03-01", "1"), vu("2024-03-01", "2"))
        val frise = construireFrise(journal, PlexResponse(configure = true))

        assertEquals(listOf(2020, 2024), frise.annees.map { it.annee })
    }

    // Un vu sans date exploitable et un à-voir sans année rejoignent le même groupe « Sans
    // année ». Mutation : les répartir dans deux groupes nuls distincts, ou les faire
    // disparaître, casse le compte de l'un ou l'autre.
    @Test
    fun `les vus et les a-voir sans annee rejoignent le meme groupe Sans annee`() {
        val journal = listOf(vu("date-invalide", "1"))
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
        val journal = listOf(vu("2019-01-01", "1"))
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
        val frise = construireFrise(listOf(vu("2020-01-01", "1")), PlexResponse(configure = true))
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
}
