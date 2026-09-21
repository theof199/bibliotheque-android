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

### Les mondes du Voyage (brief du 16 septembre 2026, phase 2)

Hors de `colorScheme`, comme le papier jauni du cartouche : une décennie, un
monde, et le fond que prennent la carte du Voyage, sa page d'année et son rayon
de décennie — jamais le reste de l'application. Liste fixe (`ui/frise/Mondes.kt`),
décision de design, kitsch assumé. Les trois fonds marqués d'une étoile viennent
du brief mot pour mot ; les onze autres sont choisis dans le même registre.

| Décennie | Nom · sous-titre | Fond | Accent | Titre de voyageur |
|---|---|---|---|---|
| 1890 | Les origines · la manivelle | `#2A2118` ★ | `#D9B382` | Spectateur des origines |
| 1900 | La féerie · Méliès et les forains | `#1C1C22` ★ | `#E7D8A8` | Compagnon de Méliès |
| 1910 | Le muet · les grands récits | `#201A14` | `#D8C49A` | Témoin du muet |
| 1920 | L'expressionnisme · ombres obliques | `#111114` | `#BFC7D1` | Enfant de l'expressionnisme |
| 1930 | Le parlant · Hollywood se met à causer | `#16171C` | `#E3C77B` | Témoin du parlant |
| 1940 | Le noir · argentique et imperméables | `#0F1012` | `#A9B0B8` | Détective du noir |
| 1950 | Le Technicolor · large et saturé | `#1B1026` | `#F2A03D` | Enfant du Technicolor |
| 1960 | Les nouvelles vagues · caméra à l'épaule | `#0E1A2B` ★ | `#7FB3D5` | Vaguiste |
| 1970 | Le Nouvel Hollywood · grain et pellicule rayée | `#1A1208` | `#D98E36` | Nouvel Hollywoodien |
| 1980 | Le néon · VHS et synthés | `#120A22` | `#FF4FD8` | Néon kid |
| 1990 | Le blockbuster · générique en lettres d'acier | `#14181C` | `#B8C4CE` | Blockbusteur |
| 2000 | Le numérique · propre et net | `#0B1218` | `#56C4E0` | Numérique |
| 2010 | Le streaming · tout, tout de suite | `#101014` | `#E5534B` | Streameur |
| 2020 | Aujourd'hui · le voyage continue | `#0A0A0A` | `#FF6B57` | Contemporain |

Deux couleurs de plus, hors `colorScheme` elles aussi : l'or `#E6B94A` (le liseré
d'un photogramme fait, les lettres lumineuses d'une marquise et le titre de
voyageur d'un générique) et le corail existant, réemployé pour le cône de lumière
de l'année en cours et le cercle d'un tampon de passeport.

**Le motif d'un monde est simplifié, et c'est assumé** : six motifs génériques
(cercle, étoiles, diagonales, rayures, grain, bandes) dessinés au `Canvas` à très
faible opacité, réutilisés d'un monde à l'autre, plutôt que quatorze motifs sur
mesure. Un décor, jamais un élément qu'on lit.

