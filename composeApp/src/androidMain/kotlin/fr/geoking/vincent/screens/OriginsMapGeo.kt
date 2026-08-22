package fr.geoking.vincent.screens

import org.json.JSONArray
import org.json.JSONObject
import org.osmdroid.util.GeoPoint

internal data class GeoJsonFeature(
    val key: String,
    val label: String,
    val points: List<GeoPoint>,
)

internal object GeoJsonParser {

    fun parseFeatures(json: String): List<GeoJsonFeature> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        return when (root.optString("type")) {
            "FeatureCollection" -> {
                val features = root.optJSONArray("features") ?: return emptyList()
                buildList {
                    for (i in 0 until features.length()) {
                        parseFeature(features.optJSONObject(i))?.let { add(it) }
                    }
                }
            }
            "Feature" -> listOfNotNull(parseFeature(root))
            "Polygon" -> listOfNotNull(
                GeoJsonFeature("", "", parsePolygonCoords(root.optJSONArray("coordinates")?.optJSONArray(0))),
            )
            else -> emptyList()
        }
    }

    fun parsePoints(json: String): List<GeoPoint> {
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return emptyList()
        val coords = when (root.optString("type")) {
            "Polygon" -> root.optJSONArray("coordinates")?.optJSONArray(0)
            "Feature" -> {
                val geom = root.optJSONObject("geometry") ?: return emptyList()
                if (geom.optString("type") != "Polygon") return emptyList()
                geom.optJSONArray("coordinates")?.optJSONArray(0)
            }
            "FeatureCollection" -> {
                val feature = root.optJSONArray("features")?.optJSONObject(0) ?: return emptyList()
                val geom = feature.optJSONObject("geometry") ?: return emptyList()
                geom.optJSONArray("coordinates")?.optJSONArray(0)
            }
            else -> null
        } ?: return emptyList()
        return parsePolygonCoords(coords)
    }

    fun centroid(points: List<GeoPoint>): GeoPoint? {
        if (points.isEmpty()) return null
        val lat = points.map { it.latitude }.average()
        val lon = points.map { it.longitude }.average()
        return GeoPoint(lat, lon)
    }

    private fun parseFeature(feature: JSONObject?): GeoJsonFeature? {
        if (feature == null) return null
        val props = feature.optJSONObject("properties") ?: JSONObject()
        val geom = feature.optJSONObject("geometry") ?: return null
        val points = when (geom.optString("type")) {
            "Polygon" -> parsePolygonCoords(geom.optJSONArray("coordinates")?.optJSONArray(0))
            else -> emptyList()
        }
        if (points.isEmpty()) return null
        return GeoJsonFeature(
            key = props.optString("key"),
            label = props.optString("label"),
            points = points,
        )
    }

    private fun parsePolygonCoords(ring: JSONArray?): List<GeoPoint> = buildList {
        if (ring == null) return@buildList
        for (i in 0 until ring.length()) {
            val pair = ring.optJSONArray(i) ?: continue
            if (pair.length() < 2) continue
            add(GeoPoint(pair.getDouble(1), pair.getDouble(0)))
        }
    }
}
