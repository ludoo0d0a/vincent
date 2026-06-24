package fr.geoking.vincent.data

expect object Updater {
    /**
     * Checks for updates from the platform store (e.g. Play Store).
     * @param manual true if triggered by user (show "up to date" toast), false if automatic.
     */
    fun checkForUpdate(manual: Boolean)
}
