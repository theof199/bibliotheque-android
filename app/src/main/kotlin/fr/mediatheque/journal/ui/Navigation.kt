package fr.mediatheque.journal.ui

import fr.mediatheque.journal.ui.theme.IconeTabler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.frise.AnneeFrise
import fr.mediatheque.journal.ui.frise.DecennieFrise
import fr.mediatheque.journal.ui.frise.TamponDecennie
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Les vingt écrans. Un écran qui a besoin d'une donnée la porte. */
sealed interface Screen {
    data object Home : Screen
    data object Search : Screen

    /**
     * `date` et `rating` pré-remplissent le formulaire (brief « importer Letterboxd », 16 septembre
     * 2026) : nuls partout ailleurs, portés seulement quand on ouvre depuis un candidat choisi dans
     * `Screen.RapportImport` — la date et la note que le back a lues sur cette ligne du fichier.
     */
    data class Form(val result: SearchResult, val date: LocalDate? = null, val rating: Int? = null) : Screen
    data object Profile : Screen
    data object Films : Screen
    data class Edit(val item: JournalItem) : Screen
    /** La connexion SensCritique, depuis le profil (brief du 14 septembre 2026). */
    data object SensCritique : Screen
    /** « Au ciné » : mes séances et les sorties en salle (brief du 14 septembre 2026). */
    data object Cinema : Screen
    /** La Frise : le cinéma du propriétaire, année par année (brief du 15 septembre 2026). */
    data object Frise : Screen
    /**
     * Le détail d'une année de la Frise : ses vus, et ce qui reste à voir sur le Plex.
     * `voyage` (brief du 16 septembre 2026) : le fragment déjà chargé par `FriseViewModel`
     * (`GET /me/voyage`) pour cette année — nul si le Voyage n'est pas configuré côté back, ou si
     * `/me/voyage` ne connaît pas encore cette année.
     */
    data class Annee(val annee: AnneeFrise, val voyage: AnneeVoyage? = null) : Screen

    /**
     * La fiche d'un film du Voyage (brief du 21 septembre 2026, « l'année en étages »), ouverte
     * depuis une affiche d'`AnneeScreen`. Porte des identifiants, pas les données : elle lit le
     * même `AnneeViewModel` que l'année d'où elle s'est ouverte (`Root.kt`, indexé sur `annee`),
     * `salleId` et `filmId` (l'identifiant de la ligne, pas le `tmdb_id`, pour rester exact même si
     * un film revenait dans deux salles) désignant le film dans son état déjà chargé.
     */
    data class FicheVoyage(val annee: Int, val salleId: String, val filmId: String) : Screen

    /**
     * Le rayon d'une décennie (« Le calendrier devient un rayon », brief du 16 septembre 2026),
     * ouvert en touchant l'étiquette d'une décennie sur le calendrier de la Frise. Porte
     * l'agrégat déjà calculé par `FriseViewModel` (jumeau de `Screen.Annee` ci-dessus) : un
     * rayon ne recharge rien, il relit l'instantané du calendrier au moment du geste.
     */
    data class Decennie(val decennie: DecennieFrise) : Screen

    /**
     * Ce que je suis, réalisateurs ou sagas (brief du 15 septembre 2026, puis
     * généralisé le même jour pour les sagas — l'onglet « Réalisateurs »
     * devient « Suivis »). Les deux segments vivent **dans** cet écran
     * (`SuivisUi.source`, mémorisé pour la session), pas dans deux `Screen`
     * séparés.
     */
    data object Suivis : Screen

    /** La recherche d'un réalisateur ou d'une saga à suivre, ouverte par le « + » de l'écran ci-dessus, sur le segment affiché. */
    data object ChercherSuivi : Screen

    /**
     * La fiche d'un réalisateur ou d'une saga suivis : ses films, et ce que
     * j'en ai vu. Elle porte `source` (laquelle des deux) et l'identifiant
     * TMDB de l'entité, pas l'entité entière : la liste et les filmographies
     * vivent dans le `ViewModel` partagé (`Root.kt`, clé `"suivis"`), et un
     * écran qui en porterait une copie montrerait celle d'avant après un
     * rafraîchissement.
     */
    data class FicheSuivi(val source: SourceSuivi, val tmdbId: Int) : Screen

    /**
     * Choisir un film à ajouter à une saga suivie (brief « les films de saga
     * ajoutés à la main », 15 septembre 2026), empilée depuis sa fiche —
     * seulement sur une saga, jamais sur un réalisateur. Réutilise l'écran
     * de recherche de films existant (`SearchScreen`), sur une instance de
     * `SearchViewModel` propre à cet écran, en mode « choisir » : toucher un
     * résultat ajoute le film et referme l'écran, sans jamais passer par le
     * formulaire — jumeau de `Screen.ChercherSuivi` pour cette différence-là.
     */
    data class ChoisirFilmDeSaga(val tmdbId: Int) : Screen

