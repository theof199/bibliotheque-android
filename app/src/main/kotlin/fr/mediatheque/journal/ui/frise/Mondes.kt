package fr.mediatheque.journal.ui.frise

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.R
import fr.mediatheque.journal.ui.theme.PapierJauni

/**
 * Les mondes du Voyage (brief du 16 septembre 2026, phase 2 « la carte ») : une décennie, un
 * monde — un fond, un nom, un sous-titre, une couleur d'accent et un titre de voyageur.
 *
 * **Liste fixe, décision de design, kitsch assumé** : elle ne se dérive de rien et ne se charge
 * de nulle part. Les quatorze entrées couvrent 1890 → 2020 ; au-delà, `mondeDe` retombe sur la
 * dernière, et avant 1890 sur « Les origines » — le Voyage part de 1895, mais un film du journal
 * peut porter une année plus ancienne.
 *
 * Trois couleurs de fond viennent du brief mot pour mot (1890 sépia `#2A2118`, 1900 `#1C1C22`,
 * 1960 `#0E1A2B`) ; les onze autres sont choisies dans le même registre — sombres, l'appli
 * restant noire (design §1).
 *
 * L'accent d'un monde ne change **pas la police** : Manrope est la seule du dépôt (design §3).
 * Un monde se signe par sa couleur d'accent, la casse et l'espacement de son titre, et une
 * lettrine — jamais par une famille nouvelle.
 *
 * Delta de Léon du 25 septembre 2026 (« pavillon par pavillon », §C, §E, §F) : le motif de fond
 * générique (`MotifMonde`) part avec la pellicule serpentine — chaque monde gagne à la place un
 * `slogan` de marquise, un `format` de photogramme et une opacité de `grain`. Livraison 1 : le
 * grain n'est pas encore dessiné (pas d'ambiance avant la livraison 3), la donnée est posée pour
 * ne pas retoucher `Mondes.kt` à chaque livraison qui la consomme.
 *
 * Livraison 2 (28 septembre 2026, §H) : les douze mondes de 1910 à 2020 gagnent une `image` de
 * fond (nulle pour 1890 et 1900) — la ressource WebP vient de `bin/images`, ses filtres des lignes
 * « Image » de §G, ses licences de `LICENCES-IMAGES.md` à la racine. Le grain reste posé ici sans
 * être dessiné : `SectionMonde.kt` s'en charge, cette livraison-ci.
 */

/**
 * Le traitement visuel d'un photogramme (§C du delta) : chaque monde porte le sien, du sépia des
 * origines au « aujourd'hui » de 2020 — `matriceDe` en tire la `ColorMatrix` posée sur la
 * jaquette (`Photogramme.kt`), et les décorations statiques du format (bande son, bande de
 * tracking) s'y ajoutent. Les filtres d'une image de fond arrivent avec les images, livraison 2 —
 * `matriceDe` leur servira aussi, sans retouche.
 */
enum class TraitementImage {
    SEPIA, SEPIA_COLORIE, ARGENT, NOIR_ET_BLANC_DUR, BANDE_SON, BANDE_SON_CONTRASTE, SATURE,
    NB_GRANULEUX, CHAUD_DELAVE, VHS, ACIER, NUMERIQUE, STREAMING, AUJOURDHUI,
}

/**
 * Le format d'un photogramme (§C) : sa taille, l'arrondi de ses coins, la couleur de bord de son
 * état « ouverte » (nulle : la couleur par défaut du thème, `secondary`), et son traitement.
 */
data class FormatPhotogramme(
    val largeur: Dp,
    val hauteur: Dp,
    val rayon: Dp = 3.dp,
    val bord: Color? = null,
    val traitement: TraitementImage,
)

