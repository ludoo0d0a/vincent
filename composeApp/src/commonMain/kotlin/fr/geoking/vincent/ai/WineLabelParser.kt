package fr.geoking.vincent.ai

/**
 * Best-effort wine fields extracted from free text (OCR lines or a voice transcript).
 * [isConfident] gates whether Gemini is needed as a fallback.
 */
data class WineLabelFields(
    val domain: String = "",
    val appellation: String = "",
    val region: String = "",
    val vintage: String = "NM",
    /** Token for colour mapping: rouge / blanc / rose / petillant (or empty). */
    val colorHint: String = "",
    val categoryHint: String = "",
    val alcohol: Double = 0.0,
    val sugarHint: String = "",
    val grapes: List<String> = emptyList(),
    val rawText: String = "",
) {
    /** Enough signal to skip Gemini for common FR/EN labels and spoken titles. */
    val isConfident: Boolean
        get() {
            if (domain.isBlank()) return false
            val hasVintage = vintage.length == 4 && vintage != "NM"
            val hasAlcohol = alcohol > 0.0
            val hasColor = colorHint.isNotBlank()
            val hasAppellation = appellation.isNotBlank() &&
                !appellation.equals(domain, ignoreCase = true)
            return hasVintage || hasAlcohol || hasColor || hasAppellation
        }

    /** Query for catalogue TEXT_SEARCH. */
    val searchQuery: String
        get() = listOf(domain, appellation, vintage.takeIf { it != "NM" })
            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
            .distinct()
            .joinToString(" ")
}

/**
 * Deterministic wine-label / voice-transcript parser (no network, no LLM).
 * Tuned for French and English printed labels and short spoken titles.
 *
 * Accented literals use Unicode escapes so the source stays ASCII-safe.
 */
object WineLabelParser {

    private const val A_CIRC = '\u00E2'
    private const val E_ACUTE = '\u00E9'
    private const val E_GRAVE = '\u00E8'
    private const val O_CIRC = '\u00F4'

    private val vintageRe = Regex("""\b((?:19|20)\d{2})\b""")
    private val alcoholRe = Regex(
        """(?i)(\d{1,2}(?:[.,]\d{1,2})?)\s*%\s*(?:vol\.?|alc\.?)?|""" +
            """(?:alc\.?|alcohol|alcool)\.?\s*[:=]?\s*(\d{1,2}(?:[.,]\d{1,2})?)\s*%?""",
    )
    private val domainPrefixRe = Regex(
        "(?i)^(ch(?:[a${A_CIRC}]teau)|domaine|clos|maison|cave|mas|chateau|winery|estate|weingut)\\b",
    )
    private val noiseLineRe = Regex(
        "(?i)^(product of|produit de|mis en bouteille|contains sulphites|contient des sulfites|" +
            "france|italy|espagne|spain|germany|portugal|app[${E_ACUTE}e]llation|aoc|aop|igp|vin de|" +
            "grand cru|premier cru|1er cru|cru class[${E_ACUTE}e]|mill[${E_ACUTE}e]sime|vintage|" +
            "\\d+\\s*ml|\\d+\\s*cl|e\\s*\\d+|lot\\b|l\\.\\s*\\d).*\$",
    )

    private val colorRules = listOf(
        "p${E_ACUTE}tillant" to listOf(
            "p${E_ACUTE}tillant", "petillant", "sparkling", "mousseux",
            "cr${E_ACUTE}mant", "cremant", "champagne", "cava", "prosecco",
        ),
        "ros${E_ACUTE}" to listOf("ros${E_ACUTE}", "rose", "rosato", "blush"),
        "blanc" to listOf("blanc", "white", "bianco", "weiss"),
        "rouge" to listOf("rouge", "red", "rosso", "rotwein", "rot "),
    )

