package com.example.tenant_landlorddisputedocumenter.ui.inspection

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.example.tenant_landlorddisputedocumenter.ui.applyBottomNavInset
import com.example.tenant_landlorddisputedocumenter.ui.applyStandaloneToolbarInset
import com.example.tenant_landlorddisputedocumenter.ui.pulse
import android.view.View
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.tenant_landlorddisputedocumenter.ProofNestApplication
import com.example.tenant_landlorddisputedocumenter.R
import com.example.tenant_landlorddisputedocumenter.databinding.ActivityCameraCaptureBinding
import com.example.tenant_landlorddisputedocumenter.domain.model.InspectionPhase
import com.example.tenant_landlorddisputedocumenter.domain.model.Photo
import com.example.tenant_landlorddisputedocumenter.util.Ids
import com.example.tenant_landlorddisputedocumenter.util.LocationHelper
import com.example.tenant_landlorddisputedocumenter.util.PhotoStamper
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraCaptureActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PROPERTY_ID = "propertyId"
        const val EXTRA_ITEM_ID = "itemId"
        const val EXTRA_PHASE = "phase"
        const val EXTRA_CAPTURE_MODE = "captureMode"
        const val MODE_INSPECTION = "inspection"
        const val MODE_DISPUTE_EVIDENCE = "dispute_evidence"
        const val RESULT_PHOTO_ID = "result_photo_id"
        const val RESULT_PHOTO_URI = "result_photo_uri"
    }

    private lateinit var binding: ActivityCameraCaptureBinding
    private lateinit var cameraExecutor: ExecutorService
    private var imageCapture: ImageCapture? = null

    /** Required extras from the caller */
    private lateinit var propertyId: String
    private lateinit var itemId: String
    private lateinit var phase: InspectionPhase
    private var captureMode: String = MODE_INSPECTION

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) startCamera() else {
            Toast.makeText(this, R.string.capture_permission_denied, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* best-effort; capture works without GPS */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        binding = ActivityCameraCaptureBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.toolbarCamera.applyStandaloneToolbarInset()
        binding.cameraBar.applyBottomNavInset()

        propertyId = intent.getStringExtra(EXTRA_PROPERTY_ID) ?: run { finish(); return }
        itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: run { finish(); return }
        phase = if (intent.getStringExtra(EXTRA_PHASE) == "MOVE_OUT") InspectionPhase.MOVE_OUT
        else InspectionPhase.MOVE_IN
        captureMode = intent.getStringExtra(EXTRA_CAPTURE_MODE) ?: MODE_INSPECTION

        cameraExecutor = Executors.newSingleThreadExecutor()
        validateCaptureAccess()
        requestLocationIfNeeded()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }

        binding.toolbarCamera.setNavigationOnClickListener { finish() }
        binding.buttonCapture.setOnClickListener { capturePhoto() }
        updateMetadataBar()
    }

    private fun updateMetadataBar() {
        lifecycleScope.launch {
            val location = LocationHelper.currentLocation(this@CameraCaptureActivity)
            val coords = location?.let { "${"%.4f".format(it.latitude)}°, ${"%.4f".format(it.longitude)}°" } ?: "—"
            val date = java.text.SimpleDateFormat("d MMM yyyy, HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date())
            binding.barMetadata.text = getString(R.string.capture_metadata_format, coords, date)
        }
    }

    private fun validateCaptureAccess() {
        lifecycleScope.launch {
            val container = (application as ProofNestApplication).container
            val uid = container.authRepository.currentUserId.value ?: run {
                Toast.makeText(this@CameraCaptureActivity, R.string.capture_not_allowed, Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            runCatching {
                if (captureMode == MODE_DISPUTE_EVIDENCE) {
                    container.inspectionRepository.validateDisputeEvidenceCapture(propertyId, phase, uid)
                } else {
                    container.inspectionRepository.validateCapture(propertyId, phase, uid)
                }
            }.onFailure {
                Toast.makeText(
                    this@CameraCaptureActivity,
                    it.localizedMessage ?: getString(R.string.capture_not_allowed),
                    Toast.LENGTH_LONG,
                ).show()
                finish()
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .build()

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun setSavingUi(saving: Boolean) {
        binding.savingOverlay.visibility = if (saving) View.VISIBLE else View.GONE
        binding.buttonCapture.isEnabled = !saving
        binding.toolbarCamera.isEnabled = !saving
        if (saving) {
            binding.lottieSaving.playAnimation()
        } else {
            binding.lottieSaving.cancelAnimation()
        }
    }

    private fun capturePhoto() {
        val capture = imageCapture ?: return
        binding.buttonCapture.pulse(0.92f)
        setSavingUi(true)

        val photoId = Ids.newId()
        val photoDir = File(filesDir, "photos/$propertyId").apply { mkdirs() }
        val photoFile = File(photoDir, "$photoId.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        capture.takePicture(outputOptions, cameraExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                lifecycleScope.launch {
                    val uid = (application as ProofNestApplication).container
                        .authRepository.currentUserId.value ?: ""
                    val location = LocationHelper.currentLocation(this@CameraCaptureActivity)
                    val now = System.currentTimeMillis()

                    // Stamp date/GPS/uid visually onto the photo
                    PhotoStamper.stampInPlace(
                        photoFile,
                        PhotoStamper.Stamp(
                            timestampMillis = now,
                            latitude = location?.latitude,
                            longitude = location?.longitude,
                            capturedByUid = uid
                        )
                    )

                    val photo = Photo(
                        id = photoId,
                        itemId = itemId,
                        propertyId = propertyId,
                        phase = phase,
                        capturedByUid = uid,
                        capturedAtMillis = now,
                        latitude = location?.latitude,
                        longitude = location?.longitude,
                        localUri = "file://${photoFile.absolutePath}",
                        uploaded = false
                    )

                    val container = (application as ProofNestApplication).container
                    if (captureMode == MODE_DISPUTE_EVIDENCE) {
                        container.inspectionRepository.saveDisputeEvidencePhoto(photo)
                    } else {
                        container.inspectionRepository.savePhoto(photo)
                    }
                    container.inspectionRepository.syncPendingUploads()

                    runOnUiThread {
                        setResult(
                            RESULT_OK,
                            Intent().apply {
                                putExtra(RESULT_PHOTO_ID, photo.id)
                                putExtra(RESULT_PHOTO_URI, photo.localUri)
                            },
                        )
                        finish()
                    }
                }
            }

            override fun onError(exc: ImageCaptureException) {
                runOnUiThread {
                    setSavingUi(false)
                    Toast.makeText(this@CameraCaptureActivity, "Capture failed: ${exc.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    private fun requestLocationIfNeeded() {
        if (LocationHelper.hasPermission(this)) return
        requestLocationPermission.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
