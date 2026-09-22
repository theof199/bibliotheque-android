package fr.mediatheque.journal.ui.realisateur

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.VoyageDeFilmographie
import fr.mediatheque.journal.ui.frise.mondeDe
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Les fonctions pures de la page réalisateur (brief du 21 septembre 2026, « la page réalisateur » ;
 * reprise du même jour, « la page réalisateur, reprise »), jumelles de `FicheVoyageEtatsTest.kt` —
 * pas de réseau, pas de `ViewModel`.
 */
class RealisateurEtatsTest {

    // « 1861 – 1938 » quand TMDB donne les deux dates, quel que soit le genre. Mutation : n'afficher
    // que l'une des deux années, ou inverser leur ordre, casse cette assertion.
    @Test
    fun `ligneDates montre les deux annees quand TMDB les donne`() {
        assertEquals("1861 – 1938", ligneDates("1861-11-13", "1938-04-22", "homme"))
    }

    // « né en 1958 » pour un homme (reprise, décision 5). Mutation : accorder au féminin ou omettre
    // le genre casse cette assertion.
    @Test
    fun `ligneDates dit ne en pour un homme`() {
        assertEquals("né en 1958", ligneDates("1958-01-01", null, "homme"))
    }

    // « née en 1961 » pour une femme. Mutation : accorder au masculin casse cette assertion.
    @Test
    fun `ligneDates dit nee en pour une femme`() {
        assertEquals("née en 1961", ligneDates("1961-05-04", null, "femme"))
    }

    // « naissance en 1958 » sans genre connu — ni « né » ni « née ». Mutation : retomber sur l'un
    // des deux genres par défaut casse cette assertion.
    @Test
    fun `ligneDates dit naissance en sans genre connu`() {
        assertEquals("naissance en 1958", ligneDates("1958-01-01", null, null))
    }

    // Vide quand TMDB ne donne ni l'une ni l'autre. Mutation : rendre autre chose qu'une chaîne
    // vide casse cette assertion.
    @Test
    fun `ligneDates est vide sans aucune date`() {
        assertEquals("", ligneDates(null, null, null))
    }

    // Le tap sur une affiche (décision 2 du brief) : `voyage` non nul mène à sa fiche du Voyage.
    // Mutation : renvoyer `Simple` dans ce cas casse cette assertion.
    @Test
    fun `destinationFilm mene au Voyage quand la ligne voyage existe`() {
        val voyage = FakeJournalApi.filmDeFilmographie(
            27205,
            "Inception",
            2010,
            voyage = VoyageDeFilmographie(2010, "s-1", "f-1"),
        )
        val destination = destinationFilm(voyage)
        assertEquals(DestinationFilm.Voyage(2010, "s-1", "f-1"), destination)
    }

    // Sinon, la fiche simple porte le film lui-même. Mutation : renvoyer `Voyage` avec des champs
    // par défaut casse cette assertion (type incompatible en plus, mais la mutation la plus proche
    // — inverser le `if` — fait tomber cette égalité).
    @Test
    fun `destinationFilm mene a la fiche simple sans ligne voyage`() {
        val film = FakeJournalApi.filmDeFilmographie(129, "Le Voyage de Chihiro", 2001)
        assertEquals(DestinationFilm.Simple(film), destinationFilm(film))
    }

    // Une série n'a ni « Je l'ai vu » ni « Introuvable »/son inverse (décision 2 du brief) : elle
    // ne garde que Plex et Sir. Mutation : ajouter `JE_L_AI_VU` pour un type `tv` casse cette
    // assertion.
    @Test
    fun `boutonsFicheFilm exclut Je l ai vu pour une serie`() {
        val boutons = boutonsFicheFilm(type = "tv", etat = "a_demander", plexUrl = "https://plex/x")
        assertEquals(listOf(BoutonFicheFilm.VOIR_SUR_LE_PLEX, BoutonFicheFilm.DEMANDER), boutons)
    }

