package com.vincent.model

/** In-memory sample cellar so every screen renders with realistic content. */
object SampleData {

    val bottles: List<Bottle> = listOf(
        Bottle(
            id = "leoville-2016",
            domain = "Léoville-Barton",
            appellation = "Saint-Julien",
            color = WineColor.RED,
            category = WineCategory.BORDEAUX,
            vintage = "2016",
            price = 68,
            quantity = 3,
            rating = 4.2,
            cellarSpot = "B3",
            provenance = "Saint-Julien, FR",
            merchant = "Lavinia · Paris 8e",
            purchaseDate = "12 mars 2024",
            occasion = "Cave de garde",
            favorite = true,
            pairings = listOf("Viande rouge", "Agneau rôti", "Comté affiné", "Gibier"),
            drinkFrom = 2024,
            drinkTo = 2038,
            drinkNow = 0.46f,
            tastingNotes = "Cassis, cèdre et tabac blond. Tanins fermes et soyeux, " +
                "finale longue sur le graphite. Carafer 1 h.",
            source = AddSource.VOICE,
            addedLabel = "09:32",
        ),
        Bottle(
            id = "chablis-2021",
            domain = "Chablis 1er Cru",
            appellation = "Bourgogne",
            color = WineColor.WHITE,
            category = WineCategory.BOURGOGNE,
            vintage = "2021",
            price = 29,
            quantity = 6,
            rating = 4.1,
            cellarSpot = "B5",
            provenance = "Chablis, FR",
            merchant = "Nicolas",
            purchaseDate = "4 févr. 2024",
            occasion = "Tous les jours",
            pairings = listOf("Huîtres", "Poisson grillé", "Chèvre frais"),
            drinkFrom = 2023,
            drinkTo = 2028,
            drinkNow = 0.5f,
            source = AddSource.MANUAL,
            addedLabel = "Dim.",
        ),
        Bottle(
            id = "bandol-2022",
            domain = "Bandol Rosé",
            appellation = "Provence",
            color = WineColor.ROSE,
            category = WineCategory.PROVENCE,
            vintage = "2022",
            price = 18,
            quantity = 2,
            rating = 4.6,
            cellarSpot = "C1",
            provenance = "Bandol, FR",
            merchant = "Caviste local",
            purchaseDate = "20 mai 2024",
            occasion = "Fête",
            pairings = listOf("Cuisine provençale", "Grillades", "Tapas"),
            drinkFrom = 2022,
            drinkTo = 2025,
            drinkNow = 0.6f,
            source = AddSource.PHOTO,
            addedLabel = "Lun.",
        ),
        Bottle(
            id = "champagne-nm",
            domain = "Champagne Brut",
            appellation = "Reims",
            color = WineColor.SPARKLING,
            category = WineCategory.CHAMPAGNE,
            vintage = "NM",
            price = 42,
            quantity = 4,
            rating = 4.0,
            cellarSpot = "D1",
            provenance = "Reims, FR",
            merchant = "Lavinia · Paris 8e",
            purchaseDate = "1 déc. 2023",
            occasion = "À offrir",
            favorite = true,
            pairings = listOf("Apéritif", "Fruits de mer"),
            drinkFrom = 2023,
            drinkTo = 2027,
            drinkNow = 0.5f,
            source = AddSource.SCAN,
            addedLabel = "08:50",
        ),
        Bottle(
            id = "margaux-2015",
            domain = "Château Margaux",
            appellation = "Margaux",
            color = WineColor.RED,
            category = WineCategory.BORDEAUX,
            vintage = "2015",
            price = 620,
            quantity = 2,
            rating = 4.8,
            cellarSpot = "B4",
            provenance = "Margaux, FR",
            merchant = "Lavinia · Paris 8e",
            purchaseDate = "13 juin 2024",
            occasion = "Cave de garde",
            favorite = true,
            pairings = listOf("Côte de bœuf", "Truffe", "Gibier à plume"),
            drinkFrom = 2025,
            drinkTo = 2045,
            drinkNow = 0.3f,
            source = AddSource.VOICE,
            addedLabel = "09:32",
        ),
    )

    val favorites: List<Bottle> get() = bottles.filter { it.favorite }

    val recent: List<Bottle> = listOf(
        bottles.first { it.id == "margaux-2015" },
        bottles.first { it.id == "champagne-nm" },
        bottles.first { it.id == "bandol-2022" },
        bottles.first { it.id == "chablis-2021" },
    )

    val breakdown: List<ColorBreakdown> = listOf(
        ColorBreakdown(WineColor.RED, 58),
        ColorBreakdown(WineColor.WHITE, 22),
        ColorBreakdown(WineColor.ROSE, 12),
        ColorBreakdown(WineColor.SPARKLING, 8),
    )

    const val totalBottles = 142
    const val totalReferences = 38
    const val readyToDrink = 9
    const val estimatedValueEur = 4280
    const val averagePriceEur = 30
    const val addedThisMonth = 12

    /** A 5×6 rack grid for "Cave A", matching the mockup. */
    val rackA: List<RackCell> = buildRack()

    private fun buildRack(): List<RackCell> {
        fun c(row: String, color: WineColor, cat: WineCategory, vintage: String, price: Int, sel: Boolean = false) =
            RackCell(row, true, color, cat, vintage, price, sel)
        fun empty(row: String) = RackCell(row, false)
        return listOf(
            c("A", WineColor.RED, WineCategory.BORDEAUX, "16", 68),
            c("A", WineColor.RED, WineCategory.RHONE, "18", 42),
            c("A", WineColor.RED, WineCategory.RHONE, "19", 24),
            c("A", WineColor.WHITE, WineCategory.BOURGOGNE, "21", 29),
            empty("A"),
            c("A", WineColor.RED, WineCategory.BORDEAUX, "15", 55),

            c("B", WineColor.RED, WineCategory.BORDEAUX, "17", 33),
            c("B", WineColor.RED, WineCategory.BORDEAUX, "16", 48),
            c("B", WineColor.RED, WineCategory.BORDEAUX, "16", 68, sel = true),
            c("B", WineColor.WHITE, WineCategory.LOIRE, "22", 19),
            c("B", WineColor.WHITE, WineCategory.BOURGOGNE, "21", 29),
            empty("B"),

            c("C", WineColor.ROSE, WineCategory.PROVENCE, "22", 18),
            c("C", WineColor.ROSE, WineCategory.PROVENCE, "23", 15),
            c("C", WineColor.RED, WineCategory.RHONE, "18", 27),
            c("C", WineColor.RED, WineCategory.BOURGOGNE, "19", 39),
            empty("C"),
            empty("C"),

            c("D", WineColor.SPARKLING, WineCategory.CHAMPAGNE, "NM", 42),
            c("D", WineColor.SPARKLING, WineCategory.CHAMPAGNE, "14", 58),
            c("D", WineColor.WHITE, WineCategory.BOURGOGNE, "20", 22),
            c("D", WineColor.RED, WineCategory.BORDEAUX, "18", 31),
            c("D", WineColor.RED, WineCategory.RHONE, "19", 26),
            c("D", WineColor.RED, WineCategory.BORDEAUX, "17", 44),

            c("E", WineColor.RED, WineCategory.BOURGOGNE, "16", 35),
            empty("E"),
            c("E", WineColor.WHITE, WineCategory.BOURGOGNE, "19", 95),
            c("E", WineColor.ROSE, WineCategory.PROVENCE, "23", 16),
            empty("E"),
            empty("E"),
        )
    }
}
