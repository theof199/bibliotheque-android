package fr.mediatheque.journal.ui.frise

/**
 * Les crédits des images du Voyage (§I du delta de Léon, « pavillon par pavillon »,
 * livraison 2 du 28 septembre 2026) : la même donnée que `LICENCES-IMAGES.md` à la racine —
 * recopiée à la main plutôt que lue depuis ce fichier (un `.md` n'est pas une ressource Android,
 * et l'embarquer en asset pour douze lignes de texte aurait été plus lourd que ce copier-coller).
 * `bin/images` régénère `LICENCES-IMAGES.md` depuis `images/sources/SOURCES.txt` ; toute image
 * ajoutée ou retirée doit mettre à jour les deux, `CreditsImagesTest` vérifie qu'ils comptent le
 * même nombre d'entrées.
 *
 * Affichée dans Profil > Coulisses (« Crédits »), pas dans le Voyage lui-même — un détail de
 * provenance, pas une donnée de la carte.
 */
data class CreditImage(
    val fichier: String,
    val decennie: Int,
    val page: String,
    val auteur: String,
    val licence: String,
)

val CREDITS_IMAGES: List<CreditImage> = listOf(
    CreditImage(
        "fantomas-1913.jpg", 1910,
        "https://commons.wikimedia.org/wiki/File:Fantomas_early_film_poster.jpg",
        "affiche Gaumont, Fantômas (1913), Louis Feuillade", "domaine public",
    ),
    CreditImage(
        "caligari-rue.jpg", 1920,
        "https://commons.wikimedia.org/wiki/File:The_Cabinet_of_Dr_Caligari_Holstenwall.jpg",
        "photogramme, la ville de Holstenwall, 1920", "domaine public",
    ),
    CreditImage("orlok-ombre.svg", 1920, "", "silhouette dessinée", "CC0"),
    CreditImage(
        "karloff-1935.jpg", 1930,
        "https://commons.wikimedia.org/wiki/File:Boris_Karloff_as_The_Frankenstein_Monster_from_Bride_of_Frankenstein_film_trailer.jpg",
        "trailer screenshot, Universal 1935", "domaine public",
    ),
    CreditImage(
        "casablanca-1942.jpg", 1940,
        "https://commons.wikimedia.org/wiki/File:Casablanca,_Trailer_Screenshot.JPG",
        "trailer screenshot, Warner Bros. 1942", "domaine public",
    ),
    CreditImage(
        "dean-1955.jpg", 1950,
        "https://commons.wikimedia.org/wiki/File:James_Dean_in_Rebel_Without_a_Cause_trailer.jpg",
        "trailer screenshot, Warner Bros. 1955", "domaine public",
    ),
    CreditImage(
        "psycho-1960.png", 1960,
        "https://commons.wikimedia.org/wiki/File:Alfred_Hitchcock%27s_Psycho_trailer.png",
        "trailer screenshot, 1960", "domaine public",
    ),
    CreditImage(
        "mean-streets-1973.png", 1970,
        "https://commons.wikimedia.org/wiki/File:Movie_trailer_screenshot_of_Robert_D_Niro_in_Mean_Streets_(1973).png",
        "trailer screenshot, Warner Bros. 1973", "domaine public",
    ),
    CreditImage(
        "vhs-blockbuster.jpg", 1980,
        "https://commons.wikimedia.org/wiki/File:Blockbuster_VHS_tape_with_a_reminder_to_rewind_the_tape.jpg",
        "UnifiedFunctionality", "CC BY-SA 4.0",
    ),
    CreditImage(
        "multiplex-1990s.jpg", 1990,
        "https://commons.wikimedia.org/wiki/File:Wotton_Cinema_in_1997.jpg",
        "DavidSimpson, août 1997", "CC BY 3.0",
    ),
    CreditImage(
        "dlp-2006.jpg", 2000,
        "https://commons.wikimedia.org/wiki/File:NEC_Cinema_DLP_Beamer_cebit2006.JPG",
        "Darkking3, CeBIT 2006", "CC BY-SA 3.0",
    ),
    CreditImage(
        "netflix-2018.jpg", 2010,
        "https://commons.wikimedia.org/wiki/File:Netflix_iPhone.jpg",
        "stockcatalog, 2018", "CC BY 2.0",
    ),
    CreditImage(
        "marquee-2020.jpg", 2020,
        "https://commons.wikimedia.org/wiki/File:Mr._Smith_Goes_to_Wash_His_Hands_on_marquee_of_the_Broadway_Theatre_-_Mt._Pleasant,_MI_(49730008467).jpg",
        "Dan Gaken, avril 2020", "CC BY 2.0",
    ),
)
