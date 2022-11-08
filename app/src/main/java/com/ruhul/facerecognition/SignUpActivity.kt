package com.ruhul.facerecognition

import android.annotation.SuppressLint
import android.graphics.*
import android.media.Image
import android.os.Bundle
import android.text.InputType
import android.util.Pair
import android.util.Size
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.ruhul.facerecognition.SimilarityClassifier.Recognition
import com.ruhul.facerecognition.databinding.ActivitySignUpBinding
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.ReadOnlyBufferException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.experimental.inv
import kotlin.properties.Delegates

class SignUpActivity : AppCompatActivity() {

    private var cameraFacingSide by Delegates.notNull<Int>()
    private var cameraProvider: ProcessCameraProvider? = null
    private var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>? = null
    var cameraSelector: CameraSelector? = null

    var detector: FaceDetector? = null
    var tfLite: Interpreter? = null

    var developerMode = false
    var distance = 1.0f
    var start = true
    var flipX: Boolean = false


    private lateinit var intValues: IntArray

    //Input size for model
    private var inputSize = 112


    var isModelQuantized = false
    private lateinit var embeding: Array<FloatArray>
    var IMAGE_MEAN = 128.0f
    var IMAGE_STD = 128.0f

    //Output size of model
    private var OUTPUT_SIZE = 192

    private val registered = java.util.HashMap<String, Recognition>() //saved Faces

    private val SELECT_PICTURE = 1


