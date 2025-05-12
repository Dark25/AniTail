package com.anitail.music.di

import android.annotation.SuppressLint
import androidx.hilt.work.WorkerAssistedFactory
import androidx.work.ListenableWorker
import com.anitail.music.services.AutoBackupWorker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoMap
import dagger.multibindings.StringKey

@Module
@InstallIn(SingletonComponent::class)
object AutoBackupWorkerModule {

    @SuppressLint("RestrictedApi")
    @Provides
    @IntoMap
    @StringKey("com.anitail.music.services.AutoBackupWorker")
    fun provideAutoBackupWorkerFactory(
        factory: AutoBackupWorker.Factory
    ): WorkerAssistedFactory<out ListenableWorker> {
        return factory
    }
}
