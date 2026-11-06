package app.shomar.ui.categories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shomar.data.CategoryRepository
import app.shomar.data.model.CategoryKind
import app.shomar.ui.common.CategoryOption
import app.shomar.ui.common.CategoryOptions
import app.shomar.ui.common.ChoiceButton
import app.shomar.ui.common.Picker
import kotlinx.coroutines.launch
import app.shomar.data.AppState

private val TOP_LEVEL = CategoryOption(id = "", label = "None (top level)")

@Composable
fun CategoriesScreen(state: AppState, repository: CategoryRepository, modifier: Modifier = Modifier) {
    val categories by state.categories.collectAsState()
    val scope = rememberCoroutineScope()

    var name by rememberSaveable { mutableStateOf("") }
    var isExpense by rememberSaveable { mutableStateOf(true) }
    var parentId by rememberSaveable { mutableStateOf("") }

    val kind = if (isExpense) CategoryKind.EXPENSE else CategoryKind.INCOME
    // Only top-level categories can be a parent: categories go two levels deep at most.
    val parentOptions = remember(categories, kind) {
        listOf(TOP_LEVEL) + categories
            .filter { it.kind == kind && it.parentId == null }
            .sortedBy { it.name.lowercase() }
            .map { CategoryOption(it.id, it.name) }
    }
    val selectedParent = parentOptions.firstOrNull { it.id == parentId } ?: TOP_LEVEL

    LazyColumn(modifier = modifier.padding(horizontal = 16.dp)) {
        item {
            Column {
                Text("Categories", style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.height(16.dp))

                Row {
                    ChoiceButton("Expense", selected = isExpense) { isExpense = true }
                    Spacer(Modifier.width(8.dp))
                    ChoiceButton("Income", selected = !isExpense) { isExpense = false }
                }
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("New category name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Picker(
                    label = "Inside",
                    options = parentOptions,
                    selected = selectedParent,
                    optionLabel = { it.label },
                    onSelect = { parentId = it.id },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    enabled = name.isNotBlank(),
                    onClick = {
                        val newName = name.trim()
                        val parent = selectedParent.id.takeIf { it.isNotEmpty() }
                        name = ""
                        scope.launch { repository.create(newName, kind, parent) }
                    },
                ) {
                    Text("Add category")
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        for (listKind in CategoryKind.entries) {
            val options = CategoryOptions.forKind(categories, listKind)
            item {
                Text("${listKind.label} categories", style = MaterialTheme.typography.titleMedium)
            }
            if (options.isEmpty()) {
                item { Text("None yet.") }
            }
            items(options, key = { it.id }) { option ->
                ListItem(
                    headlineContent = { Text(option.label) },
                    trailingContent = {
                        TextButton(onClick = { scope.launch { repository.delete(option.id) } }) {
                            Text("Delete")
                        }
                    },
                )
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}