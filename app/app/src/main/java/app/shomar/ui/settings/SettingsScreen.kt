package app.shomar.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.shomar.data.calendar.CalendarSystems
import app.shomar.data.settings.AppSettings
import app.shomar.ui.common.ChoiceButton

@Composable
fun SettingsScreen(settings: AppSettings, modifier: Modifier = Modifier) {
    val calendar by settings.calendar.collectAsState()

    Column(modifier = modifier.padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        Text("Calendar", style = MaterialTheme.typography.titleMedium)
        Text(
            "Dates are shown in this calendar, and months (for example on the dashboard) " +
                    "follow its months. Your stored data does not change.",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            CalendarSystems.all.forEach { system ->
                ChoiceButton(system.displayName, selected = system.id == calendar.id) {
                    settings.setCalendar(system)
                }
                Spacer(Modifier.width(8.dp))
            }
        }
    }
}