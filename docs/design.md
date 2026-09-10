# Le design de l'application

*Écrit le 3 septembre 2026, après une séance de questions avec le propriétaire.
Ce document complète celui de la conception, dont le §4 décrit les écrans et
leurs gestes sans dire de quoi ils ont l'air. Il vit dans `biblio-android`,
sous `docs/design.md`. Il précède toute ligne d'interface : `ui/theme/` se
construit à partir de lui, pas l'inverse.*

L'idée tient en une phrase : **l'application s'efface derrière les films.**
Fond noir, texte gris, une seule couleur pour ce qu'on touche, et les
affiches sont la seule chose colorée à l'écran. La référence d'ambiance
donnée par le propriétaire est Keekup, pour son fond quasi noir d'où les
affiches ressortent ; on s'en inspire pour l'ambiance, on ne le copie pas.

Trois couleurs à l'écran : le noir, le gris du texte, le corail. La quatrième
est l'affiche. **Le corail a un seul sens : ce que le propriétaire choisit ou
déclenche.** Un bouton, une note sélectionnée, une réaction cochée. Jamais un
titre, jamais une décoration, jamais une erreur.

---

## 1. Ce qui a été décidé avec le propriétaire

| Décision | Raison |
|---|---|
| **Sombre uniquement.** Pas de thème clair, pas de suivi du réglage système. | Le choix du propriétaire. Un seul thème, c'est moitié moins de valeurs à tenir et à vérifier sur le téléphone, et aucun écran qu'on n'aurait jamais regardé. |
| **Palette fixe.** Pas de couleur dynamique Android. | La couleur dynamique prendrait les teintes du fond d'écran et se battrait avec les affiches. Ici, rien ne doit se battre avec les affiches. |
| **Sobre.** L'interface s'efface derrière les films. | Demande explicite. Conséquence : pas d'ornement, pas d'illustration, pas de dégradé, pas d'ombre portée. |
| **Un accent : rouge corail, esprit cinéma.** | Le choix du propriétaire parmi quatre. |
| **Manrope, embarquée.** | Le propriétaire voulait une sans-serif avec un peu plus de caractère que Roboto. Manrope est géométrique, douce, très lisible aux petites tailles, et libre (licence OFL). |
| **Affiches à coins arrondis discrets**, comme des vignettes. | Le choix du propriétaire. |
| **L'icône : un clap, volet qui claque à l'ouverture.** | Choisie par le propriétaire le 10 septembre 2026 parmi quatre pistes. Le clap s'ouvre puis claque sur l'écran de démarrage Android 12+ (§10). |

---

## 2. La palette

Les valeurs, avec le token Material 3 qu'elles remplissent dans
`darkColorScheme(...)`. Tout ce qui n'est pas listé garde la valeur que
Material dérive, et n'est utilisé nulle part.

| Token | Valeur | Rôle |
|---|---|---|
| `background`, `surface` | `#000000` | Le fond de tous les écrans. |
| `surfaceContainer` | `#141414` | Les champs de saisie, le bloc d'un message d'erreur. |
| `surfaceContainerHigh` | `#1F1F1F` | Une pastille de note non sélectionnée, l'affiche de remplacement, le snackbar. |
| `onSurface` | `#F2F2F2` | Le texte principal : titres, corps, messages. |
| `onSurfaceVariant` | `#9A9A9A` | Le texte secondaire : année, réalisateur, date, phrase sous le commentaire. |
| `outline` | `#2E2E2E` | La bordure d'un champ au repos, d'une réaction décochée. |
| `primary` | `#FF6B57` | Le corail. Boutons pleins, note sélectionnée, bordure d'un champ qui a le focus, indicateurs de chargement. |
| `onPrimary` | `#000000` | Le texte sur un bouton corail. |
| `secondaryContainer` | `#3A1812` | Le fond d'une réaction cochée. |
| `onSecondaryContainer` | `#FFB4A6` | Le texte d'une réaction cochée. |
| `error` | `#FFC067` | Uniquement la bordure d'un champ dont la saisie est refusée. |

Trois choix qu'on ne devine pas :

- **Noir pur, pas quasi noir.** Sur l'écran OLED du téléphone, `#000000`
  éteint les pixels : les affiches deviennent les seuls rectangles allumés, ce
  qui est exactement l'effet cherché. Les deux gris de surface, `#141414` et
  `#1F1F1F`, donnent les deux marches de relief dont l'interface a besoin, et
  pas une de plus.
