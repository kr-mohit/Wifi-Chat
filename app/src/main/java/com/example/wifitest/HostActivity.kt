package com.example.wifitest

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ServerSocket

class HostActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var btnStart: Button
    private lateinit var helper: WifiDirectHelper

    // Lifecycle-aware scope
    private val hostScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_host)

        tvStatus = findViewById(R.id.tvHostStatus)
        btnStart = findViewById(R.id.btnHostStart)

        helper = WifiDirectHelper(this)

        btnStart.setOnClickListener {
            tvStatus.text = "Preparing..."
            // Prepare will check permissions, Wi-Fi, location, hotspot etc. and then call our onReady
            helper.prepareAndThen {
                runOnUiThread { tvStatus.text = "Environment OK — Creating group..." }
                createGroup()
            }
        }

        // register to receive connection info changes so we can take action
        helper.setConnectionInfoCallback { info ->
            if (info.groupFormed && info.isGroupOwner) {
                // once group is formed and we're owner, open server socket
                runOnUiThread { tvStatus.text = "Group formed — starting server socket..." }
                waitForClient()
            }
        }

        helper.registerReceivers()
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        helper.createGroupWithRetries(
            onCreated = {
                runOnUiThread { tvStatus.text = "Group created. Waiting for client..." }
            },
            onFailure = { reason ->
                runOnUiThread { tvStatus.text = "createGroup failed: $reason (will retry)" }
                hostScope.launch {
                    delay(1200)
                    createGroup()
                }
            }
        )
    }


    // Called when group is formed and we are group owner
    private fun waitForClient() {
        hostScope.launch {
            try {
                // Close any old sockets first
                SocketHolder.socket?.let { try { it.close() } catch (e: Exception) {} }
                SocketHolder.serverSocket?.let { try { it.close() } catch (e: Exception) {} }

                val port = 8988
                val server = ServerSocket(port)
                SocketHolder.serverSocket = server

                withContext(Dispatchers.Main) {
                    tvStatus.text = "Server listening on port 8080, waiting for client..."
                }
                Log.d("HostActivity", "Server listening on port $port")

                val client = server.accept() // blocks until client connects
                SocketHolder.socket = client
                SocketHolder.reader = client.getInputStream().bufferedReader()
                SocketHolder.writer = client.getOutputStream().bufferedWriter()

                // Send your name first
                val myName = intent.getStringExtra("userName") ?: "Host"
                SocketHolder.writer?.apply {
                    write(myName)
                    newLine()
                    flush()
                }

                // Read client's name
                val clientName = SocketHolder.reader?.readLine() ?: "Client"

                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@HostActivity)
                        .setTitle("Success")
                        .setMessage("Client connected successfully.")
                        .setPositiveButton("Go to Chat") { dialog, _ ->
                            if (SocketHolder.socket != null &&
                                SocketHolder.reader != null &&
                                SocketHolder.writer != null
                            ) {
                                val intent = Intent(this@HostActivity, ChatActivity::class.java).apply {
                                    putExtra("friendName", clientName) // or a known name
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@HostActivity, "Error: Socket not ready", Toast.LENGTH_LONG).show()
                            }
                        }
                        .setCancelable(false)
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Server socket error: ${e.message}"
                }
                Log.e("HostActivity", "Server error", e)
            }
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        hostScope.cancel()
        helper.cleanup()
    }
}
