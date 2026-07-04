package fr.geoking.vincent.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import fr.geoking.vincent.db.ProducerEntity
import fr.geoking.vincent.db.RackEntity
import fr.geoking.vincent.db.SupplierEntity
import fr.geoking.vincent.db.TastingEntity
import fr.geoking.vincent.db.toBottle
import fr.geoking.vincent.db.toEntity
import fr.geoking.vincent.db.toProducer
import fr.geoking.vincent.db.toRack
import fr.geoking.vincent.db.toSupplier
import fr.geoking.vincent.db.toTasting
import fr.geoking.vincent.debug.InternalLog
import fr.geoking.vincent.model.Bottle
import fr.geoking.vincent.model.Producer
import fr.geoking.vincent.model.Rack
import fr.geoking.vincent.model.Supplier
import fr.geoking.vincent.model.Tasting
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "VincentCloudSync"
private const val PREFS = "vincent_cloud_sync"
private const val COL_USERS = "users"
private const val COL_BOTTLES = "bottles"
private const val COL_RACKS = "racks"
private const val COL_TASTINGS = "tastings"
private const val COL_PRODUCERS = "producers"
private const val COL_SUPPLIERS = "suppliers"
private const val FIELD_UPDATED_AT = "updatedAt"

private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
private val mutex = Mutex()

private lateinit var appContext: Context
private var currentUid: String? = null
private var fullSyncRunning = false

actual fun initCloudSync(platformContext: Any, repos: CloudSyncRepos) {
    appContext = platformContext as Context
    CloudSyncEngine.repos = repos
    currentUid = FirebaseAuth.getInstance().currentUser?.uid
}

actual fun cloudSyncOnReady() {
    val uid = currentUid ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
    currentUid = uid
    if (!CloudSync.active) return
    scope.launch { CloudSyncEngine.fullSync(uid) }
}

actual fun cloudSyncOnAuthChanged(userId: String?) {
    currentUid = userId
    if (!CloudSync.active || userId == null || !CloudSyncEngine.isReady) return
    scope.launch { CloudSyncEngine.fullSync(userId) }
}

actual fun cloudSyncPushBottle(bottle: Bottle) = CloudSyncEngine.enqueueBottle(bottle)
actual fun cloudSyncPushRack(rack: Rack) = CloudSyncEngine.enqueueRack(rack)
actual fun cloudSyncDeleteRack(rackId: String) = CloudSyncEngine.enqueueRackDelete(rackId)
actual fun cloudSyncPushTasting(tasting: Tasting) = CloudSyncEngine.enqueueTasting(tasting)
actual fun cloudSyncPushProducer(producer: Producer) = CloudSyncEngine.enqueueProducer(producer)
actual fun cloudSyncPushSupplier(supplier: Supplier) = CloudSyncEngine.enqueueSupplier(supplier)

actual fun cloudSyncRefresh() {
    val uid = currentUid ?: FirebaseAuth.getInstance().currentUser?.uid ?: return
    if (!CloudSync.active) return
    scope.launch { CloudSyncEngine.fullSync(uid) }
}

private object CloudSyncEngine {
    lateinit var repos: CloudSyncRepos
    val isReady: Boolean get() = ::repos.isInitialized

    private val prefs by lazy {
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    }

    fun enqueueBottle(bottle: Bottle) = schedulePush { pushBottle(it, bottle) }
    fun enqueueRack(rack: Rack) = schedulePush { pushRack(it, rack) }
    fun enqueueRackDelete(rackId: String) = schedulePush { deleteRack(it, rackId) }
    fun enqueueTasting(tasting: Tasting) = schedulePush { pushTasting(it, tasting) }
    fun enqueueProducer(producer: Producer) = schedulePush { pushProducer(it, producer) }
    fun enqueueSupplier(supplier: Supplier) = schedulePush { pushSupplier(it, supplier) }

    fun schedulePush(block: suspend (String) -> Unit) {
        if (!CloudSync.active || fullSyncRunning) return
        val uid = currentUid ?: return
        scope.launch {
            runCatching { block(uid) }.onFailure { e ->
                InternalLog.e(TAG, "Push failed: ${e.message}", e)
                CloudSync.errorMessage = e.message
            }
        }
    }

