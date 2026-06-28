package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.*

@Entity(tableName = "racks")
data class RackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val cols: Int,
    val rows: Int,
    val staggered: Boolean,
    val cellsData: String,
    val arImagePath: String? = null,
    val arCalibrationData: String? = null,
    val arMode: String? = null,
    val arAnchorData: String? = null,
)

private const val CELL_SEP = "" // Record Separator
private const val FIELD_SEP = "" // Unit Separator

fun RackEntity.toRack(): Rack = Rack(
    id = id,
    name = name,
    cols = cols,
    rows = rows,
    staggered = staggered,
    cells = cellsData.split(CELL_SEP).map { it.toRackCell() },
    arImagePath = arImagePath,
    arCalibration = arCalibrationData?.toArCalibration(),
    arMode = arMode?.let { runCatching { ArMode.valueOf(it) }.getOrNull() } ?: ArMode.PHOTO,
    arAnchor = arAnchorData?.toArAnchor(),
)

fun Rack.toEntity(): RackEntity = RackEntity(
    id = id,
    name = name,
    cols = cols,
    rows = rows,
    staggered = staggered,
    cellsData = cells.joinToString(CELL_SEP) { it.toData() },
    arImagePath = arImagePath,
    arCalibrationData = arCalibration?.toData(),
    arMode = arMode.name,
    arAnchorData = arAnchor?.toData(),
)

private fun String.toRackCell(): RackCell {
    val fields = split(FIELD_SEP)
    return RackCell(
        row = fields.getOrNull(0) ?: "",
        occupied = fields.getOrNull(1) == "1",
        color = fields.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { WineColor.valueOf(it) },
        category = fields.getOrNull(3)?.takeIf { it.isNotEmpty() }?.let { WineCategory.valueOf(it) },
        vintage = fields.getOrNull(4)?.takeIf { it.isNotEmpty() },
        price = fields.getOrNull(5)?.toIntOrNull(),
    )
}

private fun RackCell.toData(): String = listOf(
    row,
    if (occupied) "1" else "0",
    color?.name ?: "",
    category?.name ?: "",
    vintage ?: "",
    price?.toString() ?: "",
).joinToString(FIELD_SEP)

private fun String.toArCalibration(): RackArCalibration {
    val points = split(FIELD_SEP).chunked(2).mapNotNull {
        val x = it.getOrNull(0)?.toFloatOrNull()
        val y = it.getOrNull(1)?.toFloatOrNull()
        if (x != null && y != null) NormPoint(x, y) else null
    }
    return RackArCalibration(points)
}

private fun RackArCalibration.toData(): String =
    corners.joinToString(FIELD_SEP) { "${it.x}${FIELD_SEP}${it.y}" }

private fun String.toArAnchor(): RackArAnchor? {
    val f = split(FIELD_SEP)
    val markerId = f.getOrNull(0)?.takeIf { it.isNotEmpty() } ?: return null
    val nums = (1..10).map { f.getOrNull(it)?.toFloatOrNull() }
    if (nums.any { it == null }) return null
    return RackArAnchor(
        markerId = markerId,
        markerWidthMeters = nums[0]!!,
        tx = nums[1]!!, ty = nums[2]!!, tz = nums[3]!!,
        qx = nums[4]!!, qy = nums[5]!!, qz = nums[6]!!, qw = nums[7]!!,
        gridWidthMeters = nums[8]!!,
        gridHeightMeters = nums[9]!!,
        tlImagePath = f.getOrNull(11)?.takeIf { it.isNotEmpty() },
        brImagePath = f.getOrNull(12)?.takeIf { it.isNotEmpty() },
    )
}

private fun RackArAnchor.toData(): String = listOf(
    markerId,
    markerWidthMeters.toString(),
    tx.toString(), ty.toString(), tz.toString(),
    qx.toString(), qy.toString(), qz.toString(), qw.toString(),
    gridWidthMeters.toString(),
    gridHeightMeters.toString(),
    tlImagePath ?: "",
    brImagePath ?: "",
).joinToString(FIELD_SEP)
