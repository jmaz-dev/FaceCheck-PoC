package com.example.facecheckpoc.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(userModel: UserModel)

    @Query("SELECT * FROM user WHERE cpf = :cpf")
    fun getUserByCpf(cpf: String): UserModel?

}