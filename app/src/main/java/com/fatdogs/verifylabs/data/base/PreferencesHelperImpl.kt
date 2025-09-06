package com.fatdogs.verifylabs.data.base

import android.content.Context
import android.content.SharedPreferences
import com.fatdogs.verifylabs.core.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PreferencesHelperImpl @Inject constructor(
    @ApplicationContext context: Context
) : PreferenceHelper {

    private val mPrefs: SharedPreferences =
        context.getSharedPreferences(Constants.APP_SHARED_PREFERENCE_NAME, Context.MODE_PRIVATE)

    override fun setLanguage(language: String) {
        mPrefs.edit().putString(Constants.LANGUAGE, language).apply()
    }

    override fun getLanguage(): String {
        return mPrefs.getString(Constants.LANGUAGE, "english") ?: "english"
    }

    override fun setLanguageCode(languageCode: String) {
        mPrefs.edit().putString(Constants.LANGUAGE_CODE, languageCode).apply()
    }

    override fun getLanguageCode(): String {
        return mPrefs.getString(Constants.LANGUAGE_CODE, "en") ?: "en"
    }

    override fun setIsIntroScreenDone(isIntroScreenDone: Boolean) {
        mPrefs.edit().putBoolean(Constants.IS_INTRO_SCREEN_DONE, isIntroScreenDone).apply()
    }

    override fun isIntroScreenDone(): Boolean {
        return mPrefs.getBoolean(Constants.IS_INTRO_SCREEN_DONE, false)
    }

    override fun setIsLoggedIn(isLogin: Boolean) {
        mPrefs.edit().putBoolean(Constants.IS_LOGGED_IN, isLogin).apply()
    }

    override fun isLoggedIn(): Boolean {
        return mPrefs.getBoolean(Constants.IS_LOGGED_IN, false)
    }

    override fun setApiKey(apiKey: String) {
        mPrefs.edit().putString(Constants.API_KEYS, apiKey).apply()
    }

    override fun getApiKey(): String? {
        return mPrefs.getString(Constants.API_KEYS, null)
    }

    override fun setUserName(userName: String) {
        mPrefs.edit().putString(Constants.USER_NAME, userName).apply()
    }

    override fun getUserName(): String? {
        return mPrefs.getString(Constants.USER_NAME, null)
    }

    override fun setCreditReamaining(credit: Int) {
        mPrefs.edit().putInt(Constants.CREDIT_REMAINING, credit).apply()
    }

    override fun getCreditRemaining(): Int {
      mPrefs.getInt(Constants.CREDIT_REMAINING, 0)
        return mPrefs.getInt(Constants.CREDIT_REMAINING, 0)
    }

    override fun setPassword(password: String) {
        mPrefs.edit().putString(Constants.PASSWORD, password).apply()
    }

    override fun getPassword(): String? {
        return mPrefs.getString(Constants.PASSWORD, null)
    }

    override fun clear() {
        mPrefs.edit().clear().apply()
    }

    override fun setTheme(isDark: Boolean) {
        mPrefs.edit().putBoolean(Constants.DARK_THEME, isDark).apply()
    }

    override fun isThemeDark(): Boolean {
        return mPrefs.getBoolean(Constants.DARK_THEME, false)
    }


}