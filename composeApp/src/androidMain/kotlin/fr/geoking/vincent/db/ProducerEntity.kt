package fr.geoking.vincent.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import fr.geoking.vincent.model.Producer

@Entity(tableName = "producers")
data class ProducerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val region: String,
    val country: String,
    val website: String,
    val email: String,
    val phone: String,
)

fun ProducerEntity.toProducer(): Producer = Producer(
    id = id,
    name = name,
    region = region,
    country = country,
    website = website,
    email = email,
    phone = phone,
)

fun Producer.toEntity(): ProducerEntity = ProducerEntity(
    id = id,
    name = name,
    region = region,
    country = country,
    website = website,
    email = email,
    phone = phone,
)
