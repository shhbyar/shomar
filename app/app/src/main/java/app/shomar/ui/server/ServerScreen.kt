package app.shomar.ui.server

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.shomar.data.remote.ApiClient
import app.shomar.data.remote.ApiException
import app.shomar.data.remote.TokenStore
import app.shomar.data.sync.SyncEngine
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// Temporary screen to try the server connection and the sync by hand.

// Placeholder until the user picks a default currency in a real sign-up screen.
private const val TEMP_CURRENCY = "EUR"

@Composable
fun ServerScreen(
    api: ApiClient,
    tokens: TokenStore,
    sync: SyncEngine,
    serverUrl: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(TEMP_CURRENCY) }
    var busy by remember { mutableStateOf(false) }
    var status by remember {
        mutableStateOf(if (tokens.token != null) "Logged in" else "Not logged in")
    }

    fun perform(block: suspend () -> String) {
        scope.launch {
            busy = true
            status = try {
                block()
            } catch (e: ApiException) {
                "Server says: ${e.message} (HTTP ${e.code})"
            } catch (e: IOException) {
                "Can't reach the server (${e.message})"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "${e::class.java.simpleName}: ${e.message}"
            }
            busy = false
        }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Server", style = MaterialTheme.typography.headlineMedium)
        Text(serverUrl, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Email") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = currency,
            onValueChange = { currency = it },
            label = { Text("Currency for a new account (e.g. EUR)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Row {
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    perform {
                        api.register(email.trim(), password, currency.trim().uppercase())
                        api.login(email.trim(), password)
                        "Registered and logged in"
                    }
                },
            ) {
                Text("Register")
            }
            Spacer(Modifier.width(8.dp))
            Button(
                enabled = !busy,
                onClick = {
                    perform {
                        api.login(email.trim(), password)
                        "Logged in"
                    }
                },
            ) {
                Text("Log in")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    tokens.token = null
                    status = "Logged out"
                },
            ) {
                Text("Log out")
            }
        }

        Spacer(Modifier.height(16.dp))
        Row {
            Button(
                enabled = !busy,
                onClick = { perform { sync.sync().summary() } },
            ) {
                Text("Sync now")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    perform {
                        val accounts = api.count("/accounts")
                        val categories = api.count("/categories")
                        "The server has $accounts accounts and $categories categories"
                    }
                },
            ) {
                Text("Server counts")
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(status)
    }
}