/**
 * Le cadrage d'une image de fond (§H du delta, livraison 2) : deux formes seulement — le format
 * commun aux mondes illustrés et l'affiche verticale de 1910, seule exception. `largeur`/`hauteur`
 * sont la taille affichée (`Cover` du cadrage, pas celle du WebP — 800 px de large, `bin/images`) ;
 * `x`/`y` la position dans la section ; `ancrageX`/`ancrageY` l'ancrage du recadrage « cover »
 * (0,5/0,4 : centré, légèrement remonté ; 0,5/0 : centré, calé en haut — une affiche montre son
 * titre, pas ses pieds).
 *
 * Retouche du 28 septembre 2026 (retour téléphone du propriétaire, livraison 5) : `Standard` passe
 * de 390 à 358 dp de large, centrée (16 dp de marge de chaque côté, comme le carton-titre lui-même,
 * `CartonTitre.kt`) plutôt que collée aux deux bords de la section — avec le fondu sur les quatre
 * bords ajouté à `SectionMonde.masqueQuatreBords`, une image qui touchait encore les bords aurait
 * perdu trop de son sujet dans le fondu horizontal ; la marge garde le sujet dans la zone opaque.
 */
sealed class CadrageImage(val largeur: Dp, val hauteur: Dp, val x: Dp, val y: Dp, val ancrageX: Float, val ancrageY: Float) {
    /** 358 × 300 dp à top 250, centrée, ancrage 50 % / 40 % (§H, retouché) — le cadrage de onze des douze images. */
    data object Standard : CadrageImage(358.dp, 300.dp, 16.dp, 250.dp, ancrageX = 0.5f, ancrageY = 0.4f)

    /** L'affiche de 1910, seule exception de §H : 258 × 392 dp à (66,128), ancrage 50 % / 0 %. */
    data object Affiche : CadrageImage(258.dp, 392.dp, 66.dp, 128.dp, ancrageX = 0.5f, ancrageY = 0f)
}

/**
 * L'image de fond d'un monde (§H, livraison 2) : la ressource WebP (`drawable-nodpi`, produite par
 * `bin/images` depuis `images/sources/`), son cadrage et ses filtres colorimétriques — repris tels
 * quels des lignes « Image » de §G (delta de Léon, parties 1 et 2), en sémantique CSS (des
 * facteurs : `grayscale`/`sepia` 0 → 1, `saturate`/`contraste`/`luminosite` autour de 1) plutôt que
 * les `ColorMatrix` additives de `TraitementImage` ci-dessus, pour rester lisibles à côté du texte
 * du delta qu'elles recopient. `matrice()` les combine dans cet ordre — celui des lignes de §G.
 */
data class ImageDeMonde(
    val res: Int,
    val cadrage: CadrageImage,
    val alpha: Float,
    val grayscale: Float = 0f,
    val sepia: Float = 0f,
    val saturate: Float = 1f,
    val contraste: Float = 1f,
    val luminosite: Float = 1f,
)

data class Monde(
    val decennie: Int,
    val nom: String,
    val sousTitre: String,
    val fond: Color,
    val accent: Color,
    /** Le titre gagné en bouclant la décennie, en lettres lumineuses sur la marquise (brief, item 4). */
    val titreVoyageur: String,
    /** Le slogan de la marquise (§E) — nul pour 1890, qui n'en porte aucun. */
    val slogan: String?,
    val format: FormatPhotogramme,
    /** L'opacité du grain (§F) — 0 dès 1980, le grain de pellicule n'ayant plus sa place. */
    val grain: Float,
    /** L'image de fond (§H, livraison 2) — nulle pour 1890 et 1900, qui n'en portent aucune. */
    val image: ImageDeMonde?,
)

