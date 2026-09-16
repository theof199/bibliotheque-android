package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.EssentielVoyage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * La carte du Voyage (brief du 16 septembre 2026, phase 2) : la récompense d'une année, la
 * prochaine étape, la frontière qui avance, les tampons du passeport. Fonctions pures, sans
 * réseau ni `ViewModel`.
 */
class VoyageCarteTest {

    private fun essentiel(rang: Int, tmdbId: Int, etat: String) = EssentielVoyage(
        rang = rang,
        tmdb_id = tmdbId,
        title = "Film $tmdbId",
        year = 1941,
        cover_url = null,
        realisateur = "Un réalisateur",
        pourquoi = "Parce que.",
        etat = etat,
    )

    private fun vu(annee: Int?, id: String, date: String, titre: String = "Un film") =
        FakeJournalApi.item("m-$id", date, null, emptyList(), null, id = "e-$id", title = titre, year = annee)

    // Les trois paliers, à leurs bornes. Mutation : `manquants < 2` au lieu de `<= 2` fait passer
    // deux introuvables de Lion à Ours ; `manquants <= 1` idem ; `manquants == 0 -> LION` casse
    // la première.
    @Test
    fun `recompense donne la Palme sans manquant, le Lion a un ou deux, l'Ours au-dela`() {
        assertEquals(Recompense.PALME, recompense(essentielsTotal = 5, essentielsFaits = 5))
        assertEquals(Recompense.LION, recompense(essentielsTotal = 5, essentielsFaits = 4))
        assertEquals(Recompense.LION, recompense(essentielsTotal = 5, essentielsFaits = 3))
        assertEquals(Recompense.OURS, recompense(essentielsTotal = 5, essentielsFaits = 2))
        assertEquals(Recompense.OURS, recompense(essentielsTotal = 5, essentielsFaits = 0))
    }

    // Une année sans essentiel connu n'est pas une année ratée : rien ne manque, donc la Palme.
    @Test
    fun `recompense sans essentiel du tout reste la Palme`() {
        assertEquals(Recompense.PALME, recompense(essentielsTotal = 0, essentielsFaits = 0))
    }

    // Seule une année FAITE porte une récompense : ouverte ou verrouillée, il n'y a rien à
    // décerner. Mutation : décerner sans regarder le statut ferait sortir une Palme sur l'année
    // en cours, que le HUD compterait.
    @Test
    fun `recompenseDeLAnnee ne decerne rien hors d'une annee faite`() {
        val voyage = VoyageUi(
            parAnnee = mapOf(
                1895 to AnneeVoyage(1895, "faite", essentiels_total = 3, essentiels_faits = 3),
                1896 to AnneeVoyage(1896, "faite", essentiels_total = 3, essentiels_faits = 2),
                1897 to AnneeVoyage(1897, "ouverte", essentiels_total = 3, essentiels_faits = 3),
                1898 to AnneeVoyage(1898, "verrouillee", essentiels_total = 3, essentiels_faits = 0),
            ),
        )

        assertEquals(Recompense.PALME, recompenseDeLAnnee(1895, voyage))
        assertEquals(Recompense.LION, recompenseDeLAnnee(1896, voyage))
        assertNull(recompenseDeLAnnee(1897, voyage))
        assertNull(recompenseDeLAnnee(1898, voyage))
        assertNull(recompenseDeLAnnee(1899, voyage))

        assertEquals("1 Palme · 1 Lion", phraseRecompenses(voyage))
    }

    // Le pluriel, et l'ordre Palme puis Lion puis Ours — jamais l'ordre d'arrivée des années.
    @Test
    fun `phraseRecompenses accorde le pluriel et garde l'ordre des festivals`() {
        val voyage = VoyageUi(
            parAnnee = mapOf(
                1897 to AnneeVoyage(1897, "faite", essentiels_total = 5, essentiels_faits = 0),
                1895 to AnneeVoyage(1895, "faite", essentiels_total = 3, essentiels_faits = 3),
                1896 to AnneeVoyage(1896, "faite", essentiels_total = 3, essentiels_faits = 3),
            ),
        )

        assertEquals("2 Palmes · 1 Ours", phraseRecompenses(voyage))
    }

    // La prochaine étape saute les vus **et** les introuvables, dans l'ordre du rang — pas dans
    // l'ordre de la liste reçue. Mutation : `firstOrNull { it.etat != "vu" }` seul rendrait
    // l'introuvable de rang 2 ; retirer le tri rendrait le rang 4, premier de la liste.
    @Test
    fun `prochaineEtape saute les vus et les introuvables, par rang`() {
        val essentiels = listOf(
            essentiel(rang = 4, tmdbId = 4, etat = "a_trouver"),
            essentiel(rang = 1, tmdbId = 1, etat = "vu"),
            essentiel(rang = 3, tmdbId = 3, etat = "sur_le_plex"),
            essentiel(rang = 2, tmdbId = 2, etat = "introuvable"),
        )

        assertEquals(3, prochaineEtape(essentiels)?.tmdb_id)
    }

    @Test
    fun `prochaineEtape est nulle quand tout est vu ou introuvable`() {
        val essentiels = listOf(
            essentiel(rang = 1, tmdbId = 1, etat = "vu"),
            essentiel(rang = 2, tmdbId = 2, etat = "introuvable"),
        )

        assertNull(prochaineEtape(essentiels))
        assertNull(prochaineEtape(emptyList()))
    }

