package fr.geoking.vincent.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [BottleEntity::class], version = 1, exportSchema = false)
abstract class VincentDatabase : RoomDatabase() {
    abstract fun bottleDao(): BottleDao
}
