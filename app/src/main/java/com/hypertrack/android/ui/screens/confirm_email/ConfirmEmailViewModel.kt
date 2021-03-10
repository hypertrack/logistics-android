package com.hypertrack.android.ui.screens.confirm_email

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDirections
import com.hypertrack.android.interactors.*
import com.hypertrack.android.ui.common.stringFromResource
import com.hypertrack.logistics.android.github.R
import kotlinx.coroutines.launch

//todo task handle errors
class ConfirmEmailViewModel(private val loginInteractor: LoginInteractor) : ViewModel() {

    private lateinit var email: String

    val loadingState = MutableLiveData<Boolean>()
    val proceedButtonEnabled = MutableLiveData<Boolean>(false)
    val errorText = MutableLiveData<String>()
    val destination = MutableLiveData<NavDirections>()

    fun init(email: String) {
        this.email = email
    }

    fun onVerifiedClick(code: String, complete: Boolean) {
        if (complete) {
            loadingState.postValue(true)
            viewModelScope.launch {
                val res = loginInteractor.verifyByOtpCode(email = email, code = code)
                loadingState.postValue(false)
                when (res) {
                    is OtpSuccess -> {
                        destination.postValue(
                            ConfirmFragmentDirections.actionConfirmFragmentToSignInFragment(
                                email
                            )
                        )
                    }
                    is OtpError -> {
                        //todo task
                        errorText.postValue(R.string.wrong_code.stringFromResource())
                    }
                }
            }
        } else {
            //todo task
        }
    }

    fun onResendClick() {
        loadingState.postValue(true)
        viewModelScope.launch {
            loginInteractor.resendEmailConfirmation()
            loadingState.postValue(false)
        }
    }

    fun onCodeChanged(complete: Boolean) {
        proceedButtonEnabled.postValue(complete)
    }

}