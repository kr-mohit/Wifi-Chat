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
            tvStatus.text = getString(R.string.preparing)
            // Prepare will check permissions, Wi-Fi, location, hotspot etc. and then call our onReady
            helper.prepareAndThen {
                runOnUiThread { tvStatus.text = getString(R.string.environment_ok_creating_group) }
                createGroup()
            }
        }

        // register to receive connection info changes so we can take action
        helper.setConnectionInfoCallback { info ->
            if (info.groupFormed && info.isGroupOwner) {
                // once group is formed and we're owner, open server socket
                runOnUiThread { tvStatus.text =
                    getString(R.string.group_formed_starting_server_socket) }
                waitForClient()
            }
        }

        helper.registerReceivers()
    }

    @SuppressLint("MissingPermission")
    private fun createGroup() {
        helper.createGroupWithRetries(
            onCreated = {
                runOnUiThread { tvStatus.text = getString(R.string.group_created_waiting_for_client) }
            },
            onFailure = { reason ->
                runOnUiThread { tvStatus.text =
                    getString(R.string.creategroup_failed_will_retry_, reason) }
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
                SocketHolder.socket?.let { try { it.close() } catch (_: Exception) {} }
                SocketHolder.serverSocket?.let { try { it.close() } catch (_: Exception) {} }

                val port = 8988
                val server = ServerSocket(port)
                SocketHolder.serverSocket = server

                withContext(Dispatchers.Main) {
                    tvStatus.text =
                        getString(R.string.server_listening_on_port_waiting_for_client_, port)
                }
                Log.d("HostActivity", getString(R.string.server_listening_on_port_, port))

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
                        .setPositiveButton("Go to Chat") { _, _ ->
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
                    tvStatus.text = getString(R.string.server_socket_error_, e.message)
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
