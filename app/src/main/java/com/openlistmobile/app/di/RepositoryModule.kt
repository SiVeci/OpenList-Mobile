package com.openlistmobile.app.di

import com.openlistmobile.app.data.repository.AuthRepositoryImpl
import com.openlistmobile.app.data.repository.FileRepositoryImpl
import com.openlistmobile.app.data.repository.SyncRepositoryImpl
import com.openlistmobile.app.data.repository.TransferRepositoryImpl
import com.openlistmobile.app.domain.repository.AuthRepository
import com.openlistmobile.app.domain.repository.FileRepository
import com.openlistmobile.app.domain.repository.SyncRepository
import com.openlistmobile.app.domain.repository.TransferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindAuthRepository(
        authRepositoryImpl: AuthRepositoryImpl
    ): AuthRepository

    @Binds
    @Singleton
    abstract fun bindFileRepository(
        fileRepositoryImpl: FileRepositoryImpl
    ): FileRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(
        transferRepositoryImpl: TransferRepositoryImpl
    ): TransferRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(
        syncRepositoryImpl: SyncRepositoryImpl
    ): SyncRepository
}