package fr.mediatheque.journal.ui.form

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen

/** `Screen.Form` : la création d'un visionnage, depuis un résultat de recherche ou une affiche pré-remplie. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeForm(screen: Screen.Form) {
    // Ce `ViewModel` est indexé sur l'Activité (jumeau du piège réglé sur
    // `SearchViewModel.reset()`, `Root.kt`) : la clé ne donne pas de portée,
    // elle nomme une case dans son magasin. Une clé fixe (`"form"`) rendrait
    // le `FormViewModel` du premier film à tous les suivants ; l'identité du
    // film dans la clé ouvre une case par film.
    val form: FormViewModel = viewModel(key = "form:${screen.result.source}:${screen.result.external_id}") {
        FormViewModel(
            container.api,
            FormMode.Create(screen.result, screen.date, screen.rating),
            container.sensCritiqueSync,
            session::expire,
        )
    }
    FormScreen(
        form,
        nav = nav,
        realisateurResolveur = realisateurResolveur,
        onBack = nav::pop,
        reactionsFavorites = reactionsFavorites,
    )
}

/** `Screen.Edit` : la correction d'un visionnage, « Corriger », « Supprimer ». */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeEdit(screen: Screen.Edit) {
    // Indexé sur l'entrée corrigée (jumeau de `Screen.Form` ci-dessus) : une
    // clé fixe rendrait le `FormViewModel` du premier visionnage corrigé à
    // tous les suivants. Il ne reçoit jamais `nav` au constructeur (revue de
    // la tâche 6, décision 1 de la tâche 7) : c'est `FormScreen`, reçu ici
    // avec le `nav` du moment, qui consomme `ui.done` et referme la boucle
    // par `nav.home(...)` — le retour à l'accueil après « Corrigé » ou
    // « Supprimé » vient de là, pas d'ici ; « Mes films » se recharge à sa
    // prochaine ouverture, par le `LaunchedEffect` de `FilmsRoute.kt`.
    //
    // La clé porte aussi `screen.item.hashCode()` (revue du tour de
    // correction 1) : `JournalItem` est une `data class`, son hash change
    // avec la note, les réactions, le commentaire ou la date. Sans lui, la
    // clé ne dépendait que de l'identifiant de l'entrée — rouvrir une fiche
    // déjà corrigée retombait sur l'ancien `FormViewModel`, encore dans le
    // magasin de l'Activité avec le brouillon d'avant la correction, et
    // ignorait le `screen.item` frais que « Mes films » vient de fournir.
    // Les anciennes instances (une par version corrigée) restent dans ce
    // magasin pour la vie de l'Activité : négligeable pour un usage
    // personnel.
    val form: FormViewModel = viewModel(key = "edit:${screen.item.entry.id}:${screen.item.hashCode()}") {
        FormViewModel(container.api, FormMode.Edit(screen.item), container.sensCritiqueSync, session::expire)
    }
    // « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») :
    // le carton en pop-in, `poll = false` — éditer un film ne doit jamais en
    // déclencher l'écriture.
    val cartonTmdbId = screen.item.media.external_id.toIntOrNull()
    val carton: CartonViewModel? = cartonTmdbId?.let { id ->
        viewModel(key = "carton-edit-$id") { CartonViewModel(container.api, id, poll = false, session::expire) }
    }
    FormScreen(
        form,
        nav = nav,
        realisateurResolveur = realisateurResolveur,
        onBack = nav::pop,
        carton = carton,
        // L'affiche partagée (geste 8) : l'autre bout de la paire ouverte
        // depuis l'accueil ou « Mes films » — même clé qu'elles.
        volante = AfficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-journal-${screen.item.entry.id}"),
        reactionsFavorites = reactionsFavorites,
    )
}
