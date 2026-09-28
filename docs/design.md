# Le design de l'application

**Ce document dit l'identité et les quelques principes tenus. Tout le reste est libre : le code
fait foi pour le détail des écrans, et git garde l'historique.**

## Identité

**Papier et pellicule.** Un fond chaud, du papier jauni, une pellicule argentique : grain de fond
et perforations habillent l'écran plutôt qu'un noir neutre.

| Couleur | Valeur | Rôle |
|---|---|---|
| Fond | `#151009` | Le fond de tous les écrans. |
| Papier | `#F2E8D5` | Cartouches, tickets, remarques « papier ». |
| Or | `#E6B94A` | Cadres, notes, filets, célébrations. |
| Corail | `#FF6B57` | L'accent — voir principes, ci-dessous. |

Valeurs et jetons Material : `ui/theme/Color.kt`. Formes (coins 8/12/16/20 dp) : `ui/theme/Shape.kt`.
Thème et grain de fond : `ui/theme/Theme.kt`. Cadres, filets et perforations : `ui/theme/Ornements.kt`.

**Les polices**, en fichiers statiques (`res/font/`), jamais téléchargées : Manrope pour le corps
de texte, Fraunces pour les titres d'écran et les chiffres, Limelight pour les célébrations et les
cartons-titres. Styles : `ui/theme/Type.kt`.

**Les icônes** viennent de [Tabler Icons](https://tabler.io/icons) (MIT), trait 1,75, teintées à
l'usage (`ui/theme/IconeTabler.kt`). Chaîne de génération : `bin/icones`
(README, « Les icônes »).

**Les images et animations.** Des emblèmes fournis par le propriétaire (webp,
`res/drawable-nodpi/`, `ui/Emblemes.kt`) et des animations Lottie sous licence libre
(`assets/lottie/`, chargées par `ui/theme/Animations.kt`) habillent les célébrations. README,
« Les emblèmes » et « Les animations », disent comment en ajouter.

**Les mondes du Voyage.** Une décennie, un monde : chacun garde son propre fond et son propre
accent, posés par-dessus le fond chaud de l'application — liste fixe, kitsch assumé
(`ui/frise/Mondes.kt`).

## Les principes tenus

- **Le corail signale ce que le propriétaire choisit ou déclenche** — jamais un titre, jamais une
  décoration, jamais une erreur.
- **Le thème est sombre**, sans variante claire ni couleur dynamique.
- **La mention TMDB est obligatoire** (bas du profil, logo et phrase de leur kit) : condition de la
  licence des données.
- **L'accessibilité de base est tenue** : une description sur chaque image ou icône isolée, des
  cibles tactiles de 48 dp au minimum, le réglage système « Supprimer les animations » respecté.

## Où ça vit dans le code

```
ui/theme/         palette, typographie, formes, mouvement, ornements
ui/frise/         le Voyage : carte, mondes, fiches d'année et de décennie
ui/celebrations/  les calques de célébration
ui/fiche/         les fiches d'un film (entrée, Voyage, simple)
res/font/         Manrope, Fraunces, Limelight
assets/lottie/    les animations Lottie
```

## Vérification, sans émulateur

Sur le téléphone, à garder dans la liste de contrôle du README :

1. Au lancement, aucun flash clair : l'écran de démarrage et la fenêtre restent noirs.
2. Taille de police système au maximum : rien n'est coupé, un texte long passe à la ligne.
3. Les cibles s'atteignent au pouce, téléphone tenu d'une main.
4. Un film sans affiche montre son initiale, jamais une icône cassée.
5. Le snackbar se lit sur le fond et ne recouvre pas un bouton de l'écran qui suit.
