package com.example.smartvisionassist

import android.content.Intent
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.util.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var tts: TextToSpeech
    private var isReady = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        tts = TextToSpeech(this, this)

        val txtWelcome = findViewById<TextView>(R.id.txtWelcome)
        val btnLogout = findViewById<Button>(R.id.btnLogout)
        val btnScan = findViewById<Button>(R.id.btnScan)

        val user = auth.currentUser

        if (user != null) {

            val nameFromIntent = intent.getStringExtra("USERNAME")

            if (!nameFromIntent.isNullOrEmpty()) {
                txtWelcome.text = "Welcome $nameFromIntent"
                speak("Welcome $nameFromIntent")
            }

            db.collection("users").document(user.uid)
                .get()
                .addOnSuccessListener { document ->

                    val name = document.getString("name")

                    val finalName = when {
                        !name.isNullOrEmpty() -> name
                        !user.displayName.isNullOrEmpty() -> user.displayName!!
                        else -> "User"
                    }

                    txtWelcome.text = "Welcome $finalName"
                    speak("Welcome $finalName")
                }
                .addOnFailureListener {

                    val fallback = user.displayName ?: "User"
                    txtWelcome.text = "Welcome $fallback"
                    speak("Welcome $fallback")
                }

        } else {
            txtWelcome.text = "Welcome User"
        }

        btnScan.setOnClickListener {
            speak("Scanning environment")
            startActivity(Intent(this, CameraActivity::class.java))
        }

        btnLogout.setOnClickListener {
            speak("Logging out")
            auth.signOut()
            startActivity(Intent(this, IntroActivity::class.java))
            finish()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
            isReady = true
        }
    }

    private fun speak(text: String) {
        if (isReady) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    override fun onDestroy() {
        if (::tts.isInitialized) {
            tts.stop()
            tts.shutdown()
        }
        super.onDestroy()
    }
}