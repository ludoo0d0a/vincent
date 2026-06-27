package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.Supplier

@Entity(tableName = "suppliers")
data class SupplierEntity(
    @PrimaryKey val id: String,
    val name: String,
    val type: String,
    val website: String,
    val email: String,
    val phone: String,
)

fun SupplierEntity.toSupplier(): Supplier = Supplier(
    id = id,
    name = name,
    type = type,
    website = website,
    email = email,
    phone = phone,
)

fun Supplier.toEntity(): SupplierEntity = SupplierEntity(
    id = id,
    name = name,
    type = type,
    website = website,
    email = email,
    phone = phone,
)