    // Un film marqué introuvable, pas sur le Plex : « Je l'ai vu » reste (on peut l'avoir vu
    // ailleurs), et son inverse à « Introuvable » plutôt que « Marquer introuvable ». Mutation :
    // retirer l'un des deux, ou garder « Marquer introuvable » à la place de son inverse, casse
    // cette assertion.
    @Test
    fun `boutonsFicheFilm propose Je l ai vu et son inverse pour un film introuvable`() {
        val boutons = boutonsFicheFilm(type = "movie", etat = "introuvable", plexUrl = null)
        assertEquals(listOf(BoutonFicheFilm.JE_L_AI_VU, BoutonFicheFilm.RETIRER_INTROUVABLE), boutons)
    }

    // Un film vu n'a plus ni « Je l'ai vu » ni « Introuvable »/son inverse. Mutation : garder l'un
    // des deux malgré `etat == "vu"` casse cette assertion.
    @Test
    fun `boutonsFicheFilm est vide pour un film vu sans lien Plex`() {
        assertEquals(emptyList<BoutonFicheFilm>(), boutonsFicheFilm(type = "movie", etat = "vu", plexUrl = null))
    }

    // Le résolveur : un seul crédit mène directement à sa page. Mutation : rendre `Plusieurs` ou
    // `Aucun` dans ce cas casse cette assertion.
    @Test
    fun `resultatTapRealisateur mene directement a la page pour un seul credit`() {
        val nolan = FakeJournalApi.realisateurCredit(525, "Christopher Nolan")
        assertEquals(ResultatRealisateur.Un(525), resultatTapRealisateur(listOf(nolan)))
    }

    // Plusieurs crédits : la feuille, avec la liste entière. Mutation : ne garder que le premier
    // casse cette assertion.
    @Test
    fun `resultatTapRealisateur propose une feuille pour plusieurs credits`() {
        val a = FakeJournalApi.realisateurCredit(1, "Un")
        val b = FakeJournalApi.realisateurCredit(2, "Deux")
        assertEquals(ResultatRealisateur.Plusieurs(listOf(a, b)), resultatTapRealisateur(listOf(a, b)))
    }

    // Aucun crédit : le bandeau. Mutation : rendre `Un(0)` ou toute autre valeur par défaut casse
    // cette assertion.
    @Test
    fun `resultatTapRealisateur ne mene nulle part sans credit`() {
        assertEquals(ResultatRealisateur.Aucun, resultatTapRealisateur(emptyList()))
    }

    // Le nom du réalisateur devient touchable à la création aussi (retouche du 21 septembre 2026) :
    // seulement quand la source du résultat est TMDB, l'unique fournisseur dont `external_id` est
    // un `tmdb_id` de film. Mutation : rendre l'identifiant quelle que soit la source casse cette
    // assertion.
    @Test
    fun `filmTmdbIdTouchable rend l id sur une source tmdb`() {
        assertEquals(27205, filmTmdbIdTouchable("tmdb", "27205"))
    }

    // Une autre source (SensCritique, Letterboxd, …) ne connaît pas de `tmdb_id` : nul, le nom
    // reste affiché mais inerte. Mutation : rendre l'identifiant quand même casse cette assertion.
    @Test
    fun `filmTmdbIdTouchable est nul sur une autre source`() {
        assertEquals(null, filmTmdbIdTouchable("senscritique", "27205"))
    }

    // --- La grille verticale par décennie (reprise du 21 septembre 2026, décision 1) -------------

    // 1895 rejoint « Années 1890 » : l'en-tête arrondit à la décennie, pas au millésime. Mutation :
    // arrondir au millésime lui-même (« Années 1895 ») casse cette assertion.
    @Test
    fun `regrouperParDecennie fait rejoindre 1895 aux annees 1890`() {
        val film = FakeJournalApi.filmDeFilmographie(1, "L'Arrivee d'un train", 1895)
        val decennies = regrouperParDecennie(listOf(film))
        assertEquals(1890, decennies[0].decennie)
    }

    // Le libellé lui-même : « Années 1980 », jamais le millésime nu. Mutation : afficher le
    // millésime seul (sans « Années ») casse cette assertion.
    @Test
    fun `libelleDecennie ecrit Annees suivi de la decennie`() {
        assertEquals("Années 1980", libelleDecennie(1980))
    }

    // Sans décennie connue (aucun film daté du groupe), un libellé de repli plutôt qu'un texte nul.
    // Mutation : renvoyer une chaîne vide au lieu du repli casse cette assertion.
    @Test
    fun `libelleDecennie a un repli sans decennie connue`() {
        assertEquals("Année inconnue", libelleDecennie(null))
    }

