package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.TamponVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse

/**
 * Le Voyage : les états d'une année et d'une génération (chronique, carton ou fournée), en
 * fonctions pures — testées en JVM, sans réseau ni `ViewModel`.
 *
 * Réécrit pour le brief du 21 septembre 2026 (« l'année en étages ») : l'année n'est plus une case
 * qu'on coche (`faite`/`ouverte`/`verrouillee`) mais un lieu qu'on creuse tant qu'on veut —
 * `ouverte` avant l'année en cours (toujours creusable), `en_cours` sur elle, `verrouillee` après.
 */

/** Le statut d'une année du Voyage, tel que `GET /me/voyage` le donne par année. */
enum class StatutAnneeVoyage { OUVERTE, EN_COURS, VERROUILLEE }

/** `INCONNU` (nul) : l'année n'est pas encore dans `/me/voyage` (chargement pas terminé, ou année hors de la plage servie). */
fun statutAnneeVoyage(brut: String?): StatutAnneeVoyage? = when (brut) {
    "ouverte" -> StatutAnneeVoyage.OUVERTE
    "en_cours" -> StatutAnneeVoyage.EN_COURS
    "verrouillee" -> StatutAnneeVoyage.VERROUILLEE
    else -> null
}

/**
 * Le ticket qu'on n'a pas encore montré (brief du 21 septembre 2026, « le ticket ») : ce que le
 * calque de `Root.kt` affiche — `emis_le` n'a pas sa place ici, rien ne s'en sert à l'écran.
 */
data class TicketAMontrerUi(val annee: Int, val motif: String)

/** Ma progression, mise en forme pour l'écran — `VoyageResponse.toVoyageUi()` plus bas. */
data class VoyageUi(
    val configure: Boolean = false,
    /** La première année du Voyage (1895) — la carte commence là, quoi que le journal contienne de plus ancien. */
    val depart: Int = 1895,
    val anneeEnCours: Int = 1895,
    val parAnnee: Map<Int, AnneeVoyage> = emptyMap(),
    /** Non nul une seule fois, tant que je ne l'ai pas montré (brief du 21 septembre 2026, « le ticket »). */
    val ticketAMontrer: TicketAMontrerUi? = null,
    /** Le passeport : les décennies bouclées, décennie croissante (étape 5, « les récompenses »). */
    val tampons: List<TamponVoyage> = emptyList(),
)

fun VoyageResponse.toVoyageUi(): VoyageUi = VoyageUi(
    configure = configure,
    depart = depart,
    anneeEnCours = annee_en_cours,
    parAnnee = annees.associateBy { it.annee },
    ticketAMontrer = ticket_a_montrer?.let { TicketAMontrerUi(it.annee, it.motif) },
    tampons = tampons,
)

/** Le statut d'une année précise, tel que la carte et `Screen.Decennie` le colorent. */
fun statutVoyage(annee: Int, voyage: VoyageUi): StatutAnneeVoyage? = statutAnneeVoyage(voyage.parAnnee[annee]?.statut)

/**
 * L'état d'une génération (chronique d'année, carton de film ou année en détail) qui se relit à
 * intervalle — « en préparation » relu toutes les *N* secondes, un nombre d'essais donné au plus,
 * puis abandon.
 */
enum class EtatChronique { PRETE, EN_PREPARATION, ABANDON, NON_CONFIGURE }

/** Le plafond de relectures avant abandon du carton d'un film (« dix fois au plus »), toutes les trois secondes. */
const val CHRONIQUE_ESSAIS_MAX = 10

/**
 * Le plafond de la chronique (ou de l'ouverture) d'une **année** : à cinq secondes l'essai, trois
 * minutes d'attente au lieu de cinquante secondes — le chroniqueur d'une année écrit beaucoup plus
 * long que le carton d'un film, et la minute passée à cinquante secondes laissait le cartouche vide
 * alors que le texte arrivait.
 */
const val CHRONIQUE_ANNEE_ESSAIS_MAX = 36

/**
 * Décide l'état suivant à partir d'une réponse `{ configure, statut }` (les DTO du Voyage
 * partagent cette forme) et du compte d'essais déjà faits.
 *
 * `NON_CONFIGURE` et `PRETE` ne comptent jamais d'essai de plus : rien n'est en train d'attendre.
 * `ABANDON` est atteint au dixième essai `en_preparation`, pas au onzième — un appelant qui
 * s'arrête sur `ABANDON` ne redemande donc jamais une onzième fois.
 */
fun etatChroniqueSuivant(
    configure: Boolean,
    statut: String?,
    essaisPrecedents: Int,
    plafond: Int = CHRONIQUE_ESSAIS_MAX,
): Pair<EtatChronique, Int> {
    if (!configure) return EtatChronique.NON_CONFIGURE to essaisPrecedents
    if (statut == "prete") return EtatChronique.PRETE to essaisPrecedents

    val essais = essaisPrecedents + 1
    return if (essais >= plafond) EtatChronique.ABANDON to essais else EtatChronique.EN_PREPARATION to essais
}

