package app.shomar.data.remote

import android.content.Context

/**
 * Keeps the login token on the phone (private to this app).
 * TODO: move to the Android Keystore before the app holds real data.
 */
class TokenStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var token: String?
        get() = prefs.getString(KEY_TOKEN, null)
        set(value) {
            val editor = prefs.edit()
            if (value == null) editor.remove(KEY_TOKEN) else editor.putString(KEY_TOKEN, value)
            editor.apply()
        }

    private companion object {
        const val KEY_TOKEN = "access_token"
    }
}
