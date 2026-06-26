package fr.geoking.vincent.data

expect object Settings {
    val internalLogEnabled: Boolean
    fun setInternalLogEnabled(enabled: Boolean)
}
