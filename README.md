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
| Langage | Kotlin 2.1.0 |
| UI | Compose Multiplatform 1.7.3 (Material 3) |
| Build | AGP 8.7.3 · Gradle 8.11.1 |
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
├── androidMain/            # MainActivity + manifeste (entrée Android)
└── commonMain/com/vincent/
    ├── App.kt              # navigation racine (Scaffold + bottom bar + overlays)
    ├── theme/Theme.kt      # palette lie-de-vin + Material3 + typo
    ├── model/              # Models.kt (WineColor, Bottle, RackCell…) + SampleData.kt
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
  qu'une implémentation **persistante (Room ou SQLDelight)** remplace les listes
  en mémoire sans toucher à l'UI.
- **Reste à brancher** : persistance disque, sync cloud, reconnaissance
  d'étiquette (ML Kit) et dictée (Speech-to-Text) côté `androidMain`.
- **Bouteilles** : dessinées vectoriellement (`ui/WineBottle`) — capsule, corps,
  étiquette — donc nettes à toute taille, sans asset bitmap. Remplaçables par de
  vraies photos plus tard.
- **Connexion Google** : écran UI uniquement ; câbler Credential Manager /
  Google Sign-In côté `androidMain` pour l'authentification réelle.
- L'icône de lanceur n'est pas fournie (le thème système `Material.Light` est
  utilisé) — ajouter un `ic_launcher` dans `androidMain/res` pour la prod.