    suspend fun fullSync(uid: String) {
        mutex.withLock {
            if (!CloudSync.active) return@withLock
            fullSyncRunning = true
            CloudSync.syncing = true
            CloudSync.errorMessage = null
            try {
                withContext(Dispatchers.IO) {
                    mergeBottles(uid)
                    mergeRacks(uid)
                    mergeTastings(uid)
                    mergeProducers(uid)
                    mergeSuppliers(uid)
                    reloadUiState()
                }
                CloudSync.lastSyncedAt = System.currentTimeMillis()
                InternalLog.i(TAG, "Full sync completed for $uid")
            } catch (e: Exception) {
                InternalLog.e(TAG, "Full sync failed", e)
                CloudSync.errorMessage = e.message
            } finally {
                fullSyncRunning = false
                CloudSync.syncing = false
            }
        }
    }

    private suspend fun reloadUiState() {
        Cellar.reloadFromRepository()
        Racks.reloadFromRepository()
        Tastings.reloadFromRepository()
        Producers.reloadFromRepository()
        Suppliers.reloadFromRepository()
    }

    // --- merge helpers ---

    private suspend fun mergeBottles(uid: String) {
        val local = repos.cellar.loadAll()
        val cloud = pullBottles(uid)
        val merged = merge(
            collection = COL_BOTTLES,
            local = local,
            cloud = cloud,
            id = { it.id },
            preserveLocal = { localBottle, remoteBottle ->
                remoteBottle.copy(
                    photoBottle = localBottle?.photoBottle,
                    photoLabel = localBottle?.photoLabel,
                    photoBack = localBottle?.photoBack,
                )
            },
            push = { u, b -> pushBottle(u, b) },
        )
        merged.forEach { repos.cellar.upsert(it) }
    }

    private suspend fun mergeRacks(uid: String) {
        val local = repos.racks.loadAll()
        val cloud = pullRacks(uid)
        val merged = merge(
            collection = COL_RACKS,
            local = local,
            cloud = cloud,
            id = { it.id },
            preserveLocal = { localRack, remoteRack ->
                remoteRack.copy(arImagePath = localRack?.arImagePath)
            },
            push = { u, r -> pushRack(u, r) },
        )
        merged.forEach { repos.racks.upsert(it) }
    }

    private suspend fun mergeTastings(uid: String) {
        val local = repos.tastings.loadAll()
        val cloud = pullTastings(uid)
        val merged = merge(
            collection = COL_TASTINGS,
            local = local,
            cloud = cloud,
            id = { it.id },
            preserveLocal = { _, remote -> remote },
            push = { u, t -> pushTasting(u, t) },
        )
        merged.forEach { repos.tastings.upsert(it) }
    }

    private suspend fun mergeProducers(uid: String) {
        val local = repos.producers.loadAll()
        val cloud = pullProducers(uid)
        val merged = merge(
            collection = COL_PRODUCERS,
            local = local,
            cloud = cloud,
            id = { it.id },
            preserveLocal = { _, remote -> remote },
            push = { u, p -> pushProducer(u, p) },
        )
        merged.forEach { repos.producers.upsert(it) }
    }

    private suspend fun mergeSuppliers(uid: String) {
        val local = repos.suppliers.loadAll()
        val cloud = pullSuppliers(uid)
        val merged = merge(
            collection = COL_SUPPLIERS,
            local = local,
            cloud = cloud,
            id = { it.id },
            preserveLocal = { _, remote -> remote },
            push = { u, s -> pushSupplier(u, s) },
        )
        merged.forEach { repos.suppliers.upsert(it) }
    }

