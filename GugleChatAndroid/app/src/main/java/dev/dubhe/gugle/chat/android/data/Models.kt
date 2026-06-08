package dev.dubhe.gugle.chat.android.data

// ===== Auth =====
data class LoginRequest(val username: String, val password: String)
data class RegisterRequest(val username: String, val email: String, val password: String)
data class AuthResponse(val token: String, val tokenType: String, val user: UserInfo)
data class UserInfo(val id: Long, val username: String, val email: String, val nickname: String, val avatarUrl: String?)

// ===== Channel =====
data class Channel(
    val id: Long, val name: String, val description: String?,
    val type: String, val createdBy: Long, val memberCount: Int,
    val joined: Boolean, val createdAt: String, val updatedAt: String
)
data class CreateChannelRequest(val name: String, val type: String, val description: String?)
data class ChannelMember(val id: Long, val userId: Long, val role: String, val joinedAt: String)

// ===== Message =====
data class Message(
    val id: Long, val channelId: Long, val userId: Long, val username: String,
    val content: String, val type: String, val parentId: Long?,
    val fileIds: List<Long>?, val editedAt: String?, val createdAt: String
)
data class SendMessageRequest(val content: String, val type: String = "TEXT")

// ===== Generic API Response =====
data class ApiResponse<T>(val code: Int, val message: String, val data: T)