    private val sugarRules = listOf(
        "moelleux" to listOf("moelleux", "liquoreux", "doux", "sweet", "dessert"),
        "demi-sec" to listOf("demi-sec", "demi sec", "off-dry", "off dry", "semi-dry"),
        "sec" to listOf("sec", "dry", "brut", "extra brut"),
    )

    private val regionKeywords = listOf(
        "bordeaux" to "Bordeaux",
        "m${E_ACUTE}doc" to "M${E_ACUTE}doc",
        "medoc" to "M${E_ACUTE}doc",
        "saint-julien" to "Saint-Julien",
        "saint-${E_ACUTE}milion" to "Saint-${E_ACUTE}milion",
        "saint-emilion" to "Saint-${E_ACUTE}milion",
        "pauillac" to "Pauillac",
        "margaux" to "Margaux",
        "pessac" to "Pessac-L${E_ACUTE}ognan",
        "graves" to "Graves",
        "sauternes" to "Sauternes",
        "bourgogne" to "Bourgogne",
        "burgundy" to "Bourgogne",
        "chablis" to "Chablis",
        "beaune" to "Beaune",
        "gevrey" to "Gevrey-Chambertin",
        "vosne" to "Vosne-Roman${E_ACUTE}e",
        "meursault" to "Meursault",
        "puligny" to "Puligny-Montrachet",
        "chassagne" to "Chassagne-Montrachet",
        "c${O_CIRC}te-r${O_CIRC}tie" to "C${O_CIRC}te-R${O_CIRC}tie",
        "cote-rotie" to "C${O_CIRC}te-R${O_CIRC}tie",
        "hermitage" to "Hermitage",
        "ch${A_CIRC}teauneuf" to "Ch${A_CIRC}teauneuf-du-Pape",
        "chateauneuf" to "Ch${A_CIRC}teauneuf-du-Pape",
        "c${O_CIRC}tes du rh${O_CIRC}ne" to "C${O_CIRC}tes du Rh${O_CIRC}ne",
        "cotes du rhone" to "C${O_CIRC}tes du Rh${O_CIRC}ne",
        "rh${O_CIRC}ne" to "Rh${O_CIRC}ne",
        "rhone" to "Rh${O_CIRC}ne",
        "provence" to "Provence",
        "bandol" to "Bandol",
        "loire" to "Loire",
        "sancerre" to "Sancerre",
        "vouvray" to "Vouvray",
        "muscadet" to "Muscadet",
        "champagne" to "Champagne",
        "alsace" to "Alsace",
        "languedoc" to "Languedoc",
        "roussillon" to "Roussillon",
        "beaujolais" to "Beaujolais",
        "rioja" to "Rioja",
        "chianti" to "Chianti",
        "barolo" to "Barolo",
        "toscana" to "Toscana",
        "tuscany" to "Tuscany",
        "napa" to "Napa Valley",
        "sonoma" to "Sonoma",
    )

    private val grapeNames = listOf(
        "Cabernet Sauvignon", "Cabernet Franc", "Merlot", "Pinot Noir", "Pinot Gris", "Pinot Grigio",
        "Pinot Blanc", "Chardonnay", "Sauvignon Blanc", "Syrah", "Shiraz", "Grenache", "Garnacha",
        "Mourv${E_GRAVE}dre", "Mourvedre", "Tempranillo", "Sangiovese", "Nebbiolo", "Riesling",
        "Gew\u00FCrztraminer", "Gewurztraminer", "Viognier", "Chenin Blanc", "Semillon", "S${E_ACUTE}millon",
        "Malbec", "Petit Verdot", "Gamay", "Zinfandel", "Carignan", "Cinsault", "Ugni Blanc",
        "Muscat", "Moscato", "Vermentino", "Aligot${E_ACUTE}", "Aligote",
    )

    fun parse(raw: String): WineLabelFields {
        val text = raw.replace('\u00a0', ' ').trim()
        if (text.isEmpty()) return WineLabelFields(rawText = raw)

        val lines = text.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }

        val blob = lines.joinToString("\n")
        val lower = blob.lowercase()

