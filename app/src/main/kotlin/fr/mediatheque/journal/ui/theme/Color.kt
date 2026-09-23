package fr.mediatheque.journal.ui.theme

import androidx.compose.ui.graphics.Color

// Papier et pellicule (peaufinage du 23 septembre 2026, docs/design.md §2) : la sobriété du fond
// noir pur cède à une palette chaude, papier jauni et pellicule argentique. Une constante par
// ligne, et rien d'autre.
val Fond = Color(0xFF151009)
val Surface1 = Color(0xFF1E1710)
val Surface2 = Color(0xFF261D14)
val Texte = Color(0xFFECE2CC)
val TexteSecondaire = Color(0xFFB3A688)
/**
 * rgba(230,185,74,0.25) de la maquette, mélangé à plat sur `Fond` (#151009) : Compose ne pose pas
 * une couleur translucide sur un fond arbitraire aussi simplement qu'un `rgba()` CSS au-dessus
 * d'un fond connu d'avance, donc l'opaque équivalent est calculé une fois ici :
 * R 230×0,25+21×0,75=73, V 185×0,25+16×0,75=58, B 74×0,25+9×0,75=25 → #493A19.
 */
val Filet = Color(0xFF493A19)
val Corail = Color(0xFFFF6B57)
/** L'or de la pellicule : cadres, notes, filets — posé en `secondary`/`tertiary` (Theme.kt). */
val Or = Color(0xFFE6B94A)
val ReactionFond = Color(0xFF3A1812)
val ReactionTexte = Color(0xFFFFB4A6)
val Ambre = Color(0xFFFFC067)
// Le cartouche papier jauni du Voyage, et désormais toute pastille de note, réaction ou remarque
// « papier » à travers l'appli — jamais dans `colorScheme` : une décoration ponctuelle, pas une
// couleur sémantique de l'interface (même statut que `Or`, `ReactionFond`). Inchangés par
// l'habillage du 23 septembre 2026.
val PapierJauni = Color(0xFFF2E8D5)
/** Le texte du cartouche, sur le papier jauni — trop sombre pour `Texte`/`TexteSecondaire`, pensés pour le fond sombre. */
val TextePapier = Color(0xFF2A2016)
/** Le cadre ornementé du cartouche, un ton plus soutenu que le papier lui-même. */
val CadrePapier = Color(0xFF8A7A57)
