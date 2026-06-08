package dev.dubhe.gugle.chat.android.data

import retrofit2.http.*
import retrofit2.Call

interface ApiService {
    // Auth
    @POST("api/auth/login")
    fun login(@Body req: LoginRequest): Call<ApiResponse<AuthResponse>>

    @POST("api/auth/register")
    fun register(@Body req: RegisterRequest): Call<ApiResponse<AuthResponse>>

    @GET("api/auth/me")
    fun getMe(): Call<ApiResponse<UserInfo>>

    // Channels
    @GET("api/channels")
    fun getChannels(): Call<ApiResponse<List<Channel>>>

    @POST("api/channels")
    fun createChannel(@Body req: CreateChannelRequest): Call<ApiResponse<Channel>>

    // Messages
    @GET("api/channels/{channelId}/messages")
    fun getMessages(@Path("channelId") channelId: Long, @Query("before") before: Long?): Call<ApiResponse<List<Message>>>

    // Files
    @Multipart
    @POST("api/files/upload")
    fun uploadFile(@Part file: okhttp3.MultipartBody.Part): Call<ApiResponse<FileInfo>>

    // Voice users
    @GET("api/channels/voice-users")
    fun getVoiceUsers(): Call<ApiResponse<Map<String, VoiceRoomInfo>>>
}

data class FileInfo(val id: String, val filename: String, val originalName: String, val size: Long, val contentType: String, val url: String)
data class VoiceRoomInfo(val users: List<VoiceUser>, val hostId: Long)
data class VoiceUser(val userId: Long, val username: String, val quality: Double)
