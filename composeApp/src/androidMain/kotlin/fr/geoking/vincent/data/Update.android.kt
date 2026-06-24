package fr.geoking.vincent.data

actual object Updater {
    var triggerUpdate: ((Boolean) -> Unit)? = null

    actual fun checkForUpdate(manual: Boolean) {
        triggerUpdate?.invoke(manual)
    }
}
