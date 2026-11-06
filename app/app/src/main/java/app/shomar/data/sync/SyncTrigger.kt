package app.shomar.data.sync

/** Something that can ask for a sync soon. Repositories call it after a local change. */
fun interface SyncTrigger {
    fun requestSync()
}