val MONDES: List<Monde> = listOf(
    Monde(
        1890, "Les origines", "la manivelle", Color(0xFF2A2118), Color(0xFFD9B382), "Spectateur des origines",
        slogan = null, grain = 0.09f, image = null,
        format = FormatPhotogramme(56.dp, 44.dp, rayon = 4.dp, traitement = TraitementImage.SEPIA),
    ),
    Monde(
        1900, "La féerie", "Méliès et les forains", Color(0xFF1C1C22), Color(0xFFE7D8A8), "Compagnon de Méliès",
        slogan = "Méliès et les forains", grain = 0.09f, image = null,
        format = FormatPhotogramme(56.dp, 44.dp, traitement = TraitementImage.SEPIA_COLORIE),
    ),
    Monde(
        1910, "Le muet", "les grands récits", Color(0xFF201A14), Color(0xFFD8C49A), "Témoin du muet",
        slogan = "Les grands récits", grain = 0.09f,
        format = FormatPhotogramme(56.dp, 44.dp, rayon = 2.dp, traitement = TraitementImage.ARGENT),
        // Affiche Gaumont de Fantômas (1913), domaine public (§G, §I) — seule exception au
        // cadrage Standard : une affiche verticale, ancrée en haut plutôt qu'au centre.
        image = ImageDeMonde(R.drawable.voyage_1910, CadrageImage.Affiche, alpha = 0.55f, grayscale = 1f, contraste = 1.2f, luminosite = 0.72f),
    ),
    Monde(
        1920, "L’expressionnisme", "ombres obliques", Color(0xFF111114), Color(0xFFBFC7D1), "Enfant de l’expressionnisme",
        slogan = "Ombres obliques", grain = 0.09f,
        format = FormatPhotogramme(56.dp, 44.dp, traitement = TraitementImage.NOIR_ET_BLANC_DUR),
        // La rue de Holstenwall, Le Cabinet du docteur Caligari (1920), domaine public (§G, §I).
        image = ImageDeMonde(R.drawable.voyage_1920, CadrageImage.Standard, alpha = 0.62f, grayscale = 1f, contraste = 1.45f, luminosite = 0.55f),
    ),
    Monde(
        1930, "Le parlant", "Hollywood se met à causer", Color(0xFF16171C), Color(0xFFE3C77B), "Témoin du parlant",
        slogan = "100 % parlant · chantant", grain = 0.07f,
        format = FormatPhotogramme(56.dp, 41.dp, traitement = TraitementImage.BANDE_SON),
        // Boris Karloff, trailer de Bride of Frankenstein (1935), domaine public (§G, §I).
        image = ImageDeMonde(R.drawable.voyage_1930, CadrageImage.Standard, alpha = 0.6f, grayscale = 1f, contraste = 1.3f, luminosite = 0.55f),
    ),
    Monde(
        1940, "Le noir", "argentique et imperméables", Color(0xFF0F1012), Color(0xFFA9B0B8), "Détective du noir",
        slogan = "Séance de minuit", grain = 0.07f,
        format = FormatPhotogramme(56.dp, 41.dp, traitement = TraitementImage.BANDE_SON_CONTRASTE),
        // Casablanca, trailer (1942), domaine public (§G, §I).
        image = ImageDeMonde(R.drawable.voyage_1940, CadrageImage.Standard, alpha = 0.62f, grayscale = 1f, contraste = 1.6f, luminosite = 0.5f),
    ),
    Monde(
        1950, "Le Technicolor", "large et saturé", Color(0xFF1B1026), Color(0xFFF2A03D), "Enfant du Technicolor",
        slogan = "En CinemaScope · Technicolor", grain = 0.07f,
        format = FormatPhotogramme(68.dp, 29.dp, bord = Color(0xFFF2A03D), traitement = TraitementImage.SATURE),
        // James Dean, trailer de Rebel Without a Cause (1955), domaine public (§G, §I).
        image = ImageDeMonde(R.drawable.voyage_1950, CadrageImage.Standard, alpha = 0.62f, saturate = 1.9f, contraste = 1.15f, luminosite = 0.6f),
    ),
    Monde(
        1960, "Les nouvelles vagues", "caméra à l’épaule", Color(0xFF0E1A2B), Color(0xFF7FB3D5), "Vaguiste",
        slogan = "Caméra à l'épaule", grain = 0.11f,
        format = FormatPhotogramme(60.dp, 36.dp, bord = Color(0xFF7FB3D5), traitement = TraitementImage.NB_GRANULEUX),
        // Trailer de Psycho (1960), domaine public (§G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_1960, CadrageImage.Standard, alpha = 0.62f, grayscale = 1f, contraste = 1.25f, luminosite = 0.6f),
    ),
    Monde(
        1970, "Le Nouvel Hollywood", "grain et pellicule rayée", Color(0xFF1A1208), Color(0xFFD98E36), "Nouvel Hollywoodien",
        slogan = "Grain et pellicule rayée", grain = 0.13f,
        format = FormatPhotogramme(62.dp, 34.dp, bord = Color(0xFFD98E36), traitement = TraitementImage.CHAUD_DELAVE),
        // Robert De Niro, trailer de Mean Streets (1973), domaine public (§G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_1970, CadrageImage.Standard, alpha = 0.65f, sepia = 0.45f, saturate = 1.3f, contraste = 1.05f, luminosite = 0.6f),
    ),
    Monde(
        1980, "Le néon", "VHS et synthés", Color(0xFF120A22), Color(0xFFFF4FD8), "Néon kid",
        slogan = "VHS et synthés", grain = 0f,
        format = FormatPhotogramme(56.dp, 42.dp, rayon = 2.dp, bord = Color(0xFFFF4FD8), traitement = TraitementImage.VHS),
        // Cassette VHS Blockbuster (CC BY-SA 4.0, UnifiedFunctionality — §G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_1980, CadrageImage.Standard, alpha = 0.65f, saturate = 1.4f, contraste = 1.1f, luminosite = 0.55f),
    ),
    Monde(
        1990, "Le blockbuster", "générique en lettres d’acier", Color(0xFF14181C), Color(0xFFB8C4CE), "Blockbusteur",
        slogan = "Générique en lettres d'acier", grain = 0f,
        format = FormatPhotogramme(62.dp, 34.dp, bord = Color(0xFFB8C4CE), traitement = TraitementImage.ACIER),
        // Wotton Cinema, 1997 (CC BY 3.0, DavidSimpson — §G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_1990, CadrageImage.Standard, alpha = 0.65f, contraste = 1.15f, luminosite = 0.5f),
    ),
    Monde(
        2000, "Le numérique", "propre et net", Color(0xFF0B1218), Color(0xFF56C4E0), "Numérique",
        slogan = "Propre et net", grain = 0f,
        format = FormatPhotogramme(64.dp, 36.dp, rayon = 3.dp, bord = Color(0xFF56C4E0), traitement = TraitementImage.NUMERIQUE),
        // NEC Cinema DLP, CeBIT 2006 (CC BY-SA 3.0, Darkking3 — §G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_2000, CadrageImage.Standard, alpha = 0.65f, contraste = 1.05f, luminosite = 0.55f),
    ),
    Monde(
        2010, "Le streaming", "tout, tout de suite", Color(0xFF101014), Color(0xFFE5534B), "Streameur",
        slogan = "Tout, tout de suite", grain = 0f,
        format = FormatPhotogramme(64.dp, 36.dp, rayon = 4.dp, bord = Color(0xFFE5534B), traitement = TraitementImage.STREAMING),
        // Netflix sur iPhone, 2018 (CC BY 2.0, stockcatalog — §G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_2010, CadrageImage.Standard, alpha = 0.65f, contraste = 1.05f, luminosite = 0.5f),
    ),
    Monde(
        2020, "Aujourd’hui", "le voyage continue", Color(0xFF0A0A0A), Color(0xFFFF6B57), "Contemporain",
        slogan = "Le voyage continue", grain = 0f,
        format = FormatPhotogramme(64.dp, 36.dp, rayon = 4.dp, traitement = TraitementImage.AUJOURDHUI),
        // Marquise du Broadway Theatre, avril 2020 (CC BY 2.0, Dan Gaken — §G suite, §I).
        image = ImageDeMonde(R.drawable.voyage_2020, CadrageImage.Standard, alpha = 0.65f, contraste = 1.05f, luminosite = 0.5f),
    ),
)

