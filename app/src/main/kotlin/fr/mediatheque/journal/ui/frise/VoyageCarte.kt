package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.EssentielVoyage
import fr.mediatheque.journal.api.dto.JournalItem

/**
 * La carte du Voyage (brief du 16 septembre 2026, phase 2) : les règles qui décident ce que
 * l'écran montre — la récompense d'une année, la prochaine étape, l'avancée de la frontière, les
 * tampons du passeport. Fonctions pures, testées en JVM sans réseau ni `ViewModel`, comme
 * `VoyageEtats.kt` à côté.
 */

/**
 * La récompense d'une année faite (brief, item 5) — les étoiles de la maquette sont remplacées
 * par trois festivals.
 *
 * Une année est « faite » quand chacun de ses essentiels est vu **ou** marqué introuvable : les
 * manquants (`total - faits`) sont donc exactement les introuvables. D'où la lecture du brief :
 * tout vu → la Palme, un ou deux introuvables → le Lion, au-delà → l'Ours.
 */
enum class Recompense(val singulier: String, val pluriel: String) {
    PALME("Palme", "Palmes"),
    LION("Lion", "Lions"),
    OURS("Ours", "Ours"),
}

fun recompense(essentielsTotal: Int, essentielsFaits: Int): Recompense {
    val manquants = (essentielsTotal - essentielsFaits).coerceAtLeast(0)
    return when {
        manquants == 0 -> Recompense.PALME
        manquants <= 2 -> Recompense.LION
        else -> Recompense.OURS
    }
}

/** La récompense d'une année dont on a déjà le statut et les comptes sous la main — nulle hors d'une année faite. */
fun recompenseFaite(statut: StatutAnneeVoyage?, essentielsTotal: Int?, essentielsFaits: Int?): Recompense? =
    if (statut != StatutAnneeVoyage.FAITE) null else recompense(essentielsTotal ?: 0, essentielsFaits ?: 0)

/** La récompense d'une année du Voyage — nulle tant que l'année n'est pas faite. */
fun recompenseDeLAnnee(annee: Int, voyage: VoyageUi): Recompense? {
    val fragment = voyage.parAnnee[annee] ?: return null
    return recompenseFaite(statutAnneeVoyage(fragment.statut), fragment.essentiels_total, fragment.essentiels_faits)
}

/** « 3 Palmes · 1 Lion » : le compte du HUD, dans l'ordre Palme, Lion, Ours, sans les zéros. */
fun phraseRecompenses(voyage: VoyageUi): String {
    val comptes = voyage.parAnnee.keys.mapNotNull { recompenseDeLAnnee(it, voyage) }
        .groupingBy { it }
        .eachCount()
    return Recompense.entries
        .mapNotNull { r -> comptes[r]?.takeIf { it > 0 }?.let { n -> "$n ${if (n > 1) r.pluriel else r.singulier}" } }
        .joinToString(" · ")
}

/**
 * La carte « Prochaine étape » du bas (brief, item 6) : le premier essentiel de l'année en cours
 * qui n'est ni vu ni introuvable, dans l'ordre du rang.
 *
 * Les deux états sautés ne sont pas le même geste : un vu est fait, un introuvable a été écarté à
 * la main — aucun des deux ne doit revenir proposer « Voir ».
 */
fun prochaineEtape(essentiels: List<EssentielVoyage>): EssentielVoyage? =
    essentiels.sortedBy { it.rang }.firstOrNull { it.etat != "vu" && it.etat != "introuvable" }

/**
 * Ce qu'une frontière qui avance vient de boucler (brief, item 9) : l'année quittée, et la
 * décennie quittée si la frontière a changé de monde.
 *
 * `avant` est la frontière mémorisée, `apres` celle que `GET /me/voyage` vient de rendre. C'est
 * `avant` qui est bouclée, pas `apres` : la frontière est l'année **en cours**, celle qu'on
 * quitte est celle d'avant. Une décennie se boucle quand l'année quittée et la nouvelle ne sont
 * plus dans le même monde (1899 → 1900 boucle les années 1890).
 */
data class FrontiereAvancee(val anneeBouclee: Int, val decennieBouclee: Int?)

fun detecterFrontiereAvancee(avant: Int?, apres: Int?): FrontiereAvancee? {
    if (avant == null || apres == null || apres <= avant) return null
    val decennieAvant = mondeDe(avant).decennie
    val decennieApres = mondeDe(apres).decennie
    return FrontiereAvancee(
        anneeBouclee = avant,
        decennieBouclee = decennieAvant.takeIf { it != decennieApres },
    )
}

/** Un film du générique de fin : son titre et son année de sortie. */
data class FilmGenerique(val titre: String, val annee: Int)

/**
 * Un tampon du passeport (brief, item 10), qui porte aussi tout ce que son générique affiche
 * (item 9) : l'écran `Screen.Generique` ne recharge donc rien, il relit ce tampon.
 *
 * `premiereEntree` et `derniereEntree` sont des dates ISO du journal (`finished_at`), jamais
 * reformatées ici : `formatDate` (`ui/Format.kt`) s'en charge à l'affichage. Elles sont nulles
 * quand la décennie est bouclée sans qu'aucun film de ces années-là ne soit au journal — une
 * décennie peut se boucler sur des essentiels tous marqués introuvables.
 */
data class TamponDecennie(
    val decennie: Int,
    val titreVoyageur: String,
    val premiereEntree: String?,
    val derniereEntree: String?,
    val films: List<FilmGenerique>,
)

/**
 * Les tampons du passeport : une ligne par décennie bouclée, ses films tirés du journal.
 *
 * Une décennie est bouclée quand **toutes** ses années connues de `/me/voyage` sont faites — une
 * seule année ouverte ou verrouillée dans le lot, et la décennie ne l'est pas. Les années que le
 * back ne sert pas (avant le départ du Voyage, 1895 pour les années 1890) ne comptent ni pour ni
 * contre : le Voyage ne les demande jamais.
 *
 * Les films sont ceux du journal dont l'année de **sortie** tombe dans la décennie (jamais la
 * date de visionnage : le générique des années 1890 ne liste pas ce qu'on a vu en 1890), triés
 * par année puis par titre. Les dates, elles, sont bien celles des visionnages.
 */
fun tamponsPasseport(voyage: VoyageUi, journal: List<JournalItem>): List<TamponDecennie> {
    val bouclees = voyage.parAnnee.values
        .groupBy { mondeDe(it.annee).decennie }
        .filterValues { annees -> annees.isNotEmpty() && annees.all { statutAnneeVoyage(it.statut) == StatutAnneeVoyage.FAITE } }
        .keys
        .sorted()

    return bouclees.map { decennie ->
        val duMonde = journal.filter { it.media.year != null && mondeDe(it.media.year!!).decennie == decennie }
        val dates = duMonde.map { it.entry.finished_at }.sorted()
        TamponDecennie(
            decennie = decennie,
            titreVoyageur = mondeDeLaDecennie(decennie).titreVoyageur,
            premiereEntree = dates.firstOrNull(),
            derniereEntree = dates.lastOrNull(),
            films = duMonde
                .map { FilmGenerique(it.media.title, it.media.year!!) }
                .sortedWith(compareBy({ it.annee }, { it.titre })),
        )
    }
}