    /**
     * La page d'un réalisateur (brief du 21 septembre 2026, « la page réalisateur ») : sa fiche et
     * sa filmographie complète — la même, suivi ou non. Ouverte depuis la liste de Suivis (un
     * réalisateur), ou depuis un nom de réalisateur touchable ailleurs (`RealisateurResolveur`).
     * Porte l'identifiant, pas les données : `Root.kt` indexe le `RealisateurViewModel` dessus.
     */
    data class Realisateur(val tmdbId: Int) : Screen

    /**
     * La fiche simple d'un film (décision 2 du brief du 21 septembre 2026), ouverte au tap sur une
     * affiche de la filmographie dont `voyage` est nul — sinon c'est `Screen.FicheVoyage` qui
     * s'ouvre. `realisateurTmdbId` désigne la page dont elle vient : elle relit son
     * `RealisateurViewModel` (`Root.kt`, même clé) plutôt que de porter le film lui-même.
     */
    data class FicheFilm(val realisateurTmdbId: Int, val filmTmdbId: Int) : Screen

    /**
     * L'import Letterboxd (brief du 16 septembre 2026), empilée depuis le profil dès qu'un fichier
     * est choisi. Un seul écran pour les deux états du design (§5, §6) : « Import en cours… » tant
     * que `LetterboxdImportViewModel.ui` ne porte ni rapport ni erreur, le rapport ou le message
     * d'erreur ensuite — la même instance de `ViewModel`, indexée sur l'Activité (`Root.kt`, clé
     * `"letterboxd-import"`), pas de donnée portée ici : le retour système pendant l'attente dépile
     * l'écran sans annuler la requête, qui continue sur cette instance.
     */
    data object RapportImport : Screen

    /**
     * Le générique de fin d'une décennie bouclée (brief du 16 septembre 2026, phase 2) : plein
     * écran, sans barre du bas, empilé depuis le Voyage au moment où la frontière change de monde,
     * ou depuis un tampon du passeport (profil). Porte le tampon entier — l'écran ne recharge rien.
     */
    data class Generique(val tampon: TamponDecennie) : Screen
}

/** Les cinq entrées de la barre de navigation du bas (décision du propriétaire du 14 septembre 2026 ; Cinema ajoutée le même jour ; Frise le 15 septembre 2026, entre Home et Cinema ; Suivis le même jour, entre Frise et Cinema, sous le nom « Réalisateurs » jusqu'aux sagas, le même jour encore). */
enum class BottomTab { Home, Frise, Suivis, Cinema, Profile }

/**
 * Décide, pour un écran donné, si la barre du bas est visible et laquelle de ses entrées est
 * sélectionnée : `null` la cache. Fonction pure, sans dépendance à Compose, testée en JVM
 * (`NavigationTest.kt`) — c'est elle, et elle seule, qui fixe la matrice des dix-sept écrans, plutôt
 * que de la reposer à chaque site d'appel.
 *
 * « Mes films » affiche « Profil » sélectionnée, pas « Accueil » : dans `Root.kt`, cet écran ne
 * s'empile que depuis `Screen.Profile` (`onFilms`), jamais depuis l'accueil. `Screen.Annee`,
 * comme le formulaire et la recherche, est un détail empilé sans barre : on y arrive toujours
 * depuis `Screen.Frise`, jamais directement. Même règle pour la fiche d'un réalisateur ou d'une
 * saga et pour leur recherche, qui ne s'empilent que depuis `Screen.Suivis`.
 */
fun Screen.bottomBarTab(): BottomTab? = when (this) {
    Screen.Home -> BottomTab.Home
    Screen.Frise -> BottomTab.Frise
    Screen.Suivis -> BottomTab.Suivis
    Screen.Cinema -> BottomTab.Cinema
    Screen.Profile, Screen.Films -> BottomTab.Profile
    Screen.Search, is Screen.Form, is Screen.Edit, Screen.SensCritique, is Screen.Annee, is Screen.Decennie,
    is Screen.FicheVoyage, Screen.ChercherSuivi, is Screen.FicheSuivi, is Screen.ChoisirFilmDeSaga, Screen.RapportImport,
    is Screen.Generique, is Screen.Realisateur, is Screen.FicheFilm,
    -> null
}

