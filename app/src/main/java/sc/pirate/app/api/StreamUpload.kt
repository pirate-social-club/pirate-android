package sc.pirate.app.api

import java.io.InputStream

data class StreamUpload(
    val contentLength: Long,
    val mimeType: String,
    val openStream: () -> InputStream,
)
