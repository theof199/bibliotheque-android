package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.VoyageResponse

/**
 * Le Voyage (brief du 16 septembre 2026, phase 1 « le moteur ») : les états d'une année et d'une
 * génération (chronique ou carton), en fonctions pures — testées en JVM, sans réseau ni
 * `ViewModel`, comme `construireFrise`/`construireDecennies` juste au-dessus.
 */

/** Le statut d'une année du Voyage, tel que `GET /me/voyage` le donne par année. */
enum class StatutAnneeVoyage { FAITE, OUVERTE, VERROUILLEE }

/** `INCONNU` : l'année n'est pas encore dans `/me/voyage` (chargement pas terminé, ou année hors de la plage servie). */
fun statutAnneeVoyage(brut: String?): StatutAnneeVoyage? = when (brut) {
    "faite" -> StatutAnneeVoyage.FAITE
    "ouverte" -> StatutAnneeVoyage.OUVERTE
    "verrouillee" -> StatutAnneeVoyage.VERROUILLEE
    else -> null
}

/** Ma progression, mise en forme pour l'écran — `VoyageResponse.toVoyageUi()` plus bas. */
data class VoyageUi(
    val configure: Boolean = false,
    /** La première année du Voyage (1895) — la carte commence là, quoi que le journal contienne de plus ancien. */
    val depart: Int = 1895,
    val frontiere: Int? = null,
    val frontiereOuverte: Boolean = false,
    val parAnnee: Map<Int, AnneeVoyage> = emptyMap(),
)

fun VoyageResponse.toVoyageUi(): VoyageUi = VoyageUi(
    configure = configure,
    depart = depart,
    frontiere = frontiere,
    frontiereOuverte = frontiere_statut == "ouverte",
    parAnnee = annees.associateBy { it.annee },
)

/** Le statut d'une année précise, tel que la Frise et `Screen.Decennie` le colorent. */
fun statutVoyage(annee: Int, voyage: VoyageUi): StatutAnneeVoyage? = statutAnneeVoyage(voyage.parAnnee[annee]?.statut)

/** La phrase de tête du calendrier (brief du 16 septembre 2026) — remplace « Tu en es à » du Plex. */
fun phraseFrontiere(voyage: VoyageUi): String? {
    val annee = voyage.frontiere ?: return null
    return if (voyage.frontiereOuverte) "Tu en es à $annee" else "$annee se prépare…"
}

/**
 * L'état d'une génération (chronique d'année ou carton de film) qui se relit à intervalle — brief
 * du 16 septembre 2026 : « en préparation » relu toutes les *N* secondes, dix fois au plus, puis
 * abandon.
 */
enum class EtatChronique { PRETE, EN_PREPARATION, ABANDON, NON_CONFIGURE }

/** Le plafond de relectures avant abandon du carton d'un film (le brief : « dix fois au plus »), toutes les trois secondes. */
const val CHRONIQUE_ESSAIS_MAX = 10

/**
 * Le plafond de la chronique d'une **année**, porté de dix à trente-six par le brief du
 * 16 septembre 2026, phase 2 : à cinq secondes l'essai, trois minutes d'attente au lieu de
 * cinquante secondes — le chroniqueur d'une année écrit beaucoup plus long que le carton d'un
 * film, et la minute passée à cinquante secondes laissait le cartouche vide alors que le texte
 * arrivait.
 */
const val CHRONIQUE_ANNEE_ESSAIS_MAX = 36

/**
 * Décide l'état suivant à partir d'une réponse `{ configure, statut }` (les deux DTO du Voyage
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
