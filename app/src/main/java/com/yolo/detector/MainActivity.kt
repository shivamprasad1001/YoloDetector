package com.yolo.detector

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.yolo.detector.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.parseColor("#E8EAF0")
        window.navigationBarColor = Color.parseColor("#E8EAF0")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.photoButton.setOnClickListener {
            startActivity(Intent(this, PhotoDetectActivity::class.java))
        }

        binding.cameraButton.setOnClickListener {
            startActivity(Intent(this, CameraActivity::class.java))
        }

        binding.githubRow.setOnClickListener {
            openUrl("https://github.com/shivamprasad1001")
        }

        binding.portfolioRow.setOnClickListener {
            openUrl("https://shivamprasad1001.in")
        }
    }

    private fun openUrl(url: String) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
