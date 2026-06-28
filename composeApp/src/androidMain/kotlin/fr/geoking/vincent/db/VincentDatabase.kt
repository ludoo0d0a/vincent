package fr.geoking.vincent.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BottleEntity::class,
        RackEntity::class,
        TastingEntity::class,
        ProducerEntity::class,
        SupplierEntity::class,
        XWineEntity::class,
    ],
    version = 7,
    exportSchema = false
)
abstract class VincentDatabase : RoomDatabase() {
    abstract fun bottleDao(): BottleDao
    abstract fun rackDao(): RackDao
    abstract fun tastingDao(): TastingDao
    abstract fun producerDao(): ProducerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun xWineDao(): XWineDao
}
