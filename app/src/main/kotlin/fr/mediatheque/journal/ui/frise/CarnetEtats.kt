package fr.mediatheque.journal.ui.frise

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.ui.formatDateTime
import java.io.File
import java.io.IOException

/**
 * Le carnet d'une année (brief du 22 septembre 2026, « le carnet ») : un PDF fabriqué à la
 * demande, jamais ouvert dans l'appli — juste téléchargé puis remis au système (`ouvrirCarnet`).
 * Fonctions pures, testées en JVM, comme `PodiumEtats.kt`/`SeanceEtats.kt` à côté.
 */

/** Le carnet d'une année, tel qu'`AnneeViewModel` le range depuis `AnneeVoyageDetailResponse.carnet`. */
data class CarnetUi(val fabriqueLe: String, val pages: Int)

/** « 1 page », « 12 pages » — l'accord, partagé par la fiche d'année et le profil. */
private fun accordPages(pages: Int): String = "$pages ${if (pages == 1) "page" else "pages"}"

/**
 * Le libellé du bouton « Faire le carnet »/« Refaire le carnet »/« Le carnet se fabrique… »
 * (décision 2 du brief du 22 septembre 2026, « le carnet ») : fonction pure, testée en JVM.
 */
fun libelleBoutonCarnet(carnet: CarnetUi?, carnetEnCours: Boolean): String = when {
    carnetEnCours -> "Le carnet se fabrique…"
    carnet == null -> "Faire le carnet"
    else -> "Refaire le carnet"
}

/** « Fabriqué le 21 septembre 2026 · 12 pages » (décision 2) : fonction pure, testée en JVM. */
fun ligneFabriqueLeCarnet(carnet: CarnetUi): String =
    "Fabriqué le ${formatDateTime(carnet.fabriqueLe)} · ${accordPages(carnet.pages)}"

/** Un carnet déjà fabriqué, tel que `CarnetsViewModel` (`ui/profile/`) le range depuis `GET /me/voyage/carnets`. */
data class CarnetProfilUi(val annee: Int, val pages: Int, val fabriqueLe: String)

/** Une ligne du bloc « Carnets » du profil (décision 3) : fabriqué, ou en fabrication. */
sealed interface LigneCarnetProfil {
    val annee: Int
    data class Fabrique(val carnet: CarnetProfilUi) : LigneCarnetProfil {
        override val annee: Int get() = carnet.annee
    }
    data class EnFabrication(override val annee: Int) : LigneCarnetProfil
}

/**
 * Les lignes du bloc « Carnets » du profil (décision 3 du brief du 22 septembre 2026), année
 * croissante : la fabrication en cours l'emporte sur un carnet déjà fabriqué la même année (un
 * « Refaire » en cours), pour ne jamais lister deux fois la même année. Fonction pure, testée en
 * JVM.
 */
fun lignesCarnetsProfil(carnets: List<CarnetProfilUi>, enCours: List<Int>): List<LigneCarnetProfil> {
    val enCoursSet = enCours.toSet()
    val fabriques = carnets.filterNot { it.annee in enCoursSet }.map { LigneCarnetProfil.Fabrique(it) }
    val fabrication = enCours.map { LigneCarnetProfil.EnFabrication(it) }
    return (fabriques + fabrication).sortedBy { it.annee }
}

/**
 * « 1895 · 12 pages · 21 septembre 2026 », ou « 1896 · en fabrication » (décision 3) : fonction
 * pure, testée en JVM.
 */
fun texteLigneCarnetProfil(ligne: LigneCarnetProfil): String = when (ligne) {
    is LigneCarnetProfil.Fabrique -> "${ligne.carnet.annee} · ${accordPages(ligne.carnet.pages)} · ${formatDateTime(ligne.carnet.fabriqueLe)}"
    is LigneCarnetProfil.EnFabrication -> "${ligne.annee} · en fabrication"
}

/** « carnet-1895.pdf » (décision 4) : fonction pure, testée en JVM. */
fun nomFichierCarnet(annee: Int): String = "carnet-$annee.pdf"

/**
 * L'année proposée pour son carnet après l'usage d'un ticket qui vient de faire avancer l'année en
 * cours (décision 1 du brief du 22 septembre 2026) — celle qu'on quitte
 * (`FrontiereAvancee.anneeBouclee`), jamais celle que le ticket ouvre vers. Même site que le clap
 * et la snackbar « *N* dans la boîte ! » (`VoyageScreen.kt`, `vm.avancees`) : pas un site de plus,
 * un ticket pouvant s'encaisser depuis la fiche d'une année, le calque ou le portefeuille sans
 * qu'aucun des trois ne sache lui-même que l'année en cours vient d'avancer. Fonction pure, testée
 * en JVM.
 */
fun anneeProposeeCarnet(avancee: FrontiereAvancee): Int = avancee.anneeBouclee

/**
 * Ouvre le PDF d'un carnet (décision 4 du brief du 22 septembre 2026) : le télécharge
 * (`telecharger`, l'appel réseau du `ViewModel` de l'écran — sa session déjà portée par son
 * cookie ; `null` dit qu'il a déjà géré l'échec lui-même, typiquement une session expirée) dans
 * `cacheDir/carnets/carnet-1895.pdf`, puis `ACTION_VIEW` sur une URI `FileProvider`,
 * `FLAG_GRANT_READ_URI_PERMISSION` — jamais de visionneuse dans l'appli, comme `ouvrirPlex`
 * (`FicheVoyageScreen.kt`). `onMessage` ne reçoit que l'échec : un succès parle par l'`Intent`.
 */
suspend fun ouvrirCarnet(contexte: Context, annee: Int, telecharger: suspend () -> ByteArray?, onMessage: suspend (String) -> Unit) {
    val octets = try {
        telecharger() ?: return
    } catch (e: ApiError) {
        onMessage(e.message ?: "Le carnet n’a pas pu être téléchargé.")
        return
    }
    val fichier = try {
        val dossier = File(contexte.cacheDir, "carnets").apply { mkdirs() }
        File(dossier, nomFichierCarnet(annee)).apply { writeBytes(octets) }
    } catch (e: IOException) {
        onMessage("Le carnet n’a pas pu être téléchargé.")
        return
    }
    val uri = FileProvider.getUriForFile(contexte, "${contexte.packageName}.fileprovider", fichier)
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "application/pdf")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    // Lancer puis rattraper, plutôt que `resolveActivity` avant : la visibilité des paquets
    // (Android 11+) rend `resolveActivity` nul dès que le manifeste ne déclare pas la requête —
    // le `<queries>` du manifeste la déclare, et l'exception reste le seul constat fiable.
    try {
        contexte.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        onMessage("Aucune application ne sait ouvrir un PDF.")
    }
}
