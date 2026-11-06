package app.shomar.data.remote

import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** The server answered, but with an error status. `message` is the server's explanation. */
class ApiException(val code: Int, message: String) : Exception(message)

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Thin client for the shomar server. Network failures surface as IOException,
 * server-side errors as ApiException.
 */
class ApiClient(
    private val baseUrl: String,
    private val http: OkHttpClient,
    private val tokens: TokenStore,
) {
    private val json = Json { ignoreUnknownKeys = true }

    // ---- Authentication ----

    suspend fun register(email: String, password: String, defaultCurrency: String) {
        val payload = JsonObject(
            mapOf(
                "email" to JsonPrimitive(email),
                "password" to JsonPrimitive(password),
                "default_currency" to JsonPrimitive(defaultCurrency),
            ),
        )
        val request = Request.Builder()
            .url(baseUrl + "/auth/register")
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        call(request)
    }

    /** Logs in and remembers the token. */
    suspend fun login(email: String, password: String) {
        // The server's login endpoint is a standard OAuth2 form; "username" holds the email.
        val form = FormBody.Builder().add("username", email).add("password", password).build()
        val request = Request.Builder().url(baseUrl + "/auth/login").post(form).build()
        val body = call(request)
        tokens.token = json.parseToJsonElement(body)
            .jsonObject.getValue("access_token").jsonPrimitive.content
    }

    // ---- Reading: everything changed since an instant, deleted rows included ----

    suspend fun listAccounts(updatedSince: String): List<AccountDto> =
        json.decodeFromString(
            ListSerializer(AccountDto.serializer()),
            get("/accounts?updated_since=${encode(updatedSince)}"),
        )

    suspend fun listCategories(updatedSince: String): List<CategoryDto> =
        json.decodeFromString(
            ListSerializer(CategoryDto.serializer()),
            get("/categories?updated_since=${encode(updatedSince)}"),
        )

    suspend fun listTransactions(updatedSince: String, limit: Int, offset: Int): List<TransactionDto> =
        json.decodeFromString(
            ListSerializer(TransactionDto.serializer()),
            get("/transactions?updated_since=${encode(updatedSince)}&limit=$limit&offset=$offset"),
        )

    /** Number of items a list endpoint returns, e.g. count("/accounts"). */
    suspend fun count(path: String): Int =
        json.parseToJsonElement(get(path)).jsonArray.size

    // ---- Writing: PUT is idempotent (create or replace), so retrying is safe ----

    suspend fun putAccount(id: String, body: AccountPut) {
        put("/accounts/$id", json.encodeToString(AccountPut.serializer(), body))
    }

    suspend fun putCategory(id: String, body: CategoryPut) {
        put("/categories/$id", json.encodeToString(CategoryPut.serializer(), body))
    }

    suspend fun putTransaction(id: String, body: TransactionPut) {
        put("/transactions/$id", json.encodeToString(TransactionPut.serializer(), body))
    }

    /** For example delete("/transactions/<id>"). */
    suspend fun delete(path: String) {
        call(authorized(path).delete().build())
    }

    // ---- Plumbing ----

    private suspend fun get(path: String): String = call(authorized(path).get().build())

    private suspend fun put(path: String, jsonBody: String) {
        call(authorized(path).put(jsonBody.toRequestBody(JSON_MEDIA_TYPE)).build())
    }

    private fun authorized(path: String): Request.Builder {
        val builder = Request.Builder().url(baseUrl + path)
        tokens.token?.let { builder.header("Authorization", "Bearer $it") }
        return builder
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

    private suspend fun call(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ApiException(response.code, errorMessage(text, response.code))
            }
            text
        }
    }

    private fun errorMessage(body: String, code: Int): String =
        runCatching { json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.content }
            .getOrNull() ?: "HTTP $code"
}
