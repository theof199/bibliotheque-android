package fr.mediatheque.journal.ui.theme

import android.animation.ValueAnimator

/**
 * Les animations réduites (la barre du bas · les cinq enseignes, 25 septembre 2026) : vrai quand
 * le téléphone a coupé les animations — l'échelle de durée des animations à 0 dans les options
 * développeur, ou « Supprimer les animations » dans l'accessibilité, qui la pose à 0 elle aussi.
 * `ValueAnimator.areAnimatorsEnabled()` lit ce réglage système ; l'appli ne le lisait nulle part
 * avant la lampe de la barre du bas, qui saute au lieu de glisser quand il est vrai.
 *
 * Une simple fonction plutôt qu'un `CompositionLocal` : elle ne dépend pas de Compose et peut
 * servir telle quelle aux écrans qui suivront (la feuille de lecture, l'accueil). Le réglage ne
 * change pas pendant qu'on regarde un écran : un `remember { animationsReduites() }` au site
 * d'appel suffit, relu à la composition suivante.
 */
fun animationsReduites(): Boolean = !ValueAnimator.areAnimatorsEnabled()
