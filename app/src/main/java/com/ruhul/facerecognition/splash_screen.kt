package com.ruhul.facerecognition

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.ruhul.facerecognition.R
import android.content.Intent
import android.os.Handler
import com.ruhul.facerecognition.MainActivity

class splash_screen : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash_screen)
        val handler = Handler()
        handler.postDelayed({
            val intent = Intent(this@splash_screen, MainActivity::class.java)
            finish()
            startActivity(intent)
        }, 2500)
    }
}