- **Le texte sur corail est noir, pas blanc.** Le blanc sur `#FF6B57` ne
  passe pas le contraste minimal pour du texte (2,8 : 1) ; le noir passe
  (7,5 : 1). C'est aussi ce qui fait qu'un bouton corail lit comme un objet
  plein posé sur le noir, plutôt qu'un rectangle avec du texte dedans.
- **Les erreurs ne sont pas rouges.** L'accent est déjà rouge ; un message
  d'erreur rouge dirait « touche-moi ». Le message du back s'affiche donc en
  `onSurface` dans un bloc `surfaceContainer`, avec « Réessayer » en corail
  quand il est `retryable`. Le token `error`, ambre, ne sert qu'à souligner
  un champ refusé, ce qui arrive deux fois dans l'application : un mot de
  passe vide, une date dans le futur.

Contrastes vérifiés une fois, consignés ici pour ne pas les recalculer :
`onSurface` sur fond 18,8 : 1 ; `onSurfaceVariant` sur fond 7,5 : 1 ;
`primary` sur fond 7,5 : 1 ; `onPrimary` sur `primary` 7,5 : 1 ;
`onSecondaryContainer` sur `secondaryContainer` 9,4 : 1 ; `error` sur fond
13 : 1. Tous au-dessus de 4,5 : 1.

Les barres système (statut et navigation) sont transparentes, l'application
dessine bord à bord (`enableEdgeToEdge()`), et leurs icônes sont claires.
Le fond de la fenêtre et de l'écran de démarrage est `#000000` aussi : au
lancement, rien de clair ne doit apparaître, pas même une image.

---

## 3. La typographie

Manrope, en fichiers statiques dans `res/font/` : `manrope_regular`,
`manrope_medium`, `manrope_semibold`, `manrope_bold`, avec `OFL.txt` à côté.
Pas de police téléchargeable : le téléphone n'a pas à dépendre des services
Google pour afficher un titre.

Six styles, et l'application n'en utilise aucun autre. Tout en `sp`, pour
que la taille de police du téléphone soit respectée ; aucune hauteur fixe
autour d'un texte, pour qu'il puisse grandir.

| Style Material | Taille / interligne / graisse | Où |
|---|---|---|
| `displaySmall` | 40 / 44 / 700 | Les deux chiffres du profil : « 87 » et « 12 ». |
| `titleLarge` | 22 / 28 / 600 | Le titre de l'écran, le titre du film en tête du formulaire. |
| `titleMedium` | 16 / 22 / 600 | Le titre d'un film dans une liste, le pseudo sur le profil. |
| `bodyLarge` | 16 / 24 / 400 | Les champs de saisie, le commentaire, les messages. |
| `bodyMedium` | 14 / 20 / 400 | Le réalisateur et l'année, la date, la phrase d'une réaction, « Rien qu'à toi », la mention TMDB. |
| `labelLarge` | 15 / 20 / 600 | Les boutons, le chiffre d'une pastille de note. |

Interlettrage nul partout. Pas de capitales pour les étiquettes, pas de
graisse ou de couleur sur un mot isolé dans une phrase.

Dans une liste, un film s'écrit sur deux lignes : le titre en `titleMedium`,
puis le réalisateur et l'année en `bodyMedium onSurfaceVariant`, séparés
d'une virgule : « Hayao Miyazaki, 2001 ». Dans « Mes films », la seconde
ligne est la date du visionnage, écrite en toutes lettres : « 3 septembre
2026 ». La note, si elle existe, est à droite de la ligne, en
`titleMedium onSurface` : « 8 ». Les réactions sont sous le titre, emojis
seuls, sans leur phrase.

---

## 4. Formes, tailles, espacements

| | |
|---|---|
| Grille | 8 dp. Marges d'écran 16 dp. Entre deux lignes de liste, 12 dp. |
| Cibles tactiles | 48 dp au minimum, sans exception. |
| Affiche, coins | 8 dp. |
| Champs, boutons, blocs de message | 12 dp. |
| Pastilles de note (formulaire) | Cercles pleins, 48 dp. |
| Pastille de note (accueil) | Pilule pleine, ~22 dp de haut, posée en bas à droite de la jaquette. |
| Réactions | Bords entièrement ronds, hauteur 40 dp, cible 48 dp par le padding. |
| Bouton principal | Pleine largeur, hauteur 52 dp. |
| Affiche dans une liste | 56 × 84 dp (ratio 2 : 3). |
| Affiche en tête du formulaire | 96 × 144 dp, à gauche du titre. |
| Affiche à l'accueil | Calculée : un tiers de la largeur d'écran moins les écarts (ratio 2 : 3). |

