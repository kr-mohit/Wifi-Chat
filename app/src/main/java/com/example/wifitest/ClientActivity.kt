package com.example.wifitest

import android.content.Intent
import android.net.wifi.p2p.WifiP2pDevice
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import android.widget.Button
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket

class ClientActivity : AppCompatActivity() {

    private lateinit var listDevices: ListView
    private lateinit var tvStatus: TextView
    private lateinit var btnScan: Button

    private lateinit var helper: WifiDirectHelper
    private val clientScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val devices = mutableListOf<WifiP2pDevice>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_client)

        listDevices = findViewById(R.id.listDevices)
        tvStatus = findViewById(R.id.tvClientStatus)
        btnScan = findViewById(R.id.btnClientScan)

        helper = WifiDirectHelper(this)

        btnScan.setOnClickListener {
            tvStatus.text = "Preparing..."
            helper.prepareAndThen {
                runOnUiThread { tvStatus.text = "Ready — discovering peers..." }
                helper.discoverPeers()
            }
        }

        helper.setOnPeersChanged { newPeers ->
            runOnUiThread {
                devices.clear()
                devices.addAll(newPeers)
                listDevices.adapter = ArrayAdapter(
                    this,
                    android.R.layout.simple_list_item_1,
                    devices.map { it.deviceName }
                )
                tvStatus.text = if (devices.isEmpty()) "No devices found" else "Select a device"
            }
        }

        listDevices.setOnItemClickListener { _, _, pos, _ ->
            val dev = devices[pos]
            tvStatus.text = "Connecting to ${dev.deviceName}..."
            helper.connectToDevice(dev) { ok ->
                runOnUiThread {
                    if (!ok) {
                        tvStatus.text = "Failed to initiate connection"
                    } else {
                        // wait for connection info via helper's connectionInfoCallback
                        tvStatus.text = "Connection initiated — waiting for connection info..."
                    }
                }
            }
        }

        helper.setConnectionInfoCallback { info ->
            if (info.groupFormed && !info.isGroupOwner) {
                // connect socket to group owner ip
                val hostIp = info.groupOwnerAddress?.hostAddress ?: return@setConnectionInfoCallback
                runOnUiThread { tvStatus.text = "Group formed, connecting to host $hostIp ..." }
                connectToSocket(hostIp)
            }
        }

        helper.registerReceivers()
    }

    private fun connectToSocket(ip: String) {
        clientScope.launch {
            try {
                // Ensure host is ready
                delay(500)

                val port = 8988 // must match host
                val socket = Socket()
                socket.connect(InetSocketAddress(ip, port), 5000)

                SocketHolder.socket = socket
                SocketHolder.reader = socket.getInputStream().bufferedReader()
                SocketHolder.writer = socket.getOutputStream().bufferedWriter()

                // Send your name
                val myName = intent.getStringExtra("userName") ?: "Client"
                SocketHolder.writer?.apply {
                    write(myName)
                    newLine()
                    flush()
                }

                // Read host's name
                val hostName = SocketHolder.reader?.readLine() ?: "Host"

                withContext(Dispatchers.Main) {
                    AlertDialog.Builder(this@ClientActivity)
                        .setTitle("Success")
                        .setMessage("Connected to host!")
                        .setPositiveButton("Go to chat") { _, _ ->
                            if (SocketHolder.socket != null &&
                                SocketHolder.reader != null &&
                                SocketHolder.writer != null
                            ) {
                                val intent = Intent(this@ClientActivity, ChatActivity::class.java).apply {
                                    putExtra("friendName", hostName) // use Wi-Fi Direct device name
                                    // optionally: putExtra("friendImageUri", user image if available)
                                }
                                startActivity(intent)
                            } else {
                                Toast.makeText(this@ClientActivity, "Error: Socket not ready", Toast.LENGTH_LONG).show()
                            }
                        }
                        .show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    tvStatus.text = "Socket error: ${e.message}"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        clientScope.cancel()
        helper.cleanup()
    }
}