/**
 * La barre de navigation du bas (Material 3), visible sur l'accueil, la Frise, « Suivis »,
 * « Au ciné », « Mes films » et le profil ; cachée sur le formulaire, la recherche, l'écran
 * SensCritique, le détail d'une année de la Frise, la fiche d'un réalisateur ou d'une saga et
 * leur recherche (décision du propriétaire du 14 septembre 2026, en remplacement de
 * l'`IconButton` profil de l'accueil, jugé inaccessible ; troisième entrée « Au ciné » ajoutée le
 * même jour ; quatrième entrée « Frise » le 15 septembre 2026, entre « Accueil » et « Au ciné » ;
 * cinquième entrée « Réalisateurs » le même jour, entre « Frise » et « Au ciné », renommée
 * « Suivis » le même jour encore quand les sagas l'ont rejointe — icône inchangée).
 * Toucher l’écran où l’on est déjà ne fait rien ; depuis « Mes films », « Profil » est surlignée
 * mais reste touchable et ramène au profil (`Root.kt` lui passe un `pop`).
 * Hauteur 64 dp, avec un libellé sous chaque icône (revue du 24 septembre 2026 : la barre sans
 * libellés, choisie le 14 septembre 2026 pour tenir sous 56 dp, laissait deviner l'icône du
 * Voyage) — encore en dessous des 80 dp par défaut d'un `NavigationBar` Material. Un `Row` sous
 * les mêmes insets et la même couleur que `NavigationBar`, avec les `NavigationBarItem` de
 * Material dedans, `alwaysShowLabel = true`. L'icône du Voyage devient une carte (`tabler:map`,
 * ex-`timeline`) : « Frise » d'origine désignait le calendrier, mais l'écran s'appelle « Voyage »
 * partout ailleurs dans l'appli.
 */