**Les pastilles de note tiennent sur deux rangées de cinq**, pas une de dix.
Sur 360 dp de large moins les marges, dix cercles de 48 dp ne rentrent pas ;
dix cercles de 28 dp rentreraient mais seraient injouables au pouce. La
rangée du haut porte 1 à 5, celle du bas 6 à 10.

**L'affiche de remplacement.** Quand `cover_url` manque ou ne charge pas :
un rectangle `surfaceContainerHigh` aux mêmes dimensions, avec la première
lettre du titre centrée en `titleMedium onSurfaceVariant`. Pas d'icône
d'image cassée, pas de texte « indisponible » : une liste où une affiche
manque doit rester calme.

---

## 5. Les écrans, composant par composant

Tous les composants sont ceux de Material 3 pour Compose, dans la palette
ci-dessus, sans style ajouté.

| Écran | Composants |
|---|---|
| **Connexion** | Deux `OutlinedTextField` (pseudo, mot de passe avec bascule de visibilité), bordure `outline` au repos et `primary` au focus. Un `Button` plein corail « Se connecter ». Le message du back sous le bouton, dans son bloc. Sur `429`, le bouton passe en désactivé (opacité 38 %, standard Material) le temps de `Retry-After`. Contenu centré verticalement. |
| **Accueil** | Une grille de jaquettes sur trois colonnes (`LazyVerticalGrid`), du visionnage le plus récent au plus ancien, chacune avec sa note en pastille `surfaceContainerHigh` en bas à droite quand elle existe. Toucher une jaquette ouvre le formulaire de correction, pré-rempli, comme une ligne de « Mes films ». Le `Button` plein « Ajouter un film », pleine largeur, est descendu en bas de l'écran, hors du défilement de la grille. Un `IconButton` profil en haut à droite, icône de personne en `onSurface`, cible 48 dp. Demandé par le propriétaire le 10 septembre 2026, après le premier essai sur téléphone. |
| **Recherche** | Une barre en haut : `IconButton` retour, puis un `TextField` sans bordure sur `surfaceContainer`, pleine largeur, focus et clavier ouverts à l'arrivée, croix d'effacement quand il y a du texte. Dessous, une `LazyColumn` de lignes : affiche, titre, réalisateur et année. Pendant une requête, un `LinearProgressIndicator` de 2 dp en corail, juste sous la barre. |
| **Formulaire** | En tête, l'affiche et le titre en `titleLarge`. La date : un champ tactile qui affiche « 3 septembre 2026 » et ouvre un `DatePickerDialog` limité à aujourd'hui inclus. La note : deux rangées de cinq pastilles. Les réactions : des `FilterChip` dans un `FlowRow`, emoji puis phrase, qui passent à la ligne. Le commentaire : `OutlinedTextField`, trois lignes au minimum, qui grandit, avec « Rien qu'à toi » en `supportingText`. Le bouton « Enregistrer » plein, corail, pleine largeur, collé en bas de l'écran au-dessus du clavier. En correction, il dit « Corriger », et un `TextButton` « Supprimer » en `onSurfaceVariant` se tient sous lui ; il ouvre un `AlertDialog` à deux boutons, « Annuler » et « Supprimer ». |
| **Profil** | Le pseudo en `titleMedium`. Puis la phrase en une ligne, où seuls les deux nombres sont en `displaySmall onSurface` et le reste en `bodyLarge onSurfaceVariant` : **87** films vus, **12** cette année. Un `ListItem` « Mes films » avec chevron à droite. Un `TextButton` « Se déconnecter » en `onSurfaceVariant`. En bas, la mention TMDB (§9). |
| **Mes films** | Une `LazyColumn` de lignes : affiche, titre, date, emojis des réactions, note à droite. Un `CircularProgressIndicator` corail en fin de liste tant qu'un `next_cursor` reste à charger. |

Un écran a au plus un élément corail plein. Quand le formulaire en montre
plusieurs, ce sont des états sélectionnés, pas des boutons, et un seul bouton.

---

## 6. Les états

