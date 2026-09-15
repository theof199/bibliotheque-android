package fr.mediatheque.journal.ui.realisateurs

import fr.mediatheque.journal.FakeJournalApi.Companion.filmDe
import fr.mediatheque.journal.FakeJournalApi.Companion.realisateur
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Les fonctions pures des réalisateurs (brief du 15 septembre 2026) : ce que compte une ligne,
 * quel film vient ensuite, quel réalisateur est « en cours ». Aucune ne touche au réseau ni à
 * un `ViewModel` ; chacune est le calcul qu'un écran se contenterait de refaire à la main, mal.
 */
class RealisateursTest {
    private val kubrick = listOf(
        filmDe(1, "Les Sentiers de la gloire", 1957, entryId = "e-1", rating = 9),
        filmDe(2, "Spartacus", 1960, entryId = "e-2", rating = 7),
        filmDe(3, "Lolita", 1962),
        filmDe(4, "Docteur Folamour", 1964, entryId = "e-4", rating = 10),
    )

    // Mutation : compter `films.size` au lieu des seuls `vu != null` donne 4 ; ne compter que le
    // premier film vu en donne 1.
    @Test
    fun `filmsVus ne compte que les films journalises`() {
        assertEquals(3, filmsVus(kubrick))
        assertEquals(0, filmsVus(listOf(filmDe(9, "Fear and Desire", 1953))))
        assertEquals(0, filmsVus(emptyList()))
    }

    // Le *premier* non vu dans l'ordre du back, pas le premier film tout court, pas le dernier
    // non vu. Mutation : `films.firstOrNull()` rendrait « Les Sentiers de la gloire », déjà vu ;
    // `lastOrNull { it.vu == null }` rendrait « Barry Lyndon » dans le second cas.
    @Test
    fun `prochainAVoir prend le premier non vu, dans l ordre`() {
        assertEquals("Lolita", prochainAVoir(kubrick)?.title)

        val deuxNonVus = kubrick + filmDe(5, "Barry Lyndon", 1975)
        assertEquals("Lolita", prochainAVoir(deuxNonVus)?.title)
    }

    // Tout vu : plus de « prochain » du tout — ni un film au hasard, ni le dernier.
    @Test
    fun `prochainAVoir est nul quand tout est vu`() {
        assertNull(prochainAVoir(kubrick.filter { it.vu != null }))
        assertNull(prochainAVoir(emptyList()))
    }

    // Un film introuvable (décision du propriétaire du 15 septembre 2026) n'est jamais le
    // prochain, même non vu et même en tête. Mutation : ne filtrer que sur `vu == null` (l'ancien
    // corps de la fonction) rendrait « Lolita » dans les deux cas.
    @Test
    fun `prochainAVoir ignore les films marques introuvables`() {
        val lolitaIntrouvable = listOf(
            filmDe(1, "Les Sentiers de la gloire", 1957, entryId = "e-1", rating = 9),
            filmDe(2, "Spartacus", 1960, entryId = "e-2", rating = 7),
            filmDe(3, "Lolita", 1962, introuvable = true),
            filmDe(4, "Docteur Folamour", 1964),
        )
        assertEquals("Docteur Folamour", prochainAVoir(lolitaIntrouvable)?.title)

        // Tout le reste est introuvable ou vu : plus de prochain du tout, comme « tout vu ».
        assertNull(prochainAVoir(lolitaIntrouvable.filter { it.title != "Docteur Folamour" }))
    }

    // La phrase exacte de la ligne (brief du 15 septembre 2026). Mutation : inverser vus et
    // total, oublier l'année du prochain, ou garder « · prochain : » quand il n'y a plus rien à
    // voir, casse l'une des trois assertions.
    @Test
    fun `resumeFilmographie dit le compte puis le prochain`() {
        assertEquals("3 vus sur 4 · prochain : Lolita (1962)", resumeFilmographie(kubrick))
        assertEquals("3 vus sur 3", resumeFilmographie(kubrick.filter { it.vu != null }))
        assertEquals(
            "0 vus sur 1 · prochain : Sans année",
            resumeFilmographie(listOf(filmDe(7, "Sans année"))),
        )
    }

