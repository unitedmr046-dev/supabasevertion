package com.iyonzkasir.hardware

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.iyonzkasir.core.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import kotlin.coroutines.resume

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun BarcodeScannerDialog(
    title: String = "Scan Barcode",
    hintText: String = "Arahkan kamera ke barcode / QR",
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(true) }
    val camPerm = rememberPermissionState(Manifest.permission.CAMERA)

    val galleryPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val bmp = withContext(Dispatchers.IO) { decodeSampledUri(ctx, uri, 1280) }
                if (bmp == null) { error = "Gagal membaca gambar"; return@launch }
                scanBitmapAsync(bmp) { code ->
                    bmp.recycle()
                    if (code != null) result = code else error = "Barcode tidak ditemukan di gambar"
                }
            }
        }
    }

    LaunchedEffect(Unit) { if (!camPerm.status.isGranted) camPerm.launchPermissionRequest() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, null, tint = AppColors.brand)
                Spacer(Modifier.width(AppSp.sm))
                Text(title)
            }
        },
        text = {
            Column(Modifier.fillMaxWidth().heightIn(min = 320.dp, max = 420.dp), verticalArrangement = Arrangement.spacedBy(AppSp.sm)) {
                if (!camPerm.status.isGranted) {
                    Card(colors = CardDefaults.cardColors(containerColor = AppColors.cardAlt)) {
                        Column(Modifier.padding(AppSp.lg).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, null, tint = AppColors.brand, modifier = Modifier.size(44.dp))
                            Spacer(Modifier.height(AppSp.sm))
                            Text("Izin Kamera Dibutuhkan", fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(AppSp.md))
                            Button(onClick = { camPerm.launchPermissionRequest() }, colors = ButtonDefaults.buttonColors(containerColor = AppColors.brand)) {
                                Text("Izinkan Kamera")
                            }
                        }
                    }
                } else if (result != null) {
                    LaunchedEffect(result) { onResult(result!!) }
                } else {
                    Box(Modifier.fillMaxWidth().weight(1f).clip(AppRd.card).background(Color.Black)) {
                        CameraPreviewBox { code ->
                            if (scanning && code.isNotBlank()) { scanning = false; result = code }
                        }
                        ScannerOverlayBox(AppColors.brand)
                        Surface(Modifier.align(Alignment.BottomCenter).padding(AppSp.md), color = Color.Black.copy(alpha = 0.6f), shape = AppRd.pill) {
                            Text(hintText, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    OutlinedButton(onClick = { galleryPicker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Pilih Foto Barcode dari Galeri", style = MaterialTheme.typography.bodySmall)
                    }
                }
                error?.let { Text(it, color = AppColors.danger, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}
@Composable
private fun CameraPreviewBox(onBarcodeDetected: (String) -> Unit) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(
                Barcode.FORMAT_EAN_13, Barcode.FORMAT_EAN_8, Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E, Barcode.FORMAT_CODE_128, Barcode.FORMAT_CODE_39, Barcode.FORMAT_QR_CODE
            ).build()
        )
    }

    LaunchedEffect(lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val provider = runCatching {
            suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                future.addListener({ runCatching { cont.resume(future.get()) }.onFailure { cont.cancel(it) } }, ContextCompat.getMainExecutor(ctx))
            }
        }.getOrNull() ?: return@LaunchedEffect

        runCatching { provider.unbindAll() }
        val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build().apply {
            setAnalyzer(executor) { proxy -> processProxy(scanner, proxy, onBarcodeDetected) }
        }
        runCatching { provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis) }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { scanner.close() }
            runCatching { executor.shutdown() }
        }
    }
    AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
}

@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun processProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    proxy: ImageProxy,
    onDetected: (String) -> Unit
) {
    val media = proxy.image ?: run { proxy.close(); return }
    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
    scanner.process(img)
        .addOnSuccessListener { list ->
            val code = list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            if (code != null) onDetected(code)
        }
        .addOnCompleteListener { proxy.close() }
}

private fun decodeSampledUri(ctx: Context, uri: Uri, maxDim: Int): Bitmap? = runCatching {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    var sample = 1
    while (opts.outWidth / sample > maxDim || opts.outHeight / sample > maxDim) sample *= 2
    opts.inSampleSize = sample
    opts.inJustDecodeBounds = false
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
}.getOrNull()

private fun scanBitmapAsync(bmp: Bitmap, onResult: (String?) -> Unit) {
    val scanner = BarcodeScanning.getClient()
    scanner.process(InputImage.fromBitmap(bmp, 0))
        .addOnSuccessListener { list -> onResult(list.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue) }
        .addOnFailureListener { onResult(null) }
        .addOnCompleteListener { runCatching { scanner.close() } }
}

@Composable
private fun ScannerOverlayBox(accent: Color) {
    Canvas(Modifier.fillMaxSize()) {
        val bw = size.width * 0.74f
        val bh = size.height * 0.45f
        val left = (size.width - bw) / 2f
        val top = (size.height - bh) / 2f
        val dim = Color.Black.copy(alpha = 0.45f)
        drawRect(dim, Offset.Zero, Size(size.width, top))
        drawRect(dim, Offset(0f, top + bh), Size(size.width, size.height - top - bh))
        drawRect(dim, Offset(0f, top), Size(left, bh))
        drawRect(dim, Offset(left + bw, top), Size(size.width - left - bw, bh))
        drawRect(accent, Offset(left, top), Size(bw, bh), style = Stroke(width = 5f))
    }
}