| État | Ce qu'on voit |
|---|---|
| **Chargement** d'une recherche | La barre linéaire de 2 dp sous le champ. Les résultats précédents restent affichés jusqu'aux nouveaux. |
| **Chargement** d'une liste | Un indicateur circulaire corail, 40 dp, centré dans l'espace vide ; en pagination, le même en fin de liste. |
| **Enregistrement** en cours | Le bouton reste en place, garde son texte, passe en désactivé, et un indicateur circulaire de 20 dp en `onPrimary` apparaît à gauche du texte. Il ne change pas de taille. |
| **Vide**, l'accueil et « Mes films » | « Aucun film pour l'instant. » en `bodyLarge onSurfaceVariant`, centré. |
| **Vide**, recherche sans résultat | « Rien trouvé pour “…”. » sous la barre, même style. Champ vide : rien du tout. |
| **Erreur**, toutes | Le `message` du back tel quel, `bodyLarge onSurface`, dans un bloc `surfaceContainer` à coins 12 dp, marge 16 dp. Si `retryable`, un `TextButton` « Réessayer » corail, aligné à droite dans le bloc. Panne réseau : le même bloc avec « L'API est injoignable. » |
| **Erreur**, second appel du geste | Le même bloc, avec « Le film est ajouté, mais pas ton visionnage. » et « Réessayer », qui ne relance que `POST /me/journal`. |
| **Succès** | Un `Snackbar` « Enregistré », fond `surfaceContainerHigh`, texte `onSurface`, deux secondes, sans action. Pas de flash clair : la version inversée de Material n'est pas utilisée. |
| **Désactivé** | Opacité 38 %, valeur de Material, rien de spécifique. |

Une erreur dit ce qui s'est passé et ce qu'on peut faire, jamais « oups »,
jamais d'excuse ; c'est déjà le ton des messages du back, on le garde.

---

## 7. Mouvement

Le minimum, et tout ce qui bouge répond à un geste :

- Entre deux écrans, un fondu de 200 ms, sans glissement. Le retour arrière,
  le même à l'envers.
- Une pastille ou une réaction qui change d'état : sa couleur en 150 ms
  (`animateColorAsState`). Rien ne grossit, rien ne rebondit.
- Le snackbar entre et sort comme Material le fait.
- Rien au lancement, rien à l'arrivée d'une liste, rien au chargement d'une
  affiche : elles apparaissent quand elles sont là.

Le réglage système « Supprimer les animations » est respecté ; Compose le
fait seul.

---

## 8. Accessibilité

- Tous les textes en `sp`, aucune hauteur fixe autour d'un texte. À la taille
  de police maximale du téléphone, les pastilles restent sur deux rangées et
  les réactions passent à la ligne autant qu'il faut.
- Les contrastes du §2 sont tous au-dessus de 4,5 : 1.
- Les cibles font 48 dp.
- `contentDescription` : une affiche dit « Affiche de {titre} » ; une pastille
  dit « Note {n} sur 10 » et porte son état sélectionné ; une réaction porte sa
  phrase, jamais son emoji seul ; l'icône du haut à droite dit « Profil » ; la
  croix de la recherche dit « Effacer ».
- Le focus clavier se voit : bordure `primary` sur les champs, contour
  Material sur les boutons.

---

## 9. La mention TMDB

En bas du profil, sous « Se déconnecter », avec 24 dp au-dessus : le logo TMDB
tiré de leur kit de marque, dans sa version prévue pour fond sombre, largeur
88 dp, non recoloré, non déformé ; puis la phrase exigée, en anglais telle
qu'ils la donnent, en `bodyMedium onSurfaceVariant`, centrée. Les conditions
du kit (taille minimale, marges, versions autorisées) se lisent sur leur page
au moment d'intégrer le fichier, et ce qui y est écrit l'emporte sur ce
paragraphe.

---

## 10. Où ça vit dans le code

```
app/src/main/kotlin/fr/mediatheque/journal/ui/theme/
  Color.kt     une constante par ligne du §2, et rien d'autre
  Type.kt      la famille Manrope et les six styles du §3
  Shape.kt     8, 12, rond
  Theme.kt     JournalTheme : darkColorScheme(...) avec les valeurs de Color.kt,
               pas de dynamicDarkColorScheme, pas de branche claire, pas de
               isSystemInDarkTheme()
app/src/main/res/
  font/        manrope_regular.ttf, _medium, _semibold, _bold, OFL.txt
  values/themes.xml      windowBackground #000000, splash background #000000
  values-v31/themes.xml  windowSplashScreenAnimatedIcon, windowSplashScreenAnimationDuration
  mipmap-anydpi-v26/ic_launcher.xml   icône adaptative (fond + premier plan)
  drawable/ic_launcher_foreground.xml   le clap, statique, volet au repos (-30°, ouvert)
  drawable/ic_launcher_animated.xml     le même clap, animé (AnimatedVectorDrawable)
  animator/ic_launcher_volet_claque.xml la rotation du groupe « volet »
  interpolator/rebond_franc.xml         l'overshoot du claquement
```