/**
 * Le monde d'une année. 1899 appartient encore aux origines, 1900 ouvre la féerie : la bascule se
 * fait au millésime rond, jamais à `annee - 1`. Une année antérieure à 1890 (un film du journal
 * daté de 1888) rejoint les origines plutôt que de sortir de la liste, et une année postérieure
 * au dernier monde rejoint le dernier.
 */
fun mondeDe(annee: Int): Monde {
    val index = (annee - MONDES.first().decennie).floorDiv(10)
    return MONDES[index.coerceIn(0, MONDES.lastIndex)]
}

/** Le monde d'une décennie déjà arrondie — `mondeDe` sait le faire, ce nom dit juste l'intention. */
fun mondeDeLaDecennie(decennie: Int): Monde = mondeDe(decennie)

/**
 * Le numéro de chapitre du HUD, en chiffres romains — « Chapitre I · Les origines ». `index` est
 * la place du monde dans `MONDES` (0 pour 1890), le chapitre commençant à I et non à zéro.
 *
 * L'algorithme est le général, pas une table des quatorze valeurs : une quinzième décennie
 * (2030) donnera « XV » sans qu'on y retouche.
 */
fun chapitreRomain(index: Int): String {
    val valeurs = listOf(
        1000 to "M", 900 to "CM", 500 to "D", 400 to "CD", 100 to "C", 90 to "XC",
        50 to "L", 40 to "XL", 10 to "X", 9 to "IX", 5 to "V", 4 to "IV", 1 to "I",
    )
    var reste = index + 1
    if (reste <= 0) return ""
    return buildString {
        valeurs.forEach { (valeur, lettres) ->
            while (reste >= valeur) {
                append(lettres)
                reste -= valeur
            }
        }
    }
}