    // Le total compte les introuvables (brief du 15 septembre 2026), la mention « · N
    // introuvables » ne sort que s'il y en a, et le prochain qui suit les ignore. Mutation :
    // exclure les introuvables du total ferait dire « 3 vus sur 3 » ; les compter sans jamais
    // ajouter la mention laisserait « 3 vus sur 4 · prochain : Docteur Folamour » sans dire
    // pourquoi Lolita n'est pas le prochain.
    @Test
    fun `resumeFilmographie compte les introuvables et l annonce`() {
        val lolitaIntrouvable = listOf(
            filmDe(1, "Les Sentiers de la gloire", 1957, entryId = "e-1", rating = 9),
            filmDe(2, "Spartacus", 1960, entryId = "e-2", rating = 7),
            filmDe(3, "Lolita", 1962, introuvable = true),
            filmDe(4, "Docteur Folamour", 1964),
        )
        assertEquals(
            "2 vus sur 4 · 1 introuvables · prochain : Docteur Folamour (1964)",
            resumeFilmographie(lolitaIntrouvable),
        )
        // Aucun introuvable : la mention disparaît entièrement, pas « · 0 introuvables ».
        assertEquals("3 vus sur 4 · prochain : Lolita (1962)", resumeFilmographie(kubrick))
    }

    // « … » tant que la réponse n'est pas là, « indisponible » quand elle a échoué : c'est ce qui
    // garde l'écran entier pendant que ses lignes se remplissent une à une. Mutation : rendre le
    // même texte pour les deux états, ou laisser la ligne vide en attente, casse ce test.
    @Test
    fun `libelleLigne distingue l attente, la panne et le compte`() {
        assertEquals("…", libelleLigne(EtatFilmographie.EnAttente))
        assertEquals("indisponible", libelleLigne(EtatFilmographie.Indisponible))
        assertEquals("3 vus sur 4 · prochain : Lolita (1962)", libelleLigne(EtatFilmographie.Pret(kubrick)))
    }

    private val nolan = realisateur(525, "Christopher Nolan")
    private val kub = realisateur(240, "Stanley Kubrick")
    private val miyazaki = realisateur(608, "Hayao Miyazaki")

    // Le plus de films vus **parmi ceux qui ont encore quelque chose à voir**. Mutation : retirer
    // la condition « au moins un non vu » élirait Miyazaki (4 vus, tout vu) ; prendre le minimum
    // au lieu du maximum élirait Nolan (1 vu).
    @Test
    fun `realisateurEnCours prend le plus vu parmi ceux qui ont encore a voir`() {
        val filmographies = mapOf(
            nolan.tmdb_id to EtatFilmographie.Pret(
                listOf(filmDe(11, "Memento", 2000, entryId = "e-11"), filmDe(12, "Inception", 2010)),
            ),
            kub.tmdb_id to EtatFilmographie.Pret(kubrick),
            miyazaki.tmdb_id to EtatFilmographie.Pret(
                (1..4).map { filmDe(20 + it, "Film $it", 1990 + it, entryId = "e-2$it") },
            ),
        )

        val enCours = realisateurEnCours(listOf(nolan, kub, miyazaki), filmographies)

        assertEquals("Stanley Kubrick", enCours?.realisateur?.name)
        assertEquals("Lolita", enCours?.prochain?.title)
    }

    // Une filmographie qui n'est pas encore là, ou qui a échoué, ne concourt pas — mais elle ne
    // doit pas non plus retenir les autres : la ligne « Ensuite » de l'accueil apparaît dès qu'un
    // réalisateur a de quoi la remplir, sans attendre que les N appels soient tous revenus.
    // Mutation : refuser de trancher tant qu'une filmographie n'est pas `Pret`
    // (`if (filmographies.values.any { it !is Pret }) return null`) rend ce test nul alors que le
    // précédent, lui, reste vert.
    @Test
    fun `realisateurEnCours ignore les filmographies en attente ou indisponibles`() {
        val filmographies = mapOf(
            nolan.tmdb_id to EtatFilmographie.EnAttente,
            kub.tmdb_id to EtatFilmographie.Indisponible,
            miyazaki.tmdb_id to EtatFilmographie.Pret(
                listOf(filmDe(30, "Porco Rosso", 1992, entryId = "e-30"), filmDe(31, "Mononoké", 1997)),
            ),
        )

        assertEquals("Hayao Miyazaki", realisateurEnCours(listOf(nolan, kub, miyazaki), filmographies)?.realisateur?.name)
    }