    private suspend fun <T> merge(
        collection: String,
        local: List<T>,
        cloud: List<CloudDoc<T>>,
        id: (T) -> String,
        preserveLocal: (local: T?, remote: T) -> T,
        push: suspend (uid: String, item: T) -> Unit,
    ): List<T> {
        val uid = currentUid ?: return local
        val localMap = local.associateBy(id)
        val cloudMap = cloud.associateBy { id(it.data) }
        val allIds = localMap.keys + cloudMap.keys
        val out = ArrayList<T>(allIds.size)
        for (docId in allIds) {
            val l = localMap[docId]
            val c = cloudMap[docId]
            val merged = when {
                l == null && c != null -> {
                    setLocalUpdatedAt(collection, docId, c.updatedAt)
                    preserveLocal(null, c.data)
                }
                l != null && c == null -> {
                    push(uid, l)
                    l
                }
                l != null && c != null -> {
                    val localTs = localUpdatedAt(collection, docId)
                    if (c.updatedAt > localTs) {
                        setLocalUpdatedAt(collection, docId, c.updatedAt)
                        preserveLocal(l, c.data)
                    } else {
                        if (localTs > c.updatedAt) push(uid, l)
                        l
                    }
                }
                else -> continue
            }
            out += merged
        }
        return out
    }

    // --- push ---

    private suspend fun pushBottle(uid: String, bottle: Bottle) {
        val updatedAt = System.currentTimeMillis()
        val map = bottle.forCloud().toEntity().toCloudMap(updatedAt)
        userCollection(uid, COL_BOTTLES).document(bottle.id).set(map, SetOptions.merge()).await()
        setLocalUpdatedAt(COL_BOTTLES, bottle.id, updatedAt)
    }

    private suspend fun pushRack(uid: String, rack: Rack) {
        val updatedAt = System.currentTimeMillis()
        val map = rack.forCloud().toEntity().toCloudMap(updatedAt)
        userCollection(uid, COL_RACKS).document(rack.id).set(map, SetOptions.merge()).await()
        setLocalUpdatedAt(COL_RACKS, rack.id, updatedAt)
    }

    private suspend fun deleteRack(uid: String, rackId: String) {
        userCollection(uid, COL_RACKS).document(rackId).delete().await()
        prefs.edit().remove(tsKey(COL_RACKS, rackId)).apply()
    }

    private suspend fun pushTasting(uid: String, tasting: Tasting) {
        val updatedAt = System.currentTimeMillis()
        val map = tasting.toEntity().toCloudMap(updatedAt)
        userCollection(uid, COL_TASTINGS).document(tasting.id).set(map, SetOptions.merge()).await()
        setLocalUpdatedAt(COL_TASTINGS, tasting.id, updatedAt)
    }

    private suspend fun pushProducer(uid: String, producer: Producer) {
        val updatedAt = System.currentTimeMillis()
        val map = producer.toEntity().toCloudMap(updatedAt)
        userCollection(uid, COL_PRODUCERS).document(producer.id).set(map, SetOptions.merge()).await()
        setLocalUpdatedAt(COL_PRODUCERS, producer.id, updatedAt)
    }

    private suspend fun pushSupplier(uid: String, supplier: Supplier) {
        val updatedAt = System.currentTimeMillis()
        val map = supplier.toEntity().toCloudMap(updatedAt)
        userCollection(uid, COL_SUPPLIERS).document(supplier.id).set(map, SetOptions.merge()).await()
        setLocalUpdatedAt(COL_SUPPLIERS, supplier.id, updatedAt)
    }

    // --- pull ---

    private suspend fun pullBottles(uid: String): List<CloudDoc<Bottle>> =
        userCollection(uid, COL_BOTTLES).get().await().documents.mapNotNull { it.toCloudBottle() }

    private suspend fun pullRacks(uid: String): List<CloudDoc<Rack>> =
        userCollection(uid, COL_RACKS).get().await().documents.mapNotNull { it.toCloudRack() }

    private suspend fun pullTastings(uid: String): List<CloudDoc<Tasting>> =
        userCollection(uid, COL_TASTINGS).get().await().documents.mapNotNull { it.toCloudTasting() }

    private suspend fun pullProducers(uid: String): List<CloudDoc<Producer>> =
        userCollection(uid, COL_PRODUCERS).get().await().documents.mapNotNull { it.toCloudProducer() }