/**
 * Une fournée (« En voir plus » sur une salle, brief du 21 septembre 2026) qui se relit : elle
 * s'arrête dès que `fournee_en_cours` retombe côté back (de nouveaux films, ou « Salle épuisée »),
 * abandon sinon au plafond — même intervalle et même compte que le carton d'un film
 * (`CHRONIQUE_ESSAIS_MAX`, trois secondes, dix fois) : une fournée est un appel du même ordre de
 * grandeur (spec du 19 septembre 2026, §7 : « une fournée ~1,5 ¢ »).
 */
enum class EtatFournee { EN_COURS, TERMINEE, ABANDON }

fun etatFourneeSuivant(
    fourneeEnCours: Boolean,
    essaisPrecedents: Int,
    plafond: Int = CHRONIQUE_ESSAIS_MAX,
): Pair<EtatFournee, Int> {
    if (!fourneeEnCours) return EtatFournee.TERMINEE to essaisPrecedents

    val essais = essaisPrecedents + 1
    return if (essais >= plafond) EtatFournee.ABANDON to essais else EtatFournee.EN_COURS to essais
}

/**
 * La relecture après un enregistrement réussi (décision 2 du brief du 21 septembre 2026, « le
 * ticket ») : seul un film de l'année en cours peut avoir fait mûrir un ticket cette fois-ci — un
 * film d'une année déjà creusée, ou pas encore ouverte, n'a aucune chance d'en avoir gagné un.
 * `anneeFilm` nul (année inconnue) ne relit jamais non plus.
 */
fun doitRelireApresCreation(anneeFilm: Int?, anneeEnCours: Int): Boolean = anneeFilm == anneeEnCours

/** Le plafond de la relecture du ticket : douze essais à cinq secondes l'un, une minute au plus. */
const val TICKET_RELECTURE_ESSAIS_MAX = 12

enum class EtatRelectureTicket { EN_COURS, TROUVE, ABANDON }

/**
 * Décide l'état suivant de la relecture d'un ticket après un enregistrement (décision 2 du brief
 * du 21 septembre 2026) : s'arrête dès qu'il est là, abandon au plafond sinon — jumeau
 * d'`etatFourneeSuivant`.
 */
fun etatRelectureTicketSuivant(
    ticketTrouve: Boolean,
    essaisPrecedents: Int,
    plafond: Int = TICKET_RELECTURE_ESSAIS_MAX,
): Pair<EtatRelectureTicket, Int> {
    if (ticketTrouve) return EtatRelectureTicket.TROUVE to essaisPrecedents

    val essais = essaisPrecedents + 1
    return if (essais >= plafond) EtatRelectureTicket.ABANDON to essais else EtatRelectureTicket.EN_COURS to essais
}

/**
 * La relecture après un tap « Ajouter à la chronique » (décision 1 du brief du 21 septembre 2026,
 * « la chronique et les salles ») : s'arrête dès que le paragraphe est là, abandon au plafond sinon
 * — réutilise `etatChroniqueSuivant` avec un statut synthétique, jumeau d'`etatFourneeSuivant` et
 * d'`etatRelectureTicketSuivant`, sur le plafond de l'année (`CHRONIQUE_ANNEE_ESSAIS_MAX`) : une
 * relecture de trois minutes, pas cinquante secondes — un paragraphe est un appel du même ordre de
 * grandeur qu'une fournée, pas du carton d'un film.
 */
fun etatParagrapheSuivant(
    paragrapheTrouve: Boolean,
    essaisPrecedents: Int,
    plafond: Int = CHRONIQUE_ANNEE_ESSAIS_MAX,
): Pair<EtatChronique, Int> = etatChroniqueSuivant(
    configure = true,
    statut = if (paragrapheTrouve) "prete" else "en_preparation",
    essaisPrecedents = essaisPrecedents,
    plafond = plafond,
)

/**
 * L'éligibilité du bouton « Ajouter à la chronique » sur l'écran de correction du journal
 * (`Screen.Edit`, décision 1 du brief du 21 septembre 2026) : le statut lu dans `/me/voyage`, déjà
 * chargé par la Frise — année en cours ou ouverte seulement, jamais verrouillée ni inconnue (le
 * Voyage pas encore chargé, ou le film hors de la plage qu'il sert).
 */
fun eligibleChroniqueDepuisEdition(statutAnnee: StatutAnneeVoyage?): Boolean =
    statutAnnee == StatutAnneeVoyage.OUVERTE || statutAnnee == StatutAnneeVoyage.EN_COURS

/**
 * L'état de la zone « Ouvrir une nouvelle salle » (décision 3 du brief du 21 septembre 2026) : le
 * bouton, l'étagère fantôme pendant que la demande s'écrit, ou le motif du refus sous le bouton —
 * jamais les deux à la fois. `statutDemande` vient de `demande_salle.statut`, nul quand il n'y a
 * pas de demande en cours ni de refus pas encore vu.
 */
enum class EtatZoneSalleVoyage { BOUTON, FANTOME, REFUS }

fun etatZoneSalleVoyage(statutDemande: String?): EtatZoneSalleVoyage = when (statutDemande) {
    "en_cours" -> EtatZoneSalleVoyage.FANTOME
    "refusee" -> EtatZoneSalleVoyage.REFUS
    else -> EtatZoneSalleVoyage.BOUTON
}
