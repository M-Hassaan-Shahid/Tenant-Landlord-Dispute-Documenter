package com.example.tenant_landlorddisputedocumenter.data.remote

/**
 * Cloudinary credentials for unsigned image uploads (free Storage alternative).
 *
 * Both values are safe to ship in the app: the cloud name is public and an *unsigned*
 * upload preset is designed for client-side use. Fill these from your Cloudinary dashboard:
 *   - CLOUD_NAME:    Dashboard → "Cloud name"
 *   - UPLOAD_PRESET: Settings → Upload → Upload presets → an Unsigned preset's name
 */
object CloudinaryConfig {
    const val CLOUD_NAME = "dspdmxdaq"
    const val UPLOAD_PRESET = "ml_default"
}
