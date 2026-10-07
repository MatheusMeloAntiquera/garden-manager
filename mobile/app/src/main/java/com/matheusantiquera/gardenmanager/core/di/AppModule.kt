package com.matheusantiquera.gardenmanager.core.di

import com.matheusantiquera.gardenmanager.core.datastore.EncryptedTokenStore
import com.matheusantiquera.gardenmanager.core.datastore.TokenStore
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
abstract class AppModule {

    @Binds
    abstract fun bindTokenStore(impl: EncryptedTokenStore): TokenStore

    companion object {
        @Provides
        @Singleton
        @ApplicationScope
        fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob())

        /** Relógio do app, trocável nos testes para fixar "agora" (padrão de data e hora dos formulários). */
        @Provides
        @Singleton
        fun provideClock(): Clock = Clock.systemDefaultZone()
    }
}