    private suspend fun pullSuppliers(uid: String): List<CloudDoc<Supplier>> =
        userCollection(uid, COL_SUPPLIERS).get().await().documents.mapNotNull { it.toCloudSupplier() }

    private fun userCollection(uid: String, name: String) =
        FirebaseFirestore.getInstance().collection(COL_USERS).document(uid).collection(name)

    private fun localUpdatedAt(collection: String, id: String): Long =
        prefs.getLong(tsKey(collection, id), 0L)

    private fun setLocalUpdatedAt(collection: String, id: String, ts: Long) {
        prefs.edit().putLong(tsKey(collection, id), ts).apply()
    }

    private fun tsKey(collection: String, id: String) = "ts/$collection/$id"
}

private data class CloudDoc<T>(val data: T, val updatedAt: Long)

private fun Bottle.forCloud(): Bottle = copy(photoBottle = null, photoLabel = null, photoBack = null)

private fun Rack.forCloud(): Rack = copy(arImagePath = null)

private fun fr.geoking.vincent.db.BottleEntity.toCloudMap(updatedAt: Long): Map<String, Any> = mapOf(
    "id" to id,
    "domain" to domain,
    "appellation" to appellation,
    "color" to color,
    "category" to category,
    "vintage" to vintage,
    "price" to price,
    "quantity" to quantity,
    "rating" to rating,
    "cellarSpot" to cellarSpot,
    "provenance" to provenance,
    "merchant" to merchant,
    "purchaseDate" to purchaseDate,
    "occasion" to occasion,
    "favorite" to favorite,
    "pairings" to pairings,
    "drinkFrom" to drinkFrom,
    "drinkTo" to drinkTo,
    "drinkNow" to drinkNow,
    "tastingNotes" to tastingNotes,
    "description" to description,
    "pairingNotes" to pairingNotes,
    "grapes" to grapes,
    "flavorProfile" to flavorProfile,
    "maturity" to maturity,
    "source" to source,
    "addedLabel" to addedLabel,
    FIELD_UPDATED_AT to updatedAt,
)

private fun RackEntity.toCloudMap(updatedAt: Long): Map<String, Any> = buildMap {
    put("id", id)
    put("name", name)
    put("cols", cols)
    put("rows", rows)
    put("staggered", staggered)
    put("cellsData", cellsData)
    arCalibrationData?.let { put("arCalibrationData", it) }
    arMode?.let { put("arMode", it) }
    arAnchorData?.let { put("arAnchorData", it) }
    put("format", format)
    put("staggerOffset", staggerOffset)
    put(FIELD_UPDATED_AT, updatedAt)
}

private fun TastingEntity.toCloudMap(updatedAt: Long): Map<String, Any> = buildMap {
    put("id", id)
    bottleId?.let { put("bottleId", it) }
    put("wineName", wineName)
    put("date", date)
    put("rating", rating)
    put("notes", notes)
    color?.let { put("color", it) }
    vintage?.let { put("vintage", it) }
    if (place.isNotBlank()) put("place", place)
    put(FIELD_UPDATED_AT, updatedAt)
}

private fun ProducerEntity.toCloudMap(updatedAt: Long): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "region" to region,
    "country" to country,
    "website" to website,
    "email" to email,
    "phone" to phone,
    FIELD_UPDATED_AT to updatedAt,
)

private fun SupplierEntity.toCloudMap(updatedAt: Long): Map<String, Any> = mapOf(
    "id" to id,
    "name" to name,
    "type" to type,
    "website" to website,
    "email" to email,
    "phone" to phone,
    FIELD_UPDATED_AT to updatedAt,
)

