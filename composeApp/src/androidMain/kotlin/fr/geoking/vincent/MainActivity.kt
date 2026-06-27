package fr.geoking.vincent

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.play.core.appupdate.AppUpdateManager
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import fr.geoking.vincent.data.*
import fr.geoking.vincent.db.*
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.getString
import vincent.composeapp.generated.resources.*

private val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bottles ADD COLUMN imageUri TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bottles ADD COLUMN photoBottleUri TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bottles ADD COLUMN photoBackUri TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE racks ADD COLUMN arMode TEXT")
        db.execSQL("ALTER TABLE racks ADD COLUMN arAnchorData TEXT")
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `racks` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `cols` INTEGER NOT NULL, `rows` INTEGER NOT NULL, `staggered` INTEGER NOT NULL, `cellsData` TEXT NOT NULL, `arImagePath` TEXT, `arCalibrationData` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `tastings` (`id` TEXT NOT NULL, `bottleId` TEXT, `wineName` TEXT NOT NULL, `date` TEXT NOT NULL, `rating` REAL NOT NULL, `notes` TEXT NOT NULL, `color` TEXT, `vintage` TEXT, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `producers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `region` TEXT NOT NULL, `country` TEXT NOT NULL, `website` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, PRIMARY KEY(`id`))")
        db.execSQL("CREATE TABLE IF NOT EXISTS `suppliers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `type` TEXT NOT NULL, `website` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, PRIMARY KEY(`id`))")
    }
}

class MainActivity : ComponentActivity() {

    private val appUpdateManager: AppUpdateManager by lazy { AppUpdateManagerFactory.create(applicationContext) }

    // Flexible update: as soon as the download finishes, auto-complete it (restarts the app).
    private val installListener = InstallStateUpdatedListener { state ->
        if (state.installStatus() == InstallStatus.DOWNLOADED) {
            MainScope().launch {
                Toast.makeText(this@MainActivity, getString(Res.string.update_downloaded), Toast.LENGTH_SHORT).show()
                appUpdateManager.completeUpdate()
            }
        }
    }

    // Launches Google's update confirmation dialog; the result needs no handling
    // (cancel = nothing; the install listener drives the rest).
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Edge-to-edge icon contrast the modern (non-deprecated) way: dark status/nav
        // icons over our light background — no setStatusBarColor/setNavigationBarColor.
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }

        val db = Room.databaseBuilder(
            applicationContext,
            VincentDatabase::class.java,
            "vincent.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5).build()
        val repository = RoomCellarRepository(db.bottleDao())
        val rackRepo = RoomRackRepository(db.rackDao())
        val tastingRepo = RoomTastingRepository(db.tastingDao())
        val producerRepo = RoomProducerRepository(db.producerDao())
        val supplierRepo = RoomSupplierRepository(db.supplierDao())

        Settings.init(applicationContext)
        MainScope().launch {
            Cellar.bootstrap(repository)
            Racks.bootstrap(rackRepo)
            Tastings.bootstrap(tastingRepo)
            Producers.bootstrap(producerRepo)
            Suppliers.bootstrap(supplierRepo)
        }

        // App Check attests calls to the Gemini proxy Worker. Play Integrity in
        // release; the debug provider in debug builds (register the logged token).
        // The debug factory ships only in debug (debugImplementation), so it is
        // loaded reflectively to keep its class off the release classpath.
        val appCheckFactory: AppCheckProviderFactory = if (BuildConfig.DEBUG) {
            Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                .getMethod("getInstance")
                .invoke(null) as AppCheckProviderFactory
        } else {
            PlayIntegrityAppCheckProviderFactory.getInstance()
        }
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckFactory)

        bootstrapAuth()

        appUpdateManager.registerListener(installListener)
        Updater.triggerUpdate = { manual -> checkForUpdate(manual) }
        checkForUpdate(manual = false)

        setContent {
            App()
        }
    }

    /** On startup: if Play has an update, show the (flexible) update popup. */
    private fun checkForUpdate(manual: Boolean) {
        appUpdateManager.appUpdateInfo.addOnSuccessListener { info ->
            if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE &&
                info.isUpdateTypeAllowed(AppUpdateType.FLEXIBLE)
            ) {
                appUpdateManager.startUpdateFlowForResult(
                    info,
                    updateLauncher,
                    AppUpdateOptions.newBuilder(AppUpdateType.FLEXIBLE).build(),
                )
            } else if (manual) {
                MainScope().launch {
                    val msg = if (info.updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE) {
                        getString(Res.string.update_ineligible)
                    } else {
                        getString(Res.string.update_up_to_date)
                    }
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
                }
            }
        }.addOnFailureListener {
            if (manual) {
                MainScope().launch {
                    Toast.makeText(this@MainActivity, getString(Res.string.update_error), Toast.LENGTH_SHORT).show()
                }
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
        Updater.triggerUpdate = null
        super.onDestroy()
    }
}
