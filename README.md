# Journal — l'appli Android de la médiathèque

Un bouton, une recherche, un formulaire : le journal de mes films, sur l'API de
`../biblio-back`. Conception dans
`../biblio-back/docs/superpowers/specs/2026-09-03-journal-android-design.md`,
apparence dans `docs/design.md`.

## Démarrer

Rien à installer sauf Docker : le JDK, le SDK Android et Gradle vivent dans
l'image `Dockerfile.build`, que le premier `bin/…` construit tout seul (quelques
minutes, le SDK pèse). Une fois :

    printf 'UID=%s\nGID=%s\n' "$(id -u)" "$(id -g)" > .env
    cp local.properties.example local.properties   # puis l'IP du poste
    bin/pair ip:port                                # le code de « Associer l'appareil »
    # ADB_DEVICE=ip:port dans .env                  # le port de « Débogage sans fil » — bin/install le retrouve seul (voir plus bas)

`UID` et `GID` ne sont pas décoratifs : l'image recrée l'utilisateur de l'hôte
(`ARG UID/GID`), sans quoi `build/` et `.gradle/` reviennent en `root` et ne
s'effacent plus sans `sudo`. Le `.env` est ignoré par git, `.env.example` le
rappelle.

Ensuite, à chaque fois :

    bin/build            # app/build/outputs/apk/debug/app-debug.apk
    bin/install          # sur le téléphone, par-dessus la version en place
    bin/logs             # le journal du téléphone

N'importe quelle autre commande passe par `bin/dans` :

    bin/dans ./gradlew tasks

La version de développement vise l'instance locale de l'API (`local.properties`),
lancée dans `../biblio-back` par `npm run dev` ou
`docker compose --profile full up -d`. Elle l'atteint en HTTP en clair, ce que
seul `app/src/debug/res/xml/network_security_config.xml` autorise : la version
`release`, qui vise `https://mini-mediatheque.fr/api`, n'a pas ce fichier et
refuse tout `http://`.

Une instance locale doit aussi mettre `STORAGE_PUBLIC_URL=http://<ip du
poste>:3000/covers` dans le `.env` de `biblio-back` : sans ce réglage, les
jaquettes de l'accueil et de « Mes films » sortent en
`http://localhost:3000/…` et c'est sur lui-même que le téléphone va les
chercher (constaté le 10 septembre 2026).

`bin/logs` filtre `logcat` sur le pid de l'application, parce que `logcat`
n'imprime pas le nom du paquet : il faut donc que l'application tourne au moment
où on l'appelle. Après un plantage, le processus est mort et sa trace ne sortira
plus par ce chemin ; le tampon des plantages, lui, la garde :

    bin/dans sh -c 'adb connect "$ADB_DEVICE" && adb -s "$ADB_DEVICE" logcat -b crash'

(les guillemets simples comptent : `ADB_DEVICE` n'existe que dans le conteneur,
le développer sur le poste ne rendrait rien.)

Le port du débogage sans fil change à chaque extinction d'écran. `bin/install`
le retrouve avant de se connecter : le téléphone l'annonce en mDNS (service
`_adb-tls-connect._tcp`), que le poste écoute avec `avahi-browse` — le conteneur,
lui, ne reçoit pas le multicast. Trouvé, il remplace `ADB_DEVICE` dans `.env`
(`bin/logs` en profite) ; absent, le script le dit et garde la valeur de `.env` :
c'est le moment de rallumer l'écran, ou de refaire `bin/pair` si l'association
a sauté (« failed to connect » avec un téléphone qui répond au ping).

Repli sans `adb` (téléphone trop ancien, réseau qui isole ses clients) : copier
`app/build/outputs/apk/debug/app-debug.apk` sur le téléphone et l'ouvrir.

### La construction `release`

`bin/build release` exige une clé de signature, décrite dans
`signing/release.properties` (hors du dépôt, ignoré par git). Sans elle, la
variante `release` est refusée **en entier**, en quelques secondes et avec une
phrase qui dit quoi faire — plutôt qu'une fois toute la compilation faite, sur
le message technique d'AGP, ou pire en produisant un APK que le téléphone
refuserait. Le prix de ce choix : on ne peut pas compiler la variante `release`
juste pour voir, tant qu'il n'y a pas de clé. C'est voulu — aucune construction
ne doit fabriquer une clé au passage.

Le script qui crée cette clé, `bin/keystore`, la produit une fois : voir
« Mettre en service » plus bas.

## Mettre en service

La variante `release` vise `https://mini-mediatheque.fr/api` (`buildConfigField`
dans `app/build.gradle.kts`) et exige la clé de signature ci-dessus.

    bin/keystore          # une fois ; lit le mot de passe deux fois, sans echo
    bin/build release      # app/build/outputs/apk/release/app-release.apk
    bin/install release    # sur le téléphone, à côté de la version « dev »

