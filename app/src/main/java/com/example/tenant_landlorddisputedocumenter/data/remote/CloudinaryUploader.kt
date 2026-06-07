package com.example.tenant_landlorddisputedocumenter.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads avatars, evidence photos, and signatures to Cloudinary via an unsigned upload
 * preset. No server-side signing (and therefore no Cloud Function or paid billing plan)
 * is needed; see [CloudinaryConfig] for the preset requirements.
 */
class CloudinaryUploader {
    suspend fun upload(file: File, folder: String): String = withContext(Dispatchers.IO) {
        val boundary = "----ProofNest${System.currentTimeMillis()}"
        val endpoint = URL("https://api.cloudinary.com/v1_1/${CloudinaryConfig.CLOUD_NAME}/auto/upload")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            doOutput = true
            requestMethod = "POST"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        try {
            conn.outputStream.use { out ->
                fun field(name: String, value: String) {
                    out.write(("--$boundary\r\n").toByteArray())
                    out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                    out.write((value + "\r\n").toByteArray())
                }
                field("upload_preset", CloudinaryConfig.UNSIGNED_UPLOAD_PRESET)
                if (folder.isNotBlank()) field("folder", folder)

                out.write(("--$boundary\r\n").toByteArray())
                out.write(
                    ("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                        .toByteArray(),
                )
                out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
                file.inputStream().use { it.copyTo(out) }
                out.write("\r\n".toByteArray())
                out.write(("--$boundary--\r\n").toByteArray())
            }

            val code = conn.responseCode
            val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (code !in 200..299) {
                error("Cloudinary upload failed ($code): $body")
            }
            JSONObject(body).getString("secure_url")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
    }
}
