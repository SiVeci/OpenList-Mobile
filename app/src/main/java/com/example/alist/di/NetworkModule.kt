package com.example.alist.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)

        // TODO: Phase 2 - Insert interceptor for "Ignore SSL certificate errors" if user configuration allows it
        // val trustAllCerts = ...
        // builder.sslSocketFactory(...)
        
        return builder.build()
    }

    // TODO: Phase 2 - provideRetrofit() using provideOkHttpClient
}
