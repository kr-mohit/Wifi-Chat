package com.example.wifitest

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatImageButton
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.*

class ChatActivity : AppCompatActivity() {

    private lateinit var tvFriendName: TextView
    private lateinit var profilePic: ImageView
    private lateinit var recycler: RecyclerView
    private lateinit var editMessage: EditText
    private lateinit var btnSend: AppCompatImageButton

    private val messages = mutableListOf<ChatMessage>()
    private lateinit var adapter: ChatAdapter

    private var readJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    private var friendName: String = "Friend"
    private var friendImage: Bitmap? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        recycler = findViewById(R.id.recyclerChat)
        editMessage = findViewById(R.id.editMessage)
        btnSend = findViewById(R.id.btnSend)
        tvFriendName = findViewById(R.id.tvFriendName)
        profilePic = findViewById(R.id.profilePic)

        friendName = intent.getStringExtra("friendName") ?: "Demo"
        friendImage = intent.getParcelableExtra("friendImage")

        tvFriendName.text = friendName
        val defaultBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_user_placeholder)
        findViewById<ImageView>(R.id.profilePic).setImageBitmap(friendImage ?: defaultBitmap)

        adapter = ChatAdapter(messages)
        recycler.adapter = adapter
        recycler.layoutManager = LinearLayoutManager(this)

        startListeningForMessages()

        btnSend.setOnClickListener {
            val msg = editMessage.text.toString()
            if (msg.isNotEmpty()) {
                sendMessage(msg)
                adapter.addMessage(ChatMessage(msg, true))
                editMessage.text.clear()
                recycler.scrollToPosition(messages.size - 1)
            }
        }
    }

    private fun startListeningForMessages() {
        val reader = SocketHolder.reader ?: return

        readJob = scope.launch(Dispatchers.IO) {
            try {
                while (isActive) {
                    val msg = reader.readLine() ?: break

                    withContext(Dispatchers.Main) {
                        adapter.addMessage(ChatMessage(msg, false))
                        recycler.scrollToPosition(messages.size - 1)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun sendMessage(message: String) {
        val writer = SocketHolder.writer ?: return
        scope.launch(Dispatchers.IO) {
            try {
                writer.write(message)
                writer.newLine()
                writer.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        readJob?.cancel()
    }
}