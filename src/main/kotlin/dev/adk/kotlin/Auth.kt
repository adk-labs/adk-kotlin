package dev.adk.kotlin

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

data class AuthCredential(
    val apiKey: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val authUri: String? = null,
    val authResponseUri: String? = null,
    val metadata: Map<String, String> = emptyMap(),
) : Serializable

data class AuthConfig(
    val authScheme: String,
    val rawAuthCredential: AuthCredential? = null,
    val exchangedAuthCredential: AuthCredential? = null,
    val credentialKey: String,
    val metadata: Map<String, String> = emptyMap(),
) : Serializable {
    init {
        require(authScheme.isNotBlank()) { "authScheme cannot be blank." }
        require(credentialKey.isNotBlank()) { "credentialKey cannot be blank." }
    }
}

class AuthHandler(
    private val authConfig: AuthConfig,
) {
    fun generateAuthRequest(): AuthConfig =
        authConfig.copy(
            exchangedAuthCredential = authConfig.exchangedAuthCredential ?: authConfig.rawAuthCredential,
        )

    fun getAuthResponse(state: Map<String, String>): AuthCredential? =
        state["temp:${authConfig.credentialKey}"]?.let(::decodeAuthCredential)
}

interface CredentialService {
    suspend fun saveCredential(
        authConfig: AuthConfig,
        appName: String,
        userId: String,
        credential: AuthCredential,
    )

    suspend fun loadCredential(
        authConfig: AuthConfig,
        appName: String,
        userId: String,
    ): AuthCredential?
}

class InMemoryCredentialService : CredentialService {
    private val credentials = ConcurrentHashMap<String, AuthCredential>()

    override suspend fun saveCredential(
        authConfig: AuthConfig,
        appName: String,
        userId: String,
        credential: AuthCredential,
    ) {
        credentials[storageKey(authConfig, appName, userId)] = credential
    }

    override suspend fun loadCredential(
        authConfig: AuthConfig,
        appName: String,
        userId: String,
    ): AuthCredential? = credentials[storageKey(authConfig, appName, userId)]

    private fun storageKey(
        authConfig: AuthConfig,
        appName: String,
        userId: String,
    ): String = "$appName:$userId:${authConfig.credentialKey}"
}

internal fun AuthCredential.encodeToState(): String {
    val bytes =
        ByteArrayOutputStream().use { outputStream ->
            ObjectOutputStream(outputStream).use { objectStream ->
                objectStream.writeObject(this)
            }
            outputStream.toByteArray()
        }
    return Base64.getEncoder().encodeToString(bytes)
}

internal fun decodeAuthCredential(serialized: String): AuthCredential? =
    runCatching {
        val bytes = Base64.getDecoder().decode(serialized)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { input ->
            input.readObject() as? AuthCredential
        }
    }.getOrNull()
