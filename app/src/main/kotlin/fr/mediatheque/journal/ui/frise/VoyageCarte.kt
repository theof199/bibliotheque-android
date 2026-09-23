package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.TamponVoyage

/**
 * La carte du Voyage : les règles qui décident ce que l'écran montre — la récompense d'une année,
 * l'avancée de la frontière, les tampons du passeport. Fonctions pures, testées en JVM sans réseau
 * ni `ViewModel`, comme `VoyageEtats.kt` à côté.
 *
 * Étape 5 du brief du 21 septembre 2026 (« les récompenses ») : `GET /me/voyage` sert désormais
 * `recompense` par année et `tampons` (spec du 19 septembre 2026, §6) — plus l'ancien calcul par
 * essentiels (`essentielsTotal`/`essentielsFaits`), remplacé par `recompenseDe`, simple lecture de
 * ce que le back a déjà tranché. `tamponsPasseport` construit pour de vrai, depuis `tampons` et le
 * journal ; `detecterNouveauTampon` dit quand un générique doit se rejouer tout seul.
 *
 * `affiche_url` (étape 2, « le podium ») sert le photogramme d'une année : `afficheAnnee`
 * en dessous choisit entre elle et le dernier film vu.
 */

/**
 * Le photogramme d'une année, sur la carte (brief du 21 septembre 2026, « le podium ») :
 * `affiche_url` (l'affiche du n°1 du podium) quand elle existe, sinon le dernier film vu de l'année
 * comme avant le podium — jamais l'inverse, un podium posé devant primer sur ce qui n'est qu'un
 * repli.
 */
fun afficheAnnee(afficheUrl: String?, dernierVu: String?): String? = afficheUrl ?: dernierVu

/**
 * La récompense d'une année — les trois festivals de la spec du 19 septembre 2026, §6 : Ours
 * (commencée), Lion (les essentiels), Palme (les essentiels et deux salles de plus).
 */
enum class Recompense(val singulier: String, val pluriel: String) {
    PALME("Palme", "Palmes"),
    LION("Lion", "Lions"),
    OURS("Ours", "Ours"),
}

/**
 * La récompense telle que le back la tranche (`AnneeVoyage.recompense`, `AnneeVoyageDetailResponse
 * .recompense`) — `"ours"`/`"lion"`/`"palme"`, nulle pour tout le reste (une année sans film vu, ou
 * un texte inconnu). Le calcul lui-même (§6 de la spec) vit côté back ; l'appli ne fait que lire.
 */
fun recompenseDe(brut: String?): Recompense? = when (brut) {
    "ours" -> Recompense.OURS
    "lion" -> Recompense.LION
    "palme" -> Recompense.PALME
    else -> null
}

/** « 3 Palmes · 1 Lion » : le compte du HUD, dans l'ordre Palme, Lion, Ours, sans les zéros — vide tant que rien n'est décerné. */
fun phraseRecompenses(recompenses: List<Recompense>): String {
    val comptes = recompenses.groupingBy { it }.eachCount()
    return Recompense.entries
        .mapNotNull { r -> comptes[r]?.takeIf { it > 0 }?.let { n -> "$n ${if (n > 1) r.pluriel else r.singulier}" } }
        .joinToString(" · ")
}

/**
 * Ce qu'une frontière qui avance vient de boucler : l'année quittée, et la décennie quittée si la
 * frontière a changé de monde.
 *
 * `avant` est l'année en cours mémorisée, `apres` celle que `GET /me/voyage` vient de rendre.
 * C'est `avant` qui est bouclée, pas `apres` : l'année en cours est celle qu'on quitte, pas celle
 * qu'on rejoint. Une décennie se boucle quand l'année quittée et la nouvelle ne sont plus dans le
 * même monde (1899 → 1900 boucle les années 1890).
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

/** Un film du générique de fin : son titre, son année de sortie, et son réalisateur s'il est connu. */
data class FilmGenerique(val titre: String, val annee: Int, val realisateur: String? = null)

/**
 * Un tampon du passeport, qui porte aussi tout ce que son générique affiche : l'écran
 * `Screen.Generique` ne recharge donc rien, il relit ce tampon.
 *
 * `recompenses` (étape 5) porte celle de chacune des dix années de la décennie qui en a une — le
 * générique en tire son compte de festivals (décision 4 du brief du 21 septembre 2026, « les
 * récompenses »), dans le même ordre que le HUD de la carte. `recompensesParAnnee` (habillage du
 * 23 septembre 2026, geste 13) garde l'année de chacune : le générique en tire les rôles « Palmes »
 * et « Lions », qui veulent les millésimes et pas seulement le compte.
 */
data class TamponDecennie(
    val decennie: Int,
    val titreVoyageur: String,
    val premiereEntree: String?,
    val derniereEntree: String?,
    val films: List<FilmGenerique>,
    val recompenses: List<Recompense> = emptyList(),
    val recompensesParAnnee: List<Pair<Int, Recompense>> = emptyList(),
)

/**
 * Un tampon complet, pour une décennie donnée — jumeau construit par `tamponsPasseport` (une
 * décennie déjà bouclée) et par `PasseportViewModel.ouvrirGenerique` (le générique, au tap).
 *
 * Les films sont ceux du journal dont l'année de **sortie** tombe dans la décennie (jamais la
 * date de visionnage : le générique des années 1890 ne liste pas ce qu'on a vu en 1890), triés
 * par année puis par titre ; les deux dates sont bien celles des visionnages. `journal` vide (la
 * légère liste du passeport, avant tout tap) rend des films et des dates vides, sans planter :
 * seul le nombre de récompenses ne dépend pas de lui.
 */
