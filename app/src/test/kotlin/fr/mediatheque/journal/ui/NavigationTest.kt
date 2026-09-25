package fr.mediatheque.journal.ui

import androidx.compose.material3.SnackbarHostState
import fr.mediatheque.journal.api.dto.Carnet
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalMedia
import fr.mediatheque.journal.api.dto.LogEntry
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class NavigationTest {
    @Test
    fun `showBriefly referme la snackbar apres deux secondes, pas avant`() = runTest {
        val host = SnackbarHostState()
        launch { host.showBriefly("Enregistré") }

        advanceTimeBy(1_999)
        assertNotNull(host.currentSnackbarData)

        advanceTimeBy(2)
        assertNull(host.currentSnackbarData)
    }

    // `home()` envoie sur un `Channel` plutôt que sur un `State` remis à `null` (Critique 1 de la
    // vague finale) : un `State` change de valeur à sa propre lecture, ce qui coupait la coroutine
    // de `HomeScreen` avant la fin des deux secondes de la snackbar. Mutation : faire de `home()`
    // un `trySend` répété deux fois casse la première assertion (deux messages reçus, pas un) ;
    // ne plus envoyer casse la même assertion (aucun message reçu).
    @Test
    fun `home envoie le message une seule fois, pas deux`() = runTest {
        val nav = Navigator()
        val recus = mutableListOf<String>()
        val job = launch { nav.messages.collect { recus += it } }

        nav.home("Enregistré")
        runCurrent()
        job.cancel()

        assertEquals(listOf("Enregistré"), recus)
    }

    // `home(null)` (le cas d'une suppression sans message, ou d'un retour sans rien à dire) ne
    // doit rien envoyer sur le canal : sinon la prochaine snackbar affichée serait vide.
    @Test
    fun `home sans message n envoie rien`() = runTest {
        val nav = Navigator()
        val recus = mutableListOf<String>()
        val job = launch { nav.messages.collect { recus += it } }

        nav.home(null)
        runCurrent()
        job.cancel()

        assertEquals(emptyList<String>(), recus)
    }

    // `searchVisits` ne bouge que sur un `push(Screen.Search)` : c'est ce qui garde
    // `LaunchedEffect(nav.searchVisits)` dans `Root.kt` de rejouer `search.reset()` seulement à
    // l'entrée depuis l'accueil, jamais au retour par `pop`, jamais sur un `push` vers un autre
    // écran (mineur 8 de la vague finale). Mutation : incrémenter sur tout `push` (pas seulement
    // vers `Screen.Search`) fait tomber la troisième assertion ; incrémenter aussi dans `pop`, ou
    // ne jamais incrémenter dans `push`, fait tomber respectivement la deuxième et la première.
    @Test
    fun `push incremente le compteur de visite de recherche, jamais pop, jamais un autre ecran`() {
        val nav = Navigator()

        nav.push(Screen.Search)
        val premiereVisite = nav.searchVisits

        nav.pop()
        val apresPop = nav.searchVisits
        assertEquals("pop ne doit rien incrementer", premiereVisite, apresPop)

        nav.push(Screen.Search)
        val secondeVisite = nav.searchVisits
        assertNotEquals("un second push vers Search doit changer la valeur", premiereVisite, secondeVisite)

        nav.push(Screen.Form(SearchResult("tmdb", "129", "movie", "Le Voyage de Chihiro", 2001)))
        val apresPushForm = nav.searchVisits
        assertEquals("un push vers un autre ecran ne doit rien incrementer", secondeVisite, apresPushForm)
    }

    // `bottomBarTab()` fixe la matrice des dix-huit écrans pour la barre de navigation du bas
    // (décision du propriétaire du 14 septembre 2026, troisième entrée « Au ciné » ajoutée le
    // même jour ; quatrième entrée « Frise » et cinquième « Réalisateurs » le 15 septembre
    // 2026, renommée « Suivis » le même jour quand les sagas l'ont rejointe) : visible sur
    // l'accueil, la Frise, « Suivis », « Au ciné », « Mes films » et le profil — « Mes films »
    // affichant « Profil » sélectionnée, puisqu'elle ne s'empile que depuis `Screen.Profile`
    // dans `Root.kt`, jamais depuis l'accueil — cachée sur la recherche, le formulaire (création
    // ou correction), la fiche d'une entrée (« la fiche · trois visages », 25 septembre 2026),
    // l'écran SensCritique, le détail d'une année de la Frise, la recherche
    // d'une entité suivie et sa fiche. Mutation : faire retourner `BottomTab.Home` pour
    // `Screen.Films` casse l'assertion sur `visibles` ; rendre non nul le résultat pour l'un des
    // écrans cachés, ou nul pour l'un des visibles, casse la boucle correspondante.
    @Test
    fun `bottomBarTab fixe la visibilite et la selection des dix-huit ecrans`() {
        val visibles = mapOf(
            Screen.Home to BottomTab.Home,
            Screen.Frise to BottomTab.Frise,
            Screen.Suivis to BottomTab.Suivis,
            Screen.Cinema to BottomTab.Cinema,
            Screen.Profile to BottomTab.Profile,
            Screen.Films to BottomTab.Profile,
        )
        visibles.forEach { (screen, tab) -> assertEquals(tab, screen.bottomBarTab()) }

        val exemple = SearchResult("tmdb", "129", "movie", "Le Voyage de Chihiro", 2001)
        val itemExemple = JournalItem(
            entry = LogEntry(id = "entry-1", media_id = "media-1", finished_at = "2026-09-03"),
            media = JournalMedia(id = "media-1", title = "Le Voyage de Chihiro"),
            carnet = Carnet(),
        )
        val anneeExemple = fr.mediatheque.journal.ui.frise.AnneeFrise(2001, listOf(itemExemple), emptyList())
        val caches: List<Screen> = listOf(
            Screen.Search,
            Screen.Form(exemple),
            Screen.Edit(itemExemple),
            Screen.FicheEntree(itemExemple),
            Screen.SensCritique,
            Screen.Annee(anneeExemple),
            Screen.ChercherSuivi,
            Screen.FicheSuivi(SourceSuivi.REALISATEURS, 240),
            Screen.ChoisirFilmDeSaga(8091),
            Screen.RapportImport,
            Screen.Realisateur(525),
            Screen.FicheFilm(525, 27205),
        )
        caches.forEach { screen -> assertNull(screen.bottomBarTab()) }
    }

    // La clé du défilement sauvegardé (peaufinage du 23 septembre 2026, « le défilement survit au
    // retour ») : fonction pure de la position dans la pile et de l'écran. Mutation : une clé qui
    // ignore la position (ne renvoyant que l'écran) fait tomber la première assertion, deux
    // occurrences du même écran à des places différentes se confondant alors.
    @Test
    fun `saveableKey depend de la position et de l ecran`() {
        assertNotEquals(saveableKey(0, Screen.Home), saveableKey(1, Screen.Home))
        assertEquals(saveableKey(2, Screen.Frise), saveableKey(2, Screen.Frise))
        assertNotEquals(saveableKey(1, Screen.Frise), saveableKey(1, Screen.Suivis))
    }

    // La clé reste courte même pour un écran qui porte une grosse charge (relecture du
    // 23 septembre 2026) : `toString()` d'un `Screen.Annee` embarquerait tous ses films, une clé
    // de plusieurs Ko dans le `SaveableStateHolder` et le Bundle d'instance. Mutation : revenir à
    // `"$index:$screen"` (l'ancienne formule) fait dépasser la longueur attendue.
    @Test
    fun `saveableKey reste courte meme pour un ecran charge`() {
        val filmExemple = JournalItem(
            entry = LogEntry(id = "entry-1", media_id = "media-1", finished_at = "2026-09-03"),
            media = JournalMedia(id = "media-1", title = "Le Voyage de Chihiro"),
            carnet = Carnet(),
        )
        val anneeChargee = fr.mediatheque.journal.ui.frise.AnneeFrise(2001, List(50) { filmExemple }, emptyList())
        val cle = saveableKey(3, Screen.Annee(anneeChargee))
        assertTrue("la cle devrait faire moins de 80 caracteres, en fait ${cle.length}", cle.length < 80)
    }

    // `clesLibereesParChangementDePile` ne rend que la queue dépilée (un `pop`, ou `home()` qui
    // vide davantage), jamais ce qui reste en tête ni ce qui s'ajoute. Mutation : libérer aussi la
    // tête commune fait tomber la première assertion (elle attend une liste vide sur un simple
    // `push`) ; ne rien libérer sur un `pop` fait tomber la deuxième.
    @Test
    fun `clesLibereesParChangementDePile ne rend que la queue depilee`() {
        val ancienne = listOf(Screen.Home, Screen.Frise, Screen.Suivis)

        assertEquals(
            emptyList<String>(),
            clesLibereesParChangementDePile(listOf(Screen.Home), listOf(Screen.Home, Screen.Frise)),
        )

        val apresPop = clesLibereesParChangementDePile(ancienne, listOf(Screen.Home, Screen.Frise))
        assertEquals(listOf(saveableKey(2, Screen.Suivis)), apresPop)

        val apresHome = clesLibereesParChangementDePile(ancienne, listOf(Screen.Home))
        assertEquals(listOf(saveableKey(1, Screen.Frise), saveableKey(2, Screen.Suivis)), apresHome)
    }

    // Le sens d'une transition (geste 7, « transitions avec profondeur ») se déduit de la seule
    // taille de la pile avant et après. Mutation : inverser les deux branches (`1 -> Pop`, `-1 ->
    // Push`) fait tomber les deux premières assertions.
    @Test
    fun `sensDeTransition distingue push, pop et remplace`() {
        assertEquals(SensTransition.Push, sensDeTransition(listOf(Screen.Home), listOf(Screen.Home, Screen.Frise)))
        assertEquals(SensTransition.Pop, sensDeTransition(listOf(Screen.Home, Screen.Frise), listOf(Screen.Home)))
        assertEquals(
            SensTransition.Remplace,
            sensDeTransition(listOf(Screen.Home, Screen.Frise, Screen.Suivis), listOf(Screen.Home)),
        )
        assertEquals(SensTransition.Remplace, sensDeTransition(listOf(Screen.Home), listOf(Screen.Home)))
    }
}