/** Le chapitre d'une année : « Chapitre I · Les origines ». */
fun chapitreDe(annee: Int): String {
    val monde = mondeDe(annee)
    return "Chapitre ${chapitreRomain(MONDES.indexOf(monde))} · ${monde.nom}"
}

/**
 * Les trois couleurs du podium d'une année (brief du 21 septembre 2026, « le podium ») : sobres,
 * jamais un aplat de plus dans le thème global. La marche 1 prend l'accent du monde — son « ambre »,
 * qui varie donc d'un chapitre à l'autre — la 2 le papier jauni du cartouche kitsch, la 3 le sépia
 * déjà posé sur une affiche pas encore vue (`AnneeScreen.kt`).
 */
fun Monde.couleurPodium(place: Int): Color = when (place) {
    1 -> accent
    2 -> PapierJauni
    else -> TeinteSepia
}

// --- Les traitements de couleur d'un photogramme (§C du delta, retouche du 28 septembre 2026) --

/**
 * Sature à 0 la désature complètement (noir et blanc) ; `setToSaturation` est l'extension déjà
 * posée dans le dépôt (`ui/Cover.kt`, `FiltreDesature`).
 */
private fun matriceSaturation(saturation: Float): androidx.compose.ui.graphics.ColorMatrix =
    androidx.compose.ui.graphics.ColorMatrix().apply { setToSaturation(saturation) }

/** Contraste multiplicatif autour du gris moyen (échelle 0–255 des `ColorMatrix`, comme `setToSaturation`). */
private fun matriceContraste(contraste: Float): androidx.compose.ui.graphics.ColorMatrix {
    val decalage = (1f - contraste) * 127.5f
    return androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            contraste, 0f, 0f, 0f, decalage,
            0f, contraste, 0f, 0f, decalage,
            0f, 0f, contraste, 0f, decalage,
            0f, 0f, 0f, 1f, 0f,
        ),
    )
}

/** Luminosité additive (échelle 0–255). */
private fun matriceLuminosite(delta: Float): androidx.compose.ui.graphics.ColorMatrix =
    androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            1f, 0f, 0f, 0f, delta,
            0f, 1f, 0f, 0f, delta,
            0f, 0f, 1f, 0f, delta,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

/** La matrice sépia classique, pleine intensité. */
private val MATRICE_SEPIA_PLEINE = androidx.compose.ui.graphics.ColorMatrix(
    floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    ),
)

/** Un sépia d'intensité réglable : interpolation linéaire entre l'identité et le sépia plein — une approximation visuelle assumée, jamais colorimétrique. */
private fun matriceSepia(intensite: Float): androidx.compose.ui.graphics.ColorMatrix {
    if (intensite >= 1f) return MATRICE_SEPIA_PLEINE
    val identite = androidx.compose.ui.graphics.ColorMatrix()
    val valeurs = FloatArray(20) { i -> identite.values[i] + (MATRICE_SEPIA_PLEINE.values[i] - identite.values[i]) * intensite }
    return androidx.compose.ui.graphics.ColorMatrix(valeurs)
}

