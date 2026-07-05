# Todo — Vincent

## Fonctionnalités

- [ ] Carte des origines
  - [ ] OpenWines — contours AOC/IGP en GeoJSON (dept 44 seulement pour l'instant) comme source de la carte des appellations. Licence ODbL, attribution OSM/INAO obligatoire. Données cartographiques (pas un catalogue de vins) → usage carte/liste d'appellations, pas recherche.

- [ ] Wine Enthusiast (catalogue archive)
  - [ ] Provider `TEXT_SEARCH` secondaire via Worker + D1, alimenté par le dataset Kaggle WineMag (`winemag-data-130k-v2.csv`, ~130k lignes) — pas d'API officielle Wine Enthusiast.
  - [ ] Route Worker `GET /v1/catalog/search?q=…` (même auth App Check + Firebase que grapeminds) ; nouveau `WineMagProvider` dans `ProductLookup.android.kt`, **après** grapeminds pour garder l'enrichissement quand grapeminds matche.
  - [ ] Add : préremplissage domaine/appellation/région ; pas d'appel `enrich()` sur les picks WineMag ; badge source « archive 2017 ».
  - **Limitations**
    - Données **figées en juin 2017** (scraping WineEnthusiast) — pas de mises à jour, pas de nouveaux millésimes ni vins récents.
    - Doublons connus dans le CSV (~130k → ~97k uniques après dédup) ; qualité variable.
    - Pas de fenêtre de garde, profil aromatique ni accords mets-vin (contrairement à grapeminds `ENRICH`).
    - Points critiques 80–100 ≠ note utilisateur Vincent (/5) — affichage informatif seulement, ne pas écraser `Bottle.rating`.
    - Licence / usage commercial du dataset Kaggle à valider avant publication Play Store ; UI : « catalogue archive (2017) », pas « Wine Enthusiast officiel ».
    - Pas de barcode, pas de prix fiable (prix 2017 USD).
