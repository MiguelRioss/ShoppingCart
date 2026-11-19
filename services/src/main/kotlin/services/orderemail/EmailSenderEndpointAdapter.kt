package services.orderemail

import config.Environment
import java.net.URI
import java.util.logging.Logger

/** Selects the configured Email Sender endpoint independently of ShoppingCart's database mode. */
class EmailSenderEndpointAdapter(
    private val mode: () -> String? = { Environment.get("EMAIL_SENDER_MODE") },
    private val onlineBaseUrl: () -> String? = { Environment.get("EMAIL_SENDER_ONLINE_BASE_URL") },
    private val offlineBaseUrl: () -> String? = { Environment.get("EMAIL_SENDER_OFFLINE_BASE_URL") }
) {
    private val logger = Logger.getLogger(javaClass.name)

    fun baseUrl(): String {
        val selectedMode = mode()?.trim()?.uppercase()
            ?: throw EmailSenderConfigurationException("EMAIL_SENDER_MODE must be ONLINE or OFFLINE")
        val variable = when (selectedMode) {
            "ONLINE" -> "EMAIL_SENDER_ONLINE_BASE_URL"
            "OFFLINE" -> "EMAIL_SENDER_OFFLINE_BASE_URL"
            else -> throw EmailSenderConfigurationException("EMAIL_SENDER_MODE must be ONLINE or OFFLINE")
        }
        val value = when (selectedMode) {
            "ONLINE" -> onlineBaseUrl()
            else -> offlineBaseUrl()
        }?.trim()?.trimEnd('/')?.takeIf(String::isNotBlank)
            ?: throw EmailSenderConfigurationException("$variable is required when EMAIL_SENDER_MODE=$selectedMode")
        val uri = runCatching { URI(value) }.getOrNull()
        if (uri == null || uri.scheme !in setOf("http", "https") || uri.host.isNullOrBlank() ||
            uri.rawQuery != null || uri.rawFragment != null || uri.userInfo != null
        ) throw EmailSenderConfigurationException("$variable must be an absolute HTTP(S) URL")
        logger.info("Email Sender endpoint selected mode=$selectedMode host=${uri.host}")
        return value
    }
}
