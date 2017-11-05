import java.time.OffsetDateTime

data class Message  (
        val messageId: String,
        val payloadInfo: String,
        val payload: String,
        val timestamp: OffsetDateTime,
        val tags: Set<String> = HashSet(),
        val isEncrypted:Boolean = false,
        val traceId: String?,
        val agent: String?)
