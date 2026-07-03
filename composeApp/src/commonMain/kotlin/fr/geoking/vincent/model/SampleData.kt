package fr.geoking.vincent.model

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
            agingPotential = 22,
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
            agingPotential = 7,
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
            agingPotential = 3,
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
            agingPotential = 5,
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
            agingPotential = 30,
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

    /** Seed racks for the cellar screen — editable at runtime via the Racks store. */
    fun seedRacks(): List<Rack> = listOf(caveA(), caveB(), refrigeree(), casierX())

    private data class Spec(val color: WineColor, val cat: WineCategory, val vintage: String, val price: Int)

    private fun c(row: String, color: WineColor, cat: WineCategory, vintage: String, price: Int, sel: Boolean = false) =
        RackCell(row, true, color, cat, vintage, price, sel)
    private fun e(row: String) = RackCell(row, false)

    // Cave A — 6×6 grid.
    private fun caveA(): Rack = Rack(
        "Cave A", 6, 6, false,
        listOf(
            c("A", WineColor.RED, WineCategory.BORDEAUX, "16", 68), c("A", WineColor.RED, WineCategory.RHONE, "18", 42), c("A", WineColor.RED, WineCategory.RHONE, "19", 24),
            c("A", WineColor.WHITE, WineCategory.BOURGOGNE, "21", 29), e("A"), c("A", WineColor.RED, WineCategory.BORDEAUX, "15", 55),

            c("B", WineColor.RED, WineCategory.BORDEAUX, "17", 33), c("B", WineColor.RED, WineCategory.BORDEAUX, "16", 48), c("B", WineColor.RED, WineCategory.BORDEAUX, "16", 68, sel = true),
            c("B", WineColor.WHITE, WineCategory.LOIRE, "22", 19), c("B", WineColor.WHITE, WineCategory.BOURGOGNE, "21", 29), e("B"),

            c("C", WineColor.ROSE, WineCategory.PROVENCE, "22", 18), c("C", WineColor.ROSE, WineCategory.PROVENCE, "23", 15), c("C", WineColor.RED, WineCategory.RHONE, "18", 27),
            c("C", WineColor.RED, WineCategory.BOURGOGNE, "19", 39), e("C"), e("C"),

            c("D", WineColor.SPARKLING, WineCategory.CHAMPAGNE, "NM", 42), c("D", WineColor.SPARKLING, WineCategory.CHAMPAGNE, "14", 58), c("D", WineColor.WHITE, WineCategory.BOURGOGNE, "20", 22),
            c("D", WineColor.RED, WineCategory.BORDEAUX, "18", 31), c("D", WineColor.RED, WineCategory.RHONE, "19", 26), c("D", WineColor.RED, WineCategory.BORDEAUX, "17", 44),

            c("E", WineColor.RED, WineCategory.BOURGOGNE, "16", 35), e("E"), c("E", WineColor.WHITE, WineCategory.BOURGOGNE, "19", 95),
            c("E", WineColor.ROSE, WineCategory.PROVENCE, "23", 16), e("E"), c("E", WineColor.RED, WineCategory.RHONE, "20", 28),

            c("F", WineColor.RED, WineCategory.BORDEAUX, "19", 37), c("F", WineColor.WHITE, WineCategory.LOIRE, "20", 21), c("F", WineColor.RED, WineCategory.BOURGOGNE, "18", 52),
            e("F"), c("F", WineColor.ROSE, WineCategory.PROVENCE, "22", 17), c("F", WineColor.SPARKLING, WineCategory.CHAMPAGNE, "NM", 39),
        ),
    )

    // Cave B — 4×8, bottles stored "en quinconce" (staggered rows).
    private val mixB = listOf(
        Spec(WineColor.SPARKLING, WineCategory.CHAMPAGNE, "NM", 45),
        Spec(WineColor.WHITE, WineCategory.BOURGOGNE, "21", 32),
        Spec(WineColor.WHITE, WineCategory.LOIRE, "22", 19),
        Spec(WineColor.RED, WineCategory.BORDEAUX, "16", 58),
        Spec(WineColor.RED, WineCategory.RHONE, "18", 27),
        Spec(WineColor.ROSE, WineCategory.PROVENCE, "23", 16),
    )

    private fun caveB(): Rack {
        val cols = 4; val rows = 8
        val cells = (0 until cols * rows).map { i ->
            val rl = rowLabel(i / cols)
            if (i % 5 == 4) e(rl) else mixB[i % mixB.size].let { c(rl, it.color, it.cat, it.vintage, it.price) }
        }
        return Rack("Cave B", cols, rows, true, cells)
    }

    // Casier en X — 2×2 squares (4×4 bottles), four bottles per X-bin en quinconce.
    private fun casierX(): Rack {
        val cols = 4; val rows = 4
        val mix = listOf(
            Spec(WineColor.RED, WineCategory.BORDEAUX, "17", 52),
            Spec(WineColor.RED, WineCategory.RHONE, "19", 28),
            Spec(WineColor.WHITE, WineCategory.BOURGOGNE, "21", 31),
            Spec(WineColor.ROSE, WineCategory.PROVENCE, "23", 16),
            Spec(WineColor.SPARKLING, WineCategory.CHAMPAGNE, "NM", 44),
        )
        val cells = (0 until cols * rows).map { i ->
            val rl = rowLabel(i / cols)
            if (i % 7 == 6) e(rl) else mix[i % mix.size].let { c(rl, it.color, it.cat, it.vintage, it.price) }
        }
        return Rack("Casier X", cols, rows, false, cells, format = RackFormat.X)
    }

    // Réfrigérée — small 4×3, mostly whites/champagne.
    private fun refrigeree(): Rack {
        val cols = 4; val rows = 3
        val chilled = listOf(
            Spec(WineColor.SPARKLING, WineCategory.CHAMPAGNE, "NM", 49),
            Spec(WineColor.WHITE, WineCategory.BOURGOGNE, "20", 24),
            Spec(WineColor.ROSE, WineCategory.PROVENCE, "23", 17),
        )
        val cells = (0 until cols * rows).map { i ->
            val rl = rowLabel(i / cols)
            if (i == 5 || i == 10) e(rl) else chilled[i % chilled.size].let { c(rl, it.color, it.cat, it.vintage, it.price) }
        }
        return Rack("Réfrigérée", cols, rows, false, cells)
    }
}
