package com.example.streaky.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Singleton для хранения сессии пользователя в SharedPreferences.
 *
 * Использование:
 *   UserSession.init(context)          — вызвать один раз в Application/Activity
 *   UserSession.save(id, email)        — после успешного входа/регистрации
 *   UserSession.userId                 — текущий user_id (или -1 если не авторизован)
 *   UserSession.isLoggedIn             — true если пользователь авторизован
 *   UserSession.clear()                — выход из аккаунта
 */
object UserSession {

    private const val PREFS_NAME    = "streaky_prefs"
    private const val KEY_USER_ID   = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val NO_USER       = -1L

    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Сохранить сессию после входа или регистрации. */
    fun save(id: Long, email: String) {
        prefs.edit()
            .putLong(KEY_USER_ID, id)
            .putString(KEY_USER_EMAIL, email)
            .apply()
    }

    /** Текущий user_id. Возвращает -1 если пользователь не авторизован. */
    val userId: Long
        get() = prefs.getLong(KEY_USER_ID, NO_USER)

    /** Email текущего пользователя. */
    val email: String
        get() = prefs.getString(KEY_USER_EMAIL, "") ?: ""

    /** True если пользователь авторизован (есть валидный user_id). */
    val isLoggedIn: Boolean
        get() = userId != NO_USER

    /** Очистить сессию (выход из аккаунта). */
    fun clear() {
        prefs.edit().clear().apply()
    }
}