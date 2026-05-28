package com.openlistmobile.app.di

import android.content.Context
import androidx.room.Room
import com.openlistmobile.app.data.local.AppDatabase
import com.openlistmobile.app.data.local.DirectoryCacheDao
import com.openlistmobile.app.data.local.ServerProfileDao
import com.openlistmobile.app.data.local.TransferTaskDao
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
        )
            .addMigrations(AppDatabase.MIGRATION_4_5)
            .fallbackToDestructiveMigration()
            .build()
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
