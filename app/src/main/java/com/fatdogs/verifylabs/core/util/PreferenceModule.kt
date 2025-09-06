package com.fatdogs.verifylabs.core.util

import com.fatdogs.verifylabs.data.base.PreferenceHelper
import com.fatdogs.verifylabs.data.base.PreferencesHelperImpl
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