package app.shomar.ui.common

import app.shomar.data.model.Category
import app.shomar.data.model.CategoryKind

data class CategoryOption(val id: String, val label: String)

object CategoryOptions {
    private const val SEPARATOR = " › "

    /**
     * Categories of one kind in display order: top-level ones by name, each followed by its
     * subcategories ("Food › Groceries"). Subcategories whose parent is missing are left out.
     */
    fun forKind(categories: List<Category>, kind: CategoryKind): List<CategoryOption> {
        val ofKind = categories.filter { it.kind == kind }
        val childrenByParent = ofKind.filter { it.parentId != null }.groupBy { it.parentId }
        val result = mutableListOf<CategoryOption>()
        for (parent in ofKind.filter { it.parentId == null }.sortedBy { it.name.lowercase() }) {
            result += CategoryOption(parent.id, parent.name)
            for (child in childrenByParent[parent.id].orEmpty().sortedBy { it.name.lowercase() }) {
                result += CategoryOption(child.id, parent.name + SEPARATOR + child.name)
            }
        }
        return result
    }

    /** Display names of all categories by id, built in one pass (for lists with many rows). */
    fun labelsById(categories: List<Category>): Map<String, String> {
        val byId = categories.associateBy { it.id }
        return categories.associate { category ->
            val parent = byId[category.parentId]
            category.id to if (parent != null) parent.name + SEPARATOR + category.name else category.name
        }
    }

    /** The display name of one category, or null if the id is null or no longer exists. */
    fun labelFor(categoryId: String?, categories: List<Category>): String? {
        val category = categories.firstOrNull { it.id == categoryId } ?: return null
        val parent = categories.firstOrNull { it.id == category.parentId }
        return if (parent != null) parent.name + SEPARATOR + category.name else category.name
    }
}