**L'icône : le clap.** Fond `#000000` (`ic_launcher_background.xml`), premier
plan corail `#FF6B57` : le corps fixe et le volet, un `<group
android:name="volet">` pivoté en bas à gauche (32,48), au repos à −30°,
volet ouvert — le tiroir et l'écran d'accueil montrent le clap ouvert.
(Repos à −14° jusqu'au 10 septembre 2026, changé le même jour sur demande du
propriétaire : « un clap plus actif ».) C'est la même géométrie, dans
`ic_launcher_foreground.xml`, qui sert d'icône de l'application (tiroir,
raccourcis) et de base à l'animation : aucune duplication de `pathData` à
garder synchronisée. À −30°, le coin de l'extrémité du volet déborde du
cercle de sécurité de 66 dp (et même du cadre de 72 dp) : les masques de
lanceur le recadrent proprement, sans bord irrégulier ; c'était déjà
(légèrement) le cas à −14°, et réduire à −26° ne le règle pas — voir le
commentaire du fichier.

Sur l'écran de démarrage, Android 12+ seulement
(`android:windowSplashScreenAnimatedIcon`, `values-v31/themes.xml`), le clap
s'anime en deux temps puis reste fermé : ouvert au repos (−30°), il s'arme
à −46° en 120 ms (`fast_out_linear_in`), puis claque à 0° en 260 ms avec un
rebond franc (`overshootInterpolator`, tension 2,5) — et reste à 0°, fermé,
jusqu'à la fin de l'écran de démarrage. 380 ms au total. `MainActivity`
attend le temps qui reste à jouer — pas une durée fixe — borné à [0, 380] ms
via `SplashScreenView.getIconAnimationStart()` /
`.getIconAnimationDuration()` (`Activity.getSplashScreen()`, l'API du
framework, sans dépendance ajoutée) : sur un démarrage à froid plus lent que
l'animation, elle joue jusqu'au bout sans retarder l'accueil au-delà ; sur
un démarrage tiède plus rapide, l'écran ne s'attarde pas. Sous Android 12,
pas d'écran de démarrage animé : l'icône statique (ouverte, −30°) suffit,
rien à faire. Les deux variantes (`debug`, `release`) reçoivent la même
icône.

Aucune couleur en dur dans un écran : un écran dit `MaterialTheme.colorScheme.primary`,
jamais `#FF6B57`. Aucune taille de texte en dur : un écran dit
`MaterialTheme.typography.bodyMedium`. C'est la même règle que « aucun chemin
d'API hors de `Endpoints.kt` », pour la même raison.

---

## 11. Vérification, sans émulateur

Ça se vérifie sur le téléphone, à ajouter à la liste de contrôle du `README` :

1. Au lancement, aucun flash clair : l'écran de démarrage, la fenêtre et le
   premier écran sont noirs.
2. Taille de police système au maximum : rien n'est coupé, les pastilles
   sont sur deux rangées, les réactions passent à la ligne, les boutons
   gardent leur texte.
3. En plein soleil, le texte secondaire `#9A9A9A` reste lisible ; sinon, on
   l'éclaircit et on met à jour le §2.
4. Les dix pastilles s'atteignent au pouce, téléphone tenu d'une main.
5. Un film sans affiche montre son initiale, pas une icône cassée.
6. Une réaction retirée du catalogue s'affiche comme sa clé nue, dans le
   même style qu'une phrase.
7. Un `429` à la connexion désactive le bouton et le réactive à la fin du
   délai.
8. Le snackbar « Enregistré » se lit sur le fond noir et ne recouvre pas le
   bouton d'un écran qui suit.

---

## 12. Ce qui est laissé de côté, et pourquoi

- **Un thème clair, la couleur dynamique.** Décidés contre, §1.
- **Des illustrations d'états vides, des animations décoratives, un retour
  haptique.** Contraires à « sobre » — l'animation du clap (§10) est l'icône
  elle-même, pas une décoration d'écran.
- **Une police pour les titres différente de celle du corps.** Manrope en
  600 et 700 fait le travail ; deux familles pour six écrans, c'est une de
  trop.
- **Les tests de captures d'écran** (Roborazzi tourne sans émulateur, sur la
  JVM). Possibles, utiles le jour où quelqu'un d'autre touchera au thème ;
  pas maintenant.
- **Un système de tokens au-delà de Material.** Les onze couleurs, les six
  styles et les trois rayons sont tout le système. Nommer davantage serait
  décrire ce qui n'existe pas.
