package com.dean.demo.cameraxdemo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.ImageFormat;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Bundle;
import android.os.Environment;
import android.util.DisplayMetrics;
import android.util.Log;
import android.util.Rational;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.camera.core.CameraX;
import androidx.camera.core.FlashMode;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageAnalysisConfig;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureConfig;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.core.PreviewConfig;
import androidx.core.content.ContextCompat;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.NotFoundException;
import com.google.zxing.PlanarYUVLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;

import org.androidannotations.annotations.AfterViews;
import org.androidannotations.annotations.Click;
import org.androidannotations.annotations.EActivity;
import org.androidannotations.annotations.ViewById;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.nio.ByteBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@EActivity(R.layout.activity_camera)
public class CameraActivity extends AppCompatActivity {

    private static final int REQUEST_PERMISSION = 1;

    @ViewById(R.id.texture)
    TextureView textureView;

    @ViewById(R.id.analysis_image)
    AppCompatImageView analysisImage;

    @ViewById(R.id.cropRect_image)
    AppCompatImageView cropRectImage;

    private DisplayMetrics textureMetrics;
    private ImageCapture imageCapture;
    private boolean mDebug;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @AfterViews
    void initViews() {
        if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            textureView.post(CameraActivity.this::startCamera);
        } else requestCameraPermission();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        CameraX.unbindAll();
    }

    private void requestCameraPermission() {
        requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_PERMISSION);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_PERMISSION) {
            if (ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.CAMERA)
                    == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(CameraActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED) {
                startCamera();
            } else {
                scannerFailure();
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private void startCamera() {
        textureMetrics = new DisplayMetrics();
        textureView.getDisplay().getRealMetrics(textureMetrics);
        Size size = getOptimalSize(textureMetrics.widthPixels, textureMetrics.heightPixels);
//        size = new Size(textureView.getWidth() * 4 / 3, textureView.getWidth());
//        ConstraintLayout.LayoutParams constraintLayout = new ConstraintLayout.LayoutParams(size.getHeight(), size.getWidth());
//        textureView.setLayoutParams(constraintLayout);

        /*
         Preview
         */
        PreviewConfig previewConfig = new PreviewConfig.Builder()
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetAspectRatioCustom(new Rational(size.getHeight(), size.getWidth()))
//                .setTargetResolution(new Size(textureMetrics.widthPixels, textureMetrics.heightPixels))
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();

        Preview preview = new Preview(previewConfig);
        preview.setOnPreviewOutputUpdateListener(output -> {
            ViewGroup parent = (ViewGroup) textureView.getParent();
            parent.removeView(textureView);
            parent.addView(textureView, 0);
            textureView.setSurfaceTexture(output.getSurfaceTexture());
            updateTransform();
        });

        /*
         Analysis
         */
        ImageAnalysisConfig analysisConfig = new ImageAnalysisConfig.Builder()
//                .setTargetAspectRatio(AspectRatio.RATIO_4_3)
                .setTargetAspectRatioCustom(new Rational(size.getHeight(), size.getWidth()))
//                .setTargetResolution(new Size(textureMetrics.widthPixels, textureMetrics.heightPixels))
                .setImageReaderMode(ImageAnalysis.ImageReaderMode.ACQUIRE_LATEST_IMAGE)
                .setLensFacing(CameraX.LensFacing.BACK)
                .build();

        ImageAnalysis analysis = new ImageAnalysis(analysisConfig);
        analysis.setAnalyzer(this::runOnUiThread, new BarCodeAnalyzer());

        /*
         Capture
         */
        ImageCaptureConfig captureConfig = new ImageCaptureConfig.Builder()
                .setTargetRotation(getWindowManager().getDefaultDisplay().getRotation())
                .setCaptureMode(ImageCapture.CaptureMode.MAX_QUALITY)
                .setFlashMode(FlashMode.OFF)
                .setTargetAspectRatioCustom(new Rational(size.getHeight(), size.getWidth()))
                .build();

        imageCapture = new ImageCapture(captureConfig);

        CameraX.bindToLifecycle(CameraActivity.this, preview, analysis, imageCapture);
    }

    private void updateTransform() {

        float centerX = textureView.getWidth() / 2f;
        float centerY = textureView.getHeight() / 2f;

        // Correct preview output to account for display rotation
        float rotationDegrees = 0f;
        switch (textureView.getDisplay().getRotation()) {
            case Surface.ROTATION_0:
                rotationDegrees = 0f;
                break;
            case Surface.ROTATION_90:
                rotationDegrees = 90f;
                break;
            case Surface.ROTATION_180:
                rotationDegrees = 180f;
                break;
            case Surface.ROTATION_270:
                rotationDegrees = 270f;
                break;
        }

        Matrix matrix = new Matrix();
        matrix.postRotate(-rotationDegrees, centerX, centerY);

        // Finally, apply transformations to our TextureView
        textureView.setTransform(matrix);
    }

    @UiThread
    private void scannerSuccess(String cardNumber) {
//        CameraX.unbindAll();
//        MainActivity.setGoodsInfo(cardNumber);
//        CameraActivity.this.onBackPressed();
        Toast.makeText(CameraActivity.this, cardNumber, Toast.LENGTH_SHORT).show();
    }

    @UiThread
    private void scannerFailure() {
        CameraActivity.this.onBackPressed();
    }

    @Click(R.id.take_picture_button)
    void takePicture() {
        File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/IMG" + getTime() + ".jpg");
        imageCapture.takePicture(
                file,
                this::runOnUiThread,
                new ImageCapture.OnImageSavedListener() {
                    @Override
                    public void onImageSaved(@NonNull File file) {
                        Toast.makeText(CameraActivity.this, "Photo has saved to:" + file.getAbsolutePath(), Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onError(@NonNull ImageCapture.ImageCaptureError imageCaptureError, @NonNull String message, @Nullable Throwable cause) {
                        Toast.makeText(CameraActivity.this, message, Toast.LENGTH_SHORT).show();
                        Log.e("PHOTO FAILED", message, cause);
                    }
                });
    }

    @Click(R.id.debug_button)
    void toggleDebug() {
        mDebug = !mDebug;
    }

    private class BarCodeAnalyzer implements ImageAnalysis.Analyzer {

        private long lastAnalyzedTimestamp = 0;
        private final MultiFormatReader reader = new MultiFormatReader();

        BarCodeAnalyzer() {
            List<BarcodeFormat> decodeFormats = new ArrayList<>();
            decodeFormats.add(BarcodeFormat.EAN_8);
            decodeFormats.add(BarcodeFormat.EAN_13);
            decodeFormats.add(BarcodeFormat.UPC_A);
            decodeFormats.add(BarcodeFormat.UPC_E);
            decodeFormats.add(BarcodeFormat.CODE_128);

            Map<DecodeHintType, Object> hints = new EnumMap<>(DecodeHintType.class);
//            hints.put(DecodeHintType.PURE_BARCODE, Boolean.TRUE);
            hints.put(DecodeHintType.CHARACTER_SET, "utf-8");
            hints.put(DecodeHintType.POSSIBLE_FORMATS, decodeFormats);
//            hints.put(DecodeHintType.TRY_HARDER, Boolean.TRUE);
            reader.setHints(hints);
        }

        @Override
        public void analyze(ImageProxy image, int rotationDegrees) {

//            Toast.makeText(CameraActivity.this, "width:" + image.getWidth() + "height" + image.getHeight(), Toast.LENGTH_SHORT).show();

            long currentTimestamp = System.currentTimeMillis();
            if (currentTimestamp - lastAnalyzedTimestamp >= 42) {

                byte[] data = rotate(image2Bytes(image), image.getWidth(), image.getHeight(), rotationDegrees);

                if(mDebug) {
                    analysisImage.setVisibility(View.VISIBLE);
                    cropRectImage.setVisibility(View.VISIBLE);
                    analysisImage.setImageBitmap(byte2Bitmap(image, data));
                    Rect cropRect = getCropRect(image.getWidth(), image.getHeight(), rotationDegrees != 0);
                    cropRectImage.setMinimumHeight(cropRect.height());
                    cropRectImage.setMinimumWidth(cropRect.width());
                } else {
                    analysisImage.setVisibility(View.GONE);
                    cropRectImage.setVisibility(View.GONE);
                }

                PlanarYUVLuminanceSource source = new PlanarYUVLuminanceSource(
                        data,
                        image.getHeight(),
                        image.getWidth(),
                        0, 0, image.getHeight(), image.getWidth(),
                        false);

                BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));

                try {
                    Result result = reader.decode(bitmap);
                    scannerSuccess(result.getText());
                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
                lastAnalyzedTimestamp = currentTimestamp;
            }
        }

        private Rect getCropRect(int width, int height, boolean rotate) {

            float heightScale;
            float widthScale;

            if (rotate) {
                heightScale = (float) width / textureMetrics.heightPixels;
                widthScale = (float) height / textureMetrics.widthPixels;
            } else {
                heightScale = (float) height / textureMetrics.heightPixels;
                widthScale = (float) width / textureMetrics.widthPixels;
            }

            int left = 0;
            int top = (int) (textureMetrics.heightPixels / 4 * heightScale);
            int right = (int) (textureMetrics.widthPixels * widthScale);
            int bottom = (int) (textureMetrics.heightPixels / 4 * 3 * heightScale);

            return new Rect(left, top, right, bottom);

        }

        private byte[] image2Bytes(ImageProxy image) {
            ByteBuffer byteBufferY = image.getPlanes()[0].getBuffer();
            ByteBuffer byteBufferU = image.getPlanes()[1].getBuffer();
            ByteBuffer byteBufferV = image.getPlanes()[2].getBuffer();

            int sizeY = byteBufferY.remaining();
            int sizeU = byteBufferU.remaining();
            int sizeV = byteBufferV.remaining();

            byte[] bytes21 = new byte[sizeY + sizeU + sizeV];

            byteBufferY.get(bytes21, 0, sizeY);
            for (int u = sizeY; u < sizeY + sizeU; u++) {
                bytes21[u] = -128;
            }
            for (int v = sizeY + sizeU; v < sizeY + sizeU + sizeV; v++) {
                bytes21[v] = -128;
            }
//            byteBufferV.get(bytes21, sizeY, sizeV);
//            byteBufferU.get(bytes21, sizeY + sizeV, sizeU);

            return bytes21;
        }

        private Bitmap byte2Bitmap(ImageProxy image, byte[] bytes21) {
            YuvImage yuvImage = new YuvImage(bytes21, ImageFormat.NV21, image.getHeight(), image.getWidth(), null);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            yuvImage.compressToJpeg(new Rect(0, 0, yuvImage.getWidth(), yuvImage.getHeight()), 50, outputStream);
            byte[] imageBytes = outputStream.toByteArray();
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
        }

        private byte[] rotate(final byte[] yuv, final int width, final int height, final int rotation) {
            if (rotation == 0) return yuv;
            if (rotation % 90 != 0 || rotation < 0 || rotation > 270) {
                throw new IllegalArgumentException("0 <= rotation < 360, rotation % 90 == 0");
            }
            final byte[] output = new byte[yuv.length];
            final int frameSize = width * height;
            final boolean swap = rotation % 180 != 0;
            final boolean xflip = rotation % 270 != 0;
            final boolean yflip = rotation >= 180;

            for (int j = 0; j < height; j++) {
                for (int i = 0; i < width; i++) {
                    final int yIn = j * width + i;
                    final int uIn = frameSize + (j >> 1) * width + (i & ~1);
                    final int vIn = uIn + 1;

                    final int wOut = swap ? height : width;
                    final int hOut = swap ? width : height;
                    final int iSwapped = swap ? j : i;
                    final int jSwapped = swap ? i : j;
                    final int iOut = xflip ? wOut - iSwapped - 1 : iSwapped;
                    final int jOut = yflip ? hOut - jSwapped - 1 : jSwapped;

                    final int yOut = jOut * wOut + iOut;
                    final int uOut = frameSize + (jOut >> 1) * wOut + (iOut & ~1);
                    final int vOut = uOut + 1;
                    output[yOut] = (byte) (0xff & yuv[yIn]);
                    output[uOut] = (byte) (0xff & yuv[uIn]);
                    output[vOut] = (byte) (0xff & yuv[vIn]);
                }
            }

            return output;
//            byte[] rotatedData = new byte[yuv.length];
//            for (int y = 0; y < height; y++) {
//                for (int x = 0; x < width; x++)
//                    rotatedData[x * height + height - y - 1] = yuv[x + y * width];
//            }
//            return rotatedData;
        }
    }

    private String getTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault());
        long time = new Date().getTime();
        return sdf.format(time);
    }

    private Size getOptimalSize(int width, int height) {
        Size bestSize = new Size(480, 640);
        // 摄像头管理器
        CameraManager manager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            // 遍历所有摄像头
            for (String cameraId : manager.getCameraIdList()) {
                CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
                // 跳过前置摄像头
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (facing != null && facing == CameraCharacteristics.LENS_FACING_FRONT)
                    continue;
                // 摄像头支持的所有输出格式和分辨率
                StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                assert map != null;
                // 与预览尺寸比例最相近的摄像头支持的分辨率
                Size[] sizes = map.getOutputSizes(ImageFormat.JPEG);
                Arrays.sort(sizes, (size1, size2) -> {
                    float ratio1 = (float) size1.getHeight() / size1.getWidth();
                    float ratio2 = (float) size2.getHeight() / size2.getWidth();
                    // 差异越大比例越小，越靠前
                    if (ratio1 <= ratio2) return 0; // size1, size2
                    else return 1; // size2, size1
                });
                double textureRatio = width / (double) height;
                double distanceTemp = 1;
                for (Size size : sizes) {
                    double ratio = size.getHeight() / (double) size.getWidth();
                    if (Math.abs(textureRatio - ratio) < distanceTemp) {
                        distanceTemp = Math.abs(textureRatio - ratio);
                        bestSize = size;
                    }
                }
                // 摄像头支持的最大分辨率
//                Size largestSize = Collections.max(Arrays.asList(map.getOutputSizes(ImageFormat.JPEG)), (lhs, rhs) -> Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getHeight() * rhs.getWidth()));
                Log.v("bestSize:", bestSize + "");
                break;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return bestSize;
    }
}
