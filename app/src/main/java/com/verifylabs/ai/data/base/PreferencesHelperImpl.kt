package com.verifylabs.ai.data.base

import android.content.Context
import android.content.SharedPreferences
import com.verifylabs.ai.core.util.Constants
import com.verifylabs.ai.core.util.Constants.Companion.KEY_HISTORY_RETENTION_DAYS
import com.verifylabs.ai.core.util.Constants.Companion.KEY_QUICK_RECORD_DURATION
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

    override fun setSelectedMediaPath(path: String?) {
        mPrefs.edit().putString(Constants.SELECTED_MEDIA_PATH, path).apply()
    }

    override fun getSelectedMediaPath(): String? {
        return mPrefs.getString(Constants.SELECTED_MEDIA_PATH, null)
    }

    override fun setSelectedMediaType(type: String?) {
        mPrefs.edit().putString(Constants.SELECTED_MEDIA_TYPE, type).apply()
    }

    override fun getSelectedMediaType(): String? {
        return mPrefs.getString(Constants.SELECTED_MEDIA_TYPE, null)
    }

    override fun setSavedMediaFragmentState(stateJson: String?) {
        mPrefs.edit().putString(Constants.SAVED_MEDIA_FRAGMENT_STATE, stateJson).apply()
    }

    override fun getSavedMediaFragmentState(): String? {
        return mPrefs.getString(Constants.SAVED_MEDIA_FRAGMENT_STATE, null)
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

    override fun setQuickRecordDuration(seconds: Int) {
        mPrefs.edit()
            .putInt(KEY_QUICK_RECORD_DURATION, seconds.coerceIn(10, 60))
            .apply()
    }

    override fun getQuickRecordDuration(): Int {
        return mPrefs.getInt(KEY_QUICK_RECORD_DURATION, 40) // default 40s
    }

    override fun setHistoryRetentionDays(days: Int) {
        mPrefs.edit()
            .putInt(KEY_HISTORY_RETENTION_DAYS, days.coerceIn(7, 90))
            .apply()
    }

    override fun getHistoryRetentionDays(): Int {
        return mPrefs.getInt(KEY_HISTORY_RETENTION_DAYS, 90) // default 90d
    }

}