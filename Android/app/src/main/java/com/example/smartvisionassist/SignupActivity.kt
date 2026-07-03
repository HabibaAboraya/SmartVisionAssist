package com.example.smartvisionassist

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.appcompat.app.AppCompatActivity

import com.google.android.gms.auth.api.signin.*
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.*

import com.facebook.*
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.facebook.appevents.AppEventsLogger
import com.facebook.FacebookSdk

import java.util.*

class SignupActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var tts: TextToSpeech

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var googleSignInLauncher: ActivityResultLauncher<Intent>

    private lateinit var callbackManager: CallbackManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        //Facebook SDK init
        //FacebookSdk.sdkInitialize(applicationContext)
        //AppEventsLogger.activateApp(application)

        setContentView(R.layout.activity_signup)

        auth = FirebaseAuth.getInstance()
        tts = TextToSpeech(this, this)

        // =========================
        // GOOGLE CONFIG
        // =========================
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        googleSignInLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        Log.d("GOOGLE", "Login success")
                        firebaseAuthWithGoogle(account.idToken!!)
                    } catch (e: Exception) {
                        Log.e("GOOGLE_ERROR", e.message ?: "Google error")
                    }
                }
            }

        // =========================
        // FACEBOOK CONFIG
        // =========================
        callbackManager = CallbackManager.Factory.create()

        LoginManager.getInstance().registerCallback(
            callbackManager,
            object : FacebookCallback<LoginResult> {

                override fun onSuccess(result: LoginResult) {

                    Log.d("FACEBOOK", "Login success")
                    Log.d("FACEBOOK_TOKEN", result.accessToken.token)

                    Log.d("FACEBOOK_APP_ID", result.accessToken.applicationId)
                    Log.d("FACEBOOK_USER_ID", result.accessToken.userId)
                    Log.d("FACEBOOK_EXPIRES", result.accessToken.expires.toString())

                    handleFacebookAccessToken(result.accessToken)
                }

                override fun onCancel() {
                    Log.d("FACEBOOK", "Login cancelled")
                }

                override fun onError(error: FacebookException) {
                    Log.e("FACEBOOK_ERROR", error.toString())
                }
            }
        )

        // =========================
        // BUTTONS
        // =========================
        val btnEmail = findViewById<Button>(R.id.btnEmail)
        val btnGoogle = findViewById<View>(R.id.btnGoogle)
        val btnFacebook = findViewById<View>(R.id.btnFacebook)
        val goToLogin = findViewById<Button>(R.id.goToLogin)

        // EMAIL
        btnEmail.setOnClickListener {
            speak("Continue with email")
            startActivity(Intent(this, SignupEmailActivity::class.java))
        }

        // GOOGLE
        btnGoogle.setOnClickListener {
            speak("Continue with Google")

            googleSignInClient.signOut().addOnCompleteListener {
                googleSignInLauncher.launch(googleSignInClient.signInIntent)
            }
        }

        // FACEBOOK
        btnFacebook.setOnClickListener {
            speak("Continue with Facebook")

            LoginManager.getInstance().logOut()

            LoginManager.getInstance().logInWithReadPermissions(
                this,
                listOf("public_profile", "email")
            )
        }

        // SIGN IN
        goToLogin.setOnClickListener {
            speak("Sign in")
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }
    }

    // =========================
    // GOOGLE AUTH
    // =========================
    private fun firebaseAuthWithGoogle(idToken: String) {
        val credential = GoogleAuthProvider.getCredential(idToken, null)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Log.d("GOOGLE_AUTH", "SUCCESS")
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                } else {
                    Log.e("GOOGLE_AUTH_ERROR", task.exception?.message ?: "Error")
                }
            }
    }

    // =========================
    // FACEBOOK AUTH
    // =========================
    private fun handleFacebookAccessToken(token: AccessToken) {

        Log.d("FACEBOOK_TOKEN", token.token)

        val credential = FacebookAuthProvider.getCredential(token.token)

        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->

                if (task.isSuccessful) {
                    Log.d("FACEBOOK_AUTH", "SUCCESS")
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()

                } else {

                    Log.e(
                        "FIREBASE_FACEBOOK",
                        task.exception?.message ?: "Unknown"
                    )

                    if (task.exception is FirebaseAuthUserCollisionException) {

                        Log.e(
                            "COLLISION",
                            "Email already exists with another provider"
                        )
                    }

                    task.exception?.printStackTrace()
                }
            }
    }

    // =========================
    // REQUIRED FOR FACEBOOK
    // =========================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        val handled = callbackManager.onActivityResult(requestCode, resultCode, data)
        Log.d("FACEBOOK_RESULT", "Handled: $handled")
    }

    // =========================
    // TEXT TO SPEECH
    // =========================
    private fun speak(text: String) {
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    override fun onDestroy() {
        tts.stop()
        tts.shutdown()
        super.onDestroy()
    }
}