fun construireTamponDecennie(decennie: Int, voyage: VoyageUi, journal: List<JournalItem>): TamponDecennie {
    val duMonde = journal.filter { it.media.year != null && mondeDe(it.media.year!!).decennie == decennie }
    val dates = duMonde.map { it.entry.finished_at }.sorted()
    val recompensesParAnnee = (decennie until decennie + 10)
        .mapNotNull { annee -> recompenseDe(voyage.parAnnee[annee]?.recompense)?.let { annee to it } }
    return TamponDecennie(
        decennie = decennie,
        titreVoyageur = mondeDeLaDecennie(decennie).titreVoyageur,
        premiereEntree = dates.firstOrNull(),
        derniereEntree = dates.lastOrNull(),
        films = duMonde
            .map { FilmGenerique(it.media.title, it.media.year!!, it.media.director) }
            .sortedWith(compareBy({ it.annee }, { it.titre })),
        recompenses = recompensesParAnnee.map { it.second },
        recompensesParAnnee = recompensesParAnnee,
    )
}

/**
 * Un rôle du générique (habillage du 23 septembre 2026, geste 13) : une étiquette et sa valeur,
 * dans l'ordre où le générique les déroule — réalisateurs rencontrés, Palmes, Lions, films au
 * journal. Fonction pure, testée en JVM.
 *
 * Une ligne omise plutôt que vide : sans film à réalisateur connu, la ligne « Réalisateurs
 * rencontrés » disparaît entièrement (le brief le demande), de même pour « Palmes »/« Lions » sans
 * année primée — jamais « Réalisateurs rencontrés : » suivi de rien.
 */
data class RoleGenerique(val etiquette: String, val valeur: String)

fun rolesDuGenerique(tampon: TamponDecennie): List<RoleGenerique> {
    val roles = mutableListOf<RoleGenerique>()
    val realisateurs = tampon.films.mapNotNull { it.realisateur }.distinct().sorted()
    if (realisateurs.isNotEmpty()) {
        roles += RoleGenerique("Réalisateurs rencontrés", realisateurs.joinToString(" · "))
    }
    val palmes = tampon.recompensesParAnnee.filter { it.second == Recompense.PALME }.map { it.first }.sorted()
    if (palmes.isNotEmpty()) roles += RoleGenerique("Palmes", palmes.joinToString(", "))
    val lions = tampon.recompensesParAnnee.filter { it.second == Recompense.LION }.map { it.first }.sorted()
    if (lions.isNotEmpty()) roles += RoleGenerique("Lions", lions.joinToString(", "))
    roles += RoleGenerique("Films au journal", tampon.films.size.toString())
    return roles
}

/**
 * Les tampons du passeport (étape 5, « les récompenses ») : une ligne par décennie que `tampons`
 * (`GET /me/voyage`) dit déjà bouclée — ce n'est plus calculé ici depuis les statuts par année
 * (spec du 19 septembre 2026, §6 : chacune de ses dix années a un Ours, et le ticket suivant est
 * utilisé — le back seul le sait).
 */
fun tamponsPasseport(voyage: VoyageUi, journal: List<JournalItem>): List<TamponDecennie> =
    voyage.tampons.map { tampon -> construireTamponDecennie(tampon.decennie, voyage, journal) }

/**
 * La première décennie de `tampons` que `dejaVus` ne connaît pas encore (décision 1 du brief du
 * 21 septembre 2026, « les récompenses ») — allume sa marquise et rejoue son générique
 * (`FriseViewModel.nouveauxTampons`). `dejaVus` nul au tout premier chargement, jumeau de
 * `detecterFrontiereAvancee` : sans ce garde-fou, une décennie déjà bouclée avant l'ouverture de
 * l'appli rejouerait son générique à chaque démarrage.
 */
fun detecterNouveauTampon(dejaVus: Set<Int>?, tampons: List<TamponVoyage>): Int? {
    if (dejaVus == null) return null
    return tampons.map { it.decennie }.firstOrNull { it !in dejaVus }
}

/**
 * Le monde qu'on vient d'entrer en défilant la Frise (geste 22 du complément du 23 septembre 2026
 * à l'habillage) : l'ancien monde visible, le nouveau — nul si l'un des deux est encore inconnu
 * (tout premier défilement, chargement) ou si rien n'a changé. Ne dit rien de si ce monde a déjà
 * été présenté cette session : c'est `dejaPresentes` (`rememberSaveable`, `VoyageScreen`) qui le
 * garde, comparé séparément par l'appelant — jumeau de `detecterNouveauTampon` ci-dessus, qui fait
 * la même chose pour le générique de décennie plutôt que le carton-titre d'un monde.
 */
fun mondeEntre(ancien: Int?, nouveau: Int?): Int? {
    if (ancien == null || nouveau == null || ancien == nouveau) return null
    return nouveau
}

/**
 * Un ticket du portefeuille (décision 3 du brief du 21 septembre 2026, « le ticket »), tel que
 * `PortefeuilleViewModel` (`ui/profile/`) le range depuis `GET /me/voyage/tickets` — `utiliseLe`
 * nul tant qu'il dort.
 */
data class TicketPortefeuilleUi(val annee: Int, val motif: String, val utiliseLe: String?)

/**
 * Le tri du portefeuille (décision 3) : les tickets non utilisés d'abord (par année, l'ordre du
 * Voyage), puis les compostés en dessous, par date d'utilisation — fonction pure, testée en JVM.
 */
fun trierPortefeuille(tickets: List<TicketPortefeuilleUi>): List<TicketPortefeuilleUi> {
    val (utilises, enAttente) = tickets.partition { it.utiliseLe != null }
    return enAttente.sortedBy { it.annee } + utilises.sortedBy { it.utiliseLe }
}
