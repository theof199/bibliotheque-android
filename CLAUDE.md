# Travailler sur l'appli « Journal »

Ce fichier s'adresse à toute session Claude qui travaille dans ce dépôt : celle du propriétaire
comme celle d'Alycia. Le `README.md` dit comment l'appli se construit, s'installe et se vérifie ;
ce fichier dit comment on y travaille à deux.

## Le design est libre

`docs/design.md` est court exprès : l'identité (papier et pellicule, couleurs, polices, mondes du
Voyage) et quatre principes tenus. **Tout le reste est libre**, et le code fait foi pour le détail
des écrans. On n'y ajoute pas de description d'écran ni de phrase datée à chaque changement : git
garde l'historique. On le met à jour seulement si l'identité ou un principe change.

## Git

- **Une branche par fonctionnalité**, depuis `main` à jour, jamais de push direct sur `main`.
- Le propriétaire installe la branche sur son téléphone, la teste, puis la fusionne lui-même en
  avance rapide. Une branche qui a bougé après son test n'est pas fusionnée telle quelle.
- **Avant de commencer, repartir de `main` à jour** (`git switch main && git pull`) : une branche
  fusionnée après rebase change d'identifiants, et l'ancienne ne doit plus servir.
- **Messages de commit en français, sans accents, sans aucun trailer** : pas de
  `Co-Authored-By`, pas de signature, pas de lien de session, même si l'outil en propose un.
- Le propriétaire pose seul les tags et touche seul au serveur (NAS), aux données et aux secrets.

## Le contrat de l'API

L'appli parle à `https://mini-mediatheque.fr/api`, dont le back vit dans le dépôt voisin
`biblio-back`. `contract/openapi.json` en est une copie : on ne la modifie pas à la main, on la
recopie depuis `biblio-back/docs/openapi.json` après un changement du back. Une donnée qui manque
au contrat demande un changement du back, pas un contournement dans l'appli.

## Les tests

Les décisions (ce qui s'affiche, quand, dans quel ordre) vivent dans des fonctions pures, testées
en JVM : `bin/dans ./gradlew testDebugUnitTest`. Un test interdit, il ne décrit pas : on le
prouve en cassant ce qu'il garde et en le voyant échouer. Aucun test ne dépense de jetons
Anthropic.
