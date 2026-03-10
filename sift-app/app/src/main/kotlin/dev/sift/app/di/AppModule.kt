package dev.sift.app.di

import android.content.Context
import androidx.work.WorkManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.sift.app.db.EventDao
import dev.sift.app.db.SiftDatabase
import dev.sift.app.db.VectorIndexDao
import dev.sift.app.util.AppLabelCache
import dev.sift.app.util.KeystoreManager
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        keystoreManager: KeystoreManager,
    ): SiftDatabase {
        val passphrase = keystoreManager.getOrCreateDbPassphrase()
        return SiftDatabase.build(context, passphrase)
    }

    @Provides @Singleton
    fun provideEventDao(db: SiftDatabase): EventDao = db.eventDao()

    @Provides @Singleton
    fun provideVectorIndexDao(db: SiftDatabase): VectorIndexDao = db.vectorIndexDao()

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext context: Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides @Singleton
    fun provideAppLabelCache(@ApplicationContext context: Context) = AppLabelCache(context)
}
