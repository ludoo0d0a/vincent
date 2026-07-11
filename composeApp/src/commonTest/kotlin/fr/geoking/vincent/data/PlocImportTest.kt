package fr.geoking.vincent.data

import fr.geoking.vincent.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlocImportTest {

    @Test
    fun testPlocBottlesImport() {
        val csv = """
"Nom du vin";"Millésime";"Couleur";"Stock";"Stock (en cave)";"Producteur";"Pays";"Région";"Appellation";"Cépages";"Format de bouteille";"Classement";"Cuvée";"Degré d'alcool";"Référence";"Tags";"Apogée";"T° de service";"Estimation";"Note";"Commentaires";"IdVin";"IdContact";"IdDocument";"IdDocumentCouverture"
"Albert Besombes Bourgueil L'Attire-Bouchon";2023;"Rouge";0;0;"Albert Besombes ";"France";"Vallée de la Loire";"Bourgueil";"Cabernet Franc";"Bouteille";"";"";12,5;"";"";;16;4;;"";"559588af-510a-41b8-b432-cb034b5b45bd";"8801851d-acc5-4598-bfa8-980c7c4aee34";"5b64f573-aef8-4d0f-87b9-47a82db5c219";""
        """.trimIndent()

        val result = CsvFormat.parse(csv)
        assertEquals("PLOC", result.source)
        assertEquals(CsvFormat.ImportType.BOTTLES, result.type)
        assertEquals(1, result.bottles.size)
        val b = result.bottles[0]
        assertEquals("Albert Besombes Bourgueil L'Attire-Bouchon", b.domain)
        assertEquals(2023, b.vintage.toInt())
        assertEquals(WineColor.RED, b.color)
        assertEquals(12.5, b.alcoholLevel)
        assertTrue("Cabernet Franc" in b.grapes)
    }

    @Test
    fun testPlocTastingsImport() {
        val csv = """
"Date";"Nom du vin";"Millésime";"Lieu";"Note";"EmotiPLOC";"Couleur";"Personnes présentes";"Plats";"Commentaires";"IdVin"
"12/03/2016";"Mission St. Vincent Réserve Bordeaux";2014;"Paris";;"";"#C9193A";"";"";"Un commentaire";""
        """.trimIndent()

        val result = CsvFormat.parse(csv)
        assertEquals("PLOC", result.source)
        assertEquals(CsvFormat.ImportType.TASTINGS, result.type)
        assertEquals(1, result.tastings.size)
        val t = result.tastings[0]
        assertEquals("Mission St. Vincent Réserve Bordeaux", t.wineName)
        assertEquals("12/03/2016", t.date)
        assertEquals("Paris", t.place)
        assertEquals("Un commentaire", t.notes)
    }

    @Test
    fun testPlocRacksImport() {
        val csv = """
"Nom";"Ligne";"Colonne";"Nom du vin";"Millésime";"IdVin"
"Frigo ";1;1;"";;""
"Frigo ";1;4;"";;""
"La Sommelière";1;1;"Dopff & Irion Alsace Pinot Gris";2022;"7e758c9a"
"La Sommelière";2;1;"Château De Marsan";2021;"1e543a33"
        """.trimIndent()

        val result = CsvFormat.parse(csv)
        assertEquals("PLOC", result.source)
        assertEquals(CsvFormat.ImportType.RACKS, result.type)
        assertEquals(2, result.racks.size)

        val frigo = result.racks.find { it.name.trim() == "Frigo" }!!
        assertEquals(4, frigo.cols)
        assertEquals(1, frigo.rows)

        val sommeliere = result.racks.find { it.name == "La Sommelière" }!!
        assertEquals(1, sommeliere.cols)
        assertEquals(2, sommeliere.rows)
        assertTrue(sommeliere.cells[0].occupied)
        assertEquals("2022", sommeliere.cells[0].vintage)
        assertTrue(sommeliere.cells[1].occupied)
        assertEquals("2021", sommeliere.cells[1].vintage)
    }

    @Test
    fun testPlocRacksImportWithEmptyAlphabeticCells() {
        val csv = """
"Nom";"Ligne";"Colonne";"Nom du vin";"Millésime";"IdVin"
"Ma cave a vin";10;F;"Moscato Dezzani";;"db2d1ccd-826b-48df-9c11-d5dbcd30c215"
"Ma cave a vin";10;G;"";;""
"Ma cave a vin";10;H;"";;""
"Ma cave a vin";10;I;"";;""
"Ma cave a vin";10;J;"";;""
        """.trimIndent()

        val result = CsvFormat.parse(csv)
        assertEquals("PLOC", result.source)
        assertEquals(CsvFormat.ImportType.RACKS, result.type)
        assertEquals(1, result.racks.size)

        val rack = result.racks[0]
        assertEquals("Ma cave a vin", rack.name)
        assertTrue(rack.cols >= 10, "cols should be at least 10, was ${rack.cols}")
        assertTrue(rack.rows >= 10, "rows should be at least 10, was ${rack.rows}")

        // Row 10 is index 9, Col F is index 5 (0-indexed)
        val idxF = 9 * rack.cols + 5
        assertTrue(rack.cells[idxF].occupied)

        // Col G, H, I, J should be unoccupied but their coordinate indexes should exist
        val idxG = 9 * rack.cols + 6
        val idxH = 9 * rack.cols + 7
        val idxI = 9 * rack.cols + 8
        val idxJ = 9 * rack.cols + 9
        assertTrue(!rack.cells[idxG].occupied)
        assertTrue(!rack.cells[idxH].occupied)
        assertTrue(!rack.cells[idxI].occupied)
        assertTrue(!rack.cells[idxJ].occupied)
    }

    @Test
    fun testPlocProducersImport() {
        val csv = """
"Nom";"Contact principal";"Adresse";"Complément";"Code postal";"Ville";"Pays";"Email";"Site web";"Téléphone";"Portable";"Fax";"Commentaires"
"Albert Besombes ";"";"24, rue Jules-Amiot ";"";"49404";"Saumur";"France";"emilien.boulfray@uapl.fr";"";"02 41 50 23 23";"";"";""
        """.trimIndent()

        val result = CsvFormat.parse(csv)
        assertEquals("PLOC", result.source)
        assertEquals(CsvFormat.ImportType.PRODUCERS, result.type)
        assertEquals(1, result.producers.size)
        val p = result.producers[0]
        assertEquals("Albert Besombes", p.name)
        assertEquals("Saumur", p.region)
        assertEquals("France", p.country)
        assertEquals("emilien.boulfray@uapl.fr", p.email)
        assertEquals("02 41 50 23 23", p.phone)
    }
}
