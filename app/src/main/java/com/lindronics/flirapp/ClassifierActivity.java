package com.lindronics.flirapp;

import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.UiThread;
import androidx.appcompat.app.AppCompatActivity;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatus;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.google.gson.Gson;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;

public class ClassifierActivity extends AppCompatActivity {

    private static final String TAG = "ClassifierActivity";

    private Handler handler;
    private HandlerThread handlerThread;

    private CameraHandler cameraHandler;

    private ImageView rgbImage;
    private ImageView firImage;

    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);

    private ImageWriter imageWriter = null;

    ModelHandler modelHandler;

    private TextView predictionsBox;


    /**
     * Executed when activity is created.
     * Get camera identity from intent and connect to camera.
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_classifier);

        cameraHandler = new CameraHandler();

        rgbImage = findViewById(R.id.rgb_view);
        firImage = findViewById(R.id.fir_view);

        Bundle extras = getIntent().getExtras();
        Gson gson = new Gson();

        if (extras == null) {
            finish();
            return;
        }

        String identityString = extras.getString("cameraIdentity");
        Identity cameraIdentity = gson.fromJson(identityString, Identity.class);
        cameraHandler.connect(cameraIdentity, connectionStatusListener);

//        modelHandler = new ModelHandler(this, ModelHandler.Device.GPU, 1);
        recreateModelHandler();

        predictionsBox = findViewById(R.id.predictions_box);
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
        recreateModelHandler();
        handlerThread = new HandlerThread("inference");
        handlerThread.start();
        handler = new Handler(handlerThread.getLooper());
    }

    @Override
    public synchronized void onPause() {
        handlerThread.quitSafely();
        try {
            handlerThread.join();
            handlerThread = null;
            handler = null;
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
        super.onPause();
    }

    protected synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }

    /**
     * Defines behaviour for when images are received
     */
    private final CameraHandler.StreamDataListener streamDataListener = new CameraHandler.StreamDataListener() {

        @Override
        public void images(FrameDataHolder dataHolder) {

            runOnUiThread(() -> {
                firImage.setImageBitmap(dataHolder.firBitmap);
                rgbImage.setImageBitmap(dataHolder.rgbBitmap);
            });
        }

        @Override
        public void images(Bitmap firBitmap, Bitmap rgbBitmap) {

            try {
                framesBuffer.put(new FrameDataHolder(firBitmap, rgbBitmap));
            } catch (InterruptedException e) {
                // If interrupted while waiting for adding a new item in the queue
                Log.e(TAG, "images(), unable to add incoming images to frames buffer, exception:" + e);
            }

            runOnUiThread(() -> {
                Log.d(TAG, "framebuffer size:" + framesBuffer.size());
                FrameDataHolder poll = framesBuffer.poll();
                if (poll != null) {
                    firImage.setImageBitmap(poll.firBitmap);
                    rgbImage.setImageBitmap(poll.rgbBitmap);
                }
            });

            // Classification
            runInBackground(() -> {

                // TODO implement rotation scanner
                int sensorOrientation = 0;


                if (modelHandler != null) {
//                    final long startTime = SystemClock.uptimeMillis();
                    final List<ModelHandler.Recognition> results =
                            modelHandler.recognizeImage(rgbBitmap, firBitmap, sensorOrientation);
//                    lastProcessingTimeMs = SystemClock.uptimeMillis() - startTime;
//                    LOGGER.v("Detect: %s", results);

                    runOnUiThread(() -> {
                        showResultsInBottomSheet(results);
                    });
                }
//                readyForNextImage();
            });

        }
    };

    @UiThread
    protected void showResultsInBottomSheet(List<ModelHandler.Recognition> results) {
        if (results != null && results.size() >= 1) {
            ModelHandler.Recognition recognition = results.get(0);
            if (recognition != null) {
                    predictionsBox.setText(recognition.getTitle() + String.format(" %.2f", (100 * recognition.getConfidence())) + "%");
            }

//            Recognition recognition1 = results.get(1);
//            if (recognition1 != null) {
//                if (recognition1.getTitle() != null) recognition1TextView.setText(recognition1.getTitle());
//                if (recognition1.getConfidence() != null)
//                    recognition1ValueTextView.setText(
//                            String.format("%.2f", (100 * recognition1.getConfidence())) + "%");
//            }
//
//            Recognition recognition2 = results.get(2);
//            if (recognition2 != null) {
//                if (recognition2.getTitle() != null) recognition2TextView.setText(recognition2.getTitle());
//                if (recognition2.getConfidence() != null)
//                    recognition2ValueTextView.setText(
//                            String.format("%.2f", (100 * recognition2.getConfidence())) + "%");
//            }
        }
    }

    private void recreateModelHandler() {
        if (modelHandler != null) {
            modelHandler.close();
            modelHandler = null;
        }

        try {
            modelHandler = new ModelHandler(this, ModelHandler.Device.CPU, 2);
        } catch (IOException e) {
            e.printStackTrace();
            finish();
        }
    }


    /**
     * Defines behaviour vor changes of the connection status
     */
    private final ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onConnectionStatusChanged(@NotNull ConnectionStatus connectionStatus, @org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onConnectionStatusChanged connectionStatus:" + connectionStatus + " errorCode:" + errorCode);

            runOnUiThread(() -> {

                switch (connectionStatus) {
                    case CONNECTING:
                        break;
                    case CONNECTED: {
                        cameraHandler.startStream(streamDataListener);
                    }
                    break;
                    case DISCONNECTING:
                        break;
                    case DISCONNECTED: {
                        finish();
                    }
                    break;
                }
            });
        }
    };
}