**Manrope reste la seule police** (§3) : l'accent d'un monde se fait par la
couleur, la graisse, la casse, l'espacement des lettres et une lettrine — jamais
par une famille de plus.

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
| **Accueil** | Une grille de jaquettes sur trois colonnes (`LazyVerticalGrid`), du visionnage le plus récent au plus ancien, chacune avec sa note en pastille `surfaceContainerHigh` en bas à droite quand elle existe. Toucher une jaquette ouvre le formulaire de correction, pré-rempli, comme une ligne de « Mes films ». Au-dessus de la grille, jusqu'à trois lignes « Ensuite » (brief du 15 septembre 2026, ci-dessous) : celle du Plex, puis celle du réalisateur en cours, puis celle de la saga en cours (brief du même jour, la Sagas ayant rejoint l'onglet Suivis). Le `Button` plein « Ajouter un film », pleine largeur, est descendu en bas de l'écran, hors du défilement de la grille, au-dessus de la barre de navigation du bas. Une `NavigationBar` (Material 3), cinq entrées « Accueil », « Frise », « Suivis », « Au ciné » et « Profil », est visible sur cet écran, sur la Frise, « Suivis », « Au ciné », « Mes films » et sur le profil ; « Accueil » y est sélectionnée. Elle remplace l'`IconButton` profil qui occupait le haut à droite, jugé inaccessible (décision du propriétaire du 14 septembre 2026 ; troisième entrée « Au ciné », même jour ; quatrième entrée « Frise » et cinquième « Réalisateurs », le 15 septembre 2026, renommée « Suivis » le même jour quand les sagas l'ont rejointe — icône inchangée). |
| **Ensuite** (brief du 15 septembre 2026) | Une ligne au-dessus de la grille de l'accueil : une affiche 56×84 à gauche, puis un libellé (`bodyMedium onSurfaceVariant`) et le titre du film suivi de son année entre parenthèses (`titleMedium`) — « *Le Voyage de Chihiro* (2001) ». Toucher la ligne ouvre le formulaire pré-rempli, comme une tuile « Au ciné ». Trois lignes possibles, un seul composant (`LigneEnsuite`, `ui/home/HomeScreen.kt`) : celle du Plex, libellée « Ensuite », pour le plus ancien film à voir sur le Plex ; celle du réalisateur en cours et celle de la saga en cours, toutes deux libellées « Ensuite · *Nom* » (brief du 15 septembre 2026, généralisé aux sagas le même jour), pour le prochain film à voir de l'entité suivie — de l'une ou l'autre source — qui a le plus de films vus parmi celles qui en ont encore au moins un à voir. Chacune absente si elle n'a rien à montrer. Pas de chargement bloquant : l'accueil s'affiche tout de suite, chaque ligne apparaît quand sa donnée a répondu (`GET /reference/plex` pour la première, les filmographies des réalisateurs suivis puis des sagas suivies pour les deux autres), sans rond de chargement ni saut de mise en page qui la précède. **« Ce soir »** (décision 4 du brief du 21 septembre 2026, « la séance »), même composant `LigneEnsuite`, au-dessus des trois lignes ci-dessus : libellée « Ce soir », le titre du long suivi de « + » et celui du court s'il y en a un (`texteCeSoir`), tant que `seance_prise` de `GET /me/voyage` est non nul ; disparaît d'elle-même quand le back la rend nulle (le long vu, ou la séance ignorée). Toucher la ligne ouvre la fiche de l'année. |
| **Voyage** (brief du 15 septembre 2026 « la Frise », devenu le calendrier du siècle le 16, puis **la carte** le même jour, réécrit pour « l'année en étages » le 21 septembre 2026, les récompenses le même jour encore, étape 5) | Quatrième entrée de la `NavigationBar` du bas, entre « Accueil » et « Suivis » — icône `Timeline`, `contentDescription` « Frise » ; sélectionnée sur cet écran. En tête, le HUD (fixe, hors défilement) : « Chapitre I · Les origines » (`titleLarge`, dans l'accent du monde en cours, le numéro en chiffres romains), puis « 3 années visitées · 1898 en cours » et, dès qu'une année au moins a une récompense, « 3 Palmes · 1 Lion » (`recompenseDe`, `phraseRecompenses`), tous deux en `bodyMedium onSurfaceVariant` — la ligne des récompenses est absente sinon, sans `if` de plus, `phraseRecompenses` rendant elle-même une chaîne vide. Puis la carte, dans une `LazyColumn` : une **pellicule** dessinée au `Canvas` serpente du haut vers le bas (deux courbes quadratiques par cellule, largeur 40 dp, perforations posées à la normale de la courbe sur les deux bords), et porte **un photogramme par année** (54 × 40 dp) — année **ouverte** (avant l'année en cours, toujours creusable, jamais « finie ») : l'affiche du n°1 de son podium (brief du 21 septembre 2026), ou celle du dernier film vu de l'année tant qu'aucun podium n'y est posé (`afficheAnnee`), liseré doré `#E6B94A` et « *N* films » (sa profondeur) dessous, le glyphe de sa récompense s'il y en a une ; année en cours : le même photogramme sous un cône de lumière corail (dégradé radial) avec « Tu es ici » et le **clap de l'icône** à côté ; année verrouillée : un cadre noir au liseré gris pointillé portant le millésime, sous-titré « à tourner », ou « *N* vu(s) en avance » si le journal y a déjà des films. Toucher un photogramme ouvre `Screen.Annee`. À l'ouverture, la liste défile jusqu'à l'année en cours. Les années sont groupées par **monde** (une décennie, §2) : le monde donne le fond de sa section, son motif, et un titre en tête (lettrine en `displaySmall`, « 1890 · LES ORIGINES » en `titleMedium` espacé, le sous-titre en `bodyMedium onSurfaceVariant`). Au bout de chaque monde, sa **marquise** de cinéma (`Canvas` : un bloc, un auvent en trapèze, neuf ampoules le long de l'auvent) — ampoules éteintes (`outline`) tant que la décennie n'est pas bouclée, allumées une à une en 1,5 s au moment où elle l'est, d'un coup si elle l'était déjà ; le nom de la décennie, et le titre de voyageur en lettres or quand elle est bouclée, « en cours de tournage » sinon — bouclée quand chacune de ses dix années a un Ours et que le ticket de la décennie suivante est utilisé (spec du 19 septembre 2026, §6, étape 5). L'apparition d'une décennie que le `ViewModel` n'avait pas encore vue (`detecterNouveauTampon`, comparée à la liste mémorisée, jumeau de la frontière) allume alors sa marquise et rejoue son générique tout seul — découplé du clap : une décennie peut se boucler sans que l'année en cours ne la quitte au même moment. Toucher la marquise ouvre `Screen.Decennie`. La carte « Prochaine étape » a disparu avec les essentiels qui la nourrissaient, sans retour : le podium (étape 2, ci-dessous) ne fait que fournir l'affiche du photogramme, il ne vit que dans l'année (spec du 19 septembre 2026, §2). Une année en cours qui avance (le ticket, encaissé sur sa fiche ou depuis le portefeuille — le bouton provisoire « Année suivante » a disparu, 21 septembre 2026) fait claquer le clap et affiche « 1898 dans la boîte ! » en snackbar, sans suffixe de récompense — décision du propriétaire (étape 5) : rien de plus sur ce message. **La proposition de carnet** (décision 1 du brief du 22 septembre 2026, « le carnet »), même site : un `AlertDialog` « *1895* est bouclée » / « Veux-tu son carnet ? », « Oui » lance sa fabrication (`POST .../annees/1895/carnet`) et referme le dialogue, avec un message bref « Le carnet de 1895 se fabrique » en snackbar, ou celui du back sur une `409` (une fabrication déjà en cours) ; « Plus tard » referme sans rien faire. |
| **Le ticket** (décision 2 du brief du 21 septembre 2026, « le ticket ») | Un calque posé au-dessus de l'écran courant (`Root.kt`, `TicketCalque`), quel qu'il soit — la Frise à son ouverture, ou l'accueil juste après un enregistrement — dès que `ticket_a_montrer` (`GET /me/voyage`) est non nul. Le ticket : rectangle papier `#F2E8D5` à bords perforés (`Canvas`, `bordsPerfores`, aucune image), penché de −3°, « ADMIS UNE PERSONNE » en petit espacé, l'année en `displaySmall` dans l'accent du monde de cette année, le motif en `bodyMedium`. Deux boutons dessous : « Utiliser maintenant » (`POST .../utiliser` puis `frise.refresh()` — « Tu es ici » avance, le clap claque) et « Garder dans le portefeuille » (`TextButton`, `onSurfaceVariant`) ; dans les deux cas `POST .../montre` est appelé et le calque se ferme, sans jamais se remontrer (le back ne le renvoie plus). Un fond noir translucide (`alpha` 0,72) bloque le passage vers l'écran en dessous. |
| **Décennie** (brief du 16 septembre 2026) | Le rayon d'une décennie, empilé depuis le calendrier de la Frise en touchant l'étiquette d'une décennie, sans barre du bas : une barre de titre avec retour et « Années *décennie* » (`titleLarge`). Puis un anneau de progression (`CircularProgressIndicator`, corail sur `surfaceContainerHigh`) — vus sur vus plus à voir, le pourcentage au centre, « ✓ » à 100 % — à côté de « *N* vus · *M* à voir ». Puis une étagère horizontale (`LazyRow`) des films de la décennie, dans l'ordre des années puis du titre : les vus en tuiles d'affiche notées (52×78 dp, comme l'accueil), les à voir en tuiles au liseré pointillé — celui d'`AnneeScreen`, mais en corail — avec l'année en bas ; toucher un vu ouvre `Screen.Edit`, un à voir `Screen.Form` pré-rempli. Sous l'étagère, dix puces année (`labelSmall`), touchables (`Screen.Annee`), en opacité désactivée (38 %, désactivé du §6) si l'année n'a rien, **ou si le Voyage la déclare verrouillée** (brief du 16 septembre 2026). **La palette du monde** (§2, phase 2) : le fond de l'écran et la couleur de l'anneau sont ceux de la décennie, et son nom (« Le muet · les grands récits ») s'affiche sous le titre. Retour système : à la carte du Voyage. |
| **Année** (brief du 15 septembre 2026, augmentée du Voyage le 16, réécrite pour « l'année en étages » le 21 septembre 2026, les récompenses le même jour encore, étape 5) | Empilé depuis la Frise, sans barre du bas : une barre de titre avec retour, le millésime (`titleLarge`), sa profondeur (« *N* films »), puis, sous elle, une ligne `bodySmall onSurfaceVariant` de progression (`ligneProgression`) — « *N* essentiels sur *M* · *N* salles complètes sur *M* » (depuis `progression`, `GET /me/voyage/annees/{annee}`), ou « Aucun essentiel encore » si `essentiels_total` vaut 0, absente tant que la progression n'est pas encore chargée — puis le nom du monde sous le titre, dans son accent (§2, phase 2), fond de l'écran compris. **Le cartouche**, kitsch : papier jauni (`#F2E8D5`), cadre ornementé simple, avec le glyphe de récompense (`glypheRecompense`, `Recompense`) à côté du millésime, absent sans aucun film vu. Sur une année ouverte ou en cours, il porte l'**ouverture** de la chronique — cinq à huit phrases, repliée à trois lignes (`maxLines`, `TextOverflow.Ellipsis`) avec un `TextButton` « Lire la suite » qui la déplie et fait apparaître les faits en dessous — ou « *1895* s'écrit… ça prend une minute ou deux » (indicateur circulaire) à la première visite, relu toutes les cinq secondes, **trente-six fois au plus** (trois minutes, `GET /me/voyage/annees/{annee}`), puis « Le chroniqueur n'a pas répondu, reviens plus tard. » ; la relecture repart à chaque entrée sur la page. Sur une année **verrouillée**, il laisse place au carton **« Prochainement »** façon bande-annonce, sans affiche (le back ne sert plus d'aperçu depuis que les essentiels ont disparu) : un cadre de 2 dp dans l'accent du monde, « PROCHAINEMENT » en `titleLarge` espacé, et « *N* film(s) déjà vu(s), en avance » ou « Cette année n'est pas encore ouverte » selon sa profondeur. Puis, une fois la chronique prête, **le podium** (brief du 21 septembre 2026, « le podium ») : trois photogrammes posés sur un bout de pellicule droit (`bandeDePellicule`, jumeau horizontal de la pellicule de la carte), le n°1 (84×126 dp) au centre, plus grand, le 2 (64×96 dp) à gauche, le 3 à droite, chacun bordé d'une couleur du chapitre (`Monde.couleurPodium` : l'accent du monde pour le 1, le papier jauni du cartouche pour le 2, le sépia des affiches pas vues pour le 3) et sous-titré son numéro en petit ; une marche vide est un cadre nu sur fond noir, sans texte ; occupée, elle montre l'affiche et le titre sur une ligne. Toucher une marche ouvre la feuille **« Marche *N* »** : « Retirer du podium » en tête si elle est occupée, puis mes films vus de l'année et mes programmes entièrement vus en tuiles d'affiche avec pastille de note, l'occupant actuel coché (✓ corail) ; un appui long sur une marche occupée la vide directement, sans feuille. Puis **les salles** : un bloc par salle — titre (`titleMedium`) et raison d'être (`bodySmall onSurfaceVariant`) — puis une étagère horizontale (`LazyRow`) d'affiches 72×108 dp : les films **vus** en couleur avec leur pastille de note, les autres **en sépia** (une affiche désaturée, `ColorFilter`, voilée d'un `#3A2C1E` translucide), une petite étiquette d'état sous chacune (« sur ton Plex », « demandé », « introuvable » ; rien pour « à demander », rien pour « vu » — la pastille suffit), un programme portant en coin « *N* bobines · *M* min ». En bout d'étagère, une tuile-bouton **« En voir plus »** (cadre pointillé corail) qui enfile une fournée (trois à cinq films de plus), ou **« Salle épuisée »** (grisée, inerte) si le chroniqueur n'en a plus, ou un indicateur **« La salle se remplit… »** tant que la fournée s'écrit. Toucher une affiche ouvre `Screen.FicheVoyage`. En bas de page, sur l'année en cours seulement (décision 4 du brief du 21 septembre 2026, « le ticket », à la place de l'ancien bouton provisoire « Année suivante ») : si un ticket vers l'année suivante existe et n'est pas utilisé, « Ton ticket pour *1896* t'attend » (`bodyMedium`) avec un `TextButton` « Utiliser » qui l'encaisse ; sinon, si le dernier jugement de maturité est négatif, une seule ligne discrète « Pas encore mûre : *motif* » (`bodySmall onSurfaceVariant`), sans bouton ; sinon rien. Rien de tout ça sur la carte. **La chronique et les salles** (décision 1 et 3 du brief du 21 septembre 2026, « la chronique et les salles ») : dépliée, l'ouverture est suivie des paragraphes ajoutés à la demande, dans l'ordre — la date (`labelSmall onSurfaceVariant`), le titre (`titleSmall`, accent du monde), le texte (`bodyMedium`), « à propos de *Titre* » (`bodySmall`), un filet fin (`HorizontalDivider`) entre deux paragraphes. Sous la dernière étagère, un `OutlinedButton` **« Ouvrir une nouvelle salle »** ouvre une `ModalBottomSheet` « Quelle salle ? » (`OutlinedTextField` d'une ligne, 200 caractères, « une phrase : la comédie italienne cette année-là », bouton « Demander ») ; pendant l'écriture, une étagère fantôme le remplace : « La salle s'ouvre… » en italique et trois cadres vides pointillés, relue toutes les cinq secondes, trente-six fois au plus ; une fois créée, la nouvelle étagère apparaît et le bouton revient ; un refus affiche son motif sous le bouton (`bodySmall onSurfaceVariant`), marqué vu dès son premier affichage et absent ensuite. Absent sur une année verrouillée ou en préparation. **La séance** (brief du 21 septembre 2026, « la séance »), entre le podium et les salles, sur l'année en cours seulement : `OutlinedButton` **« Composer une séance »** tant qu'il n'y a rien, ou une carte d'attente **« Le chroniqueur compose… »** (indicateur circulaire) pendant la composition, relue toutes les cinq secondes, trente-six fois au plus ; puis **la carte de soirée**, sobre — fond `surfaceContainer`, liseré dans l'accent du monde, jamais le papier jauni du cartouche — pour la séance la plus récente : « CE SOIR » en `labelSmall` espacé dans l'accent du monde, le long (affiche 56×84, titre `titleMedium`, sa salle et son état en `bodySmall`), le court en dessous en plus petit (affiche 40×60, « en ouverture », le titre de sa bobine s'il y en a une), puis « Pendant le générique » et l'anecdote en italique. Sur une séance `proposee` : quatre `TextButton` en deux rangées (« Prendre » · « Ignorer », puis « Autre long » · « Autre court » — constaté sur téléphone, 21 septembre 2026 : les quatre sur une seule ligne ne tenaient pas en largeur), chaque libellé sur une seule ligne (`maxLines = 1, softWrap = false`, jamais la police réduite) ; « Autre long »/« Autre court » ouvrent une `ModalBottomSheet` de candidats pris dans les salles déjà chargées (aucun appel), groupés par salle, Plex d'abord puis demandé puis à demander, une bobine en retrait sous son programme, l'occupant actuel coché — « Autre court » liste aussi, après les programmes et leurs bobines, les films sans programme non vus ni introuvables (décision du propriétaire du 21 septembre 2026 : « avant les longs métrages, tout est court »), sauf le long actuel de la séance. Sur une séance `prise` : l'étiquette « Prise », puis sur chaque film « Voir sur le Plex » ou « Demander sur Sir », et « Je l'ai vu » vers le formulaire pré-rempli — elle reste affichée telle quelle tant que son long n'est pas vu ; une fois vu, elle est **terminée** (corrigé le 21 septembre 2026 : le propriétaire s'était trompé en disant l'inverse), exactement comme une ignorée. Une séance `ignoree`, ou `prise` avec un long vu, ne s'affiche pas en carte : ni l'une ni l'autre n'est terminale au sens où elle bloquerait — le bouton « Composer une séance » revient aussitôt, comme s'il n'y avait pas de séance. Les séances précédentes, prises ou ignorées, sous « Séances passées » (titre du long · date, un tap ne fait rien, « · vue » en plus pour une prise dont le long est vu). **Le carnet** (décision 2 du brief du 22 septembre 2026, « le carnet »), sous la ligne du ticket : dès que la fiche a une ouverture (`OutlinedButton` « Faire le carnet », ou « Refaire le carnet » si `carnet` n'est pas nul), en dessous « Fabriqué le 21 septembre 2026 · 12 pages » (`bodySmall onSurfaceVariant`) une fois qu'il existe — un tap l'ouvre (décision 4, ci-dessous). Pendant sa fabrication (`carnet_en_cours`), le bouton se désactive et dit « Le carnet se fabrique… », relu toutes les cinq secondes, même plafond que la chronique et les salles. |
| **Fiche d'un film du Voyage** (`Screen.FicheVoyage`, nouvel écran, 21 septembre 2026) | Empilée depuis une affiche d'`AnneeScreen`, sans barre du bas, dans la palette du monde de l'année. De haut en bas : l'affiche (96×144, comme en tête du formulaire), le titre et le titre original s'il diffère, le réalisateur, la durée totale s'il s'agit d'un programme ; un cartouche papier jauni met la salle et la raison du film en évidence ; ma note (`titleMedium`) et mes réactions (emojis, `Reactions.emoji`) si je l'ai déjà vu, lues dans le journal déjà chargé par `FriseViewModel` ; le carton **« Et pendant ce temps… »** existant (`CartonCard`, `attente = false`, silencieux tant qu'il n'est pas prêt) ; pour un programme, la liste de ses bobines (affiche 40×60, titre, durée, état), chacune touchant le formulaire pré-rempli ; puis, selon l'état, jusqu'à cinq boutons : **« Voir sur le Plex »** (visible dès qu'un lien existe, quel que soit l'état — `plex_url` commence par `plex://` ou par `https://`, ouvert tel quel par un `Intent.ACTION_VIEW`, absent si nul), **« Je l'ai vu »** (disparaît une fois le film vu, seul cas où il n'a plus lieu d'être), **« Demander sur Sir »** (seulement sur « à demander », jamais sur « demandé »), **« Introuvable »** et son inverse **« Le remettre à voir »** (mutuellement exclusifs, ni l'un ni l'autre sur un film vu), **« Mettre sur le podium »** (brief du 21 septembre 2026, seulement sur un film ou un programme vu) qui ouvre la feuille **« Mettre sur le podium »** : trois lignes « Marche *N* · *occupant actuel* » ou « Marche *N* · libre », la marche qui porte déjà ce film ou ce programme cochée (✓ corail). **« Ajouter à la chronique »** (décision 1 du brief du 21 septembre 2026, « la chronique et les salles », étape 4 de la spec du 19 septembre 2026) ferme la liste, sur un film ou un programme vu seulement : « Ajouter » devient « Le chroniqueur écrit… » après le tap, puis « Dans la chronique », inerte, une fois `paragraphes` porte ce film — le paragraphe s'affiche alors dessous (`bodyMedium`). |
| **Suivis** (brief du 15 septembre 2026 ; sagas ajoutées le même jour, l'écran se nommant jusque-là « Réalisateurs » ; toucher un réalisateur ouvre sa page depuis le brief du 21 septembre 2026, « la page réalisateur », décision 4) | Cinquième entrée de la `NavigationBar` du bas, entre « Frise » et « Au ciné » — icône `Movie` inchangée, `contentDescription` « Suivis » ; sélectionnée sur cet écran. En tête, deux segments (`SingleChoiceSegmentedButtonRow`) « Réalisateurs » et « Sagas », mémorisés pour la session : un seul écran, une seule `LazyColumn`, paramétrée par le segment affiché — jamais deux écrans. Une ligne : `Portrait` (photo ou affiche ronde 40 dp, ou l'initiale sur `surfaceContainerHigh` quand TMDB n'en a pas), le nom (`titleMedium`) puis, dessous, le résumé de ses films (`bodyMedium onSurfaceVariant`) — « *N* vus sur *M* · prochain : *Titre* (*année*) », avec « · *K* introuvables » avant le prochain s'il y en a (brief du 15 septembre 2026), ou « … » tant que `GET .../films` de cette entité n'a pas répondu, ou « indisponible » s'il a échoué, sans bloquer les autres lignes. En haut à droite, un `IconButton` « + » (`contentDescription` « Ajouter un réalisateur » ou « Ajouter une saga », selon le segment) ouvre `Screen.ChercherSuivi`. Toucher une ligne d'une saga ouvre sa fiche existante ; toucher une ligne d'un réalisateur ouvre désormais `Screen.Realisateur`, sa page (ci-dessous), et non plus cette fiche. Vide : « Ajoute un réalisateur avec + » ou « Ajoute une saga avec + », même style que les autres écrans vides. |
| **Chercher un réalisateur ou une saga** (brief du 15 septembre 2026) | Jumeau de l'écran « Recherche » ci-dessous : barre de retour et `TextField` sans bordure sur `surfaceContainer`, focus et clavier ouverts à l'arrivée, croix d'effacement quand il y a du texte, placeholder « Un nom de réalisateur » ou « Un nom de saga » selon le segment d'où l'écran s'est ouvert. La requête part 400 ms après la dernière frappe (300 ms pour la recherche de films). Les lignes portent un `Portrait` (comme ci-dessus) plutôt qu'une affiche, avec le nom seul. Toucher une ligne suit l'entité puis referme l'écran ; « Ajouté » apparaît ensuite en snackbar sur la liste, derrière. |
| **Fiche d'une saga suivie** (brief du 15 septembre 2026, puis marque « introuvable », généralisation aux sagas, puis films de saga ajoutés à la main, le même jour ; réservée aux sagas depuis le brief du 21 septembre 2026, « la page réalisateur », décision 4 — un réalisateur a désormais sa propre page) | Empilée depuis « Suivis », sans barre du bas : une barre de titre avec retour, le nom de la saga, et un `IconButton` poubelle à droite (`contentDescription` « Ne plus suivre ») qui ouvre un `AlertDialog` à deux boutons avant de retirer l'entité et revenir à la liste. Sous la barre, un `TextButton` « Ajouter un film » (icône `Add`) : une collection TMDB n'est pas toujours complète — « Alien - Saga » n'a que les quatre films originaux — il ouvre `Screen.ChoisirFilmDeSaga`, la recherche de films existante en mode « choisir », sans passer par le formulaire ; toucher un résultat ajoute le film et revient à la fiche rechargée, avec « Ajouté à la saga » en snackbar, ou « Impossible pour l'instant » sur un échec. Puis l'interrupteur « Masquer les introuvables » (`Switch`, activé par défaut, mémorisé pour la session). Puis les films en `LazyColumn`, de la plus ancienne sortie à la plus récente : l'année (`bodyMedium onSurfaceVariant`), l'affiche 30×45, le titre (`bodyLarge`), suivi d'une petite mention « ajouté » (`labelSmall onSurfaceVariant`) quand le film y a été ajouté à la main, puis à droite soit la note (`titleMedium`) si le film est vu, soit « introuvable » (`labelMedium onSurfaceVariant`) s'il est marqué, soit une pastille corail « à voir » (`labelMedium onPrimary`, coins entièrement ronds) sur le premier film ni vu ni introuvable, lui seul — « ajouté » et l'une de ces trois mentions cohabitent, un film ajouté pouvant tout autant être vu, introuvable ou à voir. Un film marqué introuvable est grisé (opacité 50 %) quand l'interrupteur est désactivé, absent de la liste quand il est activé. Toucher un film vu ouvre sa correction (`Screen.Edit`, la même entrée que le journal complet) ; toucher le prochain film non vu ouvre le formulaire pré-rempli. Un appui long sur un film non vu ouvre un `ModalBottomSheet` — « Marquer introuvable » et « Annuler » s'il ne l'est pas encore, « Le remettre à voir » s'il l'est déjà ; sur un film ajouté à la main (même déjà vu), le même `ModalBottomSheet` porte aussi « Retirer de la saga » ; l'appel au back fait, la fiche se recharge, un échec affiche « Impossible pour l'instant » en snackbar sans rien changer d'autre. |
| **Réalisateur** (`Screen.Realisateur`, brief du 21 septembre 2026, « la page réalisateur » ; reprise le même jour, « la page réalisateur, reprise », l'étagère horizontale jugée illisible) | Une seule page, suivi ou non, nourrie par `GET /me/realisateurs/{tmdbId}/page` — ouverte depuis « Suivis » ou depuis n'importe quel nom de réalisateur touchable (ci-dessous). Sans barre du bas, un `IconButton` retour en tête ; le `Scaffold` prend le fond du monde du Voyage (§2, phase 2) de l'année du **premier film daté** de la filmographie, sinon 1895. Un seul défilement, `LazyVerticalGrid` (`GridCells.Fixed(3)`, gouttières 12 dp, marge 16 dp) : l'en-tête en est le tout premier item, en pleine largeur — centrés, la photo (`Portrait`, ronde, 96 dp), le nom (`titleLarge` espacé de 4 sp en gras, dans l'accent de ce monde, comme un titre de décennie), les dates s'il y en a (« 1861 – 1938 » inchangé, ou « né en 1958 »/« née en 1958 »/« naissance en 1958 » selon `genre`), la présentation, le bouton **« Suivre »**/**« Suivi »** (mêmes appels et même message que sur « Suivis »), puis un résumé (`bodySmall onSurfaceVariant`) « 42 films · 9 vus · 3 sur le Plex », accordé au singulier, « 0 vu »/« 0 sur le Plex » écrits quand même, compté sur toute la filmographie, introuvables compris. Puis l'interrupteur « Masquer les introuvables » (`Switch`, activé par défaut, mémorisé pour la session — le même que sur la fiche d'une saga ; retouche du 22 septembre 2026) : activé, les films que j'ai marqués introuvables sont absents de la grille, et une décennie qui n'avait qu'eux disparaît avec ; désactivé, ils reprennent leur place. Puis la filmographie, groupée par décennie dans l'ordre où le back la rend, un en-tête par groupe (« Années 1980 », `titleMedium` dans l'accent du monde de **cette** décennie) : d'abord les longs (`court: false` et `type: "movie"`), trois affiches par ligne ; puis, s'il y a des courts ou des séries, une ligne « 6 courts · 1 série » (`labelMedium`, chevron, tap replie/déplie, état mémorisé par décennie) suivie des courts et séries quatre par ligne, plus petits, **dépliés d'emblée** (retouche du 22 septembre 2026, le repli par défaut ayant été jugé une gêne) — une décennie sans aucun long n'a pas de ligne du tout (Lumière, 1895–1905). Chaque affiche (vue en couleur avec sa pastille de note, sépia sinon, coin « Plex », coin « TV » sur une série, liseré de 1,5 dp dans l'accent quand l'année est ouverte dans le Voyage) porte sous elle son titre (`bodyMedium`, deux lignes au plus) puis son année (`labelSmall onSurfaceVariant`) — lisibles même sans jaquette, `Cover` gardant alors son rectangle sépia. Toucher une affiche ouvre `Screen.FicheVoyage` si le film y a une ligne, sinon la fiche simple ci-dessous. Un appui long sur un non-vu ouvre la feuille « Marquer introuvable »/son inverse, comme sur la fiche d'une saga. |
| **Fiche simple d'un film** (`Screen.FicheFilm`, décision 2 du brief du 21 septembre 2026, « la page réalisateur ») | Ouverte au tap sur une affiche de la filmographie d'un réalisateur dont le film n'a pas de ligne dans le Voyage — nourrie par cette ligne de filmographie, rien d'autre à charger. En tête, retour, puis l'affiche (96×144, comme la fiche du Voyage), le titre et le titre original s'il diffère, l'année, le nom du réalisateur (touchable, ci-dessous), mon état (« Vu ») et ma note si je l'ai vu. Puis, selon l'état : **« Je l'ai vu »** (→ le formulaire pré-rempli, comme un à-voir du Plex), **« Voir sur le Plex »** (lien `plex://` puis web), **« Demander sur Sir »** (→ « demandé »), **« Introuvable »** et son inverse. Une **série** (`type: "tv"`) n'a ni « Je l'ai vu » ni formulaire : elle garde seulement Plex et Sir, avec une ligne « Les séries se suivent dans Suivis ». |
| **Le nom d'un réalisateur, touchable** (décision 3 du brief du 21 septembre 2026, « la page réalisateur ») | Partout où il s'affiche — la fiche d'un film du Voyage, l'écran de correction du journal (le réalisateur des métadonnées), la carte de soirée de la Frise (sous le titre du long et du court), la fiche simple ci-dessus, la liste de Suivis. Le tap résout `GET /reference/films/{tmdbId}/realisateurs` avec le `tmdb_id` **du film** (`RealisateurResolveur`, un cache en mémoire partagé par tous ces écrans) : un seul réalisateur mène directement à sa page ; plusieurs ouvrent une `ModalBottomSheet`, un nom par ligne ; aucun affiche un bandeau, « TMDB ne connaît pas le réalisateur de ce film ». |
| **Recherche** | Une barre en haut : `IconButton` retour, puis un `TextField` sans bordure sur `surfaceContainer`, pleine largeur, focus et clavier ouverts à l'arrivée, croix d'effacement quand il y a du texte. Dessous, une `LazyColumn` de lignes : affiche, titre, réalisateur et année. Pendant une requête, un `LinearProgressIndicator` de 2 dp en corail, juste sous la barre. |
| **Formulaire** | En tête, l'affiche et le titre en `titleLarge`. La date : un champ tactile qui affiche « 3 septembre 2026 » et ouvre un `DatePickerDialog` limité à aujourd'hui inclus. La note : deux rangées de cinq pastilles. Les réactions : des `FilterChip` dans un `FlowRow`, emoji puis phrase, qui passent à la ligne. Le commentaire : `OutlinedTextField`, trois lignes au minimum, qui grandit, avec « Rien qu'à toi » en `supportingText`. Le bouton « Enregistrer » plein, corail, pleine largeur, collé en bas de l'écran au-dessus du clavier. En correction, il dit « Corriger », et un `TextButton` « Supprimer » en `onSurfaceVariant` se tient sous lui ; il ouvre un `AlertDialog` à deux boutons, « Annuler » et « Supprimer ». **Le carton du Voyage** (brief du 16 septembre 2026) : après une création réussie, sous le bandeau « Enregistré » de l'accueil, une carte « Et pendant ce temps… » — même cartouche kitsch que celui d'`AnneeScreen` — relit `GET /reference/chroniques/films/{tmdbId}` toutes les trois secondes, dix fois au plus (« Le chroniqueur arrive… » puis, après les dix essais, « Il sera dans la fiche du film ») ; fermable d'un geste (croix). La même carte s'affiche en bas de `Screen.Edit`, mais silencieuse tant que le carton n'existe pas encore — jamais de sondage ni de « chroniqueur arrive » sur un écran de correction. Le geste local (enregistrer, corriger) n'attend jamais cette carte. **« Ajouter à la chronique »** (décision 1 du brief du 21 septembre 2026, « la chronique et les salles »), sous elle, sur `Screen.Edit` seulement : visible seulement si l'année de sortie du film est en cours ou ouverte dans `/me/voyage` déjà chargé par la Frise (absente sinon, y compris sans ce chargement) — mêmes trois états que sur la fiche du Voyage (« Ajouter », « Le chroniqueur écrit… », « Dans la chronique » avec le paragraphe dessous), un `ChroniqueViewModel` dédié, plus léger que l'`AnneeViewModel` de la fiche — il lit `GET /me/voyage/annees/{annee}` une fois à l'ouverture (décision du 21 septembre 2026) : le bouton part directement sur « Dans la chronique » si le film y a déjà son paragraphe, jamais sur un sondage ni un appel IA. |
| **Profil** | Le pseudo en `titleMedium`. Puis la phrase en une ligne, où seuls les deux nombres sont en `displaySmall onSurface` et le reste en `bodyLarge onSurfaceVariant` : **87** films vus, **12** cette année (`GET /stats`). Sous elle, la carte « Bilan » (brief du 15 septembre 2026) : un bloc `surfaceContainer` à coins 12 dp, marge 16 dp, jumeau d'`ErrorBlock` — un titre `titleMedium`, puis sept lignes `bodyLarge onSurfaceVariant`, calculées **dans l'appli** depuis le journal complet, les réactions et les deux sources suivies (jamais `GET /stats`) : films vus dont cette année, séances en salle dont cette année, note moyenne sur 10, décennies couvertes (« 1920 → 2020, 9 décennies sur 11 »), le plus ancien film vu, réalisateurs suivis et combien terminés, sagas idem. Chaque ligne dit « … » tant que sa donnée n'est pas là — trois sources indépendantes (le journal, les réalisateurs, les sagas), pas de chargement bloquant. Puis un `ListItem` « Mes films » avec chevron à droite, un `ListItem` « SensCritique » (chevron aussi), sous-titré « Non connecté » ou « Connecté : TheofB » (brief du 14 septembre 2026), puis un `ListItem` « Importer Letterboxd » (brief du 16 septembre 2026), sous-titré « Le fichier d'export de Letterboxd, ZIP ou diary.csv », qui ouvre le sélecteur de fichiers système. Un `TextButton` « Se déconnecter » en `onSurfaceVariant`. En bas, la mention TMDB (§9). La `NavigationBar` du bas (brief du 14 septembre 2026) est visible ici aussi, « Profil » sélectionnée. |
| **Générique de fin** (brief du 16 septembre 2026, phase 2, une ligne de récompenses le 21 septembre 2026, étape 5) | Plein écran, sans barre du bas, dans la palette du monde : empilé au moment où une décennie se boucle, ou rejoué depuis un tampon du passeport. Il défile tout seul de bas en haut (`animateScrollToItem` sur la dernière ligne, repris à la main dès qu'un doigt s'en mêle) : « ANNÉES 1890 » en `displaySmall` espacé dans l'accent du monde, « vues par *pseudo* », le nom du monde ; puis la liste des films du journal de ces années-là, année à gauche en accent, titre en `bodyLarge`, centrés ; puis, si la décennie a au moins une récompense, « 3 Palmes · 2 Lions · 5 Ours » (`phraseRecompenses` sur `tampon.recompenses`, une par année qui en a une) en `bodyMedium`, dans l'accent du monde ; puis « Du 11 février 2026 au 4 juin 2026 » (la première et la dernière entrée du journal), le **titre de voyageur** en lettres or `#E6B94A`, et « Fin » en `displaySmall`. Un `TextButton` « Fermer » en bas, et toucher n'importe où ferme aussi. Une décennie bouclée sans aucun film au journal le dit, plutôt que de montrer une liste vide — n'arrive plus en pratique depuis l'étape 5 (une décennie ne se boucle qu'avec un Ours, donc au moins un film, par année). |
| **Passeport** (brief du 16 septembre 2026, phase 2 ; charge ses propres données depuis le 21 septembre 2026, étape 5, « les récompenses ») | Dans le profil, **sous la carte « Bilan »**, dans un bloc `surfaceContainer` jumeau du sien : un titre « Passeport », puis une ligne par décennie bouclée — un tampon (cercle corail de 52 dp, double anneau, penché de −8°, les trois derniers chiffres de la décennie au centre), « Années 1890 · 4 juin 2026 » en `bodyLarge` et le titre de voyageur en `bodyMedium` dans l'accent du monde. `PasseportViewModel` charge lui-même `GET /me/voyage` (jamais depuis la Frise) : la carte n'est donc plus jamais vide quand Profil s'ouvre en premier ; ses tampons viennent de `tampons`, une ligne par décennie que le back dit bouclée. Toucher une ligne construit le tampon complet du générique (ses films, ses deux dates) depuis le journal déjà chargé par la Frise, ou le charge lui-même s'il manque, puis rejoue son générique. « Aucun tampon encore » tant qu'aucune décennie n'est bouclée — la carte se voit avant d'être remplie. |
| **Portefeuille** (décision 3 du brief du 21 septembre 2026, « le ticket ») | Dans le profil, **sous le Passeport**, bloc `surfaceContainer` jumeau ; charge lui-même `GET /me/voyage/tickets` (`PortefeuilleViewModel`), jamais depuis la Frise. Les tickets non utilisés en premier (le même dessin qu'au calque, en petit), avec un `TextButton` « Utiliser » (même appel, puis `frise.refresh()`) ; les compostés en dessous, grisés (`alpha` 0,5) et barrés (`barrerTicket`), « utilisé le 4 juin 2026 » (`bodyMedium onSurfaceVariant`, barré) à la place du bouton. « Aucun ticket » tant que la réponse, une fois là, est vide — pas avant qu'elle ait répondu. |
| **Dépenses** (décision 2 du brief du 21 septembre 2026, « les dépenses ») | Dans le profil, **sous le Portefeuille**, bloc `surfaceContainer` jumeau ; charge lui-même `GET /me/voyage/depenses` (`DepensesViewModel`), aucun appel IA. Une ligne « Ce mois-ci : 12,7 centimes · 10 appels » (`ligneMoisCourant`, virgule et décimale uniques, pluriel « appel »/« appels » ; « 0 centime · 0 appel » si le mois manque) ; un tap la déplie sur les mois précédents (`triMoisPrecedents`, du plus récent au plus ancien, mois courant excepté), une ligne chacun « Août 2026 : 4,2 centimes · 3 appels » (`ligneMoisPrecedent`, `formatMoisAnnee`) ; un second tap replie. |
| **Carnets** (brief du 22 septembre 2026, « le carnet ») | Dans le profil, **sous les Dépenses**, bloc `surfaceContainer` jumeau ; charge lui-même `GET /me/voyage/carnets` (`CarnetsViewModel`), jamais depuis la Frise. Une ligne par carnet déjà fabriqué, « 1895 · 12 pages · 21 septembre 2026 » (`bodyLarge onSurfaceVariant`), un tap ouvre son PDF (décision 4, ci-dessous) ; les années dont la fabrication tourne encore, en ligne grisée (`alpha` 0,5) et inerte, « 1896 · en fabrication » ; « Aucun carnet pour l'instant » une fois la réponse là, vide. **Ouvrir un carnet** (décision 4) : le client ktor de l'appli le télécharge (son cookie de session déjà posé) dans `cacheDir/carnets/carnet-1895.pdf`, puis `Intent.ACTION_VIEW` sur une URI `FileProvider` (`androidx.core.content.FileProvider`, autorité `${applicationId}.fileprovider`, chemins limités à `cache/carnets`) avec `FLAG_GRANT_READ_URI_PERMISSION` — aucune visionneuse dans l'appli. Aucune application capable : « Aucune application ne sait ouvrir un PDF. » ; un téléchargement en échec : le message du back, ou « Le carnet n'a pas pu être téléchargé. ». |
| **Import Letterboxd** (brief du 16 septembre 2026) | Empilée depuis le profil dès qu'un fichier est choisi, sans barre du bas : une barre de titre avec retour et « Import Letterboxd ». Tant que la requête est en vol : un indicateur circulaire corail centré, avec « Import en cours… » dessous (`bodyLarge onSurfaceVariant`) — jusqu'à quelques minutes pour un gros fichier ; le retour système dépile l'écran sans annuler la requête, qui continue derrière. Puis le rapport : « *N* importés · *M* déjà présents » (`titleMedium`), puis, s'il y en a, « Non reconnus » et une ligne par film non importé — le nom et l'année, « Aucun candidat » s'il n'y en a pas, sinon un `ListItem` par candidat (titre et année) ; toucher l'un d'eux ouvre le formulaire, pré-rempli avec la date et la note que le back a lues sur cette ligne du fichier. Puis, s'il y en a, « Erreurs » et une ligne par ligne en échec (« Ligne *N* : *message* »). Un `Button` plein corail « Terminé », pleine largeur, ramène au profil, qui recharge ses chiffres (même geste qu'une entrée ordinaire sur cet écran). Une panne (ZIP illisible, en-têtes fausses, réseau) rend le bloc d'erreur habituel (§6) à la place du rapport. |
| **Mes films** | Une `LazyColumn` de lignes : affiche, titre, date, emojis des réactions, note à droite. Un `CircularProgressIndicator` corail en fin de liste tant qu'un `next_cursor` reste à charger. La `NavigationBar` du bas (brief du 14 septembre 2026) est visible ici aussi ; c'est « Profil » qui y est sélectionnée, puisque cette liste ne s'ouvre que depuis le profil. |
| **Au ciné** (brief du 14 puis 15 septembre 2026) | Troisième entrée de la `NavigationBar` du bas, entre « Accueil » et « Profil » — icône `ConfirmationNumber` (un ticket ; `material-icons-extended`, le cœur n'en porte pas), `contentDescription` « Au ciné » ; sélectionnée sur cet écran. En tête, « **N** séance(s) cette année » en `titleLarge`. Puis « Sorti cette semaine dans mes cinémas » (`titleMedium`, brief du 15 septembre 2026 — ex-« Cette semaine ») : sous elle, en petit (`labelSmall`, `onSurfaceVariant`), « mis à jour à *H* h » quand la tâche de fond du back a déjà tourné. La grille, trois colonnes, mêmes tuiles que l'accueil, avec sous chacune une ligne `labelSmall` (11 sp) donnant le premier cinéma où le film joue et « +N » s'il y en a d'autres ; une coche `primary` (corail) en bas à droite de celles déjà dans le journal (rapprochement par `tmdb_id`, jamais par le titre). Une tuile sans `tmdb_id` (la résolution TMDB du back n'a rien trouvé de sûr) n'est ni touchable ni cochable. Toucher une affiche ouvre le formulaire pré-rempli, comme depuis la recherche. Puis « La semaine prochaine » (`titleMedium`, inchangée) : même grille, TMDB, sans le sous-titre de cinémas. Puis « Tes séances » (`titleMedium`) : la liste `?reaction=en_salle` de « Mes films », **même ligne** qu'elle (`JournalRow`, `ui/JournalRow.kt`) — affiche, titre, date, emojis des réactions, note à droite ; toucher une ligne ouvre la correction. |
| **Connexion SensCritique** (brief du 14 septembre 2026) | Jumeau de l'écran « Connexion » : deux `OutlinedTextField` (e-mail, mot de passe avec bascule de visibilité), un `Button` plein corail « Connecter », le message du back — ici celui de SensCritique — sous le bouton. Connecté : le pseudo en `bodyLarge`, et un `TextButton` « Déconnecter ». |
| **Feuille « Lequel sur SensCritique ? »** (brief du 14 septembre 2026) | Un `ModalBottomSheet` : une ligne par candidat (affiche 56×84, titre, année et réalisateur — SensCritique le rend, `directors[0].name`), puis un `TextButton` « Aucun de ceux-là » en bas, la liste bornée pour que ce bouton reste toujours visible même avec une dizaine de candidats. Le retour système la referme comme toute autre feuille (Material 3 ne s'y oppose pas) : à la différence de « Aucun de ceux-là », qui ne met rien en file et mémorise « ignoré », ce geste met la poussée en file sans décision — la correction suivante du film redemandera (revue du 14 septembre 2026, critique 2). |

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
| **Vide**, « Au ciné » (brief du 14 puis 15 septembre 2026) | « Tes séances » sans entrée : « Aucune séance pour l'instant. », même style que l'accueil. « La semaine prochaine » sans film cette semaine-là : « Rien cette semaine. », même style, à la place de la grille. « Sorti cette semaine dans mes cinémas » : « Pas encore de programme. » tant qu'aucun cinéma n'est configuré ou que la tâche de fond du back n'a jamais tourné ; « Rien à l'affiche aujourd'hui. » si elle a tourné et n'a simplement rien trouvé ce jour-là — deux messages distincts, même style. |
| **Erreur**, toutes | Le `message` du back tel quel, `bodyLarge onSurface`, dans un bloc `surfaceContainer` à coins 12 dp, marge 16 dp. Si `retryable`, un `TextButton` « Réessayer » corail, aligné à droite dans le bloc. Panne réseau : le même bloc avec « L'API est injoignable. » |
| **Erreur**, second appel du geste | Une ligne de contexte « Le film est ajouté, mais pas ton visionnage. » au-dessus du bloc d'erreur habituel, lequel garde le vrai message du back (`error.message`) et son « Réessayer » si `retryable` — qui ne relance que `POST /me/journal` (`FormViewModel.ViewingFailedAfterMediaAdded`, `FormScreen` ; précisé le 16 septembre 2026, la ligne de contexte s'ajoutant au message du back plutôt que de le remplacer). |
| **Succès** | Un `Snackbar` « Enregistré », fond `surfaceContainerHigh`, texte `onSurface`, deux secondes, sans action. Pas de flash clair : la version inversée de Material n'est pas utilisée. |
| **Désactivé** | Opacité 38 %, valeur de Material, rien de spécifique. |
| **Succès, poussé sur SensCritique** (brief du 14 septembre 2026) | Le même `Snackbar`, avec « · SensCritique ✓ » à la suite : « Enregistré · SensCritique ✓ » ou « Corrigé · SensCritique ✓ ». |
| **Poussée SensCritique échouée** | « Enregistré · SensCritique : réessai au prochain lancement » — le geste local, lui, a réussi ; rien ne change à son `Snackbar` habituel à part cette fin. |
| **Jeton SensCritique refusé** | « Enregistré · SensCritique : reconnecte-toi » ; l'écran Profil affiche « Non connecté » au prochain passage. |
| **Le chroniqueur écrit** — carton de film | Un indicateur circulaire de 16 dp et « Le chroniqueur arrive… », dans le cartouche kitsch, tant que `statut` reste `en_preparation`. Relu toutes les trois secondes, dix fois au plus ; au-delà, abandon silencieux — « Il sera dans la fiche du film ». |
| **Le chroniqueur écrit** (ou s'écrit) — une année, sur `AnneeScreen` (réécrit le 21 septembre 2026, « l'année en étages ») | À la première visite (`202`), le cartouche dit « *1895* s'écrit… ça prend une minute ou deux » avec l'indicateur circulaire, sans aucune salle dessinée. Relue toutes les cinq secondes, **trente-six fois au plus** (trois minutes, `GET /me/voyage/annees/{annee}`) ; la relecture repart à chaque entrée sur la page, un abandon n'étant plus définitif. Au-delà : « Le chroniqueur n'a pas répondu, reviens plus tard. » |
| **Année verrouillée** (revu le 21 septembre 2026) | Le carton « Prochainement », sans affiche (le back ne sert plus d'aperçu, les essentiels ayant disparu) : « *N* film(s) déjà vu(s), en avance » si la profondeur de l'année est positive, « Cette année n'est pas encore ouverte » sinon. |
| **Salle qui se remplit** (« En voir plus », 21 septembre 2026) | La tuile en bout d'étagère devient un indicateur circulaire de 16 dp et « La salle se remplit… », relue jusqu'à ce que `fournee_en_cours` retombe (nouveaux films, ou « Salle épuisée ») ; au plafond, abandon silencieux, la tuile retombant à « En voir plus » sans nouveau film. |
| **Salle épuisée** (21 septembre 2026) | La tuile en bout d'étagère, grisée et inerte : « Salle épuisée ». Le chroniqueur n'a plus de film qui mérite d'y entrer ; un tap n'enfile plus rien (`200`, pas `202`). |
| **Le chroniqueur écrit un paragraphe** — « Ajouter à la chronique » (décision 1 du brief du 21 septembre 2026, « la chronique et les salles ») | Le bouton passe à « Le chroniqueur écrit… », inerte, dès le tap. Relu toutes les cinq secondes, trente-six fois au plus (même plafond que l'ouverture de l'année) ; dès que `paragraphes` porte le film, le bouton devient « Dans la chronique », inerte, et le paragraphe s'affiche dessous. Au-delà du plafond, abandon silencieux : le bouton redevient « Ajouter », un nouveau tap peut relancer. |
| **La salle s'ouvre** — « Ouvrir une nouvelle salle » (décision 3 du brief du 21 septembre 2026) | Le bouton laisse place à une étagère fantôme : « La salle s'ouvre… » en italique, trois cadres pointillés vides, sans affiche. Relue toutes les cinq secondes, trente-six fois au plus. `creee` : la nouvelle étagère remplace le fantôme, le bouton revient. `refusee` : le fantôme laisse place au bouton, avec le motif du back dessous (`bodySmall onSurfaceVariant`), marqué vu dès cet affichage — absent au prochain passage. `409` (demande déjà en cours) : bandeau avec le message du back, rien à l'écran ne change. |
| **Marche du podium** (brief du 21 septembre 2026, « le podium ») | Vide : un cadre noir nu, bordé dans la couleur du chapitre pour cette marche, sans texte ni icône — un tap ouvre quand même la feuille de choix. Occupée : l'affiche, le titre sur une ligne. Après un choix ou un retrait, la fiche relit l'année (le podium se met à jour) et `frise.refresh()` met la carte à jour en tâche de fond, sans indicateur de chargement propre à la marche. |
| **Le chroniqueur compose** — « Composer une séance » (brief du 21 septembre 2026, « la séance ») | Le bouton laisse place à une carte d'attente : un indicateur circulaire de 16 dp et « Le chroniqueur compose… ». Relue toutes les cinq secondes, trente-six fois au plus (même plafond que la chronique d'une année) ; une `409` (composition déjà en cours) ou une `400` (plus rien de neuf) laisse le bouton en place et affiche le message du back en bandeau, sans marquer la carte d'attente. |
| **Composition abandonnée sans rien produire** (décision du propriétaire du 21 septembre 2026, « une composition abandonnée le dit ») | Jamais muette : si `seance_en_cours` retombe à faux sans qu'aucune séance de plus soit apparue, un bandeau « Le chroniqueur n'a pas pu composer ce soir, réessaie. » ; à l'abandon au plafond des trente-six relectures (le back ne répond plus du tout), « Le chroniqueur n'a pas répondu, reviens plus tard. » — dans les deux cas, le bouton « Composer une séance » revient (`seanceEnCours` forcé à faux, `messageEchecComposition`). Rien de tout ça dès qu'une séance de plus est apparue : c'est le seul cas de succès. |
| **Séance proposée** (21 septembre 2026) | La carte de soirée avec ses quatre actions (Prendre, Autre long, Autre court, Ignorer). « Autre long »/« Autre court » ouvrent une feuille locale, sans appel ; choisir un candidat écrit `POST .../remplacer` puis relit l'année, la carte se met à jour tout de suite, sans indicateur propre à la feuille. |
| **Séance prise / ignorée** (21 septembre 2026 ; ignorer, puis prise-et-vue corrigés le même jour : aucun des deux n'est terminal au sens où il bloquerait) | Prise, long pas encore vu : la carte reste, étiquetée « Prise », avec les actions par film (Plex, Demander, Je l'ai vu) à la place des quatre boutons. Prise et long vu, ou ignorée : la carte disparaît, le bouton « Composer une séance » revient aussitôt (comme s'il n'y avait pas de séance) et elle rejoint « Séances passées » tout de suite — une prise-et-vue porte en plus « · vue ». Une prise dont le long n'est pas vu ne rejoint les passées que lorsqu'une composition plus récente la supplante. |
| **Décennie non bouclée** (brief du 16 septembre 2026, phase 2 ; revu le 21 septembre 2026, étape 5) | Sur sa marquise : ampoules éteintes (`outline`) et « en cours de tournage » à la place du titre de voyageur, tant que chacune de ses dix années n'a pas un Ours et que le ticket de la décennie suivante n'est pas utilisé (spec du 19 septembre 2026, §6). |
| **Aucun essentiel encore** (étape 5 du brief du 21 septembre 2026, « les récompenses ») | Sous la profondeur, dans l'en-tête d'`AnneeScreen` : `ligneProgression` rend cette phrase plutôt que « *N* sur *M* » quand `progression.essentiels_total` vaut 0 — une année sans essentiel connu n'est pas encore une année à zéro. |
| **Année en cours qui avance** (revu le 21 septembre 2026, puis à l'étape 5) | Le clap claque sur la carte et « 1898 dans la boîte ! » en snackbar, deux secondes comme les autres, sans suffixe de récompense — décision du propriétaire, rien de plus sur ce message. Une décennie qui se boucle allume sa marquise (1,5 s) puis rejoue le générique de fin, mais séparément : un tampon inédit (`detecterNouveauTampon`), pas l'avancée de l'année en cours — les deux peuvent tomber sur des chargements différents. |
| **Passeport vide** (brief du 16 septembre 2026, phase 2) | « Aucun tampon encore », même style que les lignes du Bilan. |
| **Ticket gagné** (décision 2 du brief du 21 septembre 2026, « le ticket ») | Le calque s'affiche par-dessus l'écran courant dès que `ticket_a_montrer` est non nul — après un enregistrement dont le film est de l'année en cours (relu toutes les cinq secondes, douze fois au plus, une minute), ou à chaque ouverture de la Frise. Se ferme et ne se remontre jamais dès qu'on touche l'un de ses deux boutons. |
| **Portefeuille vide** (décision 3 du brief du 21 septembre 2026, « le ticket ») | « Aucun ticket », une fois `GET /me/voyage/tickets` répondu et vide — pas avant, même style que le Passeport vide. |
| **Aucun réalisateur connu sur ce film** (décision 3 du brief du 21 septembre 2026, « la page réalisateur ») | Le tap sur un nom de réalisateur touchable dont `GET /reference/films/{tmdbId}/realisateurs` rend une liste vide : un bandeau discret (`labelSmall onSurfaceVariant`) « TMDB ne connaît pas le réalisateur de ce film », sous le nom, effacé de lui-même après deux secondes — rien d'autre ne change, le nom reste affiché tel quel. |
| **Le carnet se fabrique** (brief du 22 septembre 2026, « le carnet ») | Sur la fiche d'une année, le bouton passe à « Le carnet se fabrique… », désactivé, dès le tap (optimiste) ; relu toutes les cinq secondes, même plafond que la chronique et les salles (trente-six essais). Dès que `carnet_en_cours` retombe côté back, le bouton redevient « Faire le carnet »/« Refaire le carnet » et « Fabriqué le… » affiche la date et le compte de pages tout frais. |
| **Carnets vide** (brief du 22 septembre 2026) | Dans le profil, une fois `GET /me/voyage/carnets` répondu, sans aucun carnet ni aucune fabrication en cours : « Aucun carnet pour l'instant », même style que le Passeport et le Portefeuille vides. |
| **Connexion SensCritique refusée** (revue du 14 septembre 2026, après un premier essai réel : la connexion n'est pas Firebase, l'API GraphQL de SensCritique refusait l'`idToken`) | Sous le bouton « Connecter », dans le même bloc que les autres erreurs : « Identifiants refusés. » pour toute erreur GraphQL de la mutation de connexion, quel que soit son code (SensCritique ne rend pas de code catalogué comme le faisait Firebase — le code, lui, va au journal) ; pour toute panne (réseau, réponse illisible) : « SensCritique est injoignable. », avec « Réessayer ». |

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
app/src/main/assets/
  OFL.txt      la licence Manrope — un `.txt` n'est pas une ressource valide
               sous res/font/, qui ne porte que les quatre .ttf (corrigé le
               16 septembre 2026, l'ancienne description la donnait à tort
               comme rangée là)
app/src/main/res/
  font/        manrope_regular.ttf, _medium, _semibold, _bold
  values/themes.xml      windowBackground #000000, splash background #000000
  values-v31/themes.xml  windowSplashScreenAnimatedIcon, windowSplashScreenAnimationDuration
  mipmap-anydpi-v26/ic_launcher.xml   icône adaptative (fond + premier plan)
  drawable/ic_launcher_foreground.xml   le clap, statique, volet au repos (-30°, ouvert)
  drawable/ic_launcher_animated.xml     le même clap, animé (AnimatedVectorDrawable)
  animator/ic_launcher_volet_claque.xml la rotation du groupe « volet »
  interpolator/rebond_franc.xml         l'overshoot du claquement
```

**Le Voyage** (brief du 16 septembre 2026, phases 1 « le moteur » et 2 « la
carte »), sous `app/src/main/kotlin/fr/mediatheque/journal/ui/frise/` :

```
Mondes.kt             les quatorze mondes (§2), mondeDe, chapitreRomain, chapitreDe —
                       liste fixe, aucune donnée chargée ; couleurPodium (21 septembre
                       2026, « le podium ») : les trois couleurs d'une marche
VoyageEtats.kt        StatutAnneeVoyage (ouverte/en_cours/verrouillee), VoyageUi (porte
                       désormais ticketAMontrer, et tampons depuis l'étape 5), l'état
                       d'une chronique (EtatChronique)
                       et ses deux plafonds de relecture (10 pour un film, 36 pour une
                       année), l'état d'une fournée (EtatFournee) qui relit jusqu'à ce que
                       fournee_en_cours retombe ; le ticket (21 septembre 2026, « le
                       ticket ») : doitRelireApresCreation (seulement l'année en cours),
                       EtatRelectureTicket et son plafond de douze essais ; la chronique et
                       les salles (21 septembre 2026) : etatParagrapheSuivant (jumeau,
                       plafond de l'année), eligibleChroniqueDepuisEdition (Screen.Edit),
                       EtatZoneSalleVoyage (bouton/fantôme/refus) ; la séance (21 septembre
                       2026, « la séance ») : SeancePriseUi et texteCeSoir (la ligne « Ce
                       soir » de l'accueil), VoyageUi.seancePrise depuis
                       VoyageResponse.seance_prise, etatSeanceSuivant (jumeau
                       d'etatParagrapheSuivant, plafond de l'année), messageEchecComposition
                       (le bandeau d'une composition abandonnée sans rien produire — nul dès
                       qu'une séance de plus est apparue, distinct entre l'abandon au
                       plafond et l'arrêt tout seul, corrigé le 21 septembre 2026)
SeanceEtats.kt         nouveau (21 septembre 2026, « la séance », filtre introuvable et
                       etatZoneSeance corrigés le même jour, puis candidatsSeanceCourt
                       complété le même jour encore, puis prise-et-vue corrigée le même
                       jour une dernière fois) : CandidatSeance (Film ou Bobine) et
                       GroupeCandidatsSeance, candidatsSeanceLong (films sans programme,
                       jamais vus ni introuvables) et candidatsSeanceCourt (programmes et
                       bobines non vus ni introuvables, chacun sur son propre état — même
                       filtre que le long, le back refuse un introuvable en 400 dans les
                       deux cas — puis, depuis la dernière retouche, les films sans
                       programme non vus ni introuvables aussi, `longActuelFilmId`
                       excluant celui qui joue déjà en long ce soir-là), groupés par
                       salle, chaque sous-liste triée Plex/demandé/à demander ;
                       corpsRemplacementSeance (film_id seul, + bobine_tmdb_id pour une
                       bobine) ; seanceRecente et seancesPassees (rang décroissant, jamais
                       la plus récente sauf si elle est prise et que son long est vu —
                       elle est alors terminée et rejoint les passées quand même) ;
                       EtatZoneSeance et etatZoneSeance (bouton/en cours/carte
                       proposée/carte prise — `ignoree`, comme `prise` avec un long vu,
                       rend le bouton, ni l'un ni l'autre n'étant terminal au sens où il
                       bloquerait) ; etiquetteEtatSeanceFilm (toujours un texte, y compris
                       « à demander »)
VoyageCarte.kt        afficheAnnee (21 septembre 2026, « le podium » — affiche_url avant
                       le dernier vu), Recompense (Palme/Lion/Ours), phraseRecompenses,
                       detecterFrontiereAvancee (le clap) ; le ticket (21 septembre 2026) :
                       TicketPortefeuilleUi, trierPortefeuille (non utilisés d'abord,
                       compostés par date ensuite) ; les récompenses (étape 5, 21 septembre
                       2026) : recompenseDe (lit `"ours"`/`"lion"`/`"palme"` du back,
                       remplace l'ancien calcul par essentiels), construireTamponDecennie
                       (un tampon complet — films, dates, récompenses de la décennie —
                       depuis `voyage` et le journal, journal vide pour la carte légère du
                       passeport), tamponsPasseport (une ligne par décennie que `tampons`
                       dit bouclée), detecterNouveauTampon (la première décennie inconnue
                       de `dejaVus`, nul au tout premier chargement, jumeau de
                       detecterFrontiereAvancee)
FicheVoyageEtats.kt   la fiche d'un film (brief du 21 septembre 2026) : etatFilmVoyage
                       (vu seulement si toutes les bobines le sont), boutonsFicheVoyage
                       (six boutons depuis « le podium », 21 septembre 2026),
                       lienPlexVoyage (plex:// avant le web), etiquetteEtagere ;
                       etatBoutonChronique (« la chronique et les salles », 21 septembre
                       2026) : absent/ajouter/écrit en cours/dans la chronique
PodiumEtats.kt        nouveau (21 septembre 2026, « le podium ») : CandidatPodium,
                       candidatsPodium (mes films vus de l'année + mes programmes
                       entièrement vus), corpsPodium (tmdb_id ou programme_id, jamais
                       les deux), lignesFeuillePodium (feuille « Marche N »,
                       AnneeScreen), lignesChoixMarche (feuille « Mettre sur le
                       podium », FicheVoyageScreen)
Pellicule.kt          le dessin : segmentDePellicule (les deux quadratiques et leurs
                       perforations), motifDeMonde, glypheRecompense, facadeDeMarquise ;
                       bandeDePellicule (21 septembre 2026) : la même bande, droite,
                       sous le podium d'une année ; le ticket (21 septembre 2026) :
                       bordsPerfores, barrerTicket (un billet composté)
TicketVoyage.kt       nouveau (21 septembre 2026, « le ticket ») : TicketPapier (le
                       dessin, en grand ou en petit), TicketCalque (le calque de
                       `Root.kt`), LigneTicketPortefeuille (la ligne du profil)
CarnetEtats.kt        nouveau (22 septembre 2026, « le carnet ») : CarnetUi,
                       libelleBoutonCarnet et ligneFabriqueLeCarnet (le bouton et la
                       ligne « Fabriqué le… » d'`AnneeScreen`) ; CarnetProfilUi,
                       LigneCarnetProfil et lignesCarnetsProfil (le bloc « Carnets »
                       du profil, `ProfileScreen.kt` — la fabrication en cours
                       l'emporte sur un carnet déjà fabriqué la même année) ;
                       nomFichierCarnet ; anneeProposeeCarnet (l'année qu'on quitte,
                       depuis `FrontiereAvancee`, décision 1 — pas un site de plus que
                       `vm.avancees` de `VoyageScreen.kt`) ; ouvrirCarnet (le
                       téléchargement puis l'`Intent.ACTION_VIEW` par `FileProvider`,
                       décision 4)
ClapAvatar.kt         le clap de l'icône, par AndroidView sur ic_launcher_animated.xml —
                       aucun pathData recopié, la même géométrie que le lanceur
VoyageScreen.kt       l'écran de l'onglet « Frise » : le HUD, la carte mise à plat en
                       cellules, les marquises — le photogramme d'une année ouverte
                       prend afficheAnnee (21 septembre 2026) ; les récompenses (étape 5,
                       21 septembre 2026) : le glyphe sous chaque photogramme et le compte
                       du HUD lisent recompenseDe, la marquise et le générique se
                       déclenchent sur `vm.nouveauxTampons` plutôt que sur la frontière
GeneriqueScreen.kt    le générique de fin d'une décennie, et TamponPasseport (la ligne
                       du passeport, affichée par ui/profile/ProfileScreen.kt) ; les
                       récompenses (étape 5, 21 septembre 2026) : la ligne des festivals
                       de la décennie (`phraseRecompenses` sur `tampon.recompenses`),
                       après la liste des films
AnneeScreen.kt        réécrit le 21 septembre 2026 : la page d'une année — cartouche
                       (ouverture repliable, ou « Prochainement » verrouillée), le
                       podium (BlocPodium, PhotogrammePodium, MarcheSheet, entre le
                       cartouche et les salles), les salles et leurs étagères, et la
                       ligne du bas (LigneBasAnneeEnCours, « le ticket », 21 septembre
                       2026) qui a remplacé le bouton provisoire « Année suivante » ;
                       la chronique et les salles (21 septembre 2026) : les paragraphes
                       dans le cartouche déplié, BlocNouvelleSalle (bouton, EtagereFantome,
                       NouvelleSalleSheet) sous la dernière étagère ; les récompenses
                       (étape 5, 21 septembre 2026) : le glyphe dans le cartouche à côté
                       du millésime, la ligne de `ligneProgression` sous la profondeur ;
                       la séance (21 septembre 2026, « la séance ») : BlocSeance entre le
                       podium et les salles sur l'année en cours seulement, CarteSeance,
                       BoutonSeance (deux rangées de deux, libellé sur une seule ligne,
                       corrigé le 21 septembre 2026 après un constat sur téléphone),
                       CarteAttenteSeance, LigneActionsFilmSeance, SeancesPasseesBloc,
                       RemplacementSeanceSheet (« Autre long »/« Autre court ») ; reprend
                       `ouvrirPlex` de `FicheVoyageScreen.kt`, rendue non privée pour ça ;
                       le carnet (22 septembre 2026, « le carnet ») : BlocCarnet sous la
                       ligne du ticket, dès que l'année a une ouverture (etat PRETE),
                       pas seulement l'année en cours
FicheVoyageScreen.kt  nouvel écran (21 septembre 2026) : la fiche d'un film ou d'un
                       programme, ses boutons selon l'état, « Mettre sur le podium »
                       (ChoisirMarcheSheet) depuis le 21 septembre 2026 (« le podium ») ;
                       « Ajouter à la chronique » (21 septembre 2026) en dernier, le
                       paragraphe du film affiché dessous une fois écrit
DecennieScreen.kt     le rayon d'une décennie
FriseViewModel.kt     le journal, le Plex et /me/voyage pour l'accueil comme pour la
                       carte ; le ticket (21 septembre 2026) : relireApresCreation (la
                       relecture après un enregistrement), garderTicket, utiliserTicketAMontrer ;
                       les récompenses (étape 5, 21 septembre 2026) : le canal
                       nouveauxTampons (un tampon inédit, decenniesVues mémorisée d'un
                       chargement à l'autre, nulle au tout premier), distinct d'avancees
                       (le clap, sur la frontière) ; le carnet (22 septembre 2026, « le
                       carnet ») : proposerCarnet, appelée par `VoyageScreen.kt` sur le
                       même `avancees` que le clap
AnneeViewModel.kt     réécrit le 21 septembre 2026 : les salles d'une année (GET
                       /me/voyage/annees/{annee}), « En voir plus », demander, introuvable
                       — plus d'essentiels, `Screen.FicheVoyage` lit la même instance
                       (indexée sur le seul millésime, `Root.kt`) ; le podium
                       (poserPodium, retirerPodium, 21 septembre 2026) relit l'année
                       entière après chaque écriture ; le ticket (21 septembre 2026) :
                       maturite, ticket, ligneBasAnnee, utiliserTicket — a remplacé
                       anneeSuivante ; la chronique et les salles (21 septembre 2026) :
                       ajouterChronique, ouvrirNouvelleSalle, appliquerDemandeSalle (marque
                       un refus vu une seule fois, par identifiant) ; les récompenses
                       (étape 5, 21 septembre 2026) : ProgressionUi, ligneProgression
                       (la ligne sous la profondeur, ou « Aucun essentiel encore »),
                       AnneeUi.recompense et .progression, lus sur `prete` seulement,
                       comme les salles ; la séance (21 septembre 2026, « la séance ») :
                       composerSeance (optimiste, relit jusqu'à ce que seance_en_cours
                       retombe, jumeau de voirPlus ; capture seancesAvant, envoie
                       messageEchecComposition et force seanceEnCours à faux si rien n'est
                       apparu — corrigé le 21 septembre 2026, « une composition abandonnée
                       le dit »), prendreSeance/ignorerSeance/remplacerSeance
                       (relireApresSeance, jumeau de relireApresPodium), demander met
                       aussi à jour le film de la séance en cours si son tmdb_id
                       correspond (mettreAJourSeanceFilm) ; le carnet (22 septembre 2026,
                       « le carnet ») : fabriquerCarnet (optimiste, jumeau de
                       voirPlus/composerSeance, relit jusqu'à ce que carnet_en_cours
                       retombe, même plafond que la chronique et les salles) et
                       telechargerCarnetPdf (un simple passe-plat, l'écriture dans
                       cacheDir et l'Intent restant à l'écran)
ChroniqueViewModel.kt nouveau (21 septembre 2026, « la chronique et les salles ») : jumeau
                       léger d'AnneeViewModel.ajouterChronique pour `Screen.Edit`
                       (ui/form/FormScreen.kt), sans charger l'année entière ; un `init` (décision
                       du 21 septembre 2026, « au montage ») lit `voyageAnnee` une fois à la
                       création et cherche le paragraphe de `tmdbId` dans `paragraphes`
```

**Le passeport**, sous `app/src/main/kotlin/fr/mediatheque/journal/ui/profile/` (décision 3 du
brief du 21 septembre 2026, « les récompenses ») :
`PasseportViewModel.kt`, nouveau, jumeau de `PortefeuilleViewModel.kt` ci-dessous — charge lui-même
`GET /me/voyage` (jamais depuis `FriseViewModel`), la carte n'étant donc plus jamais vide quand
Profil s'ouvre en premier. `refresh()` construit un tampon léger par entrée de `tampons`
(`construireTamponDecennie` avec un journal vide, `ui/frise/VoyageCarte.kt`) ; `ouvrirGenerique`
construit le tampon complet au tap, depuis le journal que l'appelant lui passe (celui de la Frise,
`Root.kt`) ou, s'il est vide, `journalComplet()` lui-même. `ProfileScreen.kt` porte la carte
`PasseportCard`, sous le Bilan.

**Le portefeuille**, même dossier (décision 3 du
brief du 21 septembre 2026, « le ticket ») : `PortefeuilleViewModel.kt` charge `GET
/me/voyage/tickets` lui-même (jamais depuis `FriseViewModel`) et trie par `trierPortefeuille`
(`ui/frise/VoyageCarte.kt`) ; `ProfileScreen.kt` porte la carte `PortefeuilleCard`, sous le
Passeport.

**Les dépenses** (décision 2 du brief du 21 septembre 2026, « les dépenses »), jumelles du
portefeuille, même dossier : `DepensesViewModel.kt` charge `GET /me/voyage/depenses` lui-même et
porte `DepenseMoisUi`, `ligneMoisCourant`, `ligneMoisPrecedent` et `triMoisPrecedents` (fonctions
pures) ; `formatMoisAnnee` et `formatCentimes` (le mois en toutes lettres capitalisé, la virgule à
une décimale) vivent dans `ui/Format.kt`, jumelles de `formatDate`. `ProfileScreen.kt` porte la
carte `DepensesCard`, sous le Portefeuille — un tap déplie les mois précédents, l'état ne vit que
dans le composable (`remember`).

**Les carnets** (décision 3 du brief du 22 septembre 2026, « le carnet »), sous
`app/src/main/kotlin/fr/mediatheque/journal/ui/profile/`, jumeaux du portefeuille et des dépenses :
`CarnetsViewModel.kt` charge `GET /me/voyage/carnets` lui-même (jamais depuis `FriseViewModel`) et
porte `telechargerCarnetPdf` — un simple passe-plat réseau, l'écriture dans `cacheDir` et
l'ouverture par `Intent` restant à l'écran, seul à tenir un `Context` (`ouvrirCarnet`,
`ui/frise/CarnetEtats.kt`, partagée avec `AnneeScreen.kt` et sa propre `AnneeViewModel.
telechargerCarnetPdf`/`fabriquerCarnet`, décision 2). `ProfileScreen.kt` porte la carte
`CarnetsCard`, sous les Dépenses ; `Root.kt` construit `CarnetsViewModel` (clé `"carnets"`, indexé sur
l'Activité) et appelle `refresh()` une fois par entrée sur le profil. La proposition de carnet
(décision 1), elle, ne vit dans aucun de ces deux dossiers : `FriseViewModel.proposerCarnet` et le
dialogue de `VoyageScreen.kt` (`carnetPropose`), au même site que le clap et la snackbar de
`vm.avancees`.

`FriseScreen.kt` (le calendrier du siècle, 16 septembre 2026) a disparu avec sa
teinte de case `couleurDeCase` : la carte n'a plus de cases à colorer, ses
photogrammes portent une affiche. `GET /reference/chroniques/annees/{annee}` a
disparu le 21 septembre 2026, remplacée par `GET /me/voyage/annees/{annee}` :
`ChroniqueAnneeResponse` et les essentiels qui allaient avec (`EssentielVoyage`,
`ApercuEssentiel`, la carte « Prochaine étape ») ont disparu avec elle.

**La page réalisateur** (brief du 21 septembre 2026, « la page réalisateur »), sous
`app/src/main/kotlin/fr/mediatheque/journal/ui/realisateur/` (nouveau paquet) :

```
RealisateurEtats.kt      fonctions pures : ligneDates (naissance/décès, aucune) ;
                          DestinationFilm/destinationFilm (voyage -> Screen.FicheVoyage,
                          sinon fiche simple) ; etatFilmographie et BoutonFicheFilm/
                          boutonsFicheFilm (jumeaux de FicheVoyageEtats.kt, ui/frise/,
                          une série sans Je l'ai vu ni Introuvable) ; ResultatRealisateur/
                          resultatTapRealisateur (un/plusieurs/aucun crédit)
RealisateurResolveur.kt  ViewModel léger, une seule instance partagée (Root.kt, clé
                          "realisateur-resolveur") : resoudre(filmTmdbId), en cache par
                          film (EtatRealisateursFilm), GET /reference/films/{tmdbId}/
                          realisateurs
RealisateurViewModel.kt  la page d'un réalisateur : charger (GET .../page), suivre/
                          retirer/demander/marquerIntrouvable/retirerIntrouvable
                          partagent un seul chemin (ecrire) — l'appel, puis un
                          rechargement complet, jumeau de basculerIntrouvable
                          (AnneeViewModel.kt, ui/frise/)
NomRealisateurTouchable.kt   le composant partagé par tous les noms touchables (§5) :
                          nomConnu affiché tel quel si non nul, sinon résolu à la
                          composition ; le tap résout toujours, en cache, pour choisir
                          la destination (page directe, feuille, bandeau)
RealisateurScreen.kt      Screen.Realisateur : la fiche, le bouton Suivre/Suivi,
                          l'étagère de la filmographie (AfficheFilmographie, jumelle
                          d'AfficheFilm côté Voyage — coin Plex, coin TV, liseré
                          annee_ouverte dans l'accent du monde de l'année du film)
FicheFilmScreen.kt        Screen.FicheFilm : la fiche simple, nourrie par la ligne de
                          filmographie déjà chargée — relit le même RealisateurViewModel
                          que la page d'où elle s'est ouverte (indexé sur le
                          réalisateur, Root.kt), jamais un appel de plus
```

`api/dto/Realisateurs.kt` porte les DTO neufs : `RealisateurCredit`/
`RealisateursDuFilmResponse` (`GET /reference/films/{tmdbId}/realisateurs`),
`FilmDeFilmographie`/`VoyageDeFilmographie`/`RealisateurPageResponse`
(`GET /me/realisateurs/{tmdbId}/page`) — distincts de `FilmSuivi` (`Suivis.kt`), qui ne
porte ni `type` ni les champs du Voyage (`sur_le_plex`, `demande`, `plex_url`,
`annee_ouverte`, `voyage`). `FicheSuiviScreen.kt` (`ui/suivis/`) a perdu son cas
réalisateur (décision 4) : `peutRetirerDeSaga` (`SuivisViewModel.kt`) n'a plus de
paramètre de source, cette fiche ne servant plus qu'aux sagas — un réalisateur a
désormais sa propre page.

**SensCritique** (brief du 14 septembre 2026), sous
`app/src/main/kotlin/fr/mediatheque/journal/` :

```
senscritique/
  ExternalRatingService.kt      l'interface générique (brief : « séparable » pour Cinoche demain),
                                 MatchableFilm, ExternalCandidate, les issues de recherche/poussée
  TitleMatcher.kt                l'appariement des titres, porté de l'importateur SensCritique du
                                  back (normaliserTitre, apparierCandidat, fautRepliOriginalTitle)
  SensCritiqueStore.kt            l'interface, InMemorySensCritiqueStore (tests),
                                   KeystoreSensCritiqueStore (AES-GCM AndroidKeyStore, réel),
                                   charge utile versionnée (2 : cookieRef, dateExpiration ; une
                                   version 1, Firebase, relue déconnectée)
  SensCritiqueAuthClient.kt       la connexion : une mutation GraphQL (signInWithEmailAndPassword),
                                   jamais Firebase (revue du 14 septembre 2026, après un premier
                                   essai réel — l'API GraphQL n'accepte que son propre cookieRef)
  SensCritiqueGraphQLClient.kt    les quatre appels GraphQL authentifiés, documents du site
                                   SensCritique lui-même (vérifiés le 14 septembre 2026) :
                                   searchProductExplorer (chercher), productRate (noter),
                                   productDone (marquer vu), setProductDateDone (poser la date,
                                   après productDone) — cookieRef en en-tête Authorization, brut
  SensCritiqueRatingService.kt    l'implémentation SensCritique de ExternalRatingService
  SensCritiqueSync.kt             l'orchestration : résoudre, pousser, la file, le rejeu
ui/profile/
  SensCritiqueScreen.kt, SensCritiqueViewModel.kt   l'écran de connexion, jumeau de LoginScreen
ui/form/
  SensCritiqueChoiceSheet.kt      la feuille « Lequel sur SensCritique ? »
```

**L'icône : le clap.** Fond `#000000` (`ic_launcher_background.xml`), premier
plan corail `#FF6B57` : le corps fixe et le volet, un `<group
android:name="volet">` pivoté en bas à gauche (32,48), au repos à −30°,
volet ouvert — le tiroir et l'écran d'accueil montrent le clap ouvert.
(Repos à −14° jusqu'au 10 septembre 2026, changé le même jour sur demande du
propriétaire : « un clap plus actif ».) C'est la même géométrie, dans
`ic_launcher_foreground.xml`, qui sert d'icône de l'application (tiroir,
raccourcis) et de base à l'animation : aucune duplication de `pathData` à
garder synchronisée. Tout le dessin (corps et groupe « volet ») est enveloppé
dans un groupe de mise à l'échelle centré (pivot 54,54, `scaleX`/`scaleY`
0,8), pour que le masque du lanceur ne rogne plus la pointe du volet : sans
lui, l'extrémité du volet au repos −30° était à ~41 du centre, hors du
cercle de sécurité de 66 dp (rayon 33) ; à l'échelle 0,8, elle tombe à
~32,9, dedans. Le point le plus lointain de toute l'animation, l'armement à
−46°, reste hors du cercle même à cette échelle (~37,9, et encore ~36,0 à
0,76, essayé pour voir) — mais il dépassait déjà le cadre de recadrage de
72 dp avant tout changement d'échelle, donc déjà entièrement rogné,
invisible, durant les 120 ms de l'armement : l'échelle 0,8 ne perd rien de
plus sur ce point transitoire, et 0,76 n'aurait fait que rétrécir l'icône au
repos sans le régler. Détail du calcul dans le commentaire du fichier.

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