    // C'est l'année **quittée** qui est bouclée, pas la nouvelle frontière. Mutation :
    // `anneeBouclee = apres` ferait annoncer « 1899 dans la boîte ! » alors qu'on vient d'y
    // entrer.
    @Test
    fun `detecterFrontiereAvancee boucle l'annee quittee, sans decennie dans le meme monde`() {
        val avancee = detecterFrontiereAvancee(avant = 1898, apres = 1899)

        assertEquals(1898, avancee?.anneeBouclee)
        assertNull(avancee?.decennieBouclee)
    }

    // Et la décennie quittée quand la frontière change de monde. Mutation : comparer
    // `decennieApres` à elle-même, ou rendre toujours la décennie, allumerait la marquise et
    // lancerait le générique à chaque année.
    @Test
    fun `detecterFrontiereAvancee boucle la decennie quand la frontiere change de monde`() {
        val avancee = detecterFrontiereAvancee(avant = 1899, apres = 1900)

        assertEquals(1899, avancee?.anneeBouclee)
        assertEquals(1890, avancee?.decennieBouclee)
    }

    // Un premier chargement (rien de mémorisé), une frontière qui ne bouge pas, ou qui recule
    // (une réponse en retard) ne bouclent rien : la snackbar et le générique ne se jouent pas
    // tout seuls à l'ouverture de l'écran.
    @Test
    fun `detecterFrontiereAvancee ne boucle rien sans avancee reelle`() {
        assertNull(detecterFrontiereAvancee(avant = null, apres = 1899))
        assertNull(detecterFrontiereAvancee(avant = 1899, apres = null))
        assertNull(detecterFrontiereAvancee(avant = 1899, apres = 1899))
        assertNull(detecterFrontiereAvancee(avant = 1900, apres = 1899))
    }

    // Une décennie n'est bouclée que si **toutes** ses années connues sont faites. Mutation :
    // `any` au lieu de `all` tamponnerait les années 1900 dès la première année faite.
    @Test
    fun `tamponsPasseport ne tamponne qu'une decennie entierement faite`() {
        val voyage = VoyageUi(
            parAnnee = listOf(
                AnneeVoyage(1895, "faite"), AnneeVoyage(1896, "faite"), AnneeVoyage(1897, "faite"),
                AnneeVoyage(1898, "faite"), AnneeVoyage(1899, "faite"),
                AnneeVoyage(1900, "faite"), AnneeVoyage(1901, "ouverte"), AnneeVoyage(1902, "verrouillee"),
            ).associateBy { it.annee },
        )

        assertEquals(listOf(1890), tamponsPasseport(voyage, emptyList()).map { it.decennie })
    }

    // Les dates viennent du journal (`finished_at`), la sélection des films de leur année de
    // **sortie** : un film de 1897 vu en 2026 entre dans le tampon des années 1890, pas dans
    // celui des années 2020. Mutation : filtrer sur l'année de `finished_at` viderait ce tampon.
    @Test
    fun `tamponsPasseport date ses tampons depuis le journal et trie ses films`() {
        val voyage = VoyageUi(
            parAnnee = listOf(AnneeVoyage(1895, "faite"), AnneeVoyage(1897, "faite")).associateBy { it.annee },
        )
        val journal = listOf(
            vu(1897, "b", "2026-06-04", titre = "Le second"),
            vu(1895, "a", "2026-02-11", titre = "Le premier"),
            vu(1895, "c", "2026-03-20", titre = "Aussi en 1895"),
            vu(2001, "d", "2026-09-01", titre = "Hors de la decennie"),
            vu(null, "e", "2026-09-02", titre = "Sans annee"),
        )

        val tampons = tamponsPasseport(voyage, journal)

        assertEquals(1, tampons.size)
        val tampon = tampons.first()
        assertEquals(1890, tampon.decennie)
        assertEquals("Spectateur des origines", tampon.titreVoyageur)
        assertEquals("2026-02-11", tampon.premiereEntree)
        assertEquals("2026-06-04", tampon.derniereEntree)
        // Par année puis par titre : « Aussi en 1895 » avant « Le premier », tous deux avant 1897.
        assertEquals(
            listOf("Aussi en 1895" to 1895, "Le premier" to 1895, "Le second" to 1897),
            tampon.films.map { it.titre to it.annee },
        )
    }

    // Une décennie bouclée sans aucun film au journal (tous ses essentiels marqués introuvables)
    // se tamponne quand même, sans date — jamais une date inventée ni un tampon manquant.
    @Test
    fun `tamponsPasseport tamponne sans date une decennie sans film au journal`() {
        val voyage = VoyageUi(parAnnee = mapOf(1895 to AnneeVoyage(1895, "faite")))

        val tampon = tamponsPasseport(voyage, listOf(vu(2001, "d", "2026-09-01"))).single()

        assertEquals(1890, tampon.decennie)
        assertNull(tampon.premiereEntree)
        assertNull(tampon.derniereEntree)
        assertEquals(emptyList<FilmGenerique>(), tampon.films)
    }

    // Plusieurs décennies bouclées se suivent dans l'ordre chronologique, jamais dans celui de la
    // table de hachage.
    @Test
    fun `tamponsPasseport rend les decennies dans l'ordre`() {
        val voyage = VoyageUi(
            parAnnee = listOf(AnneeVoyage(1905, "faite"), AnneeVoyage(1895, "faite"), AnneeVoyage(1915, "faite"))
                .associateBy { it.annee },
        )

        assertEquals(listOf(1890, 1900, 1910), tamponsPasseport(voyage, emptyList()).map { it.decennie })
    }
}
