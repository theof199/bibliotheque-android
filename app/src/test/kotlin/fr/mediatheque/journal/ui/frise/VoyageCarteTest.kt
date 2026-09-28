package fr.mediatheque.journal.ui.frise

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.TamponVoyage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La carte du Voyage (brief du 21 septembre 2026, « l'année en étages », puis « les récompenses »,
 * étape 5) : la récompense d'une année, la frontière qui avance, les tampons du passeport.
 * Fonctions pures, sans réseau ni `ViewModel`.
 */
class VoyageCarteTest {

    private fun vu(annee: Int?, id: String, date: String, titre: String = "Un film", realisateur: String? = null) =
        FakeJournalApi.item("m-$id", date, null, emptyList(), null, id = "e-$id", title = titre, year = annee, director = realisateur)

    // La lecture du back, telle quelle — mutation : confondre deux chaînes (`"lion"` -> OURS, par
    // exemple), ou rendre autre chose que `null` pour un texte inconnu, casserait une des branches.
    @Test
    fun `recompenseDe lit ours, lion et palme, nulle pour tout le reste`() {
        assertEquals(Recompense.OURS, recompenseDe("ours"))
        assertEquals(Recompense.LION, recompenseDe("lion"))
        assertEquals(Recompense.PALME, recompenseDe("palme"))
        assertNull(recompenseDe(null))
        assertNull(recompenseDe("faite"))
    }

    // Le pluriel, et l'ordre Palme puis Lion puis Ours — jamais l'ordre d'arrivée des années.
    @Test
    fun `phraseRecompenses accorde le pluriel et garde l'ordre des festivals`() {
        val phrase = phraseRecompenses(listOf(Recompense.OURS, Recompense.PALME, Recompense.PALME))
        assertEquals("2 Palmes · 1 Ours", phrase)
    }

    // Sans aucune année récompensée (ou avant que `GET /me/voyage` n'ait répondu), la ligne du HUD
    // ne s'affiche pas. Mutation : rendre autre chose qu'une chaîne vide sur une liste vide ferait
    // apparaître « 0 Palme » au HUD.
    @Test
    fun `phraseRecompenses est vide sans aucune recompense`() {
        assertEquals("", phraseRecompenses(emptyList()))
    }

    // Le photogramme de la carte (brief du 21 septembre 2026, « le podium ») : l'affiche du n°1 du
    // podium prime sur le dernier film vu, jamais l'inverse. Mutation : inverser l'ordre du `?:`
    // ferait retomber sur le dernier vu même quand un podium est posé.
    @Test
    fun `afficheAnnee prend l'affiche du podium avant le dernier vu, le dernier vu si nulle`() {
        assertEquals("https://podium", afficheAnnee("https://podium", "https://dernier-vu"))
        assertEquals("https://dernier-vu", afficheAnnee(null, "https://dernier-vu"))
        assertNull(afficheAnnee(null, null))
    }

    // C'est l'année **quittée** qui est bouclée, pas la nouvelle année en cours. Mutation :
    // `anneeBouclee = apres` ferait annoncer « 1899 dans la boîte ! » alors qu'on vient d'y entrer.
    @Test
    fun `detecterFrontiereAvancee boucle l'annee quittee, sans decennie dans le meme monde`() {
        val avancee = detecterFrontiereAvancee(avant = 1898, apres = 1899)

        assertEquals(1898, avancee?.anneeBouclee)
        assertNull(avancee?.decennieBouclee)
    }

    // Et la décennie quittée quand l'année en cours change de monde. Mutation : comparer
    // `decennieApres` à elle-même, ou rendre toujours la décennie, allumerait la marquise et
    // lancerait le générique à chaque année.
    @Test
    fun `detecterFrontiereAvancee boucle la decennie quand l'annee en cours change de monde`() {
        val avancee = detecterFrontiereAvancee(avant = 1899, apres = 1900)

        assertEquals(1899, avancee?.anneeBouclee)
        assertEquals(1890, avancee?.decennieBouclee)
    }

