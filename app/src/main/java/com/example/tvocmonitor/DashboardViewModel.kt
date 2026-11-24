package com.example.tvocmonitor

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.eclipse.paho.client.mqttv3.IMqttActionListener
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken
import org.eclipse.paho.client.mqttv3.IMqttToken
import org.eclipse.paho.client.mqttv3.MqttAsyncClient
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.json.JSONObject
import java.util.UUID
import kotlin.math.roundToInt

/** Semua variabel yang dikirim M5Stack (TVOC_MQTT.m5f). */
data class SensorData(
    val temperature: Double? = null,
    val tvoc: Int? = null,
    val tvocBaseline: Int? = null,
    val eco2: Int? = null,
    val eco2Baseline: Int? = null,
    val h2: Int? = null,
    val ethanol: Int? = null,
    val updatedAt: Long = 0L,
)

data class HistoryRow(val time: Long, val data: SensorData)

enum class ConnStatus(val label: String) {
    DISCONNECTED("Disconnected"),
    CONNECTING("Connecting…"),
    CONNECTED("Connected"),
    ERROR("Error"),
}

data class MqttConfig(
    val host: String = DEFAULT_HOST,
    val port: Int = DEFAULT_PORT,
    val baseTopic: String = DEFAULT_TOPIC,
) {
    companion object {
        const val DEFAULT_HOST = "test.mosquitto.org"
        const val DEFAULT_PORT = 1883
        const val DEFAULT_TOPIC = "m5fire/tvoc/b7de4536"
    }
}

class DashboardViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("mqtt_config", Context.MODE_PRIVATE)

    private val _config = MutableStateFlow(loadConfig())
    val config: StateFlow<MqttConfig> = _config.asStateFlow()

    private val _status = MutableStateFlow(ConnStatus.DISCONNECTED)
    val status: StateFlow<ConnStatus> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _data = MutableStateFlow(SensorData())
    val data: StateFlow<SensorData> = _data.asStateFlow()

    private val _history = MutableStateFlow<List<HistoryRow>>(emptyList())
    val history: StateFlow<List<HistoryRow>> = _history.asStateFlow()

    private var client: MqttAsyncClient? = null
    private val clientLock = Mutex()
    private var retryJob: Job? = null

    init {
        reconnect()
    }

    // ---------------------------------------------------------------- config
    private fun loadConfig() = MqttConfig(
        host = prefs.getString("host", MqttConfig.DEFAULT_HOST) ?: MqttConfig.DEFAULT_HOST,
        port = prefs.getInt("port", MqttConfig.DEFAULT_PORT),
        baseTopic = prefs.getString("topic", MqttConfig.DEFAULT_TOPIC) ?: MqttConfig.DEFAULT_TOPIC,
    )

    fun saveConfig(cfg: MqttConfig) {
        prefs.edit()
            .putString("host", cfg.host.trim())
            .putInt("port", cfg.port)
            .putString("topic", cfg.baseTopic.trim().trimEnd('/'))
            .apply()
        _config.value = loadConfig()
        _data.value = SensorData()
        reconnect()
    }

    fun clearHistory() {
        _history.value = emptyList()
    }

    // ---------------------------------------------------------------- MQTT
    fun reconnect() {
        retryJob?.cancel()
        viewModelScope.launch(Dispatchers.IO) {
            clientLock.withLock {
                closeClient()
                openClient(_config.value)
            }
        }
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        retryJob = viewModelScope.launch {
            delay(5_000)
            if (_status.value != ConnStatus.CONNECTED) reconnect()
        }
    }

    private fun openClient(cfg: MqttConfig) {
        _status.value = ConnStatus.CONNECTING
        _error.value = null
        try {
            val clientId = "android-tvoc-" + UUID.randomUUID().toString().take(8)
            val c = MqttAsyncClient("tcp://${cfg.host}:${cfg.port}", clientId, MemoryPersistence())
            c.setCallback(object : MqttCallbackExtended {
                override fun connectComplete(reconnect: Boolean, serverURI: String?) {
                    _status.value = ConnStatus.CONNECTED
                    try {
                        c.subscribe("${cfg.baseTopic}/#", 0)
                    } catch (e: Exception) {
                        _error.value = "Subscribe gagal: ${e.message}"
                    }
                }

                override fun connectionLost(cause: Throwable?) {
                    // automaticReconnect = true -> Paho akan mencoba lagi sendiri
                    _status.value = ConnStatus.CONNECTING
                    _error.value = cause?.message
                }

                override fun messageArrived(topic: String, message: MqttMessage) {
                    handleMessage(cfg.baseTopic, topic, String(message.payload, Charsets.UTF_8))
                }

                override fun deliveryComplete(token: IMqttDeliveryToken?) {}
            })
            val options = MqttConnectOptions().apply {
                isAutomaticReconnect = true
                isCleanSession = true
                connectionTimeout = 10
                keepAliveInterval = 60
            }
            c.connect(options, null, object : IMqttActionListener {
                override fun onSuccess(asyncActionToken: IMqttToken?) {}
                override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
                    _status.value = ConnStatus.ERROR
                    _error.value = exception?.message ?: "Gagal terhubung"
                    scheduleRetry()
                }
            })
            client = c
        } catch (e: Exception) {
            _status.value = ConnStatus.ERROR
            _error.value = e.message
            scheduleRetry()
        }
    }

    private fun closeClient() {
        val c = client ?: return
        client = null
        try {
            if (c.isConnected) c.disconnectForcibly(0, 1_000)
        } catch (ignored: Exception) {
        }
        try {
            c.close(true)
        } catch (ignored: Exception) {
        }
        _status.value = ConnStatus.DISCONNECTED
    }

    private fun String.toIntValue(): Int? = trim().toDoubleOrNull()?.roundToInt()

    private fun handleMessage(base: String, topic: String, payload: String) {
        val key = topic.removePrefix("$base/")
        val now = System.currentTimeMillis()
        if (key == "json") {
            try {
                val o = JSONObject(payload)
                fun intOf(name: String): Int? =
                    if (o.has(name) && !o.isNull(name)) o.optDouble(name).takeIf { !it.isNaN() }?.roundToInt() else null
                val d = SensorData(
                    temperature = if (o.has("temperature")) o.optDouble("temperature").takeIf { !it.isNaN() } else null,
                    tvoc = intOf("tvoc"),
                    tvocBaseline = intOf("tvoc_baseline"),
                    eco2 = intOf("eco2"),
                    eco2Baseline = intOf("eco2_baseline"),
                    h2 = intOf("h2"),
                    ethanol = intOf("ethanol"),
                    updatedAt = now,
                )
                _data.value = d
                _history.update { (it + HistoryRow(now, d)).takeLast(MAX_HISTORY) }
            } catch (e: Exception) {
                _error.value = "JSON tidak valid: ${e.message}"
            }
            return
        }
        _data.update { d ->
            when (key) {
                "temperature" -> d.copy(temperature = payload.trim().toDoubleOrNull() ?: d.temperature, updatedAt = now)
                "tvoc" -> d.copy(tvoc = payload.toIntValue() ?: d.tvoc, updatedAt = now)
                "tvoc_baseline" -> d.copy(tvocBaseline = payload.toIntValue() ?: d.tvocBaseline, updatedAt = now)
                "eco2" -> d.copy(eco2 = payload.toIntValue() ?: d.eco2, updatedAt = now)
                "eco2_baseline" -> d.copy(eco2Baseline = payload.toIntValue() ?: d.eco2Baseline, updatedAt = now)
                "h2" -> d.copy(h2 = payload.toIntValue() ?: d.h2, updatedAt = now)
                "ethanol" -> d.copy(ethanol = payload.toIntValue() ?: d.ethanol, updatedAt = now)
                else -> d
            }
        }
    }

    override fun onCleared() {
        val c = client
        client = null
        Thread {
            try {
                if (c?.isConnected == true) c.disconnectForcibly(0, 1_000)
                c?.close(true)
            } catch (ignored: Exception) {
            }
        }.start()
        super.onCleared()
    }

    companion object {
        const val MAX_HISTORY = 1_000
    }
}