    // Personne à proposer : la ligne « Ensuite · … » est absente de l'accueil, pas affichée vide.
    @Test
    fun `realisateurEnCours est nul quand tout est vu, ou quand la liste est vide`() {
        val toutVu = mapOf(kub.tmdb_id to EtatFilmographie.Pret(kubrick.filter { it.vu != null }))
        assertNull(realisateurEnCours(listOf(kub), toutVu))
        assertNull(realisateurEnCours(emptyList(), emptyMap()))
    }

    // Le seul film qui restait à voir est marqué introuvable (décision du propriétaire du
    // 15 septembre 2026) : ce réalisateur ne concourt plus, comme s'il était tout vu — la ligne
    // « Ensuite » de l'accueil ne doit jamais pointer sur un film qu'on ne peut pas trouver.
    // Mutation : revenir à l'ancien `prochainAVoir` (qui ne regarde que `vu`) élirait Kubrick.
    @Test
    fun `realisateurEnCours ignore un realisateur dont il ne reste qu un introuvable`() {
        val filmographies = mapOf(
            kub.tmdb_id to EtatFilmographie.Pret(
                kubrick.map { if (it.vu == null) it.copy(introuvable = true) else it },
            ),
            miyazaki.tmdb_id to EtatFilmographie.Pret(
                listOf(filmDe(30, "Porco Rosso", 1992, entryId = "e-30"), filmDe(31, "Mononoké", 1997)),
            ),
        )

        assertEquals("Hayao Miyazaki", realisateurEnCours(listOf(kub, miyazaki), filmographies)?.realisateur?.name)
    }

    // À égalité, le premier de la liste — `GET /me/realisateurs` la rend du plus récemment ajouté
    // au plus ancien, donc le dernier ajouté gagne. Mutation : un `maxByOrNull` qui garderait le
    // dernier maximum (ou un tri qui inverserait la liste) élirait Kubrick.
    @Test
    fun `realisateurEnCours tranche une egalite par le premier de la liste`() {
        val unVuUnAVoir = { prefixe: String ->
            EtatFilmographie.Pret(
                listOf(
                    filmDe(1, "$prefixe vu", 1990, entryId = "e-$prefixe"),
                    filmDe(2, "$prefixe a voir", 1991),
                ),
            )
        }
        val filmographies = mapOf(
            nolan.tmdb_id to unVuUnAVoir("nolan"),
            kub.tmdb_id to unVuUnAVoir("kubrick"),
        )

        assertEquals("Christopher Nolan", realisateurEnCours(listOf(nolan, kub), filmographies)?.realisateur?.name)
    }

    // Le back refuse un `q` vide (`400`) : une saisie vide, ou faite d'espaces, ne doit pas
    // partir. Mutation : rendre la saisie telle quelle (sans `trim`, sans `ifEmpty`) casse les
    // trois premières assertions.
    @Test
    fun `requeteUtile ecarte une saisie vide et coupe les espaces`() {
        assertNull(requeteUtile(""))
        assertNull(requeteUtile("   "))
        assertNull(requeteUtile("\n\t "))
        assertEquals("Kubrick", requeteUtile("  Kubrick  "))
        assertEquals("K", requeteUtile("K"))
    }

    // Le formulaire pré-rempli derrière un film non vu : même clé d'ajout que la recherche
    // (`source` + `external_id`), et le nom du réalisateur en prime — on le connaît ici, à la
    // différence de la Frise. Mutation : envoyer le `tmdb_id` de la personne au lieu de celui du
    // film, ou oublier `type = "movie"`, casse ce test.
    @Test
    fun `toSearchResult porte la cle d ajout du film et le nom du realisateur`() {
        val resultat = filmDe(3, "Lolita", 1962).toSearchResult("Stanley Kubrick")

        assertEquals("tmdb", resultat.source)
        assertEquals("3", resultat.external_id)
        assertEquals("movie", resultat.type)
        assertEquals("Lolita", resultat.title)
        assertEquals(1962, resultat.year)
        assertEquals("Stanley Kubrick", resultat.metadata.director)
    }
}