    // Un premier chargement (rien de mémorisé), une année en cours qui ne bouge pas, ou qui recule
    // (une réponse en retard) ne bouclent rien : la snackbar et le générique ne se jouent pas tout
    // seuls à l'ouverture de l'écran.
    @Test
    fun `detecterFrontiereAvancee ne boucle rien sans avancee reelle`() {
        assertNull(detecterFrontiereAvancee(avant = null, apres = 1899))
        assertNull(detecterFrontiereAvancee(avant = 1899, apres = null))
        assertNull(detecterFrontiereAvancee(avant = 1899, apres = 1899))
        assertNull(detecterFrontiereAvancee(avant = 1900, apres = 1899))
    }

    // Le tampon du passeport (étape 5, « les récompenses ») : le titre par décennie
    // (`Mondes.kt`), les deux dates de visionnage (triées, pas celles du fichier), les films triés
    // année puis titre, et les récompenses de la décennie dans l'ordre de ses années. Mutation :
    // inverser `firstOrNull`/`lastOrNull` sur `dates` échangerait les deux dates ; filtrer sur
    // `entry.finished_at` au lieu de `media.year` daterait le tampon des visionnages plutôt que des
    // sorties.
    @Test
    fun `construireTamponDecennie construit le titre, les dates, les films et les recompenses`() {
        val voyage = VoyageUi(
            parAnnee = listOf(
                AnneeVoyage(1895, "ouverte", recompense = "ours"),
                AnneeVoyage(1896, "ouverte", recompense = "palme"),
                AnneeVoyage(1897, "en_cours", recompense = null),
            ).associateBy { it.annee },
        )
        val journal = listOf(
            vu(1896, "b", "2026-03-20", "Le Voyage dans la lune", realisateur = "Georges Méliès"),
            vu(1895, "a", "2026-02-11", "L'Arrivée d'un train", realisateur = "Louis Lumière"),
            vu(1920, "c", "2026-05-01", "Hors décennie"),
        )

        val tampon = construireTamponDecennie(1890, voyage, journal)

        assertEquals(1890, tampon.decennie)
        assertEquals(mondeDeLaDecennie(1890).titreVoyageur, tampon.titreVoyageur)
        assertEquals("2026-02-11", tampon.premiereEntree)
        assertEquals("2026-03-20", tampon.derniereEntree)
        assertEquals(
            listOf("L'Arrivée d'un train" to 1895, "Le Voyage dans la lune" to 1896),
            tampon.films.map { it.titre to it.annee },
        )
        assertEquals(listOf("Louis Lumière", "Georges Méliès"), tampon.films.map { it.realisateur })
        assertEquals(listOf(Recompense.OURS, Recompense.PALME), tampon.recompenses)
        assertEquals(listOf(1895 to Recompense.OURS, 1896 to Recompense.PALME), tampon.recompensesParAnnee)
    }

    // Journal vide (la carte légère du passeport, avant tout tap) : ni film ni date, mais les
    // récompenses restent — elles ne dépendent que de `voyage`, jamais du journal.
    @Test
    fun `construireTamponDecennie sans journal garde les recompenses, sans film ni date`() {
        val voyage = VoyageUi(parAnnee = mapOf(1895 to AnneeVoyage(1895, "ouverte", recompense = "lion")))

        val tampon = construireTamponDecennie(1890, voyage, emptyList())

        assertTrue(tampon.films.isEmpty())
        assertNull(tampon.premiereEntree)
        assertNull(tampon.derniereEntree)
        assertEquals(listOf(Recompense.LION), tampon.recompenses)
    }

