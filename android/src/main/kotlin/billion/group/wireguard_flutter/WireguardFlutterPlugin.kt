package billion.group.wireguard_flutter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.Log
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.BackendException
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.PluginRegistry
import kotlinx.coroutines.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class WireguardFlutterPlugin : FlutterPlugin, ActivityAware, PluginRegistry.ActivityResultListener, WireGuardHostApi {
    private lateinit var context: Context
    private var activity: Activity? = null
    private val scope = CoroutineScope(Job() + Dispatchers.Main.immediate)
    private var backend: Backend? = null
    private val futureBackend = CompletableDeferred<Backend>()

    private lateinit var tunnelName: String
    private var havePermission = false
    private var config: com.wireguard.config.Config? = null
    private var tunnel: WireGuardTunnel? = null

    private var vpnStageSink: EventChannel.EventSink? = null

    private val TAG = "WireguardFlutterPlugin"

    override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        context = binding.applicationContext
        WireGuardHostApi.setUp(binding.binaryMessenger, this)

        val events = EventChannel(binding.binaryMessenger, "billion.group.wireguard_flutter/wgstage")
        events.setStreamHandler(object : EventChannel.StreamHandler {
            override fun onListen(arguments: Any?, events: EventChannel.EventSink?) {
                vpnStageSink = events
            }

            override fun onCancel(arguments: Any?) {
                vpnStageSink = null
            }
        })

        scope.launch(Dispatchers.IO) {
            try {
                backend = GoBackend(context)
                futureBackend.complete(backend!!)
            } catch (e: Throwable) {
                Log.e(TAG, Log.getStackTraceString(e))
            }
        }
    }

    override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
        WireGuardHostApi.setUp(binding.binaryMessenger, null)
    }

    // ActivityAware methods
    override fun onAttachedToActivity(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivity() {
        activity = null
    }

    override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
        activity = binding.activity
        binding.addActivityResultListener(this)
    }

    override fun onDetachedFromActivityForConfigChanges() {
        activity = null
    }

    // ActivityResultListener
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            havePermission = resultCode == Activity.RESULT_OK
            return true
        }
        return false
    }

    private fun updateStage(stage: String) {
        scope.launch(Dispatchers.Main) {
            vpnStageSink?.success(stage)
        }
    }

    private fun checkPermission() {
        val intent = GoBackend.VpnService.prepare(context)
        if (intent != null) {
            activity?.startActivityForResult(intent, PERMISSIONS_REQUEST_CODE)
        } else {
            havePermission = true
        }
    }

    // Pigeon API Implementation
    override fun initialize(interfaceName: String, callback: (Result<Unit>) -> Unit) {
        if (Tunnel.isNameInvalid(interfaceName)) {
            callback(Result.failure(Exception("Invalid tunnel name")))
            return
        }
        tunnelName = interfaceName
        checkPermission()
        callback(Result.success(Unit))
    }

    override fun startVpn(interfaceName: String, serverAddress: String, interfaceConfig: WgInterfaceConfig, peers: List<WgPeerConfig?>, providerBundleIdentifier: String, callback: (Result<Unit>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                if (!havePermission) {
                    checkPermission()
                    throw Exception("VPN permission not granted")
                }
                updateStage("prepare")

                val configBuilder = com.wireguard.config.Config.Builder()
                val interfaceBuilder = com.wireguard.config.Interface.Builder()

                interfaceConfig.privateKey?.let { interfaceBuilder.setPrivateKey(it) }
                interfaceConfig.addresses?.let { interfaceBuilder.parseAddresses(it.filterNotNull().joinToString(", ")) }
                interfaceConfig.dnsServers?.let { interfaceBuilder.parseDnsServers(it.filterNotNull().joinToString(", ")) }

                interfaceConfig.allowedApplications?.let { apps ->
                    interfaceBuilder.addIncludedApplications(apps.filterNotNull())
                }
                interfaceConfig.disallowedApplications?.let { apps ->
                    interfaceBuilder.addExcludedApplications(apps.filterNotNull())
                }

                configBuilder.setInterface(interfaceBuilder.build())

                peers.filterNotNull().forEach { peer ->
                    val peerBuilder = com.wireguard.config.Peer.Builder()
                    peer.publicKey?.let { peerBuilder.setPublicKey(it) }
                    peer.presharedKey?.let { peerBuilder.setPresharedKey(it) }
                    peer.endpoint?.let { peerBuilder.parseEndpoint(it) }
                    peer.allowedIps?.let { peerBuilder.parseAllowedIPs(it.filterNotNull().joinToString(", ")) }
                    peer.persistentKeepalive?.let { peerBuilder.setPersistentKeepalive(it.toString()) }
                    configBuilder.addPeer(peerBuilder.build())
                }

                val finalConfig = configBuilder.build()

                updateStage("connecting")
                futureBackend.await().setState(
                    tunnel(tunnelName) { state ->
                        val stage = when (state) {
                            Tunnel.State.UP -> "connected"
                            Tunnel.State.DOWN -> "disconnected"
                            else -> "wait_connection"
                        }
                        updateStage(stage)
                    }, Tunnel.State.UP, finalConfig
                )
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun stopVpn(callback: (Result<Unit>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                if (futureBackend.await().runningTunnelNames.isEmpty()) {
                    updateStage("disconnected")
                    throw Exception("Tunnel is not running")
                }
                updateStage("disconnecting")
                futureBackend.await().setState(tunnel(tunnelName), Tunnel.State.DOWN, config)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun stage(callback: (Result<String>) -> Unit) {
        // This should be handled by the event channel, but we can provide the last known state.
        // A proper implementation would require a more complex state management.
        callback(Result.success("unknown"))
    }

    override fun refreshStage(callback: (Result<Unit>) -> Unit) {
        // This method might not be necessary with an event channel, but we can try to poke the state.
        scope.launch(Dispatchers.IO) {
            try {
                val state = futureBackend.await().getState(tunnel(tunnelName))
                val stage = when (state) {
                    Tunnel.State.UP -> "connected"
                    Tunnel.State.DOWN -> "disconnected"
                    else -> "wait_connection"
                }
                updateStage(stage)
                callback(Result.success(Unit))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun getStats(tunnelName: String, callback: (Result<Stats>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val statistics = futureBackend.await().getStatistics(tunnel(tunnelName))
                val stats = Stats(rx = statistics.totalRx(), tx = statistics.totalTx())
                callback(Result.success(stats))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    override fun getInstalledApplications(callback: (Result<List<InstalledApp?>>) -> Unit) {
        scope.launch(Dispatchers.IO) {
            try {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val appList = apps.mapNotNull { app ->
                    if (app.packageName == context.packageName) return@mapNotNull null
                    val icon = try {
                        val drawable = app.loadIcon(pm)
                        if (drawable is BitmapDrawable) {
                            val bitmap = drawable.bitmap
                            val stream = ByteArrayOutputStream()
                            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                            stream.toByteArray()
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                    InstalledApp(
                        name = app.loadLabel(pm).toString(),
                        packageName = app.packageName,
                        icon = icon
                    )
                }
                callback(Result.success(appList))
            } catch (e: Exception) {
                callback(Result.failure(e))
            }
        }
    }

    private fun tunnel(name: String, onStateChanged: ((Tunnel.State) -> Unit)? = null): WireGuardTunnel {
        if (tunnel == null) {
            tunnel = WireGuardTunnel(name, onStateChanged)
        }
        return tunnel as WireGuardTunnel
    }
}

const val PERMISSIONS_REQUEST_CODE = 10014

class WireGuardTunnel(
    private val name: String, private val onStateChanged: ((Tunnel.State) -> Unit)? = null
) : Tunnel {
    override fun getName() = name
    override fun onStateChange(newState: Tunnel.State) {
        onStateChanged?.invoke(newState)
    }
}