    // L'ordre des groupes est celui de leur première rencontre dans la liste du back, jamais un tri
    // par décennie. Mutation : trier les décennies par ordre croissant (`sortedBy`) casse cette
    // assertion, qui donne volontairement un film de 1980 avant un film de 1970.
    @Test
    fun `regrouperParDecennie garde l ordre de rencontre, pas un tri par decennie`() {
        val recent = FakeJournalApi.filmDeFilmographie(1, "Recent", 1980)
        val ancien = FakeJournalApi.filmDeFilmographie(2, "Ancien", 1970)
        val decennies = regrouperParDecennie(listOf(recent, ancien))
        assertEquals(listOf(1980, 1970), decennies.map { it.decennie })
    }

    // Dans une décennie, les longs précèdent les courts et les séries, chaque groupe gardant l'ordre
    // de la liste d'entrée. Mutation : mélanger les deux groupes, ou classer un court parmi les
    // longs, casse cette assertion.
    @Test
    fun `regrouperParDecennie separe les longs des courts et series`() {
        val long = FakeJournalApi.filmDeFilmographie(1, "Long", 1980)
        val court = FakeJournalApi.filmDeFilmographie(2, "Court", 1980, court = true)
        val serie = FakeJournalApi.filmDeFilmographie(3, "Serie", 1980, type = "tv")
        val decennies = regrouperParDecennie(listOf(long, court, serie))
        assertEquals(listOf(long), decennies[0].longs)
        assertEquals(listOf(court, serie), decennies[0].courtsEtSeries)
    }

    // Une décennie sans long (Lumière, 1895–1905) a un groupe `longs` vide — ce que l'écran lit pour
    // la déplier d'emblée, sans ligne à taper. Mutation : y glisser le court casse cette assertion.
    @Test
    fun `regrouperParDecennie une decennie sans long a un groupe longs vide`() {
        val court = FakeJournalApi.filmDeFilmographie(1, "Court lumiere", 1895, court = true)
        val decennies = regrouperParDecennie(listOf(court))
        assertTrue(decennies[0].longs.isEmpty())
        assertEquals(listOf(court), decennies[0].courtsEtSeries)
    }

    // L'interrupteur activé retire les films que j'ai marqués introuvables, et eux seuls (retouche
    // du 22 septembre 2026). Mutation : rendre la liste entière, ou retirer les vus au lieu des
    // introuvables, casse cette assertion.
    @Test
    fun `filmsAffiches retire les introuvables quand l interrupteur est active`() {
        val visible = FakeJournalApi.filmDeFilmographie(1, "Visible", 1980)
        val perdu = FakeJournalApi.filmDeFilmographie(2, "Perdu", 1981, introuvable = true)
        val vu = FakeJournalApi.filmDeFilmographie(3, "Vu", 1982, entryId = "e3", rating = 7)
        assertEquals(listOf(visible, vu), filmsAffiches(listOf(visible, perdu, vu), masquerIntrouvables = true))
    }

    // Désactivé, il rend tout, introuvables compris, dans l'ordre. Mutation : filtrer quand même
    // casse cette assertion.
    @Test
    fun `filmsAffiches garde les introuvables quand l interrupteur est desactive`() {
        val visible = FakeJournalApi.filmDeFilmographie(1, "Visible", 1980)
        val perdu = FakeJournalApi.filmDeFilmographie(2, "Perdu", 1981, introuvable = true)
        assertEquals(listOf(visible, perdu), filmsAffiches(listOf(visible, perdu), masquerIntrouvables = false))
    }

    // Filtré avant le regroupement, une décennie dont tous les films sont introuvables disparaît
    // avec eux. Mutation : regrouper avant de filtrer laisserait un groupe vide et casse l'assertion.
    @Test
    fun `une decennie entierement introuvable disparait de la grille`() {
        val perdu = FakeJournalApi.filmDeFilmographie(1, "Perdu", 1971, introuvable = true)
        val visible = FakeJournalApi.filmDeFilmographie(2, "Visible", 1985)
        val decennies = regrouperParDecennie(filmsAffiches(listOf(perdu, visible), masquerIntrouvables = true))
        assertEquals(listOf(1980), decennies.map { it.decennie })
    }

