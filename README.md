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
    # ADB_DEVICE=ip:port dans .env                  # le port de « Débogage sans fil »

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

`bin/logs` filtre `logcat` sur le pid de l'application, parce que `logcat`
n'imprime pas le nom du paquet : il faut donc que l'application tourne au moment
où on l'appelle. Après un plantage, le processus est mort et sa trace ne sortira
plus par ce chemin ; le tampon des plantages, lui, la garde :

    bin/dans sh -c 'adb connect "$ADB_DEVICE" && adb -s "$ADB_DEVICE" logcat -b crash'

(les guillemets simples comptent : `ADB_DEVICE` n'existe que dans le conteneur,
le développer sur le poste ne rendrait rien.)

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

**Le script qui crée cette clé, `bin/keystore`, n'existe pas encore** : il
arrive avec la mise en service. Jusque-là, seule la variante `debug` se
construit.

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

## Vérifier

    bin/dans ./gradlew testDebugUnitTest

Ce qui ne se teste pas sur la JVM se vérifie sur le téléphone :

- [ ] Au lancement, aucun flash clair : écran de démarrage, fenêtre et premier écran sont noirs.
