package com.example.tenant_landlorddisputedocumenter.data.remote

/**
 * Public Cloudinary identifiers. Uploads use an unsigned upload preset, so no API secret
 * (and no Cloud Function) is required — both values below are safe to ship in the APK.
 *
 * Create the preset in the Cloudinary console: Settings -> Upload -> Upload presets ->
 * Add upload preset, with Signing Mode = Unsigned. Leave its Folder blank; the app sends
 * a per-upload folder (avatars/<uid>, signatures/<propertyId>, photos/<propertyId>/<itemId>).
 */
object CloudinaryConfig {
    const val CLOUD_NAME = "dspdmxdaq"
    const val UNSIGNED_UPLOAD_PRESET = "proofnest_unsigned"
}
