package com.infinitezerone.minibgm.core.database.di

import androidx.room3.Room
import com.infinitezerone.minibgm.core.database.BgmDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val databaseModule =
    module {
        single {
            Room
                .databaseBuilder(
                    androidContext(),
                    BgmDatabase::class.java,
                    "minibgm.db",
                ).fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }
        single { get<BgmDatabase>().airScheduleDao() }
        single { get<BgmDatabase>().userCollectionDao() }
        single { get<BgmDatabase>().airEventDao() }
    }
