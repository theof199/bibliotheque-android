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
        val boutons = boutonsFicheFilm(type = "tv", etat = "a_demander", plexUrl = "https://plex/x", entreeConnue = false)
        assertEquals(listOf(BoutonFicheFilm.VOIR_SUR_LE_PLEX, BoutonFicheFilm.DEMANDER), boutons)
    }

    // Un film marqué introuvable, pas sur le Plex : « Je l'ai vu » reste (on peut l'avoir vu
    // ailleurs), et son inverse à « Introuvable » plutôt que « Marquer introuvable ». Mutation :
    // retirer l'un des deux, ou garder « Marquer introuvable » à la place de son inverse, casse
    // cette assertion.
    @Test
    fun `boutonsFicheFilm propose Je l ai vu et son inverse pour un film introuvable`() {
        val boutons = boutonsFicheFilm(type = "movie", etat = "introuvable", plexUrl = null, entreeConnue = false)
        assertEquals(listOf(BoutonFicheFilm.JE_L_AI_VU, BoutonFicheFilm.RETIRER_INTROUVABLE), boutons)
    }

    // Un film vu n'a plus ni « Je l'ai vu » ni « Introuvable »/son inverse. Mutation : garder l'un
    // des deux malgré `etat == "vu"` casse cette assertion.
    @Test
    fun `boutonsFicheFilm est vide pour un film vu sans lien Plex`() {
        assertEquals(emptyList<BoutonFicheFilm>(), boutonsFicheFilm(type = "movie", etat = "vu", plexUrl = null, entreeConnue = false))
    }

    // « Corriger » (« la fiche · trois visages », 25 septembre 2026) prend la tête de la pile d'un
    // film vu dont l'entrée est connue. Mutation : l'ajouter après « Voir sur le Plex » casse
    // l'ordre attendu.
    @Test
    fun `boutonsFicheFilm met Corriger en tete sur un film vu dont l entree est connue`() {
        val boutons = boutonsFicheFilm(type = "movie", etat = "vu", plexUrl = "https://plex/x", entreeConnue = true)
        assertEquals(listOf(BoutonFicheFilm.CORRIGER, BoutonFicheFilm.VOIR_SUR_LE_PLEX), boutons)
    }

    // Mutation : ignorer `entreeConnue` ferait sortir un « Corriger » qui n'aurait rien à ouvrir.
    @Test
    fun `boutonsFicheFilm ne propose pas Corriger sans entree connue`() {
        val boutons = boutonsFicheFilm(type = "movie", etat = "vu", plexUrl = null, entreeConnue = false)
        assertEquals(false, boutons.contains(BoutonFicheFilm.CORRIGER))
    }

    // Jamais avec « Je l'ai vu ». Mutation : ignorer `etat` mettrait les deux boutons corail
    // l'un sur l'autre.
    @Test
    fun `boutonsFicheFilm ne propose pas Corriger sur un film pas vu`() {
        val boutons = boutonsFicheFilm(type = "movie", etat = "sur_le_plex", plexUrl = null, entreeConnue = true)
        assertEquals(false, boutons.contains(BoutonFicheFilm.CORRIGER))
    }

    // Une série n'a pas de formulaire, donc rien à corriger. Mutation : retirer `type != "tv"` de
    // la condition ferait apparaître « Corriger » sur une série.
    @Test
    fun `boutonsFicheFilm ne propose pas Corriger sur une serie`() {
        val boutons = boutonsFicheFilm(type = "tv", etat = "vu", plexUrl = null, entreeConnue = true)
        assertEquals(false, boutons.contains(BoutonFicheFilm.CORRIGER))
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

    // Le libellé lui-même : « 1980 », le millésime nu, le nom du monde venant à côté (le pavillon,
    // 25 septembre 2026). Mutation : garder « Années 1980 » casse cette assertion.
    @Test
    fun `libelleDecennie ecrit le millesime nu`() {
        assertEquals("1980", libelleDecennie(1980))
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

    // Un court entre deux longs de la même décennie reste entre les deux : l'appli ne réordonne
    // rien, elle garde l'ordre chronologique du back (décision 1 de la retouche du 22 septembre
    // 2026, « la filmographie dans l'ordre »). Mutation : faire remonter le court en tête ou en
    // queue du groupe casse cette assertion.
    @Test
    fun `regrouperParDecennie garde l ordre d entree long court melanges`() {
        val premier = FakeJournalApi.filmDeFilmographie(1, "Premier long", 1980)
        val court = FakeJournalApi.filmDeFilmographie(2, "Court entre deux", 1981, court = true)
        val second = FakeJournalApi.filmDeFilmographie(3, "Second long", 1982)
        val decennies = regrouperParDecennie(listOf(premier, court, second))
        assertEquals(listOf(premier, court, second), decennies[0].films)
    }

    // Un groupe ne sépare plus rien : `DecennieFilmographie` n'a plus qu'une seule liste `films`,
    // plus de `longs`/`courtsEtSeries` distincts. Mutation : reclasser le court après le long au
    // lieu de garder l'ordre d'entrée casse cette assertion.
    @Test
    fun `regrouperParDecennie ne separe plus les longs des courts`() {
        val long = FakeJournalApi.filmDeFilmographie(1, "Long", 1980)
        val court = FakeJournalApi.filmDeFilmographie(2, "Court", 1980, court = true)
        val decennies = regrouperParDecennie(listOf(court, long))
        assertEquals(listOf(court, long), decennies[0].films)
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

    // Films et courts métrages restent (`type` vaut toujours « movie » pour les deux) ; seules les
    // séries (`type: "tv"`) sont écartées (décision 3 de la retouche du 22 septembre 2026, « la
    // filmographie dans l'ordre »). Mutation : filtrer aussi les courts casse cette assertion.
    @Test
    fun `filmsSansSeries garde les films et les courts`() {
        val film = FakeJournalApi.filmDeFilmographie(1, "Film", 1980)
        val court = FakeJournalApi.filmDeFilmographie(2, "Court", 1980, court = true)
        assertEquals(listOf(film, court), filmsSansSeries(listOf(film, court)))
    }

    // Les séries sont absentes des groupes eux-mêmes, une fois filtrées avant `regrouperParDecennie`
    // (décision 3). Mutation : laisser passer la série casse cette assertion.
    @Test
    fun `filmsSansSeries retire les series avant regrouperParDecennie`() {
        val film = FakeJournalApi.filmDeFilmographie(1, "Film", 1980)
        val serie = FakeJournalApi.filmDeFilmographie(2, "Serie", 1980, type = "tv")
        val decennies = regrouperParDecennie(filmsSansSeries(listOf(film, serie)))
        assertEquals(listOf(film), decennies[0].films)
    }

    // « 2 sur le Plex », compté sur films et courts métrages confondus, introuvables compris (le
    // pavillon, 25 septembre 2026). Mutation : ne compter que les longs, ou oublier l'introuvable,
    // casse cette assertion.
    @Test
    fun `ligneSurLePlex compte films et courts introuvables compris`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "A", 2000, entryId = "e1", surLePlex = true),
            FakeJournalApi.filmDeFilmographie(2, "B", 2001, court = true, surLePlex = true, introuvable = true),
            FakeJournalApi.filmDeFilmographie(3, "C", 2002),
        )
        assertEquals("2 sur le Plex", ligneSurLePlex(films))
    }

    // Une série filtrée avant l'appel n'y contribue plus (décision 3 de la retouche du 22 septembre
    // 2026). Mutation : la compter quand même casse cette assertion.
    @Test
    fun `filmsSansSeries retire les series avant ligneSurLePlex`() {
        val film = FakeJournalApi.filmDeFilmographie(1, "Film", 2000, surLePlex = true)
        val serie = FakeJournalApi.filmDeFilmographie(2, "Serie", 2000, type = "tv", surLePlex = true)
        assertEquals("1 sur le Plex", ligneSurLePlex(filmsSansSeries(listOf(film, serie))))
    }

    // « 0 sur le Plex » s'écrit quand même, jamais omis. Mutation : rendre une chaîne vide casse
    // cette assertion.
    @Test
    fun `ligneSurLePlex ecrit 0 sur le Plex`() {
        assertEquals("0 sur le Plex", ligneSurLePlex(listOf(FakeJournalApi.filmDeFilmographie(1, "A", 2000))))
    }

    // L'étiquette de l'en-tête, en capitales telle qu'affichée. Mutation : inverser vus et total, ou
    // perdre l'accent de « RÉTROSPECTIVE », casse cette assertion.
    @Test
    fun `etiquetteRetrospective ecrit vus sur total en capitales`() {
        assertEquals("RÉTROSPECTIVE · 4 SUR 12", etiquetteRetrospective(4, 12))
        assertEquals("RÉTROSPECTIVE · 0 SUR 0", etiquetteRetrospective(0, 0))
    }

    // Le compte de l'en-tête compte les introuvables au total, jamais vus. Mutation : les retirer du
    // total casse cette assertion.
    @Test
    fun `compteRetrospective compte les introuvables au total`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "Vu", 2000, entryId = "e1"),
            FakeJournalApi.filmDeFilmographie(2, "Perdu", 2001, introuvable = true),
            FakeJournalApi.filmDeFilmographie(3, "A voir", 2002, court = true),
        )
        assertEquals(1 to 3, compteRetrospective(films))
    }

    // « 2 sur 4 » compte ce que la grille dessine : avec l'interrupteur, l'introuvable sort du total ;
    // sans lui, il y entre sans être vu. Mutation : compter la décennie brute, avant `filmsAffiches`,
    // casse la première assertion.
    @Test
    fun `compteDecennie suit l interrupteur des introuvables`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "Vu", 1990, entryId = "e1"),
            FakeJournalApi.filmDeFilmographie(2, "Vu aussi", 1991, entryId = "e2"),
            FakeJournalApi.filmDeFilmographie(3, "Perdu", 1992, introuvable = true),
            FakeJournalApi.filmDeFilmographie(4, "A voir", 1993),
        )
        val masquee = regrouperParDecennie(filmsAffiches(films, masquerIntrouvables = true)).single()
        val montree = regrouperParDecennie(filmsAffiches(films, masquerIntrouvables = false)).single()
        assertEquals("2 sur 3", compteDecennie(masquee.films))
        assertEquals("2 sur 4", compteDecennie(montree.films))
    }

    // Le prochain de toute la filmographie : le premier ni vu ni introuvable, décennies confondues —
    // ici en 1990, toute la décennie 1980 étant vue ou perdue. Mutation : élire l'introuvable, ou
    // le premier non vu sans regarder `introuvable`, casse cette assertion.
    @Test
    fun `prochainDeLaFilmographie saute les vus et les introuvables toutes decennies confondues`() {
        val vu = FakeJournalApi.filmDeFilmographie(1, "Vu", 1981, entryId = "e1")
        val perdu = FakeJournalApi.filmDeFilmographie(2, "Perdu", 1985, introuvable = true)
        val prochain = FakeJournalApi.filmDeFilmographie(3, "Prochain", 1990, court = true)
        val apres = FakeJournalApi.filmDeFilmographie(4, "Apres", 1991)
        val films = listOf(vu, perdu, prochain, apres)
        assertEquals(prochain, prochainDeLaFilmographie(films))
        assertEquals(prochain, prochainDeLaFilmographie(filmsAffiches(films, masquerIntrouvables = true)))
    }

    // Tout vu ou perdu : aucune case « ENSUITE ». Mutation : retomber sur le premier film casse
    // cette assertion.
    @Test
    fun `prochainDeLaFilmographie est nul quand il ne reste rien a voir`() {
        val films = listOf(
            FakeJournalApi.filmDeFilmographie(1, "Vu", 1981, entryId = "e1"),
            FakeJournalApi.filmDeFilmographie(2, "Perdu", 1985, introuvable = true),
        )
        assertEquals(null, prochainDeLaFilmographie(films))
    }

    // « Suivre », puis « Suivi » ou « Suivie » selon le genre ; un genre inconnu reste au masculin
    // d'usage. Mutation : ignorer `genre` casse la deuxième assertion.
    @Test
    fun `libelleBoutonSuivi s accorde au genre`() {
        assertEquals("Suivre", libelleBoutonSuivi(false, "femme"))
        assertEquals("Suivie", libelleBoutonSuivi(true, "femme"))
        assertEquals("Suivi", libelleBoutonSuivi(true, "homme"))
        assertEquals("Suivi", libelleBoutonSuivi(true, null))
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

    // La rétrospective complète (geste 20 du complément du 23 septembre 2026 à l'habillage) : un
    // introuvable compte, comme pour le Bilan. Mutation : exiger `vu != null` pour tous ferait
    // manquer la rétrospective d'un réalisateur dont un film est introuvable.
    @Test
    fun `retrospectiveComplete est vraie quand tout est vu ou introuvable, un introuvable comptant`() {
        val vu = FakeJournalApi.filmDeFilmographie(1, "Vu", 1980, entryId = "e1", rating = 7)
        val perdu = FakeJournalApi.filmDeFilmographie(2, "Perdu", 1981, introuvable = true)
        assertTrue(retrospectiveComplete(listOf(vu, perdu)))
    }

    // Un seul film ni vu ni introuvable suffit à la rendre fausse. Mutation : `any` au lieu d'`all`
    // la rendrait vraie dès le premier film vu, quel que soit le reste.
    @Test
    fun `retrospectiveComplete est fausse des qu un film n est ni vu ni introuvable`() {
        val vu = FakeJournalApi.filmDeFilmographie(1, "Vu", 1980, entryId = "e1", rating = 7)
        val aVoir = FakeJournalApi.filmDeFilmographie(2, "A voir", 1981)
        assertTrue(!retrospectiveComplete(listOf(vu, aVoir)))
    }

    // Une filmographie vide (par exemple après avoir retiré les séries) est complète aussi : rien
    // n'y reste à voir. Mutation : rendre faux sur une liste vide casse cette assertion.
    @Test
    fun `retrospectiveComplete est vraie sur une filmographie vide`() {
        assertTrue(retrospectiveComplete(emptyList()))
    }
}
