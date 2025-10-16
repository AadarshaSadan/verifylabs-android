package com.verifylabs.ai.data.base

interface PreferenceHelper {
    /**
    Sets the selected language.
    Aadarsha Bajagain
    bajagainsadan@gmail.com
    @param language The language to be set.
     */
    fun setLanguage(language: String)

    /**
    Retrieves the selected language.
    @return The selected language.
     */
    fun getLanguage(): String

    /**
    Sets the language code for localization purposes.
    @param languageCode The language code to be set.
     */
    fun setLanguageCode(languageCode: String)

    /**
    Retrieves the language code for localization.
    @return The language code.
     */
    fun getLanguageCode(): String

    /**
    Sets the flag indicating whether the intro screen has been completed.
    @param isIntroScreenDone True if intro screen is completed, false otherwise.
     */
    fun setIsIntroScreenDone(isIntroScreenDone: Boolean)

    /**
    Checks if the intro screen has been completed.
    @return True if intro screen is completed, false otherwise.
     */
    fun isIntroScreenDone(): Boolean

    /**
    Sets the flag indicating whether the user is logged in.
    @param isLogin True if the user is logged in, false otherwise.
     */
    fun setIsLoggedIn(isLogin: Boolean)

    /**
    Checks if the user is logged in.
    @return True if the user is logged in, false otherwise.
     */
    fun isLoggedIn(): Boolean

    /**
    Clears all the stored preferences.
     */
    fun clear()

    /**
    Sets the theme to dark or light mode.
    @param isDark True for dark theme, false for light theme.
     */
    fun setTheme(isDark: Boolean)

    /**
    Checks if the dark theme is enabled.
    @return True if dark theme is enabled, false otherwise.
     */
    fun isThemeDark(): Boolean

    /**
    Sets the API key.
    @param apiKey The API key to be set.
     */
    fun setApiKey(apiKey: String)

    /**
    Retrieves the API key.
    @return The API key, or null if not set.
     */
    fun getApiKey(): String?

    fun setUserName(userName: String)

    fun getUserName(): String?

    fun setCreditReamaining(credit: Int)

    fun getCreditRemaining(): Int

    fun setPassword(password: String)

    fun  getPassword(): String?

    fun setSelectedMediaPath(path: String?)
    fun getSelectedMediaPath(): String?

    fun setSelectedMediaType(type: String?)
    fun getSelectedMediaType(): String?

    fun setSavedMediaFragmentState(stateJson: String?)
    fun getSavedMediaFragmentState(): String?


}