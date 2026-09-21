package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
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

/** Ma progression, mise en forme pour l'écran — `VoyageResponse.toVoyageUi()` plus bas. */
data class VoyageUi(
    val configure: Boolean = false,
    /** La première année du Voyage (1895) — la carte commence là, quoi que le journal contienne de plus ancien. */
    val depart: Int = 1895,
    val anneeEnCours: Int = 1895,
    val parAnnee: Map<Int, AnneeVoyage> = emptyMap(),
)

fun VoyageResponse.toVoyageUi(): VoyageUi = VoyageUi(
    configure = configure,
    depart = depart,
    anneeEnCours = annee_en_cours,
    parAnnee = annees.associateBy { it.annee },
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