@Composable
fun JournalBottomBar(
    current: Screen,
    onHome: () -> Unit,
    onFrise: () -> Unit,
    onSuivis: () -> Unit,
    onCinema: () -> Unit,
    onProfile: () -> Unit,
) {
    val selected = current.bottomBarTab()
    Surface(color = NavigationBarDefaults.containerColor) {
        Row(
            Modifier.fillMaxWidth().windowInsetsPadding(NavigationBarDefaults.windowInsets).height(64.dp).selectableGroup(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            NavigationBarItem(
                selected = selected == BottomTab.Home,
                onClick = { if (current != Screen.Home) onHome() },
                icon = { IconeTabler("home", null) },
                label = { Text("Accueil") },
                alwaysShowLabel = true,
            )
            NavigationBarItem(
                selected = selected == BottomTab.Frise,
                onClick = { if (current != Screen.Frise) onFrise() },
                icon = { IconeTabler("map", null) },
                label = { Text("Voyage") },
                alwaysShowLabel = true,
            )
            NavigationBarItem(
                selected = selected == BottomTab.Suivis,
                onClick = { if (current != Screen.Suivis) onSuivis() },
                icon = { IconeTabler("movie", null) },
                label = { Text("Suivis") },
                alwaysShowLabel = true,
            )
            NavigationBarItem(
                selected = selected == BottomTab.Cinema,
                onClick = { if (current != Screen.Cinema) onCinema() },
                icon = { IconeTabler("ticket", null) },
                label = { Text("Au ciné") },
                alwaysShowLabel = true,
            )
            NavigationBarItem(
                selected = selected == BottomTab.Profile,
                onClick = { if (current != Screen.Profile) onProfile() },
                icon = { IconeTabler("user", null) },
                label = { Text("Profil") },
                alwaysShowLabel = true,
            )
        }
    }
}

/**
 * Une pile, et c'est tout. Pas de bibliothèque de navigation : six écrans, un
 * seul chemin, et `Crossfade` pour le fondu du design §7.
 */
class Navigator {
    var stack by mutableStateOf<List<Screen>>(listOf(Screen.Home))
        private set

    /**
     * Un message à montrer sur l'accueil, une fois — « Enregistré ». Un
     * `Channel` plutôt qu'un `State` : un `State` remis à `null` après
     * lecture change de valeur, ce qui change la clé du `LaunchedEffect` qui
     * l'affiche et annule sa coroutine avant que la snackbar n'ait fini —
     * elle clignote une frame (revue de la vague finale, Critique 1). Un
     * événement à un coup ne se relit pas : `receiveAsFlow()` sur un
     * `Channel` le rend, une fois, à qui collecte, sans jamais changer de clé.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    /**
     * Le Voyage (brief du 16 septembre 2026) : le `tmdb_id` du film qu'on vient de journaliser,
     * pour la carte « Et pendant ce temps… » sous le bandeau de l'accueil. Même raisonnement que
     * `_messages` — un événement à un coup, jamais un `State` qu'un `null` de retour effacerait
     * avant que la carte n'ait pu s'afficher.
     */
    private val _cartonRequests = Channel<Int>(Channel.BUFFERED)
    val cartonRequests: Flow<Int> = _cartonRequests.receiveAsFlow()

    /**
     * Le ticket (décision 2 du brief du 21 septembre 2026) : l'année de sortie du film qu'on vient
     * de journaliser, pour que `FriseViewModel.relireApresCreation` sache si elle vaut la peine
     * d'être relue — jumeau de `_cartonRequests` ci-dessus, un événement à un coup.
     */
    private val _ticketRelectures = Channel<Int>(Channel.BUFFERED)
    val ticketRelectures: Flow<Int> = _ticketRelectures.receiveAsFlow()

    /**
     * Un enregistrement réussi (création, correction ou suppression, 22 septembre 2026) : la Frise
     * ne relit son journal qu'à l'entrée sur ses propres écrans, jamais quand on revient dessus
     * depuis le formulaire (constat du propriétaire : « je note un film du Voyage, je reviens
     * dessus, il est encore vu comme non noté »). `_ticketRelectures` ci-dessus ne porte pas assez
     * pour combler ça : il n'émet que sur une création dont l'année est connue (`filmAnnee` reste
     * nul sur une correction ou une suppression, cf. `FormViewModel.edit`/`delete`), jamais sur les
     * deux autres gestes. Celui-ci émet à chaque succès du formulaire, quel que soit le film ou le
     * geste — c'est `home()` ci-dessous qui décide, sur la seule présence d'un message.
     */
    private val _enregistrements = Channel<Unit>(Channel.BUFFERED)
    val enregistrements: Flow<Unit> = _enregistrements.receiveAsFlow()

    /**
     * Le film enregistré (habillage du 23 septembre 2026, geste 9) : le titre et l'année qui
     * nourrissent le calque de célébration plein écran — jumeau de `_cartonRequests` (un événement
     * à un coup), posé seulement sur une création réussie, jamais une correction ni une
     * suppression (mêmes deux conditions que le carton et le ticket ci-dessus).
     */
    private val _filmsEnregistres = Channel<FilmEnregistre>(Channel.BUFFERED)
    val filmsEnregistres: Flow<FilmEnregistre> = _filmsEnregistres.receiveAsFlow()

    /**
     * Compteur dédié à `Screen.Search`, incrémenté seulement quand `push` y entre — jamais à un
     * `pop`, jamais sur un `push` vers un autre écran. Il sert à ne remettre à zéro la recherche
     * qu'à l'entrée depuis l'accueil (revue de la vague finale, mineur 8) : voir le commentaire
     * dans `Root.kt` pour pourquoi ce compteur vit hors du `Crossfade`.
     */
    var searchVisits by mutableIntStateOf(0)
        private set

    val current: Screen get() = stack.last()
    val canPop: Boolean get() = stack.size > 1

    fun push(screen: Screen) {
        if (screen is Screen.Search) searchVisits++
        stack = stack + screen
    }

    fun pop() { if (canPop) stack = stack.dropLast(1) }

    /**
     * Retour à l'accueil, pile vidée, avec un mot à dire — et, sur une création réussie (brief du
     * 16 septembre 2026), le `tmdb_id` dont l'accueil tire la carte « Et pendant ce temps… », et
     * l'année de sortie du film (brief du 21 septembre 2026, « le ticket ») qui déclenche la
     * relecture du ticket. Les deux sont nuls sur une correction ou une suppression.
     */
    fun home(message: String? = null, cartonTmdbId: Int? = null, filmAnnee: Int? = null, filmTitre: String? = null) {
        if (message != null) _messages.trySend(message)
        if (cartonTmdbId != null) _cartonRequests.trySend(cartonTmdbId)
        if (filmAnnee != null) _ticketRelectures.trySend(filmAnnee)
        // Un message ne sort que sur un geste terminé avec succès (« Enregistré », « Corrigé »,
        // « Supprimé ») — jamais sur un simple retour à l'accueil par la barre du bas.
        if (message != null) _enregistrements.trySend(Unit)
        // La célébration (geste 9) ne sort que sur une création (le carton en est le même signal :
        // `cartonTmdbId` reste nul sur une correction ou une suppression).
        if (cartonTmdbId != null && filmTitre != null) _filmsEnregistres.trySend(FilmEnregistre(filmTitre, filmAnnee))
        stack = listOf(Screen.Home)
    }
}

/** Le film et l'année qui nourrissent le calque de célébration (habillage du 23 septembre 2026, geste 9). */
data class FilmEnregistre(val titre: String, val annee: Int?)

@Composable
fun rememberNavigator(): Navigator = remember { Navigator() }

/**
 * Le défilement survit au retour (peaufinage du 23 septembre 2026) : `Root.kt` dispose la branche
 * quittée à chaque changement d'écran (`Crossfade`, puis `AnimatedContent` au geste 7), et
 * `rememberLazyListState`/`rememberScrollState` repartent donc à zéro sans un `rememberSaveableStateHolder()`
 * hoisté au-dessus, dont chaque écran lit et écrit son état par cette clé.
 *
 * Fonction pure de la position dans la pile et de l'écran : deux entrées différentes de la pile
 * (position ou écran différents) donnent deux clés, la même entrée (même position, même écran) la
 * même clé. La position seule ne suffirait pas (un `pop` suivi d'un `push` vers un autre écran
 * réutiliserait l'état de l'ancien occupant de la place) ; l'écran seul non plus (le revoir plus
 * bas dans la pile, empilé deux fois, partagerait son défilement avec lui-même).
 *
 * Le nom de la classe et `hashCode()`, pas `toString()` (relecture du 23 septembre 2026) :
 * `toString()` d'un `Screen.Annee` ou d'un `Screen.Edit` embarque l'objet entier, listes de films
 * comprises — une clé de plusieurs Ko dans le `SaveableStateHolder` et dans le Bundle d'instance.
 * `hashCode()` d'une `data class` reste stable pour un même contenu, courte, et distingue déjà
 * deux écrans différents dans l'immense majorité des cas.
 */
fun saveableKey(index: Int, screen: Screen): String = "$index:${screen::class.simpleName}:${screen.hashCode()}"

/**
 * Les clés à libérer de `rememberSaveableStateHolder()` quand la pile change : celles des entrées
 * de l'ancienne pile qui n'ont plus leur pareille (même position, même écran) dans la nouvelle —
 * essentiellement la queue dépilée par un `pop` ou vidée par `home()`. Un écran rouvert plus tard
 * reçoit donc un état neuf (« repart en haut »), sans que l'ancien n'ait jamais fui.
 */
fun clesLibereesParChangementDePile(ancienne: List<Screen>, nouvelle: List<Screen>): List<String> =
    ancienne.mapIndexedNotNull { index, screen ->
        val cle = saveableKey(index, screen)
        val nouvelEcran = nouvelle.getOrNull(index)
        if (nouvelEcran == null || saveableKey(index, nouvelEcran) != cle) cle else null
    }

/**
 * Le sens d'une transition entre deux piles (geste 7 du peaufinage du 23 septembre 2026,
 * « transitions avec profondeur ») : `Push` quand la nouvelle pile est exactement un cran plus
 * profonde (`Navigator.push`), `Pop` quand elle est exactement un cran moins profonde
 * (`Navigator.pop`), `Remplace` sinon — en particulier `home()`, qui vide la pile d'un coup et
 * peut donc la faire descendre de plus d'un cran. Fonction pure, déduite de la seule taille de la
 * pile avant et après.
 */
enum class SensTransition { Push, Pop, Remplace }

fun sensDeTransition(ancienne: List<Screen>, nouvelle: List<Screen>): SensTransition =
    when (nouvelle.size - ancienne.size) {
        1 -> SensTransition.Push
        -1 -> SensTransition.Pop
        else -> SensTransition.Remplace
    }

/**
 * Deux secondes (design §6), jamais la durée par défaut de Material : l'API
 * `SnackbarHostState.showSnackbar` n'a pas de paramètre de durée libre, donc
 * une snackbar indéfinie qu'on referme nous-mêmes après le délai (décision 3
 * de la tâche 5). Centralisé ici, un seul endroit, pour que la tâche 6
 * (le geste « Enregistré ») s'en serve aussi plutôt que de le répéter.
 *
 * `withTimeoutOrNull` plutôt qu'un `delay` suivi d'un `dismiss()` séparé : ce
 * dernier referme la snackbar *courante* du `SnackbarHostState`, pas
 * forcément la sienne — si un second appel s'est glissé entre-temps, il
 * fermerait celui de l'autre et laisserait le sien orphelin, `Indefinite`,
 * que plus personne ne referme (revue de la tâche 5). L'annulation par le
 * timeout efface elle-même `currentSnackbarData`.
 */
suspend fun SnackbarHostState.showBriefly(message: String) {
    withTimeoutOrNull(2_000) { showSnackbar(message, duration = SnackbarDuration.Indefinite) }
}
