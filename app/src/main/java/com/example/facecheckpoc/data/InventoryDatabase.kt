package com.example.facecheckpoc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserModel::class], version = 1, exportSchema = false)
abstract class InventoryDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    companion object {
        private lateinit var INSTANCE: InventoryDatabase

        fun getDatabase(context: Context): InventoryDatabase {
            if (!Companion::INSTANCE.isInitialized) {
                synchronized(InventoryDatabase::class) {
                    INSTANCE = Room.databaseBuilder(
                        context,
                        InventoryDatabase::class.java,
                        "inventorydb"
                    )
                        .allowMainThreadQueries()
                        .build()
                }
            }
            return INSTANCE
        }


    }
}