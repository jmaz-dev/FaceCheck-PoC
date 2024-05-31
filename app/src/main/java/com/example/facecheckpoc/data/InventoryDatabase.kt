package com.example.facecheckpoc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UserModel::class], version = 1)
abstract class InventoryDatabase : RoomDatabase() {

    abstract fun userDao(): UserDao

    companion object {

        private lateinit var INSTANCE: InventoryDatabase


        fun getDatabase(context: Context): InventoryDatabase {
            synchronized(InventoryDatabase::class.java) {
                if (!::INSTANCE.isInitialized) {
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