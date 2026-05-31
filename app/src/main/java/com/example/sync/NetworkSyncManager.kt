package com.example.sync

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import com.example.data.model.Diary
import com.example.data.model.Folder
import com.example.data.model.Note
import com.example.data.model.SyncPayload
import com.example.data.repository.NoteRepository
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import kotlin.random.Random

data class DiscoveredDevice(val serviceName: String, val host: String, val port: Int)

class NetworkSyncManager(
    private val context: Context,
    private val repository: NoteRepository
) {
    private val nsdManager: NsdManager? = context.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val serviceType = "_aistudionotes._tcp."
    private var serviceName = "NotesSync_${Build.MODEL.replace(Regex("[^A-Za-z0-9]"), "")}_${Random.nextInt(1000)}"
    private var serverSocket: ServerSocket? = null
    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices

    private val _syncStatus = MutableStateFlow<String>("空闲")
    val syncStatus: StateFlow<String> = _syncStatus

    private val moshi = Moshi.Builder().build()
    private val payloadAdapter = moshi.adapter(SyncPayload::class.java)

    private val executor = Executors.newCachedThreadPool()

    fun startServerAndRegister() {
        if (serverSocket != null) return
        executor.submit {
            try {
                serverSocket = ServerSocket(0)
                val port = serverSocket!!.localPort
                Log.d("NetworkSync", "Server started on port $port")
                registerService(port)
                listenForConnections()
            } catch (e: Exception) {
                Log.e("NetworkSync", "Error starting server", e)
                _syncStatus.value = "启动服务失败: ${e.message}"
            }
        }
    }

    private fun registerService(port: Int) {
        val serviceInfo = NsdServiceInfo().apply {
            serviceName = this@NetworkSyncManager.serviceName
            serviceType = this@NetworkSyncManager.serviceType
            setPort(port)
        }

        registrationListener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(NsdServiceInfo: NsdServiceInfo) {
                serviceName = NsdServiceInfo.serviceName
                Log.d("NetworkSync", "Service registered: $serviceName")
                _syncStatus.value = "已在局域网开启可被发现"
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NetworkSync", "Registration failed: $errorCode")
            }

            override fun onServiceUnregistered(arg0: NsdServiceInfo) {
                Log.d("NetworkSync", "Service unregistered")
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.e("NetworkSync", "Unregistration failed: $errorCode")
            }
        }
        nsdManager?.let {
            try {
                it.registerService(serviceInfo, NsdManager.PROTOCOL_DNS_SD, registrationListener)
            } catch (t: Throwable) {
                Log.e("NetworkSync", "Failed to register NSD service natively", t)
                _syncStatus.value = "局域网服务不可用 (设备不支持)"
            }
        } ?: run {
            _syncStatus.value = "设备不支持局域网发现"
        }
    }

    private fun listenForConnections() {
        val server = serverSocket ?: return
        while (!server.isClosed) {
            try {
                val socket = server.accept()
                Log.d("NetworkSync", "Accepted connection from ${socket.inetAddress}")
                executor.submit {
                    handleClientConnection(socket)
                }
            } catch (e: Exception) {
                Log.e("NetworkSync", "Error accepting connection", e)
                try { Thread.sleep(1000) } catch (te: Exception) {}
            }
        }
    }

    private fun handleClientConnection(socket: Socket) {
        try {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
            val payloadString = reader.readLine()
            if (payloadString != null) {
                val payload = payloadAdapter.fromJson(payloadString)
                if (payload != null) {
                    when (payload.type) {
                        "REQUEST" -> {
                            _syncStatus.value = "正在发送数据给 ${payload.deviceName}..."
                            sendOurData(socket)
                        }
                        "SYNC_DATA" -> {
                            _syncStatus.value = "正在接收来自 ${payload.deviceName} 的数据..."
                            receiveData(payload)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("NetworkSync", "Client connection error", e)
        } finally {
            socket.close()
            _syncStatus.value = "空闲"
        }
    }

    private fun sendOurData(socket: Socket) {
        try {
            // Fetch data using kotlinx.coroutines.runBlocking inside normal thread may be problematic if we don't have scope.
            // We will collect flows in NoteViewModel directly instead, but here we can just use normal global scope or runBlocking
            kotlinx.coroutines.runBlocking {
                val notes = repository.allNotes.first()
                val folders = repository.allFolders.first()
                val diaries = repository.allDiaries.first()
                
                val outPayload = SyncPayload(
                    type = "SYNC_DATA",
                    notes = notes,
                    folders = folders,
                    diaries = diaries,
                    deviceName = Build.MODEL
                )
                
                val json = payloadAdapter.toJson(outPayload)
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(json)
            }
        } catch (e: Exception) {
            Log.e("NetworkSync", "Failed to send data", e)
        }
    }

    private fun receiveData(payload: SyncPayload) {
        kotlinx.coroutines.runBlocking {
            try {
                // simple merge: just insert what is not existing by id, or overwrite.
                // For simplicity, we just insert all items ignoring ID conflicts with a new ID (unless we use insertOnConflict REPLACE)
                // Wait, Note has autoGenerate=true. Room might replace if we have same ID, or we can just reset IDs to 0 to copy.
                // Let's just insert as new or update existing.
                payload.folders.forEach { f ->
                    val existing = repository.getFolderByNameAndParent(f.name, f.parentId)
                    if (existing == null) {
                        repository.insertFolder(f.copy(id = 0L))
                    }
                }
                
                payload.notes.forEach { n ->
                    val existing = repository.getNoteById(n.id)
                    if (existing != null) {
                        repository.updateNote(n)
                    } else {
                        repository.insertNote(n.copy(id = 0L))
                    }
                }
                
                payload.diaries.forEach { d ->
                    val existing = repository.getDiaryByDate(d.date)
                    if (existing != null) {
                        repository.insertDiary(d.copy(content = existing.content + "\n\n[Synced]\n" + d.content))
                    } else {
                        repository.insertDiary(d)
                    }
                }
                _syncStatus.value = "同步完成！"
                kotlinx.coroutines.delay(2000)
                _syncStatus.value = "空闲"
            } catch (e: Exception) {
                Log.e("NetworkSync", "Receive data error", e)
                _syncStatus.value = "同步失败"
            }
        }
    }

    fun discoverServices() {
        if (discoveryListener != null) return
        _discoveredDevices.value = emptyList()
        _syncStatus.value = "正在扫描局域网..."
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d("NetworkSync", "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d("NetworkSync", "Service found: $service")
                if (service.serviceType == serviceType && service.serviceName != serviceName) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e("NetworkSync", "Resolve failed $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d("NetworkSync", "Resolve Succeeded: $serviceInfo")
                            val newDevice = DiscoveredDevice(
                                serviceInfo.serviceName,
                                serviceInfo.host.hostAddress ?: "",
                                serviceInfo.port
                            )
                            val currentList = _discoveredDevices.value.toMutableList()
                            if (currentList.none { it.serviceName == newDevice.serviceName }) {
                                currentList.add(newDevice)
                                _discoveredDevices.value = currentList
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e("NetworkSync", "Service lost: $service")
                _discoveredDevices.value = _discoveredDevices.value.filter { it.serviceName != service.serviceName }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i("NetworkSync", "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NetworkSync", "Discovery failed: Error code:$errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e("NetworkSync", "Stop discovery failed: Error code:$errorCode")
                nsdManager?.stopServiceDiscovery(this)
            }
        }
        nsdManager?.let {
            try {
                it.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            } catch (t: Throwable) {
                Log.e("NetworkSync", "Discover services failed natively", t)
                _syncStatus.value = "无法扫描 (设备不支持)"
            }
        } ?: run {
             _syncStatus.value = "设备不支持局域网扫描"
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            try {
                nsdManager?.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.e("NetworkSync", "Stop discovery error", e)
            }
            discoveryListener = null
        }
    }

    fun syncWithDevice(device: DiscoveredDevice) {
        _syncStatus.value = "正在连接到 ${device.serviceName}..."
        executor.submit {
            var socket: Socket? = null
            try {
                socket = Socket(device.host, device.port)
                val outPayload = SyncPayload(type = "REQUEST", deviceName = Build.MODEL)
                val json = payloadAdapter.toJson(outPayload)
                
                val writer = PrintWriter(socket.getOutputStream(), true)
                writer.println(json)
                
                val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                val responseString = reader.readLine()
                
                if (responseString != null) {
                    val payload = payloadAdapter.fromJson(responseString)
                    if (payload != null && payload.type == "SYNC_DATA") {
                        _syncStatus.value = "成功获取数据，正在应用..."
                        receiveData(payload)
                    }
                }
            } catch (e: Exception) {
                Log.e("NetworkSync", "Connection failed", e)
                _syncStatus.value = "连接 ${device.serviceName} 失败"
            } finally {
                socket?.close()
            }
        }
    }

    fun cleanup() {
        stopDiscovery()
        try {
            registrationListener?.let { nsdManager?.unregisterService(it) }
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e("NetworkSync", "Cleanup error", e)
        }
    }
}
