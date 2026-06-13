# Vincent — cave à vin (Kotlin Multiplatform)

App de gestion de cave à vin « type block », jeu de mots sur *vin*. Direction
visuelle *modern minimal*, accent lie-de-vin, statuts par couleur de vin
(rouge / blanc / rosé / pétillant). Reprend les features de PLOC et Vivino, UI
inspirée de myBar.

Toute l'UI vit dans **`commonMain`** (Compose Multiplatform) — donc prête à
être partagée avec iOS/desktop ; seule la cible **Android** est câblée pour
l'instant.

## Stack

| | |
|---|---|
| Langage | Kotlin 2.1.20 (KSP 2.1.20-1.0.31) |
| UI | Compose Multiplatform 1.7.3 (Material 3) |
| Build | AGP 8.7.3 · Gradle 8.11.1 |
| Toolchain | JDK 17 (build via `jvmToolchain(17)` + daemon via `gradle/gradle-daemon-jvm.properties`, auto-provisionné par foojay) |
| SDK | min 24 · compile/target 35 |

## Lancer

Le wrapper Gradle binaire (`gradle-wrapper.jar` + `gradlew`) n'est pas versionné
ici. Deux options :

**Android Studio (recommandé)** — *Ladybug* ou plus récent :
ouvrir le dossier `vincent/`, laisser synchroniser, puis ▶ sur la config
`composeApp`.

**Ligne de commande** — générer le wrapper une fois puis builder :

```bash
gradle wrapper --gradle-version 8.11.1
./gradlew :composeApp:installDebug    # sur un appareil/émulateur connecté
```

## Écrans

Bottom-nav Material à 4 onglets (Accueil · Casiers · Bouteilles · Recherche),
FAB d'ajout, plus les surfaces atteintes par navigation :

| # | Écran | Fichier |
|---|---|---|
| 1 | Tableau de bord (valeur, donut couleurs) | `screens/DashboardScreen.kt` |
| 2 | Casiers — sélecteur de mode **Couleur · Prix · Millésime · Catégorie** | `screens/CellarScreen.kt` |
| 3 | Liste / grille des bouteilles | `screens/BottlesScreen.kt` |
| 4 | Détail — accords mets-vin, fenêtre de garde, provenance/caviste/occasion | `screens/BottleDetailScreen.kt` |
| 5 | Ajout — scan / photo / voix | `screens/AddScreen.kt` |
| 6 | Recherche & filtres (couleur, prix, provenance, caviste, occasion) | `screens/SearchScreen.kt` |
| 7 | Connexion Google (cloud + favoris) | `screens/LoginScreen.kt` |
| 8 | Compte & favoris | `screens/AccountScreen.kt` |
| 9 | Dernières bouteilles (par date + source) | `screens/RecentScreen.kt` |

## Architecture

```
composeApp/src/
├── androidMain/com/vincent/
│   ├── MainActivity.kt     # entrée Android : build Room + Cellar.bootstrap
│   └── db/                 # BottleEntity, BottleDao, VincentDatabase, RoomCellarRepository
└── commonMain/com/vincent/
    ├── App.kt              # navigation racine (Scaffold + bottom bar + overlays)
    ├── theme/Theme.kt      # palette lie-de-vin + Material3 + typo
    ├── model/              # Models.kt (WineColor, Bottle, RackCell…) + SampleData.kt
    ├── data/               # Cellar (store réactif) + CellarRepository (seam persistance)
    ├── ui/                 # WineBottle (Canvas), ColorTag, Stars, ScreenHeader, VCard
    └── screens/            # les 9 écrans
```

## Notes

- **État** : `data/Cellar.kt` est la source de vérité unique (Compose snapshot
  state), seedée par `SampleData`. Les écrans sont **réactifs** : ajouter une
  bouteille (Ajout → Confirmer), servir −1 / +1, (dé)favoriser et filtrer
  modifient réellement les données, et le tableau de bord / les favoris / les
  dernières bouteilles se recalculent. L'API est volontairement étroite
  (`addBottle` / `adjustQuantity` / `toggleFavorite` + lectures dérivées) pour
  qu'une implémentation persistante remplace les listes en mémoire sans toucher
  à l'UI.
- **Persistance : Room (câblé).** `CellarRepository` (commonMain) est implémenté
  par `RoomCellarRepository` (androidMain) sur une base Room
  (`db/VincentDatabase`, entité `BottleEntity`, DAO suspend). `MainActivity`
  construit la base et appelle `Cellar.bootstrap(repo)` : au 1er lancement le
  seed est persisté, ensuite les bouteilles enregistrées remplacent le seed, et
  chaque ajout / service / favori est écrit en *write-through*. Room tourne sur
  la cible Android via KSP (`kspAndroid`) ; les enums sont stockés par nom, donc
  pas de `TypeConverter`.
- **Connexion Google (câblée).** API moderne **Credential Manager** + *Sign in
  with Google*. Le déclencheur est un `@Composable expect fun
  rememberGoogleSignIn(...)` (commonMain) dont l'`actual` Android
  (`data/GoogleSignIn.android.kt`) ouvre le sélecteur de compte ; le compte
  obtenu alimente `data/Auth.kt` (état observable), l'écran de connexion bascule
  vers l'app, et l'écran Compte affiche le vrai nom/email + déconnexion.
  ⚠️ **À renseigner** : la constante `WEB_CLIENT_ID` dans
  `GoogleSignIn.android.kt` — l'ID client **OAuth Web** (Google Cloud Console /
  Firebase). Sans lui, le flux échoue à l'exécution (le code compile).
- **Import / Export CSV (câblé).** `data/CsvFormat.kt` sérialise la cave (format
  Vincent, round-trip) et parse un CSV entrant avec **mapping de colonnes
  tolérant** : détecte Vincent / Vivino / PLOC / tableur via les en-têtes (FR/EN)
  et mappe couleur, millésime, prix, région, note… L'accès fichier passe par le
  Storage Access Framework (`data/FileTransfer*.kt`, `expect/actual`). Écran
  **Importer / Exporter** accessible depuis le Compte.
- **Reste à brancher** : sync cloud effective des données Room vers le compte,
  reconnaissance d'étiquette (ML Kit) et dictée (Speech-to-Text) côté
  `androidMain`.
- **Bouteilles** : dessinées vectoriellement (`ui/WineBottle`) — capsule, corps,
  étiquette — donc nettes à toute taille, sans asset bitmap. Remplaçables par de
  vraies photos plus tard.
- **Connexion Google** : écran UI uniquement ; câbler Credential Manager /
  Google Sign-In côté `androidMain` pour l'authentification réelle.
- L'icône de lanceur n'est pas fournie (le thème système `Material.Light` est
  utilisé) — ajouter un `ic_launcher` dans `androidMain/res` pour la prod.