    // Les rôles du générique (habillage du 23 septembre 2026, geste 13) : réalisateurs rencontrés
    // (dédupliqués, triés), Palmes puis Lions par année, films au journal en dernier. Mutation :
    // ne pas dédupliquer ferait apparaître Méliès deux fois ; ne pas filtrer les nulls ferait
    // planter `sorted()` sur un film sans réalisateur.
    @Test
    fun `rolesDuGenerique liste les realisateurs distincts, les palmes, les lions et le compte de films`() {
        val tampon = TamponDecennie(
            decennie = 1890,
            titreVoyageur = "Spectateur des origines",
            premiereEntree = "2026-02-11",
            derniereEntree = "2026-03-20",
            films = listOf(
                FilmGenerique("Le Voyage dans la lune", 1896, "Georges Méliès"),
                FilmGenerique("L'Arrivée d'un train", 1895, "Louis Lumière"),
                FilmGenerique("Cendrillon", 1899, "Georges Méliès"),
                FilmGenerique("Sans réalisateur connu", 1897, null),
            ),
            recompensesParAnnee = listOf(1895 to Recompense.OURS, 1896 to Recompense.PALME, 1899 to Recompense.LION),
        )

        val roles = rolesDuGenerique(tampon)

        assertEquals(
            listOf(
                RoleGenerique("Réalisateurs rencontrés", "Georges Méliès · Louis Lumière"),
                RoleGenerique("Palmes", "1896"),
                RoleGenerique("Lions", "1899"),
                RoleGenerique("Films au journal", "4"),
            ),
            roles,
        )
    }

    // Sans réalisateur connu ni récompense de ces deux festivals, les lignes correspondantes
    // disparaissent entièrement — jamais une ligne vide. Mutation : rendre la ligne quand même,
    // avec une valeur vide, ferait apparaître « Réalisateurs rencontrés : » sans personne.
    @Test
    fun `rolesDuGenerique omet les lignes sans matiere`() {
        val tampon = TamponDecennie(
            decennie = 1890,
            titreVoyageur = "Spectateur des origines",
            premiereEntree = null,
            derniereEntree = null,
            films = listOf(FilmGenerique("Sans réalisateur connu", 1897, null)),
            recompensesParAnnee = listOf(1895 to Recompense.OURS),
        )

        val roles = rolesDuGenerique(tampon)

        assertEquals(listOf(RoleGenerique("Films au journal", "1")), roles)
    }

