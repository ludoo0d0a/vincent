package fr.geoking.vincent

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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

private val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `xwines` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`type` TEXT NOT NULL, `grapes` TEXT NOT NULL, `country` TEXT NOT NULL, " +
                "`region` TEXT NOT NULL, `winery` TEXT NOT NULL, PRIMARY KEY(`id`))",
        )
    }
}

private val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE racks ADD COLUMN format TEXT NOT NULL DEFAULT 'GRID'")
        db.execSQL("ALTER TABLE racks ADD COLUMN staggerOffset INTEGER NOT NULL DEFAULT 0")
    }
}

// Rich wine detail from grapeminds: description, pairing prose, grapes, flavor profile, maturity.
private val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE bottles ADD COLUMN description TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bottles ADD COLUMN pairingNotes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bottles ADD COLUMN grapes TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bottles ADD COLUMN flavorProfile TEXT NOT NULL DEFAULT ''")
        db.execSQL("ALTER TABLE bottles ADD COLUMN maturity TEXT NOT NULL DEFAULT ''")
    }
}

private val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `xwines`")
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

    // Flexible update: show a non-blocking download banner while it downloads, then
    // auto-complete it (restarts the app) as soon as the download finishes.
    private val installListener = InstallStateUpdatedListener { state ->
        when (state.installStatus()) {
            InstallStatus.PENDING -> UpdateState.onDownloading(null)

            InstallStatus.DOWNLOADING -> {
                val total = state.totalBytesToDownload()
                val fraction = if (total > 0) state.bytesDownloaded().toFloat() / total else null
                UpdateState.onDownloading(fraction)
            }

            InstallStatus.DOWNLOADED -> {
                // Keep the banner up; the app restarts almost immediately.
                UpdateState.onDownloading(1f)
                MainScope().launch {
                    Toast.makeText(this@MainActivity, getString(Res.string.update_downloaded), Toast.LENGTH_SHORT).show()
                    appUpdateManager.completeUpdate()
                }
            }

            InstallStatus.FAILED, InstallStatus.CANCELED -> UpdateState.onIdle()

            else -> {}
        }
    }

    // Launches Google's update confirmation dialog; the result needs no handling
    // (cancel = nothing; the install listener drives the rest).
    private val updateLauncher =
        registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Force dark status/nav icons (light bar appearance) over our light background,
        // regardless of the device theme. SystemBarStyle.light keeps the bars transparent
        // for edge-to-edge and is re-applied correctly across configuration changes,
        // unlike a manual WindowInsetsController tweak set before setContent.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(
            applicationContext,
            VincentDatabase::class.java,
            "vincent.db",
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, VincentDatabase.MIGRATION_9_10).build()
        val repository = RoomCellarRepository(db.bottleDao())
        val rackRepo = RoomRackRepository(db.rackDao())
        val tastingRepo = RoomTastingRepository(db.tastingDao())
        val producerRepo = RoomProducerRepository(db.producerDao())
        val supplierRepo = RoomSupplierRepository(db.supplierDao())

        Settings.init(applicationContext)
        val syncRepos = CloudSyncRepos(
            cellar = repository,
            racks = rackRepo,
            tastings = tastingRepo,
            producers = producerRepo,
            suppliers = supplierRepo,
        )
        initCloudSync(applicationContext, syncRepos)
        MainScope().launch {
            Cellar.bootstrap(repository)
            Racks.bootstrap(rackRepo)
            Tastings.bootstrap(tastingRepo)
            Producers.bootstrap(producerRepo)
            Suppliers.bootstrap(supplierRepo)
            cloudSyncOnReady()
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
