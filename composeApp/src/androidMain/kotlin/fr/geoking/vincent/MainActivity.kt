package fr.geoking.vincent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.android.play.core.install.model.InstallStatus
import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import fr.geoking.tools.inappupdate.UpdateNotificationSpec
import fr.geoking.vincent.data.Appellations
import fr.geoking.vincent.data.Cellar
import fr.geoking.vincent.data.CloudSyncRepos
import fr.geoking.vincent.data.Grapes
import fr.geoking.vincent.data.Producers
import fr.geoking.vincent.data.Racks
import fr.geoking.vincent.data.Regions
import fr.geoking.vincent.data.Settings
import fr.geoking.vincent.data.Suppliers
import fr.geoking.vincent.data.Tastings
import fr.geoking.vincent.data.UpdateState
import fr.geoking.vincent.data.Updater
import fr.geoking.vincent.data.bootstrapAuth
import fr.geoking.vincent.data.cloudSyncOnReady
import fr.geoking.vincent.data.initCloudSync
import fr.geoking.vincent.data.loadBundledOriginCentroids
import fr.geoking.vincent.data.loadBundledPopularGrapes
import fr.geoking.vincent.db.RoomAppellationRepository
import fr.geoking.vincent.db.RoomCellarRepository
import fr.geoking.vincent.db.RoomGrapeRepository
import fr.geoking.vincent.db.RoomProducerRepository
import fr.geoking.vincent.db.RoomRackRepository
import fr.geoking.vincent.db.RoomRegionRepository
import fr.geoking.vincent.db.RoomSupplierRepository
import fr.geoking.vincent.db.RoomTastingRepository
import fr.geoking.vincent.db.VincentDatabase
import fr.geoking.tools.inappupdate.CheckFeedback
import fr.geoking.vincent.ui.UpdateAvailableDialog
import fr.geoking.vincent.ui.UpdateCheckFeedbackDialog
import fr.geoking.vincent.update.InAppUpdateHelper
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

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

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `regions` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `country` TEXT NOT NULL, `description` TEXT NOT NULL, PRIMARY KEY(`id`))")
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

    private val inAppUpdateHelper by lazy {
        InAppUpdateHelper(
            context = applicationContext,
            notificationSpec = UpdateNotificationSpec(
                channelId = "vincent_updates",
                channelName = getString(R.string.update_available_title),
                smallIcon = R.drawable.ic_notification,
                title = getString(R.string.update_available_title),
                message = getString(R.string.update_available_message),
                launchActivityClass = MainActivity::class.java,
            ),
        )
    }

    private val updateResultLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { /* cancel / failure: no-op */ }

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
        ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, VincentDatabase.MIGRATION_9_10, MIGRATION_10_11, VincentDatabase.MIGRATION_11_12, VincentDatabase.MIGRATION_12_13, VincentDatabase.MIGRATION_13_14, VincentDatabase.MIGRATION_14_15).build()
        val repository = RoomCellarRepository(db.bottleDao())
        val rackRepo = RoomRackRepository(db.rackDao())
        val tastingRepo = RoomTastingRepository(db.tastingDao())
        val producerRepo = RoomProducerRepository(db.producerDao())
        val supplierRepo = RoomSupplierRepository(db.supplierDao())
        val regionRepo = RoomRegionRepository(db.regionDao())
        val grapeRepo = RoomGrapeRepository(db.grapeDao())
        val appellationRepo = RoomAppellationRepository(db.appellationDao())

        Settings.init(applicationContext)
        fr.geoking.vincent.ai.GemmaModel.init(applicationContext)
        fr.geoking.vincent.ai.GemmaLlm.init(applicationContext)
        val syncRepos = CloudSyncRepos(
            cellar = repository,
            racks = rackRepo,
            tastings = tastingRepo,
            producers = producerRepo,
            suppliers = supplierRepo,
        )
        initCloudSync(applicationContext, syncRepos)
        MainScope().launch {
            val shouldSeed = !Settings.demoDataSeeded && repository.loadAll().isEmpty() && rackRepo.loadAll().isEmpty()
            Cellar.bootstrap(repository, shouldSeed)
            Racks.bootstrap(rackRepo, shouldSeed)
            Tastings.bootstrap(tastingRepo)
            Producers.bootstrap(producerRepo)
            Suppliers.bootstrap(supplierRepo)
            Regions.bootstrap(regionRepo)
            Grapes.bootstrap(grapeRepo) {
                if (Settings.demoDataSeeded) emptyList() else loadBundledPopularGrapes()
            }
            Appellations.bootstrap(appellationRepo)
            loadBundledOriginCentroids()
            if (shouldSeed) {
                Settings.setDemoDataSeeded(true)
            }
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

        inAppUpdateHelper.consumeLaunchIntent(intent)
        Updater.triggerUpdate = { manual -> inAppUpdateHelper.checkForUpdate(manual) }
        inAppUpdateHelper.checkForUpdate()

        setContent {
            val updateAvailable by inAppUpdateHelper.updateAvailable.collectAsState()
            val autoStartUpdate by inAppUpdateHelper.autoStartUpdate.collectAsState()
            val installStatus by inAppUpdateHelper.installStatus.collectAsState()
            val checkFeedback by inAppUpdateHelper.checkFeedback.collectAsState()

            LaunchedEffect(updateAvailable, autoStartUpdate) {
                if (autoStartUpdate && updateAvailable != null) {
                    inAppUpdateHelper.maybeAutoStartUpdate(updateResultLauncher)
                }
            }

            // Drive the commonMain download banner from the shared helper status.
            LaunchedEffect(installStatus) {
                val inProgress = installStatus == InstallStatus.PENDING ||
                    installStatus == InstallStatus.DOWNLOADING ||
                    installStatus == InstallStatus.INSTALLING ||
                    installStatus == InstallStatus.DOWNLOADED
                if (inProgress) UpdateState.onDownloading(null) else UpdateState.onIdle()
            }

            App()

            val dialogUpdate = updateAvailable?.takeUnless { autoStartUpdate }
            dialogUpdate?.let { info ->
                UpdateAvailableDialog(
                    onCancel = { inAppUpdateHelper.dismissUpdate() },
                    onUpdate = { inAppUpdateHelper.startUpdate(info, updateResultLauncher) },
                )
            }

            when (val feedback = checkFeedback) {
                is CheckFeedback.UpToDate -> {
                    UpdateCheckFeedbackDialog(
                        isError = false,
                        onDismiss = { inAppUpdateHelper.resetCheckFeedback() },
                    )
                }
                is CheckFeedback.Error -> {
                    UpdateCheckFeedbackDialog(
                        isError = true,
                        errorMessage = feedback.message,
                        onDismiss = { inAppUpdateHelper.resetCheckFeedback() },
                    )
                }
                CheckFeedback.None -> Unit
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        inAppUpdateHelper.consumeLaunchIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (inAppUpdateHelper.installStatus.value == InstallStatus.DOWNLOADED) {
            inAppUpdateHelper.completeUpdate()
        }
    }

    override fun onDestroy() {
        inAppUpdateHelper.unregister()
        Updater.triggerUpdate = null
        super.onDestroy()
    }
}
