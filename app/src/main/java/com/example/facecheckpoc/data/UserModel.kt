package com.example.facecheckpoc.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "User")
data class UserModel(

    @ColumnInfo(name = "id")
    @PrimaryKey(autoGenerate = true)
    var id: Int = 0,

    @ColumnInfo(name = "name")
    var name: String = "",

    @ColumnInfo(name = "cpf")
    var cpf: String = "",

    @ColumnInfo(name = "face")
    var face: ByteArray = byteArrayOf()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as UserModel

        if (id != other.id) return false
        if (name != other.name) return false
        if (cpf != other.cpf) return false
        if (!face.contentEquals(other.face)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + name.hashCode()
        result = 31 * result + cpf.hashCode()
        result = 31 * result + face.contentHashCode()
        return result
    }
}

