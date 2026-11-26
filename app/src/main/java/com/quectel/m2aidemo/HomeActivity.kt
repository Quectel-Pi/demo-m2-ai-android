package com.quectel.m2aidemo

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.card.MaterialCardView

class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.home_root)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        findViewById<MaterialCardView>(R.id.image_recognition_card).setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.text_recognition_card).setOnClickListener {
            startActivity(Intent(this, TextRecognitionActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.speech_recognition_card).setOnClickListener {
            startActivity(Intent(this, SpeechToTextActivity::class.java))
        }
    }
}