    // `tamponsPasseport` ne tamponne que les décennies que `tampons` (`GET /me/voyage`) dit
    // bouclées — plus un calcul local sur les statuts par année (le back seul sait quand le ticket
    // suivant a été utilisé). Mutation : ignorer `voyage.tampons` et dériver depuis `parAnnee`
    // ferait apparaître un tampon que le back n'a pas encore gagné.
    @Test
    fun `tamponsPasseport tamponne exactement les decennies que tampons designe`() {
        val voyage = VoyageUi(tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z")))

        val tampons = tamponsPasseport(voyage, emptyList())

        assertEquals(listOf(1890), tampons.map { it.decennie })
        assertTrue(tamponsPasseport(VoyageUi(), emptyList()).isEmpty())
    }

    // La détection d'un tampon nouveau (décision 1 du brief du 21 septembre 2026, « les
    // récompenses ») : liste vide → premier tampon, tampon déjà connu → rien, et `null` (tout
    // premier chargement) → rien non plus, sans quoi une décennie déjà bouclée avant l'ouverture de
    // l'appli rejouerait son générique à chaque démarrage.
    @Test
    fun `detecterNouveauTampon rend la premiere decennie inconnue, rien si deja vue ou au premier chargement`() {
        val tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z"))

        assertEquals(1890, detecterNouveauTampon(emptySet(), tampons))
        assertNull(detecterNouveauTampon(setOf(1890), tampons))
        assertNull(detecterNouveauTampon(null, tampons))
    }

    // Le tri du portefeuille (décision 3 du brief du 21 septembre 2026, « le ticket ») : les non
    // utilisés d'abord (par année), les compostés ensuite (par date d'utilisation). Mutation :
    // inverser les deux groupes, ou trier les compostés par année plutôt que par date, ferait
    // remonter un vieux ticket composté devant un ticket qui attend encore.
    @Test
    fun `trierPortefeuille met les non utilises d'abord, par annee, puis les compostes par date`() {
        val nonUtilise1943 = TicketPortefeuilleUi(1943, "Motif 1943", utiliseLe = null)
        val nonUtilise1942 = TicketPortefeuilleUi(1942, "Motif 1942", utiliseLe = null)
        val composteRecent = TicketPortefeuilleUi(1938, "Motif 1938", utiliseLe = "2026-09-10T10:00:00.000Z")
        val composteAncien = TicketPortefeuilleUi(1935, "Motif 1935", utiliseLe = "2026-06-04T10:00:00.000Z")

        val tries = trierPortefeuille(listOf(composteRecent, nonUtilise1943, composteAncien, nonUtilise1942))

        assertEquals(listOf(nonUtilise1942, nonUtilise1943, composteAncien, composteRecent), tries)
    }

    @Test
    fun `trierPortefeuille sur une liste vide reste vide`() {
        assertTrue(trierPortefeuille(emptyList()).isEmpty())
    }

    // --- Le Voyage, pavillon par pavillon (delta de Léon du 25 septembre 2026) -----------------

    // Les dix places sont fixes, dans l'ordre de la route (§C). Mutation : intervertir deux places
    // ferait poser un photogramme au mauvais tournant.
    @Test
    fun `emplacement rend les dix places de la route, dans l'ordre`() {
        assertEquals(10, EMPLACEMENTS_PHOTOGRAMMES.size)
        assertEquals(EmplacementPhotogramme(47f, 96f), emplacement(0))
        assertEquals(EmplacementPhotogramme(147f, 456f), emplacement(9))
    }

    // Le millésime se pose dessous par défaut, à gauche aux virages à droite (rangs 3 et 8), à
    // droite au virage à gauche (rang 6), au-dessus sur la rangée du milieu (rangs 4 et 5).
    // Mutation : confondre A_GAUCHE et A_DROITE ferait chevaucher le millésime et la route.
    @Test
    fun `positionMillesime suit le virage de chaque place`() {
        assertEquals(PositionMillesime.DESSOUS, positionMillesime(0))
        assertEquals(PositionMillesime.A_GAUCHE, positionMillesime(3))
        assertEquals(PositionMillesime.DESSUS, positionMillesime(4))
        assertEquals(PositionMillesime.DESSUS, positionMillesime(5))
        assertEquals(PositionMillesime.A_DROITE, positionMillesime(6))
        assertEquals(PositionMillesime.DESSOUS, positionMillesime(7))
        assertEquals(PositionMillesime.A_GAUCHE, positionMillesime(8))
        assertEquals(PositionMillesime.DESSOUS, positionMillesime(9))
    }

    // L'étendue du HUD (§A) : la décennie pleine, sauf la décennie en cours qui s'arrête au jour —
    // jumelle de la règle qui limite le nombre de places de la route. Mutation : ignorer
    // `anneeActuelle` afficherait « 2020 → 2029 » avant que la décennie ne soit terminée.
    @Test
    fun `etendueHud montre la decennie pleine, ou tronquee au jour pour la decennie en cours`() {
        assertEquals("1930 → 1939", etendueHud(1930, anneeActuelle = 2026))
        assertEquals("2020 → 2026", etendueHud(2020, anneeActuelle = 2026))
    }

    // La progression quantifiée (§D) : arrondie vers le bas au pas, jamais au plus proche.
    // Mutation : arrondir plutôt qu'arrondir vers le bas ferait avancer le carton en avance sur
    // son pas d'image.
    @Test
    fun `progressionQuantifiee arrondit vers le bas, au pas donne`() {
        assertEquals(0f, progressionQuantifiee(0.05f, pas = 0.0625f), 0.0001f)
        assertEquals(0.0625f, progressionQuantifiee(0.09f, pas = 0.0625f), 0.0001f)
        assertEquals(0.125f, progressionQuantifiee(0.125f, pas = 0.0625f), 0.0001f)
    }

    // Sans pas valable (jamais censé arriver), la progression n'est pas quantifiée.
    @Test
    fun `progressionQuantifiee rend t telle quelle sans pas positif`() {
        assertEquals(0.42f, progressionQuantifiee(0.42f, pas = 0f), 0.0001f)
    }

    // Le pas 16 i/s (§G, livraison 5, 1890 → 1920) : 62,5 ms rapportés à la durée totale de
    // l'entrée. Mutation : une autre fréquence (24 i/s, par exemple) romprait le saccadé voulu.
    @Test
    fun `pasSeizeImagesParSeconde rapporte 62,5 ms a la duree de l'entree`() {
        assertEquals(0.0625f, pasSeizeImagesParSeconde(1_000), 0.0001f)
        assertEquals(0.0390625f, pasSeizeImagesParSeconde(1_600), 0.0000001f)
    }

    // Sans durée valable (jamais censé arriver), aucun pas — `progressionQuantifiee` reçoit 0 et
    // rend `t` telle quelle plutôt que de diviser par zéro.
    @Test
    fun `pasSeizeImagesParSeconde rend 0 sans duree positive`() {
        assertEquals(0f, pasSeizeImagesParSeconde(0), 0.0001f)
        assertEquals(0f, pasSeizeImagesParSeconde(-500), 0.0001f)
    }

    // L'entrée d'un carton (§D) : déjà jouée si sa décennie est dans le magasin, jamais jouée
    // sinon — `EnCours` est un état transitoire que la lecture seule du magasin ne peut pas
    // retrouver. Mutation : inverser les deux branches rejouerait l'entrée à chaque visite.
    @Test
    fun `statutDuCarton lit Jouee ou Jamais depuis le magasin`() {
        assertEquals(EtatEntree.Jouee, statutDuCarton(setOf(1890, 1900), decennie = 1890))
        assertEquals(EtatEntree.Jamais, statutDuCarton(setOf(1890), decennie = 1900))
        assertEquals(EtatEntree.Jamais, statutDuCarton(emptySet(), decennie = 1890))
    }

    // La machine de l'entrée (§D, livraison 3) : déjà jouée gagne toujours, avant même la
    // visibilité ou les animations réduites. Mutation : tester `visible` avant `dejaEntre`
    // rejouerait l'entrée d'un monde déjà marqué qui redevient visible.
    @Test
    fun `etatEntreeCarton ne rejoue jamais un monde deja marque`() {
        assertEquals(EtatEntree.Jouee, etatEntreeCarton(dejaEntre = true, visible = true, reduit = false, progression = 0f))
        assertEquals(EtatEntree.Jouee, etatEntreeCarton(dejaEntre = true, visible = false, reduit = true, progression = 0f))
    }

    // Hors écran, rien ne joue (§ « active suit la visibilité ») : ni l'entrée, ni sa progression.
    // Mutation : rendre `EnCours` hors visibilité ferait jouer une entrée jamais vue.
    @Test
    fun `etatEntreeCarton n'anime rien hors ecran`() {
        assertEquals(EtatEntree.Jamais, etatEntreeCarton(dejaEntre = false, visible = false, reduit = false, progression = 0.4f))
        assertEquals(EtatEntree.Jamais, etatEntreeCarton(dejaEntre = false, visible = false, reduit = true, progression = 1f))
    }

    // Animations réduites : direct à l'état final dès la visibilité, sans passer par `EnCours`.
    // Mutation : oublier cette branche jouerait l'entrée en douceur malgré le réglage système.
    @Test
    fun `etatEntreeCarton saute a l'etat final quand les animations sont reduites`() {
        assertEquals(EtatEntree.Jouee, etatEntreeCarton(dejaEntre = false, visible = true, reduit = true, progression = 0f))
    }

    // Sinon, l'état suit la progression fournie par l'appelant — `EnCours` tant qu'elle n'a pas
    // atteint 1, `Jouee` une fois arrivée (au-delà aussi, une progression ne redescend jamais).
    @Test
    fun `etatEntreeCarton suit la progression jusqu'a Jouee`() {
        assertEquals(EtatEntree.EnCours(0f), etatEntreeCarton(dejaEntre = false, visible = true, reduit = false, progression = 0f))
        assertEquals(EtatEntree.EnCours(0.42f), etatEntreeCarton(dejaEntre = false, visible = true, reduit = false, progression = 0.42f))
        assertEquals(EtatEntree.Jouee, etatEntreeCarton(dejaEntre = false, visible = true, reduit = false, progression = 1f))
        assertEquals(EtatEntree.Jouee, etatEntreeCarton(dejaEntre = false, visible = true, reduit = false, progression = 1.2f))
    }

    // L'ambiance d'un monde (§G, livraison 3) : seulement section visible et animations non
    // réduites — les deux conditions comptent, chacune suffit à l'arrêter. Mutation : un `||` à la
    // place du `&&` laisserait tourner une ambiance hors écran dès que les animations sont
    // réduites, ou l'inverse.
    @Test
    fun `ambianceActive suit la visibilite et respecte les animations reduites`() {
        assertTrue(ambianceActive(visible = true, reduit = false))
        assertFalse(ambianceActive(visible = false, reduit = false))
        assertFalse(ambianceActive(visible = true, reduit = true))
        assertFalse(ambianceActive(visible = false, reduit = true))
    }

    // La carte en sections (§A, §B) : une section par monde, ses années dans l'ordre, et le
    // premier monde s'arrête à `anneeActuelle` plutôt qu'à la fin de sa décennie. Mutation :
    // partir de `mondeDe(depart).decennie` sans le `maxOf` couperait les années 1890 avant 1895.
    @Test
    fun `sectionsDuVoyage met la carte a plat, du depart a l'annee actuelle`() {
        val voyage = VoyageUi(depart = 1895, anneeEnCours = 1897)
        val ui = FriseUi(voyage = voyage)

        val sections = sectionsDuVoyage(ui, anneeActuelle = 1897)

        assertEquals(1, sections.size)
        assertEquals(1890, sections.single().monde.decennie)
        assertEquals(listOf(1895, 1896, 1897), sections.single().annees.map { it.annee })
        assertEquals(listOf(0, 1, 2), sections.single().annees.map { it.rang })
    }

    // Plusieurs mondes, chacun avec ses dix années au plus, jusqu'à `anneeActuelle` qui coupe le
    // dernier avant sa fin. Mutation : `derniereDecennie` mal calculée laisserait une décennie de
    // trop, ou la couperait trop tôt.
    @Test
    fun `sectionsDuVoyage couvre plusieurs mondes et tronque le dernier a aujourd'hui`() {
        val voyage = VoyageUi(depart = 1895, anneeEnCours = 1901)
        val ui = FriseUi(voyage = voyage)

        val sections = sectionsDuVoyage(ui, anneeActuelle = 1902)

        assertEquals(listOf(1890, 1900), sections.map { it.monde.decennie })
        assertEquals((1895..1899).toList(), sections[0].annees.map { it.annee })
        assertEquals((1900..1902).toList(), sections[1].annees.map { it.annee })
    }

    // La marquise d'une section est bouclée exactement quand `ui.passeport` porte sa décennie —
    // jamais calculée depuis les statuts par année (le back seul tranche). Mutation : dériver
    // `bouclee` autrement ferait allumer une marquise que le back n'a pas encore accordée.
    @Test
    fun `sectionsDuVoyage boucle la marquise exactement quand le passeport porte la decennie`() {
        val voyage = VoyageUi(depart = 1895, anneeEnCours = 1901, tampons = listOf(TamponVoyage(1890, "2026-09-01T00:00:00.000Z")))
        val ui = FriseUi(voyage = voyage, passeport = tamponsPasseport(voyage, emptyList()))

        val sections = sectionsDuVoyage(ui, anneeActuelle = 1902)

        assertTrue(sections.first { it.monde.decennie == 1890 }.bouclee)
        assertTrue(!sections.first { it.monde.decennie == 1900 }.bouclee)
    }

    // Sans année future : le Voyage ne commence pas avant `depart`, et rien avant `anneeActuelle`
    // n'est un cas d'erreur — la carte est juste vide si le Voyage n'a pas encore commencé.
    @Test
    fun `sectionsDuVoyage est vide si anneeActuelle precede le depart`() {
        val voyage = VoyageUi(depart = 1895, anneeEnCours = 1895)
        val ui = FriseUi(voyage = voyage)

        assertTrue(sectionsDuVoyage(ui, anneeActuelle = 1894).isEmpty())
    }
}
