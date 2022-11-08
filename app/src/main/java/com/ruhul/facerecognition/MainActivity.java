package com.ruhul.facerecognition;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.YuvImage;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;

import androidx.annotation.NonNull;

import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.ListenableFuture;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.ruhul.facerecognition.databinding.ActivityMainBinding;

import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LifecycleOwner;

import android.os.ParcelFileDescriptor;
import android.text.InputType;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.View;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import org.tensorflow.lite.Interpreter;

import java.io.ByteArrayOutputStream;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.ReadOnlyBufferException;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private ActivityMainBinding binding;
    String modelFile = "mobile_face_net.tflite";

    FaceDetector detector;
    Interpreter tfLite;

    CameraSelector cameraSelector;
    int cam_face = CameraSelector.LENS_FACING_BACK;
    ProcessCameraProvider cameraProvider;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private static final int MY_CAMERA_REQUEST_CODE = 100;


    TextView reco_name, preview_info, textAbove_preview;

    boolean developerMode = false;
    float distance = 1.0f;
    boolean start = true, flipX = false;
    Context context = MainActivity.this;


    int[] intValues;
    //Input size for model
    int inputSize = 112;
    boolean isModelQuantized = false;

    //Embed image from TensorFlow Lite
    // added embeedings array buffer for model output
    float[][] embed;

    float IMAGE_MEAN = 128.0f;
    float IMAGE_STD = 128.0f;
    //Output size of model
    int OUTPUT_SIZE = 192;
    private static int SELECT_PICTURE = 1;

    private HashMap<String, SimilarityClassifier.Recognition> registered = new HashMap<>(); //saved Faces
    private String log = "MainActivity:";

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        binding = DataBindingUtil.setContentView(this, R.layout.activity_main);

        loadModelTFlow();
        initView();
        requestPermission();
        clickEvent();
        cameraBind();

    }

    //Load model
    private void loadModelTFlow() {
        try {
            tfLite = new Interpreter(loadModelFile(MainActivity.this, modelFile));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void clickEvent() {
     /*   actions.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Select Action:");

                // add a checkbox list
                String[] names = {"View Recognition List", "Update Recognition List", "Save Recognitions", "Load Recognitions", "Clear All Recognitions", "Import Photo (Beta)", "Hyperparameters", "Developer Mode"};

                builder.setItems(names, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                        switch (which) {
                            case 0:
                                displaynameListview();
                                break;
                            case 1:
                                updatenameListview();
                                break;
                            case 2:
                                insertToSP(registered, 0); //mode: 0:save all, 1:clear all, 2:update all
                                break;
                            case 3:
                                registered.putAll(loadRecognitionFace());
                                break;
                            case 4:
                                clearnameList();
                                break;
                            case 5:
                                loadphoto();
                                break;
                            case 6:
                                testHyperparameter();
                                break;
                            case 7:
                                developerMode();
                                break;
                        }

                    }
                });


                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {

                    }
                });

                builder.setNegativeButton("Cancel", null);

                // create and show the alert dialog
                AlertDialog dialog = builder.create();
                dialog.show();
            }
        });*/

        binding.cameraSwitchBtn.setOnClickListener(v -> {
            if (cam_face == CameraSelector.LENS_FACING_BACK) {
                cam_face = CameraSelector.LENS_FACING_FRONT;
                flipX = true;
            } else {
                cam_face = CameraSelector.LENS_FACING_BACK;
                flipX = false;
            }
            cameraProvider.unbindAll();
            cameraBind();
        });

        binding.insertFace.setOnClickListener((v -> {
            Log.d(log, "Add Face");
            addFace();
        }));

        binding.recognize.setOnClickListener(v -> {
            if (binding.recognize.getText().toString().equals("Recognize")) {
                start = true;
                textAbove_preview.setText("Recognized Face:");
                binding.recognize.setText("Add Face");
                binding.insertFace.setVisibility(View.INVISIBLE);
                reco_name.setVisibility(View.VISIBLE);
                binding.showImage.setVisibility(View.INVISIBLE);
                preview_info.setText("");
            } else {
                textAbove_preview.setText("Face Preview: ");
                binding.recognize.setText("Recognize");
                binding.insertFace.setVisibility(View.VISIBLE);
                reco_name.setVisibility(View.INVISIBLE);
                binding.showImage.setVisibility(View.VISIBLE);
                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");

            }

        });
    }

    private void requestPermission() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, MY_CAMERA_REQUEST_CODE);
        }
    }

    private void initSharedPreferences() {
        SharedPreferences sharedPref = getSharedPreferences("Distance", Context.MODE_PRIVATE);
        distance = sharedPref.getFloat("distance", 1.00f);
    }

    private void initView() {

        binding.insertFace.setVisibility(View.INVISIBLE);
        binding.showImage.setVisibility(View.INVISIBLE);

        reco_name = findViewById(R.id.textView);
        preview_info = findViewById(R.id.textView2);
        textAbove_preview = findViewById(R.id.textAbovePreview);
        textAbove_preview.setText("Recognized Face:");
    }

    private void addFace() {
        start = false;
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle("Enter Name");
        final EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        builder.setView(input);

        builder.setPositiveButton("ADD", (dialog, which) -> {

            //Create and Initialize new object with Face embeddings and Name.
            SimilarityClassifier.Recognition result = new SimilarityClassifier.Recognition(
                    "0", "", -1f);
            result.setExtra(embed);

            FaceModel model = new FaceModel(input.getText().toString(),result);

            registered.put(input.getText().toString(), result);
            start = true;

        });

        builder.setNegativeButton("Cancel", (dialog, which) -> {
            start = true;
            dialog.cancel();
        });

        builder.show();

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == MY_CAMERA_REQUEST_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "camera permission granted", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "camera permission denied", Toast.LENGTH_LONG).show();
            }
        }
    }

    private MappedByteBuffer loadModelFile(Activity activity, String MODEL_FILE) throws IOException {
        AssetFileDescriptor fileDescriptor = activity.getAssets().openFd(MODEL_FILE);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    //Bind camera and preview view
    private void cameraBind() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder()
                .build();

        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(cam_face)
                .build();

        preview.setSurfaceProvider(binding.previewView.getSurfaceProvider());

        ImageAnalysis imageAnalysis =
                new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) //Latest frame is shown
                        .build();

        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
            InputImage image = null;

            @SuppressLint({"UnsafeExperimentalUsageError", "UnsafeOptInUsageError"})
            Image mediaImage = imageProxy.getImage();

            if (mediaImage != null) {
                image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

                detectFaceProcess(image, mediaImage, imageProxy);
            }
        });


        cameraProvider.bindToLifecycle((LifecycleOwner) this, cameraSelector, imageAnalysis, preview);


    }

    //Process acquired image to detect faces
    private void detectFaceProcess(InputImage image, Image mediaImage, ImageProxy imageProxy) {


        //Initialize Face Detector
        FaceDetectorOptions highAccuracyOpts =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                        .build();
        detector = FaceDetection.getClient(highAccuracyOpts);

        detector.process(image)
                .addOnSuccessListener(
                        faces -> {
                            binding.graphicOverlay.refreshDrawableState();
                            binding.graphicOverlay.clear();

                            if (faces.size() != 0) {

                                binding.graphicOverlay.refreshDrawableState();
                                binding.graphicOverlay.clear();

                                Face face = faces.get(0); //Get first face from detected faces


                                Rect rect = face.getBoundingBox();
                                ReactOverlay reactOverlay = new ReactOverlay(binding.graphicOverlay, rect);
                                binding.graphicOverlay.add(reactOverlay);

                                //mediaImage to Bitmap
                                Bitmap frame_bmp = toBitmap(mediaImage);

                                int rot = imageProxy.getImageInfo().getRotationDegrees();

                                //Adjust orientation of Face
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, rot, false, false);


                                //Get bounding box of face
                                RectF boundingBox = new RectF(face.getBoundingBox());

                                //Crop out bounding box from whole Bitmap(image)
                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);

                                if (flipX)
                                    cropped_face = rotateBitmap(cropped_face, 0, true, false);
                                //Scale the acquired Face to 112*112 which is required input for model
                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);

                                if (start)
                                    recognizeImage(scaled); //Send scaled bitmap to create face embeddings.

                            } else {

                                binding.graphicOverlay.refreshDrawableState();
                                binding.graphicOverlay.clear();

                                if (registered.isEmpty())
                                    reco_name.setText("Add Face");
                                else
                                    reco_name.setText("No Face Detected!");
                            }

                        })
                .addOnFailureListener(
                        e -> {
                            // Task failed with an exception
                            // ...
                        })
                .addOnCompleteListener(task -> {

                    imageProxy.close(); //v.important to acquire next frame for analysis
                });
    }

    public void recognizeImage(final Bitmap bitmap) {

        // set Face to Preview
        binding.showImage.setImageBitmap(bitmap);

        //Create ByteBuffer to store normalized image

        ByteBuffer imgData = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4);

        imgData.order(ByteOrder.nativeOrder());

        intValues = new int[inputSize * inputSize];

        //get pixel values from Bitmap to normalize
        bitmap.getPixels(intValues, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        imgData.rewind();

        for (int i = 0; i < inputSize; ++i) {
            for (int j = 0; j < inputSize; ++j) {
                int pixelValue = intValues[i * inputSize + j];
                if (isModelQuantized) {
                    // Quantized model
                    imgData.put((byte) ((pixelValue >> 16) & 0xFF));
                    imgData.put((byte) ((pixelValue >> 8) & 0xFF));
                    imgData.put((byte) (pixelValue & 0xFF));
                } else { // Float model
                    imgData.putFloat((((pixelValue >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat((((pixelValue >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
                    imgData.putFloat(((pixelValue & 0xFF) - IMAGE_MEAN) / IMAGE_STD);

                }
            }
        }
        //imgData is input to our model
        Object[] inputArray = {imgData};

        Map<Integer, Object> outputMap = new HashMap<>();

        embed = new float[1][OUTPUT_SIZE]; //output of model will be stored in this variable

        outputMap.put(0, embed);

        tfLite.runForMultipleInputsOutputs(inputArray, outputMap); //Run model


        float distance_local = Float.MAX_VALUE;
        String id = "0";
        String label = "?";

        //Compare new face with saved Faces.
        if (registered.size() > 0) {

            final List<Pair<String, Float>> nearest = findNearest(embed[0]);//Find 2 closest matching face

            if (nearest.get(0) != null) {

                final String name = nearest.get(0).first; //get name and distance of closest matching face
                // label = name;
                distance_local = nearest.get(0).second;
                if (developerMode) {
                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        reco_name.setText("Nearest: " + name + "\nDist: " + String.format("%.3f", distance_local) + "\n2nd Nearest: " + nearest.get(1).first + "\nDist: " + String.format("%.3f", nearest.get(1).second));
                    else
                        reco_name.setText("Unknown " + "\nDist: " + String.format("%.3f", distance_local) + "\nNearest: " + name + "\nDist: " + String.format("%.3f", distance_local) + "\n2nd Nearest: " + nearest.get(1).first + "\nDist: " + String.format("%.3f", nearest.get(1).second));
                } else {
                    if (distance_local < distance) //If distance between Closest found face is more than 1.000 ,then output UNKNOWN face.
                        reco_name.setText(name);
                    else
                        reco_name.setText("Unknown");
                }


            }
        }


    }

    //Compare Faces by distance between face embeddings
    private List<Pair<String, Float>> findNearest(float[] emb) {

        List<Pair<String, Float>> neighbour_list = new ArrayList<Pair<String, Float>>();
        Pair<String, Float> ret = null; //to get closest match
        Pair<String, Float> prev_ret = null; //to get second closest match

        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : registered.entrySet()) {
            final String name = entry.getKey();
            final float[] knownEmb = ((float[][]) entry.getValue().getExtra())[0];
            float distance = 0;
            for (int i = 0; i < emb.length; i++) {
                float diff = emb[i] - knownEmb[i];
                distance += diff * diff;
            }
            distance = (float) Math.sqrt(distance);
            if (ret == null || distance < ret.second) {
                prev_ret = ret;
                ret = new Pair<>(name, distance);
            }
        }


        if (prev_ret == null) prev_ret = ret;
        neighbour_list.add(ret);
        neighbour_list.add(prev_ret);

        return neighbour_list;

    }

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private static Bitmap getCropBitmapByCPU(Bitmap source, RectF cropRectF) {
        Bitmap resultBitmap = Bitmap.createBitmap((int) cropRectF.width(),
                (int) cropRectF.height(), Bitmap.Config.ARGB_8888);
        Canvas cavas = new Canvas(resultBitmap);

        // draw background
        Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG);
        paint.setColor(Color.WHITE);
        cavas.drawRect(
                new RectF(0, 0, cropRectF.width(), cropRectF.height()),
                paint);

        Matrix matrix = new Matrix();
        matrix.postTranslate(-cropRectF.left, -cropRectF.top);

        cavas.drawBitmap(source, matrix, paint);

        if (source != null && !source.isRecycled()) {
            source.recycle();
        }

        return resultBitmap;
    }

    private static Bitmap rotateBitmap(Bitmap bitmap, int rotationDegrees, boolean flipX, boolean flipY) {
        Matrix matrix = new Matrix();

        // Rotate the image back to straight.
        matrix.postRotate(rotationDegrees);

        // Mirror the image along the X or Y axis.
        matrix.postScale(flipX ? -1.0f : 1.0f, flipY ? -1.0f : 1.0f);
        Bitmap rotatedBitmap =
                Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);

        // Recycle the old bitmap if it has changed.
        if (rotatedBitmap != bitmap) {
            bitmap.recycle();
        }
        return rotatedBitmap;
    }

    //IMPORTANT. If conversion not done ,the toBitmap conversion does not work on some devices.
    private static byte[] YUV_420_888toNV21(Image image) {
        int width = image.getWidth();
        int height = image.getHeight();
        int ySize = width * height;
        int uvSize = width * height / 4;

        byte[] nv21 = new byte[ySize + uvSize * 2];

        ByteBuffer yBuffer = image.getPlanes()[0].getBuffer(); // Y
        ByteBuffer uBuffer = image.getPlanes()[1].getBuffer(); // U
        ByteBuffer vBuffer = image.getPlanes()[2].getBuffer(); // V

        int rowStride = image.getPlanes()[0].getRowStride();
        assert (image.getPlanes()[0].getPixelStride() == 1);

        int pos = 0;

        if (rowStride == width) { // likely
            yBuffer.get(nv21, 0, ySize);
            pos += ySize;
        } else {
            long yBufferPos = -rowStride; // not an actual position
            for (; pos < ySize; pos += width) {
                yBufferPos += rowStride;
                yBuffer.position((int) yBufferPos);
                yBuffer.get(nv21, pos, width);
            }
        }

        rowStride = image.getPlanes()[2].getRowStride();
        int pixelStride = image.getPlanes()[2].getPixelStride();

        assert (rowStride == image.getPlanes()[1].getRowStride());
        assert (pixelStride == image.getPlanes()[1].getPixelStride());

        if (pixelStride == 2 && rowStride == width && uBuffer.get(0) == vBuffer.get(1)) {
            // maybe V an U planes overlap as per NV21, which means vBuffer[1] is alias of uBuffer[0]
            byte savePixel = vBuffer.get(1);
            try {
                vBuffer.put(1, (byte) ~savePixel);
                if (uBuffer.get(0) == (byte) ~savePixel) {
                    vBuffer.put(1, savePixel);
                    vBuffer.position(0);
                    uBuffer.position(0);
                    vBuffer.get(nv21, ySize, 1);
                    uBuffer.get(nv21, ySize + 1, uBuffer.remaining());

                    return nv21; // shortcut
                }
            } catch (ReadOnlyBufferException ex) {
                // unfortunately, we cannot check if vBuffer and uBuffer overlap
            }

            // unfortunately, the check failed. We must save U and V pixel by pixel
            vBuffer.put(1, savePixel);
        }

        // other optimizations could check if (pixelStride == 1) or (pixelStride == 2),
        // but performance gain would be less significant

        for (int row = 0; row < height / 2; row++) {
            for (int col = 0; col < width / 2; col++) {
                int vuPos = col * pixelStride + row * rowStride;
                nv21[pos++] = vBuffer.get(vuPos);
                nv21[pos++] = uBuffer.get(vuPos);
            }
        }

        return nv21;
    }

    private Bitmap toBitmap(Image image) {

        byte[] nv21 = YUV_420_888toNV21(image);

        YuvImage yuvImage = new YuvImage(nv21, ImageFormat.NV21, image.getWidth(), image.getHeight(), null);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 75, out);

        byte[] imageBytes = out.toByteArray();
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    //Save Faces to Shared Preferences.Conversion of Recognition objects to json string
    private void insertToSP(HashMap<String, SimilarityClassifier.Recognition> jsonMap, int mode) {
        if (mode == 1)  //mode: 0:save all, 1:clear all, 2:update all
            jsonMap.clear();
        else if (mode == 0)
            jsonMap.putAll(loadRecognitionFace());
        String jsonString = new Gson().toJson(jsonMap);
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString("map", jsonString);
        editor.apply();
        Toast.makeText(context, "Recognitions Saved", Toast.LENGTH_SHORT).show();
    }

    //Load Faces from Shared Preferences.Json String to Recognition object
    private HashMap<String, SimilarityClassifier.Recognition> loadRecognitionFace() {
        SharedPreferences sharedPreferences = getSharedPreferences("HashMap", MODE_PRIVATE);
        String defValue = new Gson().toJson(new HashMap<String, SimilarityClassifier.Recognition>());
        String json = sharedPreferences.getString("map", defValue);
        TypeToken<HashMap<String, SimilarityClassifier.Recognition>> token = new TypeToken<HashMap<String, SimilarityClassifier.Recognition>>() {
        };

        HashMap<String, SimilarityClassifier.Recognition> retrievedMap = new Gson().fromJson(json, token.getType());

        //During type conversion and save/load procedure,format changes(eg float converted to double).
        //So embeddings need to be extracted from it in required format(eg.double to float).

        for (Map.Entry<String, SimilarityClassifier.Recognition> entry : retrievedMap.entrySet()) {
            float[][] output = new float[1][OUTPUT_SIZE];
            ArrayList arrayList = (ArrayList) entry.getValue().getExtra();
            arrayList = (ArrayList) arrayList.get(0);
            for (int counter = 0; counter < arrayList.size(); counter++) {
                output[0][counter] = ((Double) arrayList.get(counter)).floatValue();
            }
            entry.getValue().setExtra(output);
        }
        Toast.makeText(context, "Recognitions Loaded", Toast.LENGTH_SHORT).show();
        return retrievedMap;
    }

    //Load Photo from phone storage
    private void loadphoto() {
        start = false;
        Intent intent = new Intent();
        intent.setType("image/*");
        intent.setAction(Intent.ACTION_GET_CONTENT);
        startActivityForResult(Intent.createChooser(intent, "Select Picture"), SELECT_PICTURE);
    }

    //Similar Analyzing Procedure
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK) {
            if (requestCode == SELECT_PICTURE) {
                Uri selectedImageUri = data.getData();
                try {
                    InputImage impphoto = InputImage.fromBitmap(getBitmapFromUri(selectedImageUri), 0);
                    detector.process(impphoto).addOnSuccessListener(new OnSuccessListener<List<Face>>() {
                        @Override
                        public void onSuccess(List<Face> faces) {

                            if (faces.size() != 0) {
                                binding.recognize.setText("Recognize");
                                binding.insertFace.setVisibility(View.VISIBLE);
                                reco_name.setVisibility(View.INVISIBLE);
                                binding.showImage.setVisibility(View.VISIBLE);
                                preview_info.setText("1.Bring Face in view of Camera.\n\n2.Your Face preview will appear here.\n\n3.Click Add button to save face.");
                                Face face = faces.get(0);

                                Bitmap frame_bmp = null;
                                try {
                                    frame_bmp = getBitmapFromUri(selectedImageUri);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                Bitmap frame_bmp1 = rotateBitmap(frame_bmp, 0, flipX, false);

                                RectF boundingBox = new RectF(face.getBoundingBox());
                                Bitmap cropped_face = getCropBitmapByCPU(frame_bmp1, boundingBox);


                                Bitmap scaled = getResizedBitmap(cropped_face, 112, 112);
                                recognizeImage(scaled);
                                addFace();
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }).addOnFailureListener(new OnFailureListener() {
                        @Override
                        public void onFailure(@NonNull Exception e) {
                            start = true;
                            Toast.makeText(context, "Failed to add", Toast.LENGTH_SHORT).show();
                        }
                    });
                    binding.showImage.setImageBitmap(getBitmapFromUri(selectedImageUri));
                } catch (IOException e) {
                    e.printStackTrace();
                }


            }
        }
    }

    private Bitmap getBitmapFromUri(Uri uri) throws IOException {
        ParcelFileDescriptor parcelFileDescriptor =
                getContentResolver().openFileDescriptor(uri, "r");
        FileDescriptor fileDescriptor = parcelFileDescriptor.getFileDescriptor();
        Bitmap image = BitmapFactory.decodeFileDescriptor(fileDescriptor);
        parcelFileDescriptor.close();
        return image;
    }

}

