package app.shomar.data.model

enum class CategoryKind(val wire: String, val label: String) {
    EXPENSE("expense", "Expense"),
    INCOME("income", "Income");

    companion object {
        /** `wire` is the value the server and the local database use. */
        fun fromWire(value: String): CategoryKind = entries.first { it.wire == value }
    }
}

/** A category, or a subcategory when `parentId` is set (two levels at most). */
data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val parentId: String?,
)