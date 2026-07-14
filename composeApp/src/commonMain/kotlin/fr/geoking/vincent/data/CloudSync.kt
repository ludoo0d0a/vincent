package fr.geoking.vincent.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import fr.geoking.vincent.FeatureFlags
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Producer
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.Supplier
import fr.geoking.vincent.model.Tasting

/** Repositories needed for a full cloud ↔ local merge. */
data class CloudSyncRepos(
    val cellar: CellarRepository,
    val racks: RackRepository,
    val tastings: TastingRepository,
    val producers: ProducerRepository,
    val suppliers: SupplierRepository,
)

/** Observable cloud-sync status for the account screen. */
object CloudSync {
    var syncing by mutableStateOf(false)
    var lastSyncedAt by mutableStateOf<Long?>(null)
    var errorMessage by mutableStateOf<String?>(null)

    val active: Boolean get() = FeatureFlags.CLOUD_SYNC
}

expect fun initCloudSync(platformContext: Any, repos: CloudSyncRepos)

/** Called when Firebase Auth session changes (sign-in / sign-out). */
expect fun cloudSyncOnAuthChanged(userId: String?)

/** Called after local Room bootstrap so cloud merge runs against real data. */
expect fun cloudSyncOnReady()

expect fun cloudSyncPushBottle(bottle: Bottle)
expect fun cloudSyncPushRack(rack: Rack)
expect fun cloudSyncDeleteRack(rackId: String)
expect fun cloudSyncPushTasting(tasting: Tasting)
expect fun cloudSyncPushProducer(producer: Producer)
expect fun cloudSyncPushSupplier(supplier: Supplier)

/** Purge all cloud data for the signed-in user (called on local reset). */
expect suspend fun cloudSyncClearAll()

/** Manual refresh from the account screen. */
expect fun cloudSyncRefresh()