`bin/keystore` écrit `signing/release.jks` et `signing/release.properties`
(hors du dépôt, ignorés par git). **Copier aussitôt le dossier `signing/`
ailleurs** — un gestionnaire de mots de passe, un disque chiffré, le choix du
lieu est au propriétaire : le perdre, c'est ne plus pouvoir mettre
l'application à jour sans la désinstaller d'abord (signature différente,
Android refuse l'écrasement).

Il suffit de sauvegarder `release.jks` et le mot de passe : `release.properties`
ne contient que ce mot de passe en clair, et se réécrit. Sur un poste neuf,
dans ce dépôt :

    mkdir -p signing && cp <la copie> signing/release.jks
    printf 'keystore.path=signing/release.jks\nkeystore.password=%s\nkey.alias=journal\nkey.password=%s\n' \
      'MOT_DE_PASSE' 'MOT_DE_PASSE' > signing/release.properties
    chmod 600 signing/release.jks signing/release.properties

L'alias est `journal`, et le même mot de passe protège le trousseau et la clé :
c'est ainsi que `bin/keystore` la crée. (Sauvegarde faite le 10 septembre 2026.)

Les deux variantes cohabitent sur le même téléphone comme deux applications
distinctes, signatures différentes obligent : « Journal »
(`fr.mediatheque.journal`, la version `release`, contre l'instance en ligne)
et « Journal (dev) » (`fr.mediatheque.journal.debug`, la version `debug`,
contre l'instance locale). `bin/install` et `bin/logs` prennent tous deux
`debug` ou `release` en argument (`debug` par défaut) : `bin/install release`,
`bin/logs release`.

Après un tag posé sur `biblio-back` (le propriétaire décide, voir son
`CLAUDE.md`), recopier `docs/openapi.json` du back dans `contract/openapi.json`
ici : rien ne garde cette copie de dériver du contrat qu'elle prétend refléter,
et `ContractTest` continuerait de passer contre une version périmée sans le
dire. Puis vérifier que le NAS a basculé avant d'installer :

    curl -s https://mini-mediatheque.fr/api/health
    curl -s -o /dev/null -w '%{http_code}\n' https://mini-mediatheque.fr/api/me/journal

Attendu : `{"status":"ok",…}` puis `401` — la route existe et exige une
session. Un `404` voudrait dire que l'API en service n'a pas encore le
carnet.

## Les versions

Elles sont figées ici et dans `gradle/libs.versions.toml` ; on les monte
exprès, jamais au fil de l'eau.

| Chose | Version | Où c'est écrit |
|---|---|---|
| JDK | 17.0.20+8 | `Dockerfile.build` (`eclipse-temurin:17.0.20_8-jdk` — l'étiquette porte le correctif, `17-jdk` glisserait) |
| Gradle | 8.14.3 | `gradle/wrapper/gradle-wrapper.properties`, **qui fait foi** ; `Dockerfile.build` répète la valeur pour installer la distribution qui écrit le wrapper |
| Outils en ligne de commande Android | 11076708 | `Dockerfile.build` |
| `platform-tools` (`adb`) | 37.0.1 | `Dockerfile.build` — par l'archive versionnée, `sdkmanager` ne sachant pas épingler ce paquet |
| SDK | `platforms;android-36`, `build-tools;36.0.0` | `Dockerfile.build`, et `buildToolsVersion` dans `app/build.gradle.kts` — les deux doivent rester égaux, sinon AGP retélécharge sa version par défaut à **chaque** exécution, le conteneur mourant avec la commande |
| `compileSdk` / `targetSdk` / `minSdk` | 36 / 35 / 26 | `app/build.gradle.kts` |
| Android Gradle Plugin | 8.13.0 | `gradle/libs.versions.toml` |
| Kotlin | 2.2.20 | idem |
| Compose BOM | 2025.09.01 | idem |
| Ktor | 3.3.1 | idem |
| Coil | 3.4.0 | idem |

`compileSdk 36` n'est pas un caprice : `activity-compose` 1.11.0 exige
`minCompileSdk=36`. On compile contre 36, on promet 35.

Coil est coincé par les deux bouts, et c'est le seul endroit du jeu où deux
contraintes se croisent :

- la 3.6.x exige `minCompileSdk=37`, un SDK qui n'existe pas ;
- la 3.5.0 est compilée par Kotlin 2.4, dont le compilateur 2.2.20 ne sait pas
  lire les métadonnées (il va jusqu'à la 2.3.0) : la compilation s'arrête sur
  `kotlin.Unit`, avant même que l'application n'emploie Coil.

Reste la 3.4.0 : Kotlin 2.3.10, `minCompileSdk=35`. Monter Coil demandera donc
de monter Kotlin, et ce sera une décision à prendre en entier, pas en passant.

Les polices embarquées (`res/font/`, jamais téléchargées) sont toutes sous licence SIL Open Font
License : Manrope, Fraunces (`undercasetype/Fraunces`, statiques de `fonts/ttf/`, habillage
« papier et pellicule » du 23 septembre 2026) et Limelight (`google/fonts` → `ofl/limelight`, un
seul style, réservée aux titres de célébration). Les trois licences, `OFL-Manrope.txt`,
`OFL-Fraunces.txt` et `OFL-Limelight.txt`, vivent dans `app/src/main/assets/`.

## SensCritique

Décision du propriétaire du 14 septembre 2026 : quand il note un film dans l'appli, la note et la
date de visionnage partent aussi sur son compte SensCritique. Tout vit dans l'appli, rien ne change
au back.

- **La connexion n'est pas Firebase.** Un premier essai réel (14 septembre 2026) a montré que l'API
  GraphQL de SensCritique refuse un `idToken` Firebase (`auth/unauthenticated-user`) : la connexion
  est une mutation GraphQL comme les autres (`signInWithEmailAndPassword`, sur le même point
  d'entrée), qui rend un `cookieRef` — posé tel quel, sans le mot « Bearer », en en-tête
  `Authorization` de chaque appel authentifié (`senscritique/SensCritiqueAuthClient.kt`,
  `senscritique/SensCritiqueGraphQLClient.kt`).
- **Le mot de passe SensCritique n'est jamais stocké, ni journalisé.** Seuls `cookieRef`,
  `dateExpiration` (tel que SensCritique le rend, jamais reformaté) et le pseudo le sont, chiffrés
  par une clé AES-GCM du `AndroidKeyStore` — jamais `EncryptedSharedPreferences`, dépréciée — dans
  les `SharedPreferences` `senscritique` de l'application (`senscritique/SensCritiqueStore.kt`). Le
  même fichier chiffré garde aussi les choix faits sur la feuille « Lequel sur SensCritique ? »
  (`media_id → productId` ou « aucun ») et la file des poussées qui ont échoué : « Déconnecter »
  efface tout, file comprise. La charge utile est versionnée (2) : une ancienne charge utile
  Firebase (version 1, `refreshToken`) se relit déconnectée, mais garde sa file et ses choix.
  Avant une poussée, `dateExpiration` dépassée vaut refus : déconnexion sans appel réseau.
- **La recherche et les trois mutations de la poussée** (`senscritique/SensCritiqueGraphQLClient.kt`)
  viennent des documents GraphQL du site SensCritique lui-même, lus et vérifiés le 14 septembre 2026 :
  `searchProductExplorer` (avec un repli sans filtre univers si le serveur le refuse — `BAD_USER_INPUT`
  — et un filtre `universe == 1` côté appli dans tous les cas), puis `productRate`, `productDone` et
  `setProductDateDone` dans cet ordre. Un échec de cette dernière seule (la date) ne défait pas la
  poussée : la note et le « vu » sont déjà acquis, seule la date manque — journalisé. Un refus de
  session (codes `auth/unauthenticated-user`, `api/invalid-token`, `api/missing-token`) déconnecte,
  comme un 401/403 HTTP ; toute autre erreur part en file, réessayée au prochain lancement.

## Le chroniqueur

Le Voyage (brief du 16 septembre 2026, phase 1 « le moteur », puis phase 2 « la carte ») :
traverser l'histoire du cinéma année par année, depuis 1895 — l'onglet « Frise » en est la carte,
une pellicule qui serpente de monde en monde (`docs/design.md` §2 et §5). Le récit d'une année et le carton « Et pendant ce temps… » d'un film
sont écrits par Claude, **côté back** — rien de tout ça ne vit dans l'appli. `ANTHROPIC_API_KEY` et
`CHRONIQUES_MODEL` (`claude-opus-5` par défaut) sont des réglages de l'instance biblio-back
(`.env`, `docs/self-hosting.md` de ce dépôt-là) ; sans clé, `GET /me/voyage`, `GET
/me/voyage/annees/{annee}` et `GET /reference/chroniques/films/{tmdbId}` répondent `configure:
false` et l'appli n'affiche ni cartouche ni carte, jamais une erreur. Chaque ouverture d'année, chaque
salle et chaque carton s'écrit une fois et ne se regénère jamais — le coût, de l'ordre de quelques
centimes par écriture, est facturé au propriétaire de la clé, pas à l'appli. Rien n'est stocké sur le
téléphone au-delà de la session en cours : ouvertures, salles et cartons vivent en base côté back,
relus à chaque ouverture d'écran (`GET /me/voyage/annees/{annee}` a remplacé `GET
/reference/chroniques/annees/{annee}` le 21 septembre 2026, « l'année en étages »), jamais mis en
cache localement.

## Vérifier

    bin/dans ./gradlew testDebugUnitTest

Pour éprouver l'import Letterboxd (brief du 16 septembre 2026) sur un vrai
fichier : sur Letterboxd, Réglages → Import & Export → « Export your data »
donne un ZIP contenant `diary.csv` (et `watched.csv`, `ratings.csv`, que
l'appli n'utilise pas). C'est ce ZIP, ou `diary.csv` seul une fois extrait,
que Profil → « Importer Letterboxd » accepte.

Ce qui ne se teste pas sur la JVM se vérifie sur le téléphone :

Aucune des lignes qui suivent n'a encore été vue sur un téléphone : c'est une
liste de contrôle à jouer, pas un journal de ce qui a déjà été vérifié.

- [ ] Au lancement, aucun flash clair : écran de démarrage, fenêtre et premier écran sont noirs.
- [ ] Au lancement, le clap s'ouvre et claque sur l'écran de démarrage, puis l'accueil.
- [ ] Le titre est en Manrope, pas en Roboto : le J a une boucle nette, le a est à un étage.
- [ ] Un mauvais mot de passe : le message du back, tel quel, sous le bouton.
- [ ] Dix échecs de connexion : le bouton passe à « Patiente 900 s » et décompte.
- [ ] Une connexion réussie : l'accueil, sans écran de connexion.
- [ ] Tuer l'application, la rouvrir : l'accueil directement — le cookie a survécu.
- [ ] Couper le Wi-Fi, rouvrir : le bloc « L'API est injoignable. » avec « Réessayer ».
- [ ] L'accueil : une grille de jaquettes, trois colonnes, du plus récent au plus ancien, la note en pastille en bas à droite de chacune quand elle existe ; le bouton « Ajouter un film » au-dessus de la barre du bas, pleine largeur.
- [ ] La barre de navigation du bas (Accueil, Profil) est visible sur l'accueil, « Mes films » et le profil, jamais sur le formulaire, la recherche ou SensCritique ; « Profil » y est sélectionnée sur « Mes films » ; toucher l'entrée déjà sélectionnée ne fait rien ; le bouton « Ajouter un film » reste visible au-dessus d'elle.
- [ ] Toucher une jaquette de l'accueil ouvre le formulaire pré-rempli.
- [ ] La recherche : le clavier est ouvert à l'arrivée ; taper « chihiro » ; la barre de 2 dp apparaît puis les résultats, affiche à gauche de chaque ligne ; un film sans affiche montre son initiale.
- [ ] Une recherche sans résultat, par exemple « zzzz » : « Rien trouvé pour “zzzz”. ».
- [ ] Le retour système depuis la recherche ramène à l'accueil.
- [ ] Sans clé TMDB sur l'instance locale : le message `503` du back dans son bloc, avec « Réessayer ».
- [ ] Chercher un film, le choisir : l'affiche 96 × 144, le titre, le réalisateur et l'année.
- [ ] La date dit « 3 septembre 2026 » (aujourd'hui) ; toucher, choisir hier ; demain est grisé.
- [ ] Deux rangées de cinq pastilles, atteignables au pouce ; en toucher une, elle passe au corail en 150 ms ; la retoucher la libère.
- [ ] Les puces de réaction se touchent au pouce sans viser (cible 48 dp).
- [ ] Cocher trois réactions : elles passent en fond brun et texte saumon.
- [ ] Écrire un commentaire ; « Rien qu'à toi » dessous.
- [ ] Enregistrer : le bouton garde sa taille, l'indicateur apparaît à gauche du texte, puis retour à l'accueil, « Enregistré » en snackbar deux secondes.
- [ ] Après avoir enregistré un film, sa jaquette est dans la grille de l'accueil, parmi les films de sa date, avec sa note.
- [ ] Sur le site, connecté avec le même compte : le film est « vu », daté, noté, **sans** le commentaire ni les réactions ; connecté avec un autre compte : idem.
- [ ] Couper le Wi-Fi, enregistrer un autre film : le bloc « L'API est injoignable. » avec « Réessayer » ; le rallumer, Réessayer : enregistré.
- [ ] Le profil : le pseudo, « **N** films vus, **M** cette année » avec les deux nombres en gros ; « Mes films » avec un chevron ; « Se déconnecter » ; en bas, le logo TMDB et sa phrase, centrés.
- [ ] « Mes films » : la liste, du plus récent au plus ancien, affiche à gauche, date en toutes lettres, emojis des réactions, note à droite ; défiler jusqu'en bas quand il y a plus de quarante films charge la suite sans à-coup.
- [ ] Toucher une ligne : le formulaire pré-rempli, « Corriger », « Supprimer ». Corriger la note : retour à l'accueil, « Corrigé » en snackbar ; rouvrir « Mes films » : la ligne à jour.
- [ ] Supprimer : la boîte à deux boutons, puis « Supprimé » en snackbar ; rouvrir « Mes films » : la ligne partie. Sur le site, le film reste « vu ».
- [ ] Se déconnecter depuis le profil : l'écran de connexion. Tuer l'application et la rouvrir : toujours l'écran de connexion.
- [ ] Taille de police système au maximum sur l'accueil, le profil et « Mes films » : rien n'est coupé, les deux nombres restent lisibles, les réactions d'une ligne passent à la ligne (design §11).
- [ ] La version `debug` installée s'appelle « Journal (dev) » sur l'écran d'accueil (paquet `fr.mediatheque.journal.debug`).
- [ ] La version `release` (`bin/install release`) installée à côté s'appelle « Journal » (paquet `fr.mediatheque.journal`) : les deux applications cohabitent, aucune n'efface l'autre.
- [ ] Profil, ligne « SensCritique » : « Non connecté ». La toucher ouvre l'écran de connexion ; un mauvais mot de passe dit « Identifiants refusés. » sous le bouton.
- [ ] Une connexion réussie : le pseudo affiché, retour au profil, la ligne dit « Connecté : … ».
- [ ] Noter un premier film (note non nulle) une fois connecté : « Enregistré · SensCritique ✓ » en snackbar ; le film apparaît noté, « vu » et daté (la date du journal) sur SensCritique.
- [ ] Corriger la note d'un film déjà poussé : « Corrigé · SensCritique ✓ » ; la note change aussi sur SensCritique.
- [ ] Un film sans correspondance nette sur SensCritique (titre ambigu, ou absent de leur catalogue) : la feuille « Lequel sur SensCritique ? » s'ouvre avant le retour à l'accueil ; toucher un candidat ou « Aucun de ceux-là » referme la feuille et termine le geste.
- [ ] Rouvrir ce même film et le corriger de nouveau : la feuille ne redemande plus (le choix est mémorisé).
- [ ] Sur un film ambigu, quitter la feuille par le retour système plutôt que par un choix : le geste se termine quand même, « Enregistré · SensCritique : réessai au prochain lancement » ; la poussée reste en file sans être résolue — corriger ce même film plus tard rouvre la feuille « Lequel sur SensCritique ? ».
- [ ] Se déconnecter depuis l'écran SensCritique : la ligne du profil repasse à « Non connecté » ; noter un film ensuite : « Enregistré », sans aucun suffixe SensCritique — rien n'est tenté tant qu'on n'est pas reconnecté.
- [ ] Dans le formulaire, la réaction « 🎬 En salle » est proposée parmi les autres puces.
- [ ] « Au ciné » (troisième icône de la barre du bas, entre Accueil et Profil) : « Sorti cette semaine dans mes cinémas » et « La semaine prochaine » montrent des affiches de sorties, une coche corail sur celles déjà dans le journal ; toucher une affiche ouvre le formulaire pré-rempli.
- [ ] Sous chaque affiche de « Sorti cette semaine dans mes cinémas », une petite ligne donne un cinéma, et « +N » quand le film y joue dans plusieurs ; sous le titre, « mis à jour à *H* h » une fois que le back a une passe à son actif.
- [ ] Une tuile de « Sorti cette semaine dans mes cinémas » sans identifiant TMDB (rare) ne réagit pas au toucher et ne porte jamais la coche.
- [ ] `SORTIES_CINEMAS` vide côté back, ou la tâche de fond jamais lancée : « Pas encore de programme. » à la place de la grille.
- [ ] « Tes séances » liste les films marqués « En salle », du plus récent au plus ancien, même ligne que « Mes films » ; toucher une ligne ouvre la correction, et « N séances cette année » en tête reflète bien ce qui est journalisé.
- [ ] La barre du bas compte cinq icônes (Accueil, Frise, Suivis, Au ciné, Profil) ; « Frise » ouvre le Voyage.
- [ ] Sur l'accueil, la ligne « Ensuite » (affiche, titre, année) apparaît sans bloquer l'affichage de la grille ; absente s'il n'y a rien à voir sur le Plex ; toucher l'affiche ouvre le formulaire pré-rempli.
- [ ] Le Voyage (brief du 16 septembre 2026, phase 2, réécrit pour « l'année en étages » le 21 septembre 2026) : en tête, « Chapitre *N* · *monde* » dans la couleur du monde, « *N* années visitées · *année* en cours », puis « *N* Palmes · *N* Lions · *N* Ours » dès qu'une année au moins a une récompense (étape 5, « les récompenses ») ; la carte s'ouvre **déjà défilée** sur l'année en cours, la pellicule serpentant d'un photogramme à l'autre — perforations comprises — sans rupture entre deux cellules ni entre deux mondes, chacun portant son fond, sa lettrine et son sous-titre (« 1890 · LES ORIGINES · la manivelle »).
- [ ] Un photogramme d'année **ouverte** (avant l'année en cours) montre une affiche, un liseré doré, sa profondeur (« *N* films ») et, sous elle, le glyphe Ours/Lion/Palme de sa récompense (rien sans aucun film vu) ; l'année en cours est sous un cône corail avec « Tu es ici » et le clap à côté ; une année verrouillée est un cadre pointillé « à tourner », ou « *N* vu(s) en avance ». Toucher un photogramme ouvre l'année.
- [ ] Au bout d'un monde, sa marquise : ampoules éteintes et « en cours de tournage » tant que la décennie n'a pas ses dix Ours et son ticket suivant utilisé ; une fois bouclée, elle s'allume (d'un coup en déroulant une vieille décennie, en s'animant sur celle qui vient de l'être) et porte le titre de voyageur ; la toucher ouvre le rayon de la décennie, qui a désormais le fond et l'accent de son monde, avec son nom sous le titre.
- [ ] Une décennie qui se boucle pendant qu'on est sur le Voyage (dixième Ours, ou ticket suivant tout juste utilisé) ouvre son générique toute seule, sans attendre que l'année en cours en change.
- [ ] « Suivis » (cinquième icône, entre Frise et Au ciné, ex-« Réalisateurs ») ouvre la liste : deux segments en tête, « Réalisateurs » et « Sagas » ; chaque ligne montre la photo ou l'affiche ronde (ou l'initiale), le nom, puis « N vus sur M · prochain : Titre (année) » — « … » pendant le chargement de ses films, « indisponible » si elle a échoué ; segment vide : « Ajoute un réalisateur avec + » ou « Ajoute une saga avec + ».
- [ ] Changer de segment revient sur la liste déjà chargée (ou son chargement en cours), sans repartir de zéro ; quitter l'écran Suivis puis y revenir garde le segment choisi (mémorisé pour la session).
- [ ] Le « + » en haut à droite ouvre la recherche du segment affiché (clavier ouvert, résultats 400 ms après la frappe, placeholder « Un nom de réalisateur » ou « Un nom de saga ») ; toucher un résultat le suit, referme l'écran, et « Ajouté » apparaît en snackbar sur la liste.
- [ ] Toucher une ligne de saga ouvre sa fiche : les films dans l'ordre de sortie, note à droite des films vus, pastille corail « à voir » sur le seul premier film non vu ; toucher un film vu ouvre sa correction, toucher le prochain à voir ouvre le formulaire pré-rempli ; la poubelle en haut à droite demande confirmation puis retire l'entité et revient à la liste.
- [ ] Toucher une ligne de réalisateur ouvre sa page (brief du 21 septembre 2026, « la page réalisateur, reprise ») : le fond du monde du Voyage de l'année de son premier film, son nom en grand dans l'accent, ses dates (« 1861 – 1938 », « né en 1958 », « née en 1958 » ou « naissance en 1958 » selon TMDB), sa présentation, le bouton « Suivre »/« Suivi », puis « *N* films · *N* vus · *N* sur le Plex » ; sa filmographie en grille verticale trois affiches par ligne, groupée par décennie (« Années 1980 ») dans l'ordre du back, les longs d'abord puis, derrière « *N* courts · *N* série(s) » à déplier, les courts et séries plus petits quatre par ligne — une décennie sans long déjà dépliée ; chaque affiche porte son titre puis son année dessous, vue en couleur avec sa note, sépia sinon, coin « Plex », coin « TV » sur une série, liseré coloré quand l'année est ouverte dans le Voyage.
- [ ] Sur cette page, toucher une affiche dont le film est dans le Voyage ouvre sa fiche du Voyage ; une autre ouvre une fiche simple : affiche, titre, année, réalisateur, mon état si vu, puis « Je l'ai vu », « Voir sur le Plex », « Demander sur Sir », « Introuvable »/son inverse — une série n'a ni « Je l'ai vu » ni formulaire, avec « Les séries se suivent dans Suivis » à la place.
- [ ] Le nom d'un réalisateur est touchable partout où il s'affiche (fiche d'un film du Voyage, écran de correction, carte de soirée de la Frise, fiche simple ci-dessus) : un seul crédit ouvre sa page directement, plusieurs ouvrent une feuille à choisir, aucun affiche un bandeau qui s'efface seul.
- [ ] Sur l'accueil, une seconde ligne « Ensuite · *Nom* » apparaît sous celle du Plex pour le réalisateur suivi qui a le plus de films vus parmi ceux qui en ont encore à voir, et une troisième pour la saga suivie dans le même cas ; chacune absente si aucune entité de sa source n'a de film à voir ; toucher l'affiche ouvre le formulaire pré-rempli.
- [ ] Sur une fiche (réalisateur ou saga), un appui long sur un film non vu ouvre « Marquer introuvable » / « Annuler » ; marquer le grise avec « introuvable » à droite (interrupteur désactivé) ou le fait disparaître (activé, par défaut) ; il ne porte plus jamais la pastille « à voir » ni un « Ensuite » de l'accueil.
- [ ] Un appui long sur un film déjà marqué ouvre « Le remettre à voir » ; le geste refait, le compte de la liste affiche « · N introuvables » tant qu'il en reste un.
- [ ] Marquer un film introuvable depuis une fiche de réalisateur, puis retrouver ce même film dans une saga qui le contient (ou l'inverse) : la marque s'y affiche aussi — c'est la même marque des deux côtés.
- [ ] Chercher une saga (par exemple « alien ») : les résultats affichent l'affiche ronde et le nom ; en suivre une, sa fiche liste ses films dans l'ordre de sortie ; ceux sans date de sortie n'y figurent pas.
- [ ] Sur la fiche d'une saga (jamais sur celle d'un réalisateur), « Ajouter un film » sous le nom ouvre la recherche existante ; choisir un film y revient avec « ajouté » à droite de son titre, à sa place chronologique, et « Ajouté à la saga » en snackbar ; un appui long dessus propose aussi « Retirer de la saga », qui le fait disparaître.
- [ ] Profil : sous les deux chiffres du haut, la carte « Bilan » — sept lignes (films vus dont cette année, séances en salle dont cette année, note moyenne, décennies couvertes, le plus ancien film vu, réalisateurs suivis et terminés, sagas idem), chacune en « … » un instant avant sa vraie valeur, jamais un chiffre qui saute d'une valeur à l'autre.
- [ ] Profil, « Importer Letterboxd » : choisir le ZIP de l'export (ou `diary.csv` seul) affiche « Import en cours… » puis le rapport — comptes, non reconnus avec leurs candidats, erreurs ; un ZIP sans `diary.csv` affiche le message qui le dit.
- [ ] Toucher un candidat non reconnu ouvre le formulaire avec la date et la note de sa ligne du fichier déjà remplies ; « Terminé » revient au profil, dont les deux chiffres du haut sont à jour.
- [ ] Ouvrir l'année en cours (brief du 21 septembre 2026, « l'année en étages ») : l'écran a le fond de son monde ; à la première visite, « *1895* s'écrit… ça prend une minute ou deux » sans aucune salle dessinée, puis le cartouche jauni montre l'ouverture repliée à trois lignes — « Lire la suite » la déplie et fait apparaître les faits.
- [ ] En tête de la fiche d'année (étape 5, « les récompenses ») : sous la profondeur, « *N* essentiels sur *M* · *N* salles complètes sur *M* », ou « Aucun essentiel encore » sans aucun essentiel connu ; dans le cartouche, le glyphe de récompense à côté du millésime, absent sans aucun film vu.
- [ ] Chaque salle affiche son titre, sa raison d'être, puis une étagère : les films vus en couleur avec leur note, les autres en sépia, une étiquette d'état sous chacun (« sur ton Plex », « demandé », « introuvable » ; rien pour « à demander ») ; un programme porte « *N* bobines · *M* min » en coin.
- [ ] En bout d'étagère, « En voir plus » ajoute trois à cinq films après un aller-retour (« La salle se remplit… » pendant l'attente) ; une salle épuisée montre une tuile grisée, inerte.
- [ ] En bas de l'année en cours seulement (brief du 21 septembre 2026, « le ticket »), une fois qu'un ticket a été gagné : « Ton ticket pour *1896* t'attend » avec « Utiliser » l'encaisse, avance l'année en cours et recharge la carte ; sans ticket mais un verdict négatif, « Pas encore mûre : *motif* » sans bouton ; sinon rien — absent sur une année ouverte ou verrouillée.
- [ ] Toucher une affiche ouvre sa fiche : affiche, titre, réalisateur, la salle et la raison en évidence, ma note si vu, et selon l'état « Voir sur le Plex » (absent sans lien), « Je l'ai vu », « Demander sur Sir » (passe à « demandé »), « Introuvable » ou « Le remettre à voir ». Une année verrouillée montre le carton « Prochainement », sans affiche.
- [ ] Le podium (brief du 21 septembre 2026), entre le cartouche et les salles : trois photogrammes sur un bout de pellicule, le n°1 plus grand au centre ; une marche vide est un cadre nu ; toucher une marche ouvre la feuille (« Retirer du podium » en tête si elle est occupée, puis mes films vus de l'année et mes programmes entièrement vus, l'occupant coché) ; un appui long sur une marche occupée la vide directement ; poser le n°1 met à jour l'affiche de l'année sur la carte du Voyage.
- [ ] Dans la fiche d'un film vu, « Mettre sur le podium » ouvre la feuille des trois marches (« Marche *N* · *occupant* » ou « libre »), la marche qui porte déjà ce film cochée ; en choisir une le pose, en retire l'ancien occupant s'il l'y trouvait déjà.
- [ ] Profil, sous le Bilan : la carte « Passeport » — « Aucun tampon encore », ou un tampon corail penché par décennie bouclée avec sa date et son titre de voyageur ; le toucher rejoue le générique de fin, qui défile tout seul et se ferme d'un geste. Ouvrir Profil en premier (avant la Frise) montre déjà les tampons — jamais vide en attendant un chargement ailleurs (décision 3 du brief du 21 septembre 2026, « les récompenses »).
- [ ] Le générique de fin, après la liste des films de la décennie : « *N* Palmes · *N* Lions · *N* Ours » (étape 5), absent si aucune de ses dix années n'a de récompense.
- [ ] Le ticket (brief du 21 septembre 2026) : après avoir noté un film de l'année en cours, le calque apparaît par-dessus l'écran courant dans la minute (rectangle papier penché, « ADMIS UNE PERSONNE », l'année, le motif) ; « Utiliser maintenant » avance l'année en cours et fait claquer le clap, « Garder dans le portefeuille » referme le calque sans y toucher ; dans les deux cas il ne se remontre plus.
- [ ] Profil, sous le Passeport : la carte « Portefeuille » — « Aucun ticket », ou les tickets non utilisés en premier avec « Utiliser », les compostés en dessous grisés et barrés, « utilisé le *date* » à la place du bouton.
- [ ] Toucher « Demander sur Sir » sur un essentiel à trouver : la pastille passe à « demandé ».
- [ ] Enregistrer un film : sous « Enregistré », la carte « Et pendant ce temps… » apparaît (ou « Le chroniqueur arrive… » puis, après un moment, le contexte) ; la fermer d'un geste. En correction d'un film déjà journalisé, la même carte apparaît en bas si son carton existe déjà.
- [ ] Sur la fiche d'un film vu du Voyage (brief du 21 septembre 2026, « la chronique et les salles »), « Ajouter à la chronique » devient « Le chroniqueur écrit… » après le tap, puis « Dans la chronique » avec le paragraphe affiché dessous ; l'ouverture dépliée de l'année montre ensuite ce même paragraphe, daté, à la suite des faits. Sur l'écran de correction d'un film dont l'année est en cours ou ouverte, le même bouton apparaît en bas ; sur un film d'une année verrouillée ou déjà creusée ailleurs, il est absent.
- [ ] En bas de la dernière étagère d'une année, « Ouvrir une nouvelle salle » ouvre « Quelle salle ? » (un champ, « Demander ») ; pendant l'écriture, une étagère fantôme pointillée la remplace ; une fois créée, la nouvelle étagère apparaît à sa place et le bouton revient ; un refus affiche son motif sous le bouton, une seule fois.
- [ ] Redemander une salle alors qu'une demande est déjà en cours : un bandeau avec le message du back, rien d'autre ne change à l'écran.
- [ ] Dans « Quelle salle ? » (brief du 22 septembre 2026, « les pistes ») : une pastille par piste au-dessus du champ, un tap la remplit dans le champ et affiche sa raison dessous ; retoucher le texte garde la piste envoyée ; « Demander » avec elle la fait disparaître de la liste tout de suite ; sans aucune piste, « D'autres pistes » dit « Le chroniqueur cherche… » puis affiche trois nouvelles pastilles.
- [ ] Ouvrir la correction d'un film déjà dans la chronique de son année : le bouton dit « Dans la chronique », paragraphe affiché dessous, dès l'ouverture de l'écran, sans passer par « Le chroniqueur écrit… ».
- [ ] Profil, sous le Portefeuille : la carte « Dépenses » — « Ce mois-ci : *X* centimes · *N* appels » ; un tap déplie les mois précédents, un par un, du plus récent au plus ancien ; un second tap replie.
- [ ] Dans l'année en cours (brief du 21 septembre 2026, « la séance »), entre le podium et les salles : « Composer une séance » ; après le tap, « Le chroniqueur compose… » puis la carte de soirée — long, court s'il y en a un, l'anecdote en italique — avec « Prendre », « Autre long », « Autre court », « Ignorer ».
- [ ] « Autre long »/« Autre court » ouvre une feuille sans attente (candidats déjà chargés, groupés par salle) ; choisir un candidat met la carte à jour tout de suite. « Prendre » étiquette la carte « Prise » et fait apparaître « Voir sur le Plex »/« Demander sur Sir » et « Je l'ai vu » sur chaque film, elle reste ainsi même après avoir vu le long ; « Ignorer » fait disparaître la carte et ramène tout de suite le bouton « Composer une séance », comme s'il n'y avait pas eu de séance.
- [ ] Sur l'accueil, « Ce soir · *Long* + *Court* » apparaît au-dessus d'« Ensuite » tant qu'une séance est prise ; toucher la ligne ouvre la fiche de l'année ; noter le long comme vu la fait disparaître au prochain passage sur l'accueil.
- [ ] Sur la fiche d'une année, sous la ligne du ticket : « Faire le carnet » passe à « Le carnet se fabrique… » (désactivé) puis à « Fabriqué le *date* · *N* pages » ; toucher cette ligne télécharge le PDF et l'ouvre dans une autre application (ou dit qu'aucune n'en sait ouvrir un, si aucune n'est installée) ; utiliser un ticket depuis la fiche, le calque ou le portefeuille propose ensuite « *1895* est bouclée, veux-tu son carnet ? » sur la Frise, « Oui » lance sa fabrication.
- [ ] Profil, sous les Dépenses : la carte « Carnets » liste mes carnets fabriqués (tap → ouvre le PDF) et les années en fabrication en ligne grisée « *année* · en fabrication » ; sans aucun carnet, « Aucun carnet pour l'instant ».
