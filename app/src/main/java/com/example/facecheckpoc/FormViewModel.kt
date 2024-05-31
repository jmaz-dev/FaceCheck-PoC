package com.example.facecheckpoc

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import com.example.facecheckpoc.data.UserModel

class FormViewModel(application: Application) : AndroidViewModel(application) {


    fun submitUser(user: UserModel) {
        Log.d("FormViewModel", "submitUser: $user")
    }
}