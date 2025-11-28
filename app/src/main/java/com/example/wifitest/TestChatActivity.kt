package com.example.wifitest

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class TestChatActivity : AppCompatActivity() {

    private lateinit var recyclerChat: RecyclerView
    private lateinit var chatAdapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()

    private lateinit var editMessage: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var toolbar: Toolbar
    private lateinit var friendName: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        recyclerChat = findViewById(R.id.recyclerChat)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        toolbar = findViewById(R.id.toolbar)
        friendName = findViewById(R.id.tvFriendName)

        chatAdapter = ChatAdapter(messages)
        recyclerChat.adapter = chatAdapter
        recyclerChat.layoutManager = LinearLayoutManager(this)

        friendName.text = getString(R.string.test_person)

        btnSend.setOnClickListener {
            val message = editMessage.text.toString()
            if (message.isNotEmpty()) {
                // Add as sent message
                chatAdapter.addMessage(ChatMessage(message, true))
                recyclerChat.scrollToPosition(messages.size - 1)

                // Simulate a received reply after a short delay
                recyclerChat.postDelayed({
                    chatAdapter.addMessage(ChatMessage("Echo: $message", false))
                    recyclerChat.scrollToPosition(messages.size - 1)
                }, 500)

                editMessage.text.clear()
            }
        }
    }
}