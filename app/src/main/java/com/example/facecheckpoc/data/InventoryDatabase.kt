package com.example.facecheckpoc.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [UserModel::class], version = 2, exportSchema = false)
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
                        .addMigrations(MIGRATION_1_2)
                        .build()
                }
            }
            return INSTANCE
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS UserModel")
            }
        }

    }

}