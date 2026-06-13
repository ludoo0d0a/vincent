package com.vincent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.room.Room
import com.vincent.data.Cellar
import com.vincent.db.RoomCellarRepository
import com.vincent.db.VincentDatabase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            VincentDatabase::class.java,
            "vincent.db",
        ).build()
        val repository = RoomCellarRepository(db.bottleDao())
        MainScope().launch { Cellar.bootstrap(repository) }

        setContent {
            App()
        }
    }
}
