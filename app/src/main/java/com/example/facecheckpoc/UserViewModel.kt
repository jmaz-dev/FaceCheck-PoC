package com.example.facecheckpoc

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.facecheckpoc.data.UserModel
import com.example.facecheckpoc.data.UserRepository
import kotlin.reflect.KProperty

class UserViewModel(application: Application) : AndroidViewModel(application) {

    private val userRepository = UserRepository(application.applicationContext)

    private val _userSubmit = MutableLiveData<Boolean>()
    val userSubmit: LiveData<Boolean> = _userSubmit

    private val _userModel = MutableLiveData<UserModel>()
    val userModel: LiveData<UserModel> = _userModel

    fun getUserByCpf(cpf: String): UserModel? {
        val user = userRepository.getUserByCpf(cpf)
        _userModel.value = user
        return user
    }

    fun submitUser(user: UserModel) {
        _userSubmit.value = userRepository.insert(user)
    }

}
//    fun getFace(): ByteArray {
//        return userModel.value?.face ?: ByteArray(0)
//    }
