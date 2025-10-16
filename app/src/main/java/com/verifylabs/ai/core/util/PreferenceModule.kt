package com.verifylabs.ai.core.util

import com.verifylabs.ai.data.base.PreferenceHelper
import com.verifylabs.ai.data.base.PreferencesHelperImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PreferenceModule {
    @Binds
    @Singleton
    abstract fun bindPreferenceHelper(impl: PreferencesHelperImpl): PreferenceHelper
}