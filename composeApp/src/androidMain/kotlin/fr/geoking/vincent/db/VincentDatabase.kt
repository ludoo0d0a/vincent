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
    ],
    version = 10,
    exportSchema = false
)
abstract class VincentDatabase : RoomDatabase() {
    companion object {
        val MIGRATION_9_10 = object : androidx.room.migration.Migration(9, 10) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bottles ADD COLUMN alcoholLevel REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE bottles ADD COLUMN sugarLevel TEXT NOT NULL DEFAULT 'SEC'")
            }
        }
    }

    abstract fun bottleDao(): BottleDao
    abstract fun rackDao(): RackDao
    abstract fun tastingDao(): TastingDao
    abstract fun producerDao(): ProducerDao
    abstract fun supplierDao(): SupplierDao
}
