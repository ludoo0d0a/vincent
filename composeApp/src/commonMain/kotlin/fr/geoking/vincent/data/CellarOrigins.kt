package fr.geoking.vincent.data

import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.WineColor

data class OriginAggregate(
    val origin: ResolvedOrigin,
    val bottleCount: Int,
    val bottles: List<Bottle>,
)

object CellarOrigins {

    fun aggregate(colorFilter: WineColor? = null): List<OriginAggregate> {
        val filtered = Cellar.matching(colorFilter)
        return filtered
            .groupBy { OriginGeocoder.resolveOrigin(it).key }
            .map { (_, group) ->
                val origin = OriginGeocoder.resolveOrigin(group.first())
                OriginAggregate(
                    origin = origin,
                    bottleCount = group.sumOf { it.quantity.coerceAtLeast(1) },
                    bottles = group,
                )
            }
            .sortedByDescending { it.bottleCount }
    }

    fun distinctOriginCount(): Int =
        Cellar.bottles.map { OriginGeocoder.resolveOrigin(it).key }.distinct().size

    fun bottlesForOrigin(originKey: String, colorFilter: WineColor? = null): List<Bottle> =
        Cellar.matching(colorFilter).filter { OriginGeocoder.resolveOrigin(it).key == originKey }
}