private fun DocumentSnapshot.toCloudBottle(): CloudDoc<Bottle>? {
    val updatedAt = getLong(FIELD_UPDATED_AT) ?: return null
    val entity = fr.geoking.vincent.db.BottleEntity(
        id = getString("id") ?: return null,
        domain = getString("domain").orEmpty(),
        appellation = getString("appellation").orEmpty(),
        color = getString("color").orEmpty(),
        category = getString("category").orEmpty(),
        vintage = getString("vintage").orEmpty(),
        price = getLong("price")?.toInt() ?: 0,
        quantity = getLong("quantity")?.toInt() ?: 0,
        rating = getDouble("rating") ?: 0.0,
        cellarSpot = getString("cellarSpot").orEmpty(),
        provenance = getString("provenance").orEmpty(),
        merchant = getString("merchant").orEmpty(),
        purchaseDate = getString("purchaseDate").orEmpty(),
        occasion = getString("occasion").orEmpty(),
        favorite = getBoolean("favorite") ?: false,
        pairings = getString("pairings").orEmpty(),
        drinkFrom = getLong("drinkFrom")?.toInt() ?: 0,
        drinkTo = getLong("drinkTo")?.toInt() ?: 0,
        drinkNow = (getDouble("drinkNow") ?: 0.5).toFloat(),
        tastingNotes = getString("tastingNotes").orEmpty(),
        description = getString("description").orEmpty(),
        pairingNotes = getString("pairingNotes").orEmpty(),
        grapes = getString("grapes").orEmpty(),
        flavorProfile = getString("flavorProfile").orEmpty(),
        maturity = getString("maturity").orEmpty(),
        source = getString("source").orEmpty(),
        addedLabel = getString("addedLabel").orEmpty(),
    )
    return CloudDoc(entity.toBottle(), updatedAt)
}

private fun DocumentSnapshot.toCloudRack(): CloudDoc<Rack>? {
    val updatedAt = getLong(FIELD_UPDATED_AT) ?: return null
    val entity = RackEntity(
        id = getString("id") ?: return null,
        name = getString("name").orEmpty(),
        cols = getLong("cols")?.toInt() ?: 0,
        rows = getLong("rows")?.toInt() ?: 0,
        staggered = getBoolean("staggered") ?: false,
        cellsData = getString("cellsData").orEmpty(),
        arCalibrationData = getString("arCalibrationData"),
        arMode = getString("arMode"),
        arAnchorData = getString("arAnchorData"),
        format = getString("format") ?: "GRID",
        staggerOffset = getBoolean("staggerOffset") ?: false,
    )
    return CloudDoc(entity.toRack(), updatedAt)
}

private fun DocumentSnapshot.toCloudTasting(): CloudDoc<Tasting>? {
    val updatedAt = getLong(FIELD_UPDATED_AT) ?: return null
    val entity = TastingEntity(
        id = getString("id") ?: return null,
        bottleId = getString("bottleId"),
        wineName = getString("wineName").orEmpty(),
        date = getString("date").orEmpty(),
        rating = getDouble("rating") ?: 0.0,
        notes = getString("notes").orEmpty(),
        color = getString("color"),
        vintage = getString("vintage"),
        place = getString("place").orEmpty(),
    )
    return CloudDoc(entity.toTasting(), updatedAt)
}

private fun DocumentSnapshot.toCloudProducer(): CloudDoc<Producer>? {
    val updatedAt = getLong(FIELD_UPDATED_AT) ?: return null
    val entity = ProducerEntity(
        id = getString("id") ?: return null,
        name = getString("name").orEmpty(),
        region = getString("region").orEmpty(),
        country = getString("country").orEmpty(),
        website = getString("website").orEmpty(),
        email = getString("email").orEmpty(),
        phone = getString("phone").orEmpty(),
    )
    return CloudDoc(entity.toProducer(), updatedAt)
}

private fun DocumentSnapshot.toCloudSupplier(): CloudDoc<Supplier>? {
    val updatedAt = getLong(FIELD_UPDATED_AT) ?: return null
    val entity = SupplierEntity(
        id = getString("id") ?: return null,
        name = getString("name").orEmpty(),
        type = getString("type").orEmpty(),
        website = getString("website").orEmpty(),
        email = getString("email").orEmpty(),
        phone = getString("phone").orEmpty(),
    )
    return CloudDoc(entity.toSupplier(), updatedAt)
}
