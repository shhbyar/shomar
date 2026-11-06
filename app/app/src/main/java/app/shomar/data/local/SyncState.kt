package app.shomar.data.local

/** Where a local row stands relative to the server. */
enum class SyncState {
    /** Identical to what the server has. */
    SYNCED,

    /** Created or edited on this phone; still to be pushed with PUT. */
    PENDING_UPSERT,

    /** Deleted on this phone; still to be pushed with DELETE. Hidden from the UI. */
    PENDING_DELETE,
}
