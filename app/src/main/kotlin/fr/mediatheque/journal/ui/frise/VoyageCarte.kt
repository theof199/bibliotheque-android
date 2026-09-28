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

// --- Le Voyage, pavillon par pavillon (delta de Léon du 25 septembre 2026) ---------------------

/**
 * Un des dix emplacements fixes de photogrammes d'un monde (§C du delta) : le coin haut-gauche
 * d'une case de référence 56 × 44, dans le repère 390 dp de large de la section (`SectionMonde.kt`
 * lit `x` en fraction de 390, `y` en dp tel quel — le repère détaillé dans le plan, « Conception »).
 * Un format plus petit ou plus grand que la référence se centre dessus plutôt que de partir du
 * même coin.
 */
data class EmplacementPhotogramme(val x: Float, val y: Float)

/** La case de référence sur laquelle un format plus petit ou plus grand se centre (§C). */
val CASE_REFERENCE_LARGEUR = 56f
val CASE_REFERENCE_HAUTEUR = 44f

/** Les dix places de la route, dans l'ordre où on les rencontre en descendant (§C). */
val EMPLACEMENTS_PHOTOGRAMMES: List<EmplacementPhotogramme> = listOf(
    EmplacementPhotogramme(47f, 96f),
    EmplacementPhotogramme(117f, 96f),
    EmplacementPhotogramme(187f, 96f),
    EmplacementPhotogramme(262f, 156f),
    EmplacementPhotogramme(157f, 216f),
    EmplacementPhotogramme(87f, 216f),
    EmplacementPhotogramme(42f, 276f),
    EmplacementPhotogramme(122f, 336f),
    EmplacementPhotogramme(262f, 396f),
    EmplacementPhotogramme(147f, 456f),
)

/** L'emplacement d'un rang donné — un rang hors bornes (jamais censé arriver, dix places au plus par monde) reste dans la liste. */
fun emplacement(rang: Int): EmplacementPhotogramme = EMPLACEMENTS_PHOTOGRAMMES[rang.coerceIn(0, EMPLACEMENTS_PHOTOGRAMMES.lastIndex)]

/** Où se pose le millésime autour d'une case, selon sa place sur la route (§C). */
enum class PositionMillesime { DESSOUS, A_GAUCHE, A_DROITE, DESSUS }

/**
 * Les rangs 3 et 8 sont les virages à droite de la route ((262,156) et (262,396)) : le millésime
 * se pose à gauche de la case. Le rang 6 est le seul virage à gauche ((42,276)) : à droite. Les
 * rangs 4 et 5 sont la rangée du milieu ((157,216) et (87,216)) : au-dessus. Les autres, sur une
 * rangée droite, le portent dessous.
 */
fun positionMillesime(rang: Int): PositionMillesime = when (rang) {
    3, 8 -> PositionMillesime.A_GAUCHE
    6 -> PositionMillesime.A_DROITE
    4, 5 -> PositionMillesime.DESSUS
    else -> PositionMillesime.DESSOUS
}

/**
 * L'étendue du HUD (§A du delta) : « 1930 → 1939 ». La décennie en cours (celle qui n'est pas
 * encore terminée) s'arrête à `anneeActuelle` (le millésime du jour, pas l'année en cours du
 * Voyage) plutôt qu'à sa borne pleine — jumelle de la règle qui limite le nombre de places de la
 * route (une décennie a moins de dix années tant qu'elle n'est pas finie).
 */
fun etendueHud(decennie: Int, anneeActuelle: Int): String = "$decennie → ${minOf(decennie + 9, anneeActuelle)}"

/**
 * Quantifie une progression `t` par pas de `pas` (16 images par seconde jusqu'en 1920, §D : un pas
 * de 62,5 ms), toujours arrondie vers le bas — l'entrée du carton reste tenue sur son image
 * jusqu'au prochain pas plutôt que de glisser en continu. `pas` nul ou négatif (jamais censé
 * arriver) rend `t` telle quelle plutôt que de diviser par zéro.
 */
fun progressionQuantifiee(t: Float, pas: Float): Float {
    if (pas <= 0f) return t
    return kotlin.math.floor(t / pas) * pas
}

/**
 * Le pas de `progressionQuantifiee` pour tenir 16 images par seconde (62,5 ms) sur une entrée de
 * `dureeMs` millisecondes (livraison 5, §G, 1890 → 1920) : un carton de 900 ms tient dans 16 pas
 * (900/62,5 = 14,4, arrondi par `progressionQuantifiee` elle-même), un de 1 600 ms dans 25,6 —
 * `dureeMs` nul ou négatif (jamais censé arriver) rend un pas nul, `progressionQuantifiee` renvoie
 * alors `t` telle quelle plutôt que de diviser par zéro.
 */
fun pasSeizeImagesParSeconde(dureeMs: Int): Float {
    if (dureeMs <= 0) return 0f
    return 62.5f / dureeMs
}

/** L'état de l'entrée d'un carton-titre (§D) : jamais jouée, en train de se jouer, ou déjà jouée — persistée par `MondesVisitesStore`. */
sealed interface EtatEntree {
    data object Jamais : EtatEntree
    data class EnCours(val progression: Float) : EtatEntree
    data object Jouee : EtatEntree
}

/**
 * L'état de l'entrée d'un carton, d'après ce que le magasin des mondes visités connaît déjà —
 * `EnCours` n'est jamais rendu ici : c'est un état transitoire que l'écran construit lui-même
 * pendant que l'animation joue (livraisons suivantes), pas quelque chose qu'une lecture du
 * magasin peut retrouver après coup.
 */
