package com.example.smartvisionassist

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import java.util.Locale

class SignupEmailActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tts: TextToSpeech
    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup_email)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        tts = TextToSpeech(this, this)

        val nameInput = findViewById<EditText>(R.id.name)
        val emailInput = findViewById<EditText>(R.id.email)
        val passwordInput = findViewById<EditText>(R.id.password)
        val btnSignup = findViewById<Button>(R.id.btnSignup)

        btnSignup.setOnClickListener {

            speak("Creating account")

            val name = nameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (name.isEmpty() || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Fill all fields", Toast.LENGTH_SHORT).show()
                speak("Please fill all fields")
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {

                        val user = auth.currentUser

                        // Save name in FirebaseAuth
                        val profileUpdates = userProfileChangeRequest {
                            displayName = name
                        }
                        user?.updateProfile(profileUpdates)

                        //Save in Firestore
                        val userId = user?.uid
                        val userMap = hashMapOf(
                            "name" to name,
                            "email" to email
                        )

                        if (userId != null) {
                            db.collection("users")
                                .document(userId)
                                .set(userMap)
                        }

                        // Pass name instantly
                        val intent = Intent(this, MainActivity::class.java)
                        intent.putExtra("USERNAME", name)
                        startActivity(intent)
                        finish()

                        speak("Account created")

                    } else {
                        val error = task.exception?.message ?: "Signup failed"
                        Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        speak("Signup failed")
                    }
                }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA &&
                result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isReady = true
            }
        }
    }

    private fun speak(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tts.stop()
        tts.shutdown()
    }
}