        val vintage = vintageRe.findAll(blob)
            .map { it.groupValues[1] }
            .firstOrNull { y -> y.toIntOrNull()?.let { it in 1950..2035 } == true }
            ?: "NM"

        val alcohol = alcoholRe.find(blob)?.let { m ->
            val n = m.groupValues.drop(1).firstOrNull { it.isNotBlank() } ?: return@let 0.0
            n.replace(',', '.').toDoubleOrNull()?.takeIf { it in 5.0..22.0 } ?: 0.0
        } ?: 0.0

        val colorHint = colorRules.firstOrNull { (_, keys) -> keys.any { it in lower } }?.first.orEmpty()
        val sugarHint = sugarRules.firstOrNull { (_, keys) -> keys.any { it in lower } }?.first.orEmpty()

        val region = regionKeywords
            .firstOrNull { (key, _) -> key in lower }
            ?.second
            .orEmpty()

        val categoryHint = region

        val grapes = grapeNames.filter { name ->
            lower.contains(name.lowercase())
        }.distinct()

        val usableLines = lines.filterNot { noiseLineRe.containsMatchIn(it) || isMostlyNumeric(it) }
        val domain = pickDomain(usableLines, blob)
        val appellation = pickAppellation(usableLines, domain, region)

        return WineLabelFields(
            domain = domain,
            appellation = appellation.ifBlank { region },
            region = region,
            vintage = vintage,
            colorHint = colorHint,
            categoryHint = categoryHint,
            alcohol = alcohol,
            sugarHint = sugarHint,
            grapes = grapes,
            rawText = text,
        )
    }

    private fun isMostlyNumeric(line: String): Boolean {
        val letters = line.count { it.isLetter() }
        val digits = line.count { it.isDigit() }
        return letters == 0 || (digits > letters && line.length < 12)
    }

    private fun pickDomain(lines: List<String>, blob: String): String {
        lines.firstOrNull { domainPrefixRe.containsMatchIn(it) && it.length in 4..80 }
            ?.let { return cleanName(it) }

        val colorStrip = Regex(
            "(?i)\\b(rouge|blanc|ros[${E_ACUTE}e]|red|white|sparkling|p[${E_ACUTE}e]tillant|petillant)\\b",
        )
        val spoken = blob.lineSequence().firstOrNull()?.let { first ->
            val cut = first
                .replace(vintageRe, " ")
                .replace(alcoholRe, " ")
                .replace(colorStrip, " ")
                .replace(Regex("""\s+"""), " ")
                .trim()
            cut.takeIf { it.length in 3..80 }
        }
        if (!spoken.isNullOrBlank() && domainPrefixRe.containsMatchIn(spoken)) {
            return cleanName(spoken)
        }

        lines.firstOrNull { line ->
            val letters = line.count { it.isLetter() }
            letters >= 3 && line.length in 3..60 && !vintageRe.matches(line)
        }?.let { return cleanName(it) }

        return spoken?.let { cleanName(it) }.orEmpty()
    }

    private fun pickAppellation(lines: List<String>, domain: String, region: String): String {
        if (region.isNotBlank()) {
            lines.firstOrNull {
                it.contains(region, ignoreCase = true) &&
                    !it.equals(domain, ignoreCase = true) &&
                    it.length <= 60
            }?.let { return cleanName(it) }
            return region
        }
        return lines
            .asSequence()
            .filter {
                !it.equals(domain, ignoreCase = true) &&
                    it.count { c -> c.isLetter() } >= 3 &&
                    it.length in 3..50 &&
                    !domainPrefixRe.containsMatchIn(it)
            }
            .firstOrNull()
            ?.let { cleanName(it) }
            .orEmpty()
    }

    private fun cleanName(s: String): String =
        s.trim().trim('\u00B7', '-', '\u2013', '\u2014', ',', '.', ':', ';')
            .replace(Regex("""\s+"""), " ")
}
