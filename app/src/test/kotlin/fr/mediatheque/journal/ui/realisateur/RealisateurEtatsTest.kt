package fr.mediatheque.journal.ui.realisateur

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.VoyageDeFilmographie
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Les fonctions pures de la page réalisateur (brief du 21 septembre 2026, « la page réalisateur »),
 * jumelles de `FicheVoyageEtatsTest.kt` — pas de réseau, pas de `ViewModel`.
 */
class RealisateurEtatsTest {

    // « 1861 – 1938 » quand TMDB donne les deux dates. Mutation : n'afficher que l'une des deux
    // années, ou inverser leur ordre, casse cette assertion.
    @Test
    fun `ligneDates montre les deux annees quand TMDB les donne`() {
        assertEquals("1861 – 1938", ligneDates("1861-11-13", "1938-04-22"))
    }

    // « née en 1961 » quand seule la naissance est connue. Mutation : rendre la chaîne vide dans
    // ce cas casse cette assertion.
    @Test
    fun `ligneDates montre seulement la naissance quand TMDB ne donne pas la mort`() {
        assertEquals("née en 1961", ligneDates("1961-05-04", null))
    }

    // Vide quand TMDB ne donne ni l'une ni l'autre. Mutation : rendre autre chose qu'une chaîne
    // vide casse cette assertion.
    @Test
    fun `ligneDates est vide sans aucune date`() {
        assertEquals("", ligneDates(null, null))
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
}