    private lateinit var binding: ActivitySignUpBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySignUpBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initCameraFacing()
        clickEvent()
    }

    private fun initCameraFacing() {
        cameraFacingSide = CameraSelector.LENS_FACING_BACK
    }

    private fun clickEvent() {

        binding.cameraBtn.setOnClickListener(View.OnClickListener {
            if (cameraFacingSide == CameraSelector.LENS_FACING_BACK) {
                cameraFacingSide = CameraSelector.LENS_FACING_FRONT
                flipX = true
            } else {
                cameraFacingSide = CameraSelector.LENS_FACING_BACK
                flipX = false
            }
            cameraProvider?.unbindAll()
            cameraBind()
        })

        binding.AddFaceBtn.setOnClickListener {
            addFace()

        }

    }

    private fun addFace() {
        start = false
        val builder =
            AlertDialog.Builder(this)
        builder.setTitle("Enter Name")
        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT
        builder.setView(input)
        builder.setPositiveButton("ADD") { _, _ -> //Create and Initialize new object with Face embeddings and Name.

            val result = Recognition("0", "", -1f)
            result.extra = embeding
            start = true
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            start = true
            dialog.cancel()
        }
        builder.show()
    }

    //Bind camera and preview view
    private fun cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture!!.addListener(Runnable {
            try {
                cameraProvider = cameraProviderFuture!!.get()
                bindPreview(cameraProvider!!)
            } catch (e: ExecutionException) {
                e.printStackTrace()
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindPreview(cameraProvider: ProcessCameraProvider) {
        val preview = Preview.Builder()
            .build()
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(cameraFacingSide)
            .build()
        preview.setSurfaceProvider(binding.previewView.surfaceProvider)

        val imageAnalysis = ImageAnalysis.Builder()
            .setTargetResolution(Size(640, 480))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
            .build()

        val executor: Executor = Executors.newSingleThreadExecutor()

        imageAnalysis.setAnalyzer(executor) { imageProxy ->
            try {
                Thread.sleep(0) //Camera preview refreshed every 10 millis(adjust as required)
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }
            var image: InputImage? = null

            @SuppressLint("UnsafeExperimentalUsageError", "UnsafeOptInUsageError")
            val mediaImage = imageProxy.image
            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                detectFaceProcess(image, mediaImage, imageProxy)
            }
        }
        cameraProvider.bindToLifecycle(
            (this as LifecycleOwner),
            cameraSelector!!,
            imageAnalysis,
            preview
        )
    }

    //Process acquired image to detect faces
    private fun detectFaceProcess(image: InputImage, mediaImage: Image, imageProxy: ImageProxy) {
        //Initialize Face Detector
        val highAccuracyOpts = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .build()
        detector = FaceDetection.getClient(highAccuracyOpts)
        val result: Task<List<Face>> = detector!!.process(image)
            .addOnSuccessListener(
                OnSuccessListener<List<Face>> { faces ->
                    if (faces.isNotEmpty()) {
                        val face = faces[0] //Get first face from detected faces

                        //mediaImage to Bitmap
                        val frameBitmap: Bitmap = toBitmap(mediaImage)
                        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

                        //Adjust orientation of Face
                        val frameBitmap1 = rotateBitmap(frameBitmap, rotationDegrees, false, false)


                        //Get bounding box of face
                        val boundingBox = RectF(face.boundingBox)

                        //Crop out bounding box from whole Bitmap(image)
                        var cropBitmap = getCropBitmapByCPU(frameBitmap1, boundingBox)

                        if (flipX) cropBitmap = cropBitmap?.let { rotateBitmap(it, 0, true, false) }

                        //Scale the acquired Face to 112*112 which is required input for model
                        val scaled: Bitmap? = cropBitmap?.let { getResizedBitmap(it, 112, 112) }

                        if (start) scaled?.let { recognizeImage(it) } //Send scaled bitmap to create face embeddings.

                    } else {

                        Toast.makeText(this, "Face Not Recognition", Toast.LENGTH_SHORT).show()

                        /*   if (registered.isEmpty()) reco_name.setText("Add Face") else reco_name.setText(
                               "No Face Detected!"
                           )*/
                    }
                })
            .addOnFailureListener(
                OnFailureListener {
                    Toast.makeText(this, "does not face detect", Toast.LENGTH_SHORT).show()
                })
            .addOnCompleteListener(OnCompleteListener<List<Face?>?> {
                imageProxy.close() //v.important to acquire next frame for analysis
            })
    }

    private fun getResizedBitmap(bm: Bitmap, newWidth: Int, newHeight: Int): Bitmap {
        val width = bm.width
        val height = bm.height
        val scaleWidth = newWidth.toFloat() / width
        val scaleHeight = newHeight.toFloat() / height
        // CREATE A MATRIX FOR THE MANIPULATION
        val matrix = Matrix()
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight)

        // "RECREATE" THE NEW BITMAP
        val resizedBitmap = Bitmap.createBitmap(
            bm, 0, 0, width, height, matrix, false
        )
        bm.recycle()
        return resizedBitmap
    }

    private fun rotateBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int,
        flipX: Boolean,
        flipY: Boolean
    ): Bitmap? {
        val matrix = Matrix()

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees.toFloat())

        // Mirror the image along the X or Y axis.
        matrix.postScale(if (flipX) -1.0f else 1.0f, if (flipY) -1.0f else 1.0f)
        val rotatedBitmap =
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle()
        }
        return rotatedBitmap
    }


    private fun getCropBitmapByCPU(source: Bitmap?, cropRectF: RectF): Bitmap? {
        val resultBitmap = Bitmap.createBitmap(
            cropRectF.width().toInt(),
            cropRectF.height().toInt(),
            Bitmap.Config.ARGB_8888
        )
        val cavas = Canvas(resultBitmap)

        // draw background
        val paint = Paint(Paint.FILTER_BITMAP_FLAG)
        paint.color = Color.WHITE
        cavas.drawRect(
            RectF(0F, 0F, cropRectF.width(), cropRectF.height()),
            paint
        )
        val matrix = Matrix()
        matrix.postTranslate(-cropRectF.left, -cropRectF.top)
        cavas.drawBitmap(source!!, matrix, paint)
        if (source != null && !source.isRecycled) {
            source.recycle()
        }
        return resultBitmap
    }

    private fun recognizeImage(bitmap: Bitmap) {
        // set Face to Preview
        binding.cropFaceView.setImageBitmap(bitmap)

        //Create ByteBuffer to store normalized image
        val imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        imgData.order(ByteOrder.nativeOrder())
        intValues = IntArray(inputSize * inputSize)

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        imgData.rewind()
        for (i in 0 until inputSize) {
            for (j in 0 until inputSize) {
                val pixelValue: Int = intValues.get(i * inputSize + j)
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((pixelValue shr 16 and 0xFF).toByte())
                    imgData.put((pixelValue shr 8 and 0xFF).toByte())
                    imgData.put((pixelValue and 0xFF).toByte())
                } else { // Float model
                    imgData.putFloat(((pixelValue shr 16 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue shr 8 and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                    imgData.putFloat(((pixelValue and 0xFF) - IMAGE_MEAN) / IMAGE_STD)
                }
            }
        }
        //imgData is input to our model
        val inputArray = arrayOf<Any>(imgData)
        val outputMap: MutableMap<Int, Any> = HashMap()
        embeding =
            Array(1) { FloatArray(OUTPUT_SIZE) } //output of model will be stored in this variable
        outputMap[0] = embeding
        tfLite?.runForMultipleInputsOutputs(inputArray, outputMap) //Run model
        var distance_local = Float.MAX_VALUE
        val id = "0"
        val label = "?"

        //Compare new face with saved Faces.
/*        if (registered.size > 0) {
            val nearest: List<Pair<String, Float>?> =
            if (nearest[0] != null) {
                val name = nearest[0]!!.first //get name and distance of closest matching face
                // label = name;
                distance_local = nearest[0]!!.second
                if (developerMode) {
                    if (distance_local < distance) {
                        *//*      reco_name.setText(
                                  """
                              Nearest: $name
                              Dist: ${String.format("%.3f", distance_local)}
                              2nd Nearest: ${nearest[1]!!.first}
                              Dist: ${String.format("%.3f", nearest[1]!!.second)}
                              """.trimIndent()
                              )*//*

                    } else {
                        *//* reco_name.setText(
                             """
                         Unknown
                         Dist: ${String.format("%.3f", distance_local)}
                         Nearest: $name
                         Dist: ${String.format("%.3f", distance_local)}
                         2nd Nearest: ${nearest[1]!!.first}
                         Dist: ${String.format("%.3f", nearest[1]!!.second)}
                         """.trimIndent()
                         )*//*
                    }
                    //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                } else {
                    if (distance_local < distance) {
                        //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        *//*reco_name.setText(name) else reco_name.setText("Unknown")*//*
                    }

                }
            }
        }*/
    }

    //Compare Faces by distance between face embeddings
/*    private fun findNearest(emb: FloatArray): List<Pair<String, Float>?>? {
        val neighbour_list: MutableList<Pair<String, Float>?> = ArrayList()
        var ret: Pair<String, Float>? = null //to get closest match
        var prev_ret: Pair<String, Float>? = null //to get second closest match
        for (faceList in registered) {
            val name: String = faceList.key
            val knownEmb = faceList.value.extra

            var distance = 0f

            for (i in emb.iterator()) {
                val diff = emb[i.toInt()] - knownEmb
                distance += diff * diff
            }

            distance = Math.sqrt(distance.toDouble()).toFloat()
            if (ret == null || distance < ret.second) {
                prev_ret = ret
                ret = Pair(name, distance)
            }
        }
        if (prev_ret == null) prev_ret = ret
        neighbour_list.add(ret)
        neighbour_list.add(prev_ret)
        return neighbour_list
    }*/

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private fun YUV_420_888toNV21(image: Image): ByteArray {
        val width = image.width
        val height = image.height
        val ySize = width * height
        val uvSize = width * height / 4
        val nv21 = ByteArray(ySize + uvSize * 2)
        val yBuffer = image.planes[0].buffer // Y
        val uBuffer = image.planes[1].buffer // U
        val vBuffer = image.planes[2].buffer // V
        var rowStride = image.planes[0].rowStride
        assert(image.planes[0].pixelStride == 1)
        var pos = 0
        if (rowStride == width) { // likely
            yBuffer[nv21, 0, ySize]
            pos += ySize
        } else {
            var yBufferPos = -rowStride.toLong() // not an actual position
            while (pos < ySize) {
                yBufferPos += rowStride.toLong()
                yBuffer.position(yBufferPos.toInt())
                yBuffer[nv21, pos, width]
                pos += width
            }
        }
        rowStride = image.planes[2].rowStride
        val pixelStride = image.planes[2].pixelStride
        assert(rowStride == image.planes[1].rowStride)
        assert(pixelStride == image.planes[1].pixelStride)
        if (pixelStride == 2 && rowStride == width && uBuffer[0] == vBuffer[1]) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            val savePixel = vBuffer[1]
            try {
                vBuffer.put(1, savePixel.inv().toByte())
                if (uBuffer[0] == savePixel.inv().toByte()) {
                    vBuffer.put(1, savePixel)
                    vBuffer.position(0)
                    uBuffer.position(0)
                    vBuffer[nv21, ySize, 1]
                    uBuffer[nv21, ySize + 1, uBuffer.remaining()]
                    return nv21 // shortcut
                }
            } catch (ex: ReadOnlyBufferException) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel)
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant
        for (row in 0 until height / 2) {
            for (col in 0 until width / 2) {
                val vuPos = col * pixelStride + row * rowStride
                nv21[pos++] = vBuffer[vuPos]
                nv21[pos++] = uBuffer[vuPos]
            }
        }
        return nv21
    }

    private fun toBitmap(image: Image): Bitmap {
        val nv21 = YUV_420_888toNV21(image)
        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 75, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }


}