fun statutDuCarton(dejaEntres: Set<Int>, decennie: Int): EtatEntree =
    if (decennie in dejaEntres) EtatEntree.Jouee else EtatEntree.Jamais

/**
 * La machine de l'entrée d'un carton-titre, image par image (livraison 3 du delta, §D et §G) :
 * la seule pièce que 1960 → 2020 (livraison 4) et 1890 → 1920 en 16 i/s (livraison 5, via
 * `progressionQuantifiee` posée en amont sur `progression`) auront à réutiliser telle quelle, en
 * n'ajoutant que leurs propres données et composables par monde. Pure, sans Compose — c'est
 * `SectionMonde.kt` qui fait avancer `progression` (0 → 1) pendant que l'entrée joue.
 *
 * L'ordre des règles compte : `dejaEntre` gagne toujours (jamais rejouée, quoi qu'il arrive) ;
 * sinon hors écran rien ne joue (`Jamais`, § « active suit la visibilité ») ; sinon les animations
 * réduites sautent direct à l'état final dès que le monde est visible (§ animations réduites) ;
 * sinon l'état suit la progression fournie, `Jouee` une fois qu'elle atteint 1.
 */
fun etatEntreeCarton(dejaEntre: Boolean, visible: Boolean, reduit: Boolean, progression: Float): EtatEntree = when {
    dejaEntre -> EtatEntree.Jouee
    !visible -> EtatEntree.Jamais
    reduit -> EtatEntree.Jouee
    progression >= 1f -> EtatEntree.Jouee
    else -> EtatEntree.EnCours(progression.coerceIn(0f, 1f))
}

/**
 * L'ambiance d'un monde (§G, livraison 3) ne tourne que section visible à l'écran, jamais hors
 * champ, jamais non plus quand les animations sont réduites — `AmbianceDeMonde.kt` ne lance sa
 * `rememberInfiniteTransition` que si cette fonction rend vrai, jumelle du `if (defilement)` de
 * `Perforations` (`theme/Ornements.kt`). Pure, testée en JVM.
 */
fun ambianceActive(visible: Boolean, reduit: Boolean): Boolean = visible && !reduit

/** Une année du Voyage, mise à plat pour sa section — jumelle de l'ancienne `Cellule.Annee`, sortie de l'écran. */
data class AnneeDuVoyage(
    val annee: Int,
    val statut: StatutAnneeVoyage?,
    val affiche: String?,
    /** Nulle sans aucun film vu (`AnneeVoyage.recompense`, étape 5, « les récompenses »). */
    val recompense: Recompense?,
    val profondeur: Int,
    val groupe: AnneeFrise,
    /** La place de l'année parmi les dix de son monde — index dans `EMPLACEMENTS_PHOTOGRAMMES`. */
    val rang: Int,
)

/**
 * Une section du Voyage : un monde, ses années, et l'état de sa marquise. Nommée `SectionDuVoyage`
 * plutôt que `SectionMonde` (revue du 28 septembre 2026, retouche de la livraison 1) : le
 * composable qui la dessine, dans le même paquet, porte déjà ce nom-là (`SectionMonde.kt`).
 */
data class SectionDuVoyage(
    val monde: Monde,
    val annees: List<AnneeDuVoyage>,
    val bouclee: Boolean,
    val rayon: DecennieFrise,
)

/**
 * La carte du Voyage, mise à plat en sections plutôt qu'en cellules (delta de Léon, §A, §B) —
 * l'ancienne `construireCarte`, privée et non testée dans `VoyageScreen.kt`, sortie de l'écran et
 * testée ici.
 *
 * Le Voyage commence à `depart` (1895) quoi que le journal contienne de plus ancien : un film de
 * 1888 se range dans les origines sans ouvrir d'année avant le départ. Les années postérieures à
 * `anneeActuelle` (aujourd'hui) n'existent pas non plus — rien à tourner dans le futur.
 */
fun sectionsDuVoyage(ui: FriseUi, anneeActuelle: Int): List<SectionDuVoyage> {
    val depart = ui.voyage.depart
    if (anneeActuelle < depart) return emptyList()

    val premiereDecennie = mondeDe(depart).decennie
    val derniereDecennie = mondeDe(anneeActuelle).decennie

    return (premiereDecennie..derniereDecennie step 10).map { decennie ->
        val monde = mondeDeLaDecennie(decennie)
        val annees = (maxOf(decennie, depart)..minOf(decennie + 9, anneeActuelle))
            .mapIndexed { rang, annee ->
                val fragment = ui.voyage.parAnnee[annee]
                val groupe = ui.annees.firstOrNull { it.annee == annee } ?: AnneeFrise(annee, emptyList(), emptyList())
                AnneeDuVoyage(
                    annee = annee,
                    statut = statutAnneeVoyage(fragment?.statut),
                    affiche = afficheAnnee(fragment?.affiche_url, groupe.vus.firstNotNullOfOrNull { it.media.cover_url }),
                    recompense = recompenseDe(fragment?.recompense),
                    profondeur = fragment?.profondeur ?: groupe.vus.size,
                    groupe = groupe,
                    rang = rang,
                )
            }
        SectionDuVoyage(
            monde = monde,
            annees = annees,
            // Allumée dès que `ui.passeport` (le tampon envoyé par `GET /me/voyage`) porte cette
            // décennie — étape 5, « les récompenses ».
            bouclee = ui.passeport.any { it.decennie == decennie },
            rayon = ui.decennies.firstOrNull { it.decennie == decennie }
                ?: DecennieFrise(decennie, 0, 0, emptyList(), (decennie until decennie + 10).map { AnneeDecennie(it, 0, 0) }),
        )
    }
}
