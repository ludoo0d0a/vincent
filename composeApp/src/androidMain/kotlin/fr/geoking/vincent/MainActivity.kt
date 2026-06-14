package fr.geoking.vincent

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.room.Room
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.db.RoomCellarRepository
import fr.geoking.vincent.db.VincentDatabase
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val appUpdateManager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(applicationContext) }

    // Flexible update: as soon as the download finishes, auto-complete it (restarts the app).
    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            Toast.makeText(this, "Mise à jour téléchargée — redémarrage…", Toast.LENGTH_SHORT).show()
            appUpdateManager.completeUpdate()
        }
    }

    // Launches Google's update confirmation dialog; the result needs no handling
    // (cancel = nothing; the install listener drives the rest).
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

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

        appUpdateManager.registerListener(installListener)
        checkForUpdate()

        setContent {
            App()
        }
    }

    /** On startup: if Play has an update, show the (flexible) update popup. */
    private fun checkForUpdate() {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // If a flexible update finished downloading while the app was backgrounded, finish it.
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.installStatus() == InstallStatus.DOWNLOADED) appUpdateManager.completeUpdate()
        }
    }

    override fun onDestroy() {
        appUpdateManager.unregisterListener(installListener)
        super.onDestroy()
    }
}