    // « 2 courts · 1 série » : les deux parties, accordées, jointes par « · ». Mutation : omettre
    // l'une des deux parties ou changer le séparateur casse cette assertion.
    @Test
    fun `libelleCourtsEtSeries joint courts et series accordes`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "C1", 1980, court = true),
            FakeJournalApi.filmDeFilmographie(2, "C2", 1980, court = true),
            FakeJournalApi.filmDeFilmographie(3, "S1", 1980, type = "tv"),
        )
        assertEquals("2 courts · 1 série", libelleCourtsEtSeries(films))
    }

    // « 1 court » seul, sans série : la partie absente est omise plutôt que de laisser un « · » nu.
    // Mutation : garder un séparateur ou une partie vide casse cette assertion.
    @Test
    fun `libelleCourtsEtSeries omet les series absentes`() {
        val films = listOf(FakeJournalApi.filmDeFilmographie(1, "C1", 1980, court = true))
        assertEquals("1 court", libelleCourtsEtSeries(films))
    }

    // « 2 séries » seules, sans court. Mutation : garder « 0 court » au lieu de l'omettre casse
    // cette assertion.
    @Test
    fun `libelleCourtsEtSeries omet les courts absents`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "S1", 1980, type = "tv"),
            FakeJournalApi.filmDeFilmographie(2, "S2", 1980, type = "tv"),
        )
        assertEquals("2 séries", libelleCourtsEtSeries(films))
    }

    // « 3 films · 2 vus · 1 sur le Plex », comptée sur films, courts et séries confondus. Mutation :
    // ne compter que les longs, ou inverser vus et Plex, casse cette assertion.
    @Test
    fun `ligneResume compte films vus et sur le plex toutes lignes confondues`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "A", 2000, entryId = "e1", surLePlex = true),
            FakeJournalApi.filmDeFilmographie(2, "B", 2001, type = "tv"),
            FakeJournalApi.filmDeFilmographie(3, "C", 2002, entryId = "e3", court = true),
        )
        assertEquals("3 films · 2 vus · 1 sur le Plex", ligneResume(films))
    }

    // « 1 film · 1 vu · 1 sur le Plex » : accord au singulier. Mutation : garder le pluriel malgré
    // un seul élément casse cette assertion.
    @Test
    fun `ligneResume accorde au singulier`() {
        val films = listOf(FakeJournalApi.filmDeFilmographie(1, "A", 2000, entryId = "e1", surLePlex = true))
        assertEquals("1 film · 1 vu · 1 sur le Plex", ligneResume(films))
    }

    // « 0 vu » et « 0 sur le Plex » s'écrivent quand même, jamais omis. Mutation : les remplacer par
    // une chaîne vide dans ce cas casse cette assertion.
    @Test
    fun `ligneResume ecrit 0 vu et 0 sur le plex`() {
        val films = listOf(FakeJournalApi.filmDeFilmographie(1, "A", 2000))
        assertEquals("1 film · 0 vu · 0 sur le Plex", ligneResume(films))
    }

    // Le monde de la page est celui de l'année du premier film daté, un film sans année en tête ne
    // comptant pas. Mutation : prendre le tout premier film sans filtrer sur `year` casse cette
    // assertion (le film sans année passerait alors devant celui de 1980).
    @Test
    fun `mondeDeLaPage prend l annee du premier film date`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "Sans annee", null),
            FakeJournalApi.filmDeFilmographie(2, "Premier date", 1980),
            FakeJournalApi.filmDeFilmographie(3, "Second date", 1990),
        )
        assertEquals(mondeDe(1980), mondeDeLaPage(films))
    }

    // Sans aucun film daté, le monde retombe sur 1895. Mutation : retomber sur une autre année (par
    // exemple 1890, la décennie plutôt que le millésime) casse cette assertion.
    @Test
    fun `mondeDeLaPage retombe sur 1895 sans aucun film date`() {
        val films = listOf(FakeJournalApi.filmDeFilmographie(1, "Sans annee", null))
        assertEquals(mondeDe(1895), mondeDeLaPage(films))
    }
}
