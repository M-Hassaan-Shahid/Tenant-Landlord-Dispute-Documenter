package com.example.tenant_landlorddisputedocumenter.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Uploads evidence photos/signatures to Cloudinary using server-signed parameters.
 */
class CloudinaryUploader(
    private val signatureProvider: CloudinarySignatureProvider,
) {
    suspend fun upload(file: File, folder: String): String = withContext(Dispatchers.IO) {
        val signed = signatureProvider.fetch(folder)
        val boundary = "----ProofNest${System.currentTimeMillis()}"
        val endpoint = URL("https://api.cloudinary.com/v1_1/${signed.cloudName}/auto/upload")
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
            field("api_key", signed.apiKey)
            field("timestamp", signed.timestamp.toString())
            field("signature", signed.signature)
            if (signed.folder.isNotBlank()) field("folder", signed.folder)

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
        conn.disconnect()

        if (code !in 200..299) {
            error("Cloudinary upload failed ($code): $body")
        }
        JSONObject(body).getString("secure_url")
    }
}
