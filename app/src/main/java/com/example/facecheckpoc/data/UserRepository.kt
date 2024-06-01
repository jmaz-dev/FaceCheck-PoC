package com.example.facecheckpoc.data

import android.content.Context

class UserRepository(context: Context) {

    private val inventoryDatabase = InventoryDatabase.getDatabase(context).userDao()


    fun insert(user: UserModel): Boolean {
        inventoryDatabase.insert(user)
        return true
    }

    fun getUserByCpf(cpf: String): UserModel? {
        return inventoryDatabase.getUserByCpf(cpf)
    }
}