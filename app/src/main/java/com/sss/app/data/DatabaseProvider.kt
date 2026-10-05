package com.sss.app.data

import android.content.Context
import androidx.room.Room
import com.sss.app.data.local.AppDatabase

object DatabaseProvider {

    @Volatile
    private var INSTANCE: AppDatabase? = null

    fun getDatabase(context: Context): AppDatabase {

        return INSTANCE ?: synchronized(this) {

            val instance = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "sss_database"
            )
                .build()

            INSTANCE = instance

            instance
        }
    }
}