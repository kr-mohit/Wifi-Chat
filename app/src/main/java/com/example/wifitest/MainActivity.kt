package com.example.wifitest

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {

    private val PREFS_NAME = "wifiChatPrefs"
    private val KEY_NAME = "userName"
    private val KEY_IMAGE = "userImageUri"

    private lateinit var tvGreeting: TextView
    private lateinit var ivProfile: ImageView
    private lateinit var ivEdit: ImageView
    private lateinit var btnSend: Button
    private lateinit var btnReceive: Button
    private lateinit var btnTestChat: Button
    private lateinit var testChatDesc: TextView

    private var tapCount = 0 // for dev mode reveal

    private val pickImageLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let {
            ivProfile.setImageURI(it)
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                .edit()
                .putString(KEY_IMAGE, it.toString())
                .apply()
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
            UserProfile.bitmap = bitmap
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        tvGreeting = findViewById(R.id.tvGreeting)
        ivProfile = findViewById(R.id.ivProfile)
        ivEdit = findViewById(R.id.ivEdit)
        btnSend = findViewById(R.id.btnSendMode)
        btnReceive = findViewById(R.id.btnReceiveMode)
        btnTestChat = findViewById(R.id.btnTestChat)
        testChatDesc = findViewById<TextView>(R.id.tvTestChatDesc)

        initUI()
        initListeners()
    }

    private fun initUI() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val name = prefs.getString(KEY_NAME, null)

        if (name.isNullOrEmpty()) {
            promptForName()
        } else {
            tvGreeting.text = "Hello, $name!"
        }

        btnTestChat.visibility = Button.GONE
        testChatDesc.visibility = TextView.GONE
    }

    private fun promptForName() {
        val editText = android.widget.EditText(this)
        AlertDialog.Builder(this)
            .setTitle("Enter your name")
            .setView(editText)
            .setCancelable(false)
            .setPositiveButton("OK") { _, _ ->
                val name = editText.text.toString().trim().ifEmpty { "User" }
                getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .putString(KEY_NAME, name)
                    .apply()
                tvGreeting.text = "Hello, $name!"
            }
            .show()
    }

    private fun initListeners() {
        ivProfile.setOnClickListener {
            pickImageLauncher.launch("image/*")
        }
        ivEdit.setOnClickListener {
            promptForName()
        }
        tvGreeting.setOnClickListener {
            tapCount++
            if (tapCount >= 10) { // 10 taps reveals the dev button
                btnTestChat.visibility = Button.VISIBLE
            }
        }
        btnSend.setOnClickListener {
            val name = tvGreeting.text.toString().removePrefix("Hello, ").trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, HostActivity::class.java).apply {
                putExtra("userName", name)
            }
            startActivity(intent)
        }

        btnReceive.setOnClickListener {
            val name = tvGreeting.text.toString().removePrefix("Hello, ").trim()
            if (name.isEmpty()) {
                Toast.makeText(this, "Enter your name", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, ClientActivity::class.java).apply {
                putExtra("userName", name)
            }
            startActivity(intent)
        }

        btnTestChat.setOnClickListener {
            startActivity(Intent(this, TestChatActivity::class.java))
        }
    }
}
