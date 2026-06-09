package dev.dubhe.gugle.chat.android.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.dubhe.gugle.chat.android.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("guglechat", 0)

    // State — declared before init to avoid NPE
    var wsManager: WebSocketManager? = null
    var voiceCall: VoiceCallManager? = null
    private val _inVoiceCall = MutableStateFlow(false)
    val inVoiceCall = _inVoiceCall.asStateFlow()
    private val _remoteVoiceStream = MutableStateFlow<org.webrtc.MediaStream?>(null)
    val remoteVoiceStream = _remoteVoiceStream.asStateFlow()
    private val _token = MutableStateFlow<String?>(prefs.getString("token", null))
    val token = _token.asStateFlow()
    private val _channels = MutableStateFlow<List<Channel>>(emptyList())
    val channels = _channels.asStateFlow()
    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages = _messages.asStateFlow()
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel = _currentChannel.asStateFlow()
    private val _loggedIn = MutableStateFlow(prefs.getString("token", null) != null)
    val loggedIn = _loggedIn.asStateFlow()

    init {
        if (prefs.getString("token", null) != null) {
            viewModelScope.launch {
                loadChannels()
                connectWs()
            }
        }
    }

    private fun buildBaseUrl(): String {
        val custom = prefs.getString("backend_url", "") ?: ""
        val url = if (custom.isNotEmpty()) custom else "http://10.0.2.2:3250"
        return if (url.endsWith("/")) url else "$url/"
    }

    private fun buildApi(): ApiService {
        val client =
            OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).addInterceptor { chain ->
                    val t = prefs.getString("token", "") ?: ""
                    val req =
                        chain.request().newBuilder().header("Authorization", "Bearer $t").build()
                    chain.proceed(req)
                }.build()
        return Retrofit.Builder().baseUrl(buildBaseUrl()).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()
            .create(ApiService::class.java)
    }

    fun setBackendUrl(url: String) {
        prefs.edit().putString("backend_url", url).commit()
    }

    fun login(username: String, password: String, backendUrl: String, onError: (String) -> Unit) {
        if (backendUrl.isNotEmpty()) setBackendUrl(backendUrl)
        val api = buildApi()
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.login(LoginRequest(username, password)).execute()
                }
                if (res.isSuccessful && res.body()?.code == 200) {
                    val data = res.body()!!.data
                    prefs.edit().putString("token", data.token).commit()
                    _token.value = data.token
                    _loggedIn.value = true
                    connectWs()
                    loadChannels(api)
                } else onError(res.body()?.message ?: "Login failed")
            } catch (e: Exception) {
                Log.e("GugleChat", "Login error", e)
                onError(e.message ?: e.toString())
            }
        }
    }

    fun register(
        username: String,
        email: String,
        password: String,
        backendUrl: String,
        onError: (String) -> Unit
    ) {
        if (backendUrl.isNotEmpty()) setBackendUrl(backendUrl)
        val api = buildApi()
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) {
                    api.register(
                        RegisterRequest(
                            username, email, password
                        )
                    ).execute()
                }
                if (res.isSuccessful && res.body()?.code == 200) {
                    val data = res.body()!!.data
                    prefs.edit().putString("token", data.token).commit()
                    _token.value = data.token
                    _loggedIn.value = true
                    connectWs()
                    loadChannels(api)
                } else onError(res.body()?.message ?: "Register failed")
            } catch (e: Exception) {
                Log.e("GugleChat", "Register error", e)
                onError(e.message ?: e.toString())
            }
        }
    }

    fun logout() {
        wsManager?.disconnect()
        prefs.edit().remove("token").apply()
        _token.value = null
        _loggedIn.value = false
    }

    fun loadChannels(api: ApiService = buildApi()) {
        viewModelScope.launch {
            try {
                val res = withContext(Dispatchers.IO) { api.getChannels().execute() }
                if (res.isSuccessful && res.body()?.code == 200) {
                    _channels.value = res.body()!!.data
                    Log.d("GugleChat", "Loaded ${_channels.value.size} channels")
                } else Log.e("GugleChat", "Channels failed: ${res.code()} ${res.body()?.message}")
            } catch (e: Exception) {
                Log.e("GugleChat", "Channels error", e)
            }
        }
    }

    fun selectChannel(channel: Channel) {
        _currentChannel.value = channel
        wsManager?.subscribe(channel.id)
        loadMessages(channel.id)
    }

    fun loadMessages(channelId: Long, before: Long? = null) {
        val api = buildApi()
        viewModelScope.launch {
            try {
                val res =
                    withContext(Dispatchers.IO) { api.getMessages(channelId, before).execute() }
                if (res.isSuccessful && res.body()?.code == 200) {
                    _messages.value = res.body()!!.data.reversed()
                }
            } catch (e: Exception) {
                Log.e("GugleChat", "Messages error", e)
            }
        }
    }

    fun sendMessage(content: String) {
        val ch = _currentChannel.value ?: return
        wsManager?.sendMessage(ch.id, content)
    }

    fun startVoiceCall(channel: Channel) {
        if (channel.type != "VOICE") return
        voiceCall = VoiceCallManager(
            getApplication(), { dest, payload -> wsManager?.sendSignal(dest, payload) }).also {
            it.setOnRemoteStream { stream -> _remoteVoiceStream.value = stream }
            it.startCall(channel.id)
        }
        _inVoiceCall.value = true
    }

    fun endVoiceCall() {
        voiceCall?.endCall()
        voiceCall = null
        _inVoiceCall.value = false
        _remoteVoiceStream.value = null
    }

    fun handleRtcMessage(type: String, data: Map<String, Any>) {
        voiceCall?.onRtcMessage(type, data)
    }

    private fun connectWs() {
        val t = _token.value
        if (t == null) {
            Log.w("GugleChat", "connectWs: no token"); return
        }
        val url = buildBaseUrl()
        Log.i("GugleChat", "connectWs: $url token=${t.take(20)}...")
        wsManager = WebSocketManager(baseUrl = url.trimEnd('/'), token = t, onMessage = { msg ->
            if (msg.channelId == _currentChannel.value?.id) {
                _messages.value = _messages.value + msg
            }
        }, onVoiceUsers = { _, _ -> }, onRtcSignal = { data ->
            val type = data["type"] as? String ?: return@WebSocketManager
            handleRtcMessage(type, data)
        })
        wsManager?.connect()
    }
}
