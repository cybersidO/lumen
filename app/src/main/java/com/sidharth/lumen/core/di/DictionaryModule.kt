package com.sidharth.lumen.core.di

import com.sidharth.lumen.core.dictionary.Dictionary
import com.sidharth.lumen.core.dictionary.WordNetDictionary
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * One dictionary is bound today. §2.4 asks for the interface to be pluggable, so adding a
 * medical dictionary later means a second implementation and a qualifier here — not a
 * change to any calling code.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DictionaryModule {

    @Binds
    @Singleton
    abstract fun bindDictionary(impl: WordNetDictionary): Dictionary
}
