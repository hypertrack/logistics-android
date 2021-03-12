package com.hypertrack.android.interactors

import com.amazonaws.services.cognitoidentityprovider.model.NotAuthorizedException
import com.amazonaws.services.cognitoidentityprovider.model.UserNotFoundException
import com.hypertrack.android.api.BackendException
import com.hypertrack.android.api.LiveAccountApi
import com.hypertrack.android.repository.AccountRepository
import com.hypertrack.android.toBase64
import com.hypertrack.android.utils.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import retrofit2.HttpException
import java.util.*

interface LoginInteractor {
    suspend fun signIn(login: String, password: String): LoginResult
    suspend fun signUp(
        login: String,
        password: String,
        userAttributes: Map<String, String>
    ): RegisterResult

    suspend fun resendEmailConfirmation(email: String)
    suspend fun verifyByOtpCode(email: String, code: String): OtpResult
}

@ExperimentalCoroutinesApi
class LoginInteractorImpl(
    private val cognito: CognitoAccountLoginProvider,
    private val accountRepository: AccountRepository,
    private val tokenService: TokenForPublishableKeyExchangeService,
    private val liveAccountUrlService: LiveAccountApi
) : LoginInteractor {

    override suspend fun signIn(login: String, password: String): LoginResult {
        val res = getPublishableKey(login.toLowerCase(Locale.getDefault()), password)
        return when (res) {
            is PublishableKey -> {
                try {
                    val pkValid = accountRepository.onKeyReceived(res.key, "true")
                    if (pkValid) {
                        res
                    } else {
                        LoginError(Exception("Invalid Publishable Key"))
                    }
                } catch (e: Exception) {
                    LoginError(e)
                }
            }
            else -> {
                res
            }
        }
    }

    override suspend fun signUp(
        login: String,
        password: String,
        userAttributes: Map<String, String>
    ): RegisterResult {
        // get Cognito token
        val res = cognito.awsInitCallWrapper()
        if (res is AwsError) {
            return SignUpError(res.exception)
        }

        // Log.v(TAG, "Initialized with user State $userStateDetails")
        val signUpResult =
            cognito.awsSignUpCallWrapper(
                login.toLowerCase(Locale.getDefault()),
                password,
                userAttributes
            )
        when (signUpResult) {
            is AwsSignUpSuccess -> {
                //todo
                return SignUpError(Exception("Confirmation request expected, but got success"))
            }
            is AwsSignUpConfirmationRequired -> {
                return ConfirmationRequired
            }
            is AwsSignUpError -> {
                return SignUpError(signUpResult.exception)
            }
        }
    }

    override suspend fun verifyByOtpCode(email: String, code: String): OtpResult {
        val res = liveAccountUrlService.verifyEmailViaOtpCode(
            "Basic ${MyApplication.SERVICES_API_KEY.toBase64()}",
            LiveAccountApi.OtpBody(
                email = email,
                code = code
            )
        )
        if (res.isSuccessful) {
            return OtpSuccess
        } else {
            BackendException(res).let {
                if (it.statusCode == "CodeMismatchException") {
                    return OtpWrongCode
                } else {
                    return OtpError(it)
                }
            }
        }
    }

    override suspend fun resendEmailConfirmation(email: String) {
        val res = liveAccountUrlService.resendOtpCode(
            "Basic ${MyApplication.SERVICES_API_KEY.toBase64()}",
            LiveAccountApi.ResendBody(email)
        )
        if (!res.isSuccessful) {
            throw HttpException(res)
        }
    }

    private suspend fun getPublishableKey(login: String, password: String): LoginResult {

        // get Cognito token
        val res = cognito.awsInitCallWrapper()
        if (res is AwsError) {
            return LoginError(res.exception)
        }

        // Log.v(TAG, "Initialized with user State $userStateDetails")
        val signInResult = cognito.awsLoginCallWrapper(login, password)
        when (signInResult) {
            is AwsSignInSuccess -> {
                val tokenRes = cognito.awsTokenCallWrapper()
                return when (tokenRes) {
                    is CognitoTokenError -> {
                        LoginError(Exception("Failed to retrieve Cognito token"))
                    }
                    is CognitoToken -> {
                        val pk = getPublishableKeyFromToken(tokenRes.token)
                        // Log.d(TAG, "Got pk $pk")
                        PublishableKey(pk)
                    }
                }
            }
            is AwsSignInError -> {
                signInResult.exception.let {
                    return when (it) {
                        is UserNotFoundException -> {
                            NoSuchUser
                        }
                        is NotAuthorizedException -> {
                            InvalidLoginOrPassword
                        }
                        else -> {
                            LoginError(it)
                        }
                    }
                }
            }
            is AwsSignInConfirmationRequired -> {
                return EmailConfirmationRequired
            }
        }
    }

    private suspend fun getPublishableKeyFromToken(token: String): String {
        val response = tokenService.getPublishableKey(token)
        if (response.isSuccessful) return response.body()?.publishableKey ?: ""
        return ""
    }
}

sealed class LoginResult
class PublishableKey(val key: String) : LoginResult()
object NoSuchUser : LoginResult()
object EmailConfirmationRequired : LoginResult()
object InvalidLoginOrPassword : LoginResult()
class LoginError(val exception: Exception) : LoginResult()

sealed class RegisterResult
object ConfirmationRequired : RegisterResult()
class SignUpError(val exception: Exception) : RegisterResult()

sealed class OtpResult
object OtpSuccess : OtpResult()
class OtpError(val exception: Exception) : OtpResult()
object OtpWrongCode : OtpResult()
