package fr.mediatheque.journal.ui.home

import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.suivis.EnCours
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * « Accueil · la porte d'entrée » (planche de Léon validée le 25 septembre 2026) : les règles de
 * l'écran en fonctions pures, testées en JVM sans réseau ni `ViewModel` (`HomeEtatsTest.kt`), comme
 * `FilmsEtats.kt` pour Mes films. Le carrousel « Ensuite » y a déménagé depuis `HomeScreen.kt` le
 * même jour, pour que l'écran ne garde que des composables.
 */

/**
 * Une carte du carrousel « Ensuite » (point 5) : laquelle des trois sources — Plex, réalisateur en
 * cours, saga en cours — jamais mêlées entre elles, `cartesEnsuite` (fonction pure, testée) décide
 * lesquelles existent et dans quel ordre.
 */
sealed interface CarteEnsuite {
    data class Plex(val film: PlexFilm) : CarteEnsuite
    data class Realisateur(val encours: EnCours) : CarteEnsuite
    data class Saga(val encours: EnCours) : CarteEnsuite
}

/**
 * Les pages du carrousel « Ensuite », dans l'ordre Plex puis réalisateur puis saga : chaque source
 * n'y figure que si elle a quelque chose à proposer, jamais un `null` glissé dans la liste.
 */
fun cartesEnsuite(plex: PlexFilm?, realisateur: EnCours?, saga: EnCours?): List<CarteEnsuite> =
    listOfNotNull(
        plex?.let { CarteEnsuite.Plex(it) },
        realisateur?.let { CarteEnsuite.Realisateur(it) },
        saga?.let { CarteEnsuite.Saga(it) },
    )

private val NOM_DU_JOUR: DateTimeFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.FRENCH)
private val NOM_DU_MOIS: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH)

/**
 * Le fronton de l'accueil : « Jeudi 24 septembre », le jour en toutes lettres à la place du
 * titre « Journal » d'avant le 25 septembre 2026. Sans l'année — c'est aujourd'hui, elle va de soi.
 * La capitale initiale, comme `formatMoisAnnee` (`Format.kt`) : le mot ouvre la ligne, alors que
 * `Locale.FRENCH` écrit les jours en minuscules. « Jeudi 1er octobre » : le premier du mois
 * s'ordinalise, comme dans `formatDate` — d'où deux formats plutôt qu'un seul `EEEE d MMMM`.
 */
fun formatJour(date: LocalDate): String {
    val jour = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()
    val nom = date.format(NOM_DU_JOUR).replaceFirstChar { it.titlecase(Locale.FRENCH) }
    return "$nom $jour ${date.format(NOM_DU_MOIS)}"
}

/**
 * Le compte à droite du fronton, lu dans `GET /stats` (`ProfileUi.thisYear`, l'année civile en
 * cours) : « 12 films cette année », « 1 film cette année », « Aucun film encore » à zéro — un
 * « 0 film cette année » sonnerait comme un reproche sur la porte d'entrée. `null` tant que
 * `/stats` n'a pas répondu : rien ne s'affiche alors, jamais un zéro provisoire (même règle que
 * `compteEnTete` de Mes films).
 */
fun compteAccueil(thisYear: Int?): String? = when (thisYear) {
    null -> null
    0 -> "Aucun film encore"
    1 -> "1 film cette année"
    else -> "$thisYear films cette année"
}

/**
 * La ligne du court sous le titre du long dans la carte « Ce soir » : « + Un chien andalou ·
 * court ». Sans durée : le back (`SeancePriseVoyage`) ne la porte pas au 25 septembre 2026. Le
 * « + » ouvre la chaîne, que la carte colore à part.
 */
fun ligneCourt(titreCourt: String): String = "+ $titreCourt · court"

/**
 * Le journal est vide pour de bon : aucune entrée **et** la dernière page lue. Tant qu'une page est
 * en route, la liste vide ne prouve rien — la vitrine vide ne doit pas clignoter au premier
 * chargement, ni le bouton rond disparaître puis revenir.
 */
fun journalVide(items: List<JournalItem>, endReached: Boolean): Boolean = items.isEmpty() && endReached
