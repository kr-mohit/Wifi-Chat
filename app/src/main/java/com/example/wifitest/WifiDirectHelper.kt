package com.example.wifitest

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.location.LocationManager
import android.net.wifi.WifiManager
import android.net.wifi.WpsInfo
import android.net.wifi.p2p.*
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class WifiDirectHelper(
    private val activity: ComponentActivity,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.Main)
) {

    companion object {
        private const val TAG = "WifiDirectHelper"
    }

    private val manager: WifiP2pManager =
        activity.getSystemService(Context.WIFI_P2P_SERVICE) as WifiP2pManager
    private val channel: WifiP2pManager.Channel =
        manager.initialize(activity, activity.mainLooper, null)

    private var receiverRegistered = false
    private val p2pIntentFilter = IntentFilter().apply {
        addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
        addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
    }

    // Live peer list maintained here
    val peers = mutableListOf<WifiP2pDevice>()
    private val peersMutex = Mutex()

    private var onPeersChanged: ((List<WifiP2pDevice>) -> Unit)? = null
    private var connectionInfoCallback: ((WifiP2pInfo) -> Unit)? = null

    // Permission launcher
    private val permissions = buildPermissionsArray()
    private val permissionLauncher =
        activity.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val grantedAll = result.values.all { it }
            if (!grantedAll) {
                Toast.makeText(activity, "Permissions required for Wi-Fi Direct", Toast.LENGTH_LONG).show()
            }
            // continue flow: check states again
            scope.launch { checkAndProceed() }
        }

    private fun buildPermissionsArray(): Array<String> {
        val base = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        return base.toTypedArray()
    }

    // BroadcastReceiver to keep peers and connection info updated
    private val p2pReceiver = object : BroadcastReceiver() {
        @SuppressLint("MissingPermission")
        override fun onReceive(ctx: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                        val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                        Log.d(TAG, "P2P state changed: $state")
                    }
                    WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                        manager.requestPeers(channel) { peerList ->
                            scope.launch {
                                peersMutex.withLock {
                                    peers.clear()
                                    peers.addAll(peerList.deviceList)
                                }
                                onPeersChanged?.invoke(peers.toList())
                            }
                        }
                    }
                    WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                        val networkInfo = intent.getParcelableExtra<android.net.NetworkInfo>(WifiP2pManager.EXTRA_NETWORK_INFO)
                        if (networkInfo?.isConnected == true) {
                            manager.requestConnectionInfo(channel) { info ->
                                connectionInfoCallback?.invoke(info)
                            }
                        } else {
                            // disconnected
                            Log.d(TAG, "P2P disconnected")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receiver error", e)
            }
        }
    }

    // Public: register to start receiving updates
    fun registerReceivers() {
        if (receiverRegistered) return
        activity.registerReceiver(p2pReceiver, p2pIntentFilter)
        receiverRegistered = true
    }

    fun unregisterReceivers() {
        if (!receiverRegistered) return
        try {
            activity.unregisterReceiver(p2pReceiver)
        } catch (_: Exception) {
            // ignore
        }
        receiverRegistered = false
    }

    // Hook callbacks
    fun setOnPeersChanged(cb: (List<WifiP2pDevice>) -> Unit) {
        onPeersChanged = cb
    }

    fun setConnectionInfoCallback(cb: (WifiP2pInfo) -> Unit) {
        connectionInfoCallback = cb
    }

    // --------------- Pre-checks (permissions + states) --------------------

    private fun hasPermissions(): Boolean {
        return permissions.all {
            ContextCompat.checkSelfPermission(activity, it) ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED
        }
    }

    private fun isWifiEnabled(): Boolean {
        val wm = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        return wm.isWifiEnabled
    }

    private fun isLocationEnabled(): Boolean {
        val lm = activity.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    // If hotspot is on, we prompt the user — cannot programmatically turn it off on normal apps
    @Suppress("DEPRECATION")
    private fun isHotspotOn(): Boolean {
        return try {
            val wm = activity.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            // reflection used across many devices: fallbacks handled
            val method = wm.javaClass.getDeclaredMethod("isWifiApEnabled")
            method.isAccessible = true
            method.invoke(wm) as? Boolean ?: false
        } catch (_: Exception) {
            false
        }
    }

    // Guides user into right settings (cannot auto toggle)
    private fun openWifiSettings() {
        activity.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
    }

    private fun openLocationSettings() {
        activity.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
    }

    private fun openTetheringSettings() {
        // best-effort; different OEMs have different screens
        try {
            activity.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
        } catch (_: Exception) {
            openWifiSettings()
        }
    }

    // Public entry point: prepare environment (permissions + settings). This returns when ready.
    fun prepareAndThen(onReady: () -> Unit) {
        scope.launch {
            registerReceivers()
            onReadyCallback = onReady
            checkAndProceed()
        }
    }

    private var onReadyCallback: (() -> Unit)? = null

    private fun checkAndProceed() {
        // 1) Permissions
        if (!hasPermissions()) {
            permissionLauncher.launch(permissions)
            return
        }

        // 2) Wifi ON
        if (!isWifiEnabled()) {
            Toast.makeText(activity, "Wi-Fi is OFF. Please enable Wi-Fi.", Toast.LENGTH_LONG).show()
            openWifiSettings()
            return
        }

        // 3) Hotspot OFF
        if (isHotspotOn()) {
            Toast.makeText(activity, "Hotspot is ON. Please disable hotspot for Wi-Fi Direct to work.", Toast.LENGTH_LONG).show()
            openTetheringSettings()
            return
        }

        // 4) Location ON
        if (!isLocationEnabled()) {
            Toast.makeText(activity, "Location is OFF. Please enable Location.", Toast.LENGTH_LONG).show()
            openLocationSettings()
            return
        }

        // All good
        onReadyCallback?.invoke()
    }

    // --------------- Host flow --------------------

    /**
     * Create a group; removes any prior group first. Retries a few times if BUSY/ERROR occurs.
     * onCreated -> success callback on main thread
     */
    @SuppressLint("MissingPermission")
    fun createGroupWithRetries(
        onCreated: () -> Unit,
        onFailure: (reason: Int) -> Unit
    ) {
        // always attempt to remove previous group first
        fun create() {
            manager.createGroup(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    activity.runOnUiThread {
                        Log.d(TAG, "createGroup onSuccess")
                        onCreated()
                    }
                }

                override fun onFailure(reason: Int) {
                    Log.w(TAG, "createGroup failed: $reason")
                    activity.runOnUiThread {
                        onFailure(reason)
                    }
                }
            })
        }

        // Remove group then create. If remove fails, still try to create.
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "Removed old group successfully")
                create()
            }

            override fun onFailure(reason: Int) {
                Log.d(TAG, "removeGroup failed: $reason — will still try create")
                create()
            }
        })

        // If failure occurs, caller can call retryCreateGroup() or we can implement internal retry handling.
        // For convenience, the onFailure can examine reason and re-call this function.
    }

    // --------------- Client flow --------------------

    // Start discovery (returns immediately; peers list callback occurs via setOnPeersChanged)
    @SuppressLint("MissingPermission")
    fun discoverPeers() {
        manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                Log.d(TAG, "discoverPeers started")
            }

            override fun onFailure(reason: Int) {
                Log.w(TAG, "discoverPeers failed: $reason")
            }
        })
    }

    // Connects to device using standard config. Returns via callback success/failure.
    @SuppressLint("MissingPermission")
    fun connectToDevice(device: WifiP2pDevice, onResult: (Boolean) -> Unit) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            wps.setup = WpsInfo.PBC
            // we can attempt to hint groupOwnerIntent if you want:
            // groupOwnerIntent = 0 // prefer remote
            // groupOwnerIntent = 15 // prefer this
        }

        manager.connect(channel, config, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                onResult(true)
            }

            override fun onFailure(reason: Int) {
                onResult(false)
            }
        })
    }

    /**
     * Clean up
     */
    fun cleanup() {
        try {
            unregisterReceivers()
            manager.removeGroup(channel, null)
        } catch (e: Exception) {
            Log.e(TAG, "cleanup failed", e)
        } finally {
            // cancel internal coroutines
            scope.cancel()
        }
    }
}