/** `base` puis `ensuite` (l'ordre de lecture) : `ensuite` s'applique au résultat de `base`, jamais l'inverse. */
private fun combiner(base: androidx.compose.ui.graphics.ColorMatrix, ensuite: androidx.compose.ui.graphics.ColorMatrix): androidx.compose.ui.graphics.ColorMatrix {
    val resultat = androidx.compose.ui.graphics.ColorMatrix(base.values.copyOf())
    resultat.timesAssign(ensuite)
    return resultat
}

/**
 * La `ColorMatrix` d'un traitement (§C du delta, retouche du 28 septembre 2026) : posée sur la
 * jaquette d'un photogramme (`Photogramme.kt`) via `ColorFilter.colorMatrix`. Nulle pour les
 * mondes récents (2000 et après) — l'image y reste telle quelle, « propre et net ». Fonction pure,
 * testée en JVM (`MondesTest`) : pas de dépendance Android, `ColorMatrix` n'étant que des nombres.
 */
fun matriceDe(traitement: TraitementImage): androidx.compose.ui.graphics.ColorMatrix? = when (traitement) {
    TraitementImage.SEPIA -> matriceSepia(1f)
    TraitementImage.SEPIA_COLORIE -> matriceSepia(1f)
    TraitementImage.ARGENT -> combiner(matriceSaturation(0f), matriceLuminosite(18f))
    TraitementImage.NOIR_ET_BLANC_DUR -> combiner(matriceSaturation(0f), matriceContraste(1.35f))
    TraitementImage.BANDE_SON -> matriceSaturation(0.2f)
    TraitementImage.BANDE_SON_CONTRASTE -> combiner(matriceSaturation(0f), matriceContraste(1.25f))
    TraitementImage.SATURE -> matriceSaturation(1.4f)
    TraitementImage.NB_GRANULEUX -> matriceSaturation(0f)
    TraitementImage.CHAUD_DELAVE -> combiner(matriceSepia(0.45f), matriceSaturation(1.3f))
    TraitementImage.VHS -> matriceSaturation(1.2f)
    TraitementImage.ACIER -> matriceSaturation(0.7f)
    TraitementImage.NUMERIQUE, TraitementImage.STREAMING, TraitementImage.AUJOURDHUI -> null
}

/**
 * La luminosité « CSS » d'une image de fond (§H, livraison 2) : un facteur multiplicatif
 * (`brightness()`), pas le décalage additif de `matriceLuminosite` ci-dessus — les valeurs des
 * lignes « Image » de §G (0,5 à 0,72) sont écrites comme des pourcentages de luminosité, pas des
 * décalages sur une échelle 0–255.
 */
private fun matriceLuminositeImage(facteur: Float): androidx.compose.ui.graphics.ColorMatrix =
    androidx.compose.ui.graphics.ColorMatrix(
        floatArrayOf(
            facteur, 0f, 0f, 0f, 0f,
            0f, facteur, 0f, 0f, 0f,
            0f, 0f, facteur, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    )

/**
 * La `ColorMatrix` d'une image de fond (§H, livraison 2) : combine ses filtres dans l'ordre où
 * §G les énumère — grayscale ou sepia (jamais les deux à la fois, dans le delta), puis saturate,
 * contraste, luminosité. Un filtre à sa valeur neutre (1 pour `saturate`/`contraste`/`luminosite`,
 * 0 pour `grayscale`/`sepia`) ne pose aucune matrice : la combinaison reste l'identité pour un
 * `ImageDeMonde` qui n'en définit aucun.
 */
fun ImageDeMonde.matrice(): androidx.compose.ui.graphics.ColorMatrix {
    var resultat = androidx.compose.ui.graphics.ColorMatrix()
    if (grayscale > 0f) resultat = combiner(resultat, matriceSaturation(1f - grayscale))
    if (sepia > 0f) resultat = combiner(resultat, matriceSepia(sepia))
    if (saturate != 1f) resultat = combiner(resultat, matriceSaturation(saturate))
    if (contraste != 1f) resultat = combiner(resultat, matriceContraste(contraste))
    if (luminosite != 1f) resultat = combiner(resultat, matriceLuminositeImage(luminosite))
    return resultat
}
