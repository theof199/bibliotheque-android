package fr.mediatheque.journal.ui.theme

import androidx.compose.ui.graphics.Color

// docs/design.md §2 — une constante par ligne du tableau, et rien d'autre.
val Noir = Color(0xFF000000)
val Surface1 = Color(0xFF141414)
val Surface2 = Color(0xFF1F1F1F)
val Texte = Color(0xFFF2F2F2)
val TexteSecondaire = Color(0xFF9A9A9A)
val Filet = Color(0xFF2E2E2E)
val Corail = Color(0xFFFF6B57)
val ReactionFond = Color(0xFF3A1812)
val ReactionTexte = Color(0xFFFFB4A6)
val Ambre = Color(0xFFFFC067)
// Le Voyage (brief du 16 septembre 2026) : le cartouche kitsch d'une chronique
// d'année ou d'un carton de film — papier jauni sur le fond noir de l'appli,
// jamais dans `colorScheme` : c'est une décoration ponctuelle, pas une
// couleur sémantique de l'interface (même statut que `Ambre`, `ReactionFond`).
val PapierJauni = Color(0xFFF2E8D5)
/** Le texte du cartouche, sur le papier jauni — trop sombre pour `Texte`/`TexteSecondaire`, pensés pour le fond noir. */
val TextePapier = Color(0xFF2A2016)
/** Le cadre ornementé du cartouche, un ton plus soutenu que le papier lui-même. */
val CadrePapier = Color(0xFF8A7A57)
