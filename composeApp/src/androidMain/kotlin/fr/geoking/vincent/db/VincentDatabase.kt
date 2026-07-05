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
        RegionEntity::class,
    ],
    version = 14,
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

        val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bottles ADD COLUMN agingPotential INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_12_13 = object : androidx.room.migration.Migration(12, 13) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE bottles ADD COLUMN addedAt INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_13_14 = object : androidx.room.migration.Migration(13, 14) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tastings ADD COLUMN place TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    abstract fun bottleDao(): BottleDao
    abstract fun rackDao(): RackDao
    abstract fun tastingDao(): TastingDao
    abstract fun producerDao(): ProducerDao
    abstract fun supplierDao(): SupplierDao
    abstract fun regionDao(): RegionDao
}
