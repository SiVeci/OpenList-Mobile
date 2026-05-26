package com.example.alist.di

import android.content.Context
import androidx.room.Room
import com.example.alist.data.local.AppDatabase
import com.example.alist.data.local.DirectoryCacheDao
import com.example.alist.data.local.ServerProfileDao
import com.example.alist.data.local.TransferTaskDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "alist_database"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideServerProfileDao(database: AppDatabase): ServerProfileDao {
        return database.serverProfileDao()
    }

    @Provides
    fun provideDirectoryCacheDao(database: AppDatabase): DirectoryCacheDao {
        return database.directoryCacheDao()
    }

    @Provides
    fun provideTransferTaskDao(database: AppDatabase): TransferTaskDao {
        return database.transferTaskDao()
    }
}
