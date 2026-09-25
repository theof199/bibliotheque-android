package fr.mediatheque.journal.ui.fiche

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalMedia
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.form.BoutonLeFilm
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.frise.mondeDe
import fr.mediatheque.journal.ui.realisateur.NomRealisateurTouchable
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur

/**
 * La fiche d'une entrée du journal (`Screen.FicheEntree`, « la fiche · trois visages », reprise
 * validée du 25 septembre 2026) : ce que j'ai vu, lu plutôt que corrigé. Toucher une entrée — Mes
 * films, la grille de l'accueil, une séance d'« Au ciné », la fiche d'une saga, le rayon d'une
 * décennie — ouvrait jusqu'ici directement le formulaire de correction ; on ouvre désormais cette
 * fiche, et le formulaire est à un bouton.
 *
 * Aucun appel réseau : tout vient du `JournalItem` déjà chargé par l'écran d'où l'on vient (titre,
 * affiche, année, réalisateur, note, réactions). Le carton de « Le film » ne se lit qu'au tap, sur
 * un `CartonViewModel` sans veille (`FicheRoutes.kt`). Le commentaire de l'entrée n'apparaît pas —
 * décision de la reprise, sur les trois fiches.
 *
 * « Corriger » pousse `Screen.Edit` avec la même entrée : la correction garde son écran, ses
 * règles et son retour à l'accueil après « Corrigé » ou « Supprimé » (`FormScreen`) ; la fiche n'en
 * duplique rien.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FicheEntreeScreen(
    item: JournalItem,
    carton: CartonViewModel?,
    resolveur: RealisateurResolveur,
    onBack: () -> Unit,
    onCorriger: () -> Unit,
    onOuvrirRealisateur: (Int) -> Unit,
    volante: AfficheVolante?,
) {
    val media = item.media
    val fond = MaterialTheme.colorScheme.background
    Scaffold(containerColor = fond) { padding ->
        // Pas de marge du haut : l'affiche passe sous la barre d'état, le disque du retour s'y
        // range lui-même (`statusBarsPadding`, `EnTeteFiche`).
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            EnTeteFiche(
                affiche = media.cover_url,
                titre = media.title,
                // `JournalMedia` ne porte pas de titre original.
                titreOriginal = null,
                note = item.entry.rating,
                realisateur = realisateurDe(media, resolveur, onOuvrirRealisateur),
                anneeEtDuree = anneeEtDuree(media.year, null),
                etiquette = etiquetteDecennie(media.year),
                couleurEtiquette = media.year?.let { mondeDe(it).accent } ?: MaterialTheme.colorScheme.secondary,
                fond = fond,
                onBack = onBack,
                volante = volante,
            )
            Spacer(Modifier.height(16.dp))
            PucesReactions(item.carnet.reactions, Modifier.padding(horizontal = 16.dp))
            BoutonsFicheEntree(carton, media.title, media.year, onCorriger)
        }
    }
}

/**
 * Le nom du réalisateur, touchable quand l'entrée porte un `tmdb_id` (`external_id` entier) — sinon
 * lu seulement. Sans nom connu, rien : la fiche ne le résout pas elle-même (ce serait un appel de
 * plus), comme le formulaire de correction avant elle.
 */
private fun realisateurDe(
    media: JournalMedia,
    resolveur: RealisateurResolveur,
    onOuvrirRealisateur: (Int) -> Unit,
): (@Composable () -> Unit)? {
    val nom = media.director?.takeIf { it.isNotBlank() } ?: return null
    val tmdbId = media.external_id.toIntOrNull()
        ?: return { Text(nom, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface) }
    return {
        NomRealisateurTouchable(
            filmTmdbId = tmdbId,
            nomConnu = nom,
            resolveur = resolveur,
            onOuvrirRealisateur = onOuvrirRealisateur,
            color = MaterialTheme.colorScheme.onSurface,
            chevron = true,
        )
    }
}

/**
 * « Corriger », seul bouton corail de la fiche, puis « Le film » au filet or, dernier comme sur
 * les deux autres fiches. Pas de Plex ni de Sir : un film déjà vu n'a rien à demander. Forme et
 * taille communes aux trois piles (`FORME_BOUTON_FICHE`, `tailleBoutonFiche`).
 */
@Composable
private fun BoutonsFicheEntree(carton: CartonViewModel?, titre: String, annee: Int?, onCorriger: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onCorriger, modifier = Modifier.tailleBoutonFiche(), shape = FORME_BOUTON_FICHE) { Text("Corriger") }
        carton?.let {
            BoutonLeFilm(
                it,
                titreConnu = titre,
                modifier = Modifier.tailleBoutonFiche(),
                bord = MaterialTheme.colorScheme.secondary,
                shape = FORME_BOUTON_FICHE,
                sousTitre = annee?.toString(),
            )
        }
    }
}
