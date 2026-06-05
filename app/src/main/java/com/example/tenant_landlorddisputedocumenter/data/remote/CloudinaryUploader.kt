package com.example.tenant_landlorddisputedocumenter.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Free replacement for Firebase Storage (which now requires the Blaze plan).
 *
 * Performs an unsigned multipart upload to Cloudinary and returns the public `secure_url`,
 * which slots straight into the existing `remoteUrl` fields on photos/signatures. Downloads
 * elsewhere already stream from that URL, so no other changes are needed.
 *
 * Config comes from [CloudinaryConfig]. Uses only HttpURLConnection — no extra dependency.
 */
class CloudinaryUploader(
    private val cloudName: String = CloudinaryConfig.CLOUD_NAME,
    private val uploadPreset: String = CloudinaryConfig.UPLOAD_PRESET,
) {
    /** Uploads [file] and returns the public HTTPS URL. [folder] groups assets (e.g. "photos"). */
    suspend fun upload(file: File, folder: String): String = withContext(Dispatchers.IO) {
        val boundary = "----ProofNest${System.currentTimeMillis()}"
        val endpoint = URL("https://api.cloudinary.com/v1_1/$cloudName/auto/upload")
        val conn = (endpoint.openConnection() as HttpURLConnection).apply {
            doOutput = true
            requestMethod = "POST"
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        conn.outputStream.use { out ->
            fun field(name: String, value: String) {
                out.write(("--$boundary\r\n").toByteArray())
                out.write("Content-Disposition: form-data; name=\"$name\"\r\n\r\n".toByteArray())
                out.write((value + "\r\n").toByteArray())
            }
            field("upload_preset", uploadPreset)
            if (folder.isNotBlank()) field("folder", folder)

            out.write(("--$boundary\r\n").toByteArray())
            out.write(
                ("Content-Disposition: form-data; name=\"file\"; filename=\"${file.name}\"\r\n")
                    .toByteArray()
            )
            out.write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            file.inputStream().use { it.copyTo(out) }
            out.write("\r\n".toByteArray())
            out.write(("--$boundary--\r\n").toByteArray())
        }

        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        conn.disconnect()

        if (code !in 200..299) {
            error("Cloudinary upload failed ($code): $body")
        }
        JSONObject(body).getString("secure_url")
    }
}
