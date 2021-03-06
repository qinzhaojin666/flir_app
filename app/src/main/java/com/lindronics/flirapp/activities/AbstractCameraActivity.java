package com.lindronics.flirapp.activities;

import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.Switch;

import androidx.appcompat.app.AppCompatActivity;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatus;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.google.gson.Gson;
import com.lindronics.flirapp.R;
import com.lindronics.flirapp.camera.AffineTransformer;
import com.lindronics.flirapp.camera.CameraHandler;
import com.lindronics.flirapp.camera.FrameDataHolder;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

abstract class AbstractCameraActivity extends AppCompatActivity implements CameraHandler.StreamDataListener {

    private static final String TAG = "AbstractCameraActivity";

    private Handler handler;
    private HandlerThread handlerThread;

    private CameraHandler cameraHandler;

    private ImageView rgbImage;
    private ImageView firImage;

    private LinkedBlockingQueue<FrameDataHolder> framesBuffer = new LinkedBlockingQueue<>(21);
    private boolean applyTransformation;
    private AffineTransformer transformer;

    /**
     * Executed when activity is created.
     * Get camera identity from intent and connect to camera.
     *
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

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

        try {
            transformer = new AffineTransformer(this);
        } catch (IOException e) {
            finish();
        }
    }

    @Override
    public synchronized void onResume() {
        super.onResume();
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

    /**
     * Receive receiveImages from camera handler
     * @param images RGB and FIR receiveImages
     */
    @Override
    public void receiveImages(FrameDataHolder images) {

        try {
            framesBuffer.put(images);
        } catch (InterruptedException e) {
            // If interrupted while waiting for adding a new item in the queue
            Log.e(TAG, "receiveImages(), unable to add incoming receiveImages to frames buffer, exception:" + e);
        }

        runOnUiThread(() -> {
            Log.d(TAG, "framebuffer size:" + framesBuffer.size());
            FrameDataHolder poll = framesBuffer.poll();
            if (poll != null) {
                if (applyTransformation) {
                    poll.rgbBitmap = transformer.transform(poll.rgbBitmap);
                }
                firImage.setImageBitmap(poll.firBitmap);
                rgbImage.setImageBitmap(poll.rgbBitmap);
            }
        });
    }

    /**
     * Run procedure in background
     * @param r Runnable to run
     */
    synchronized void runInBackground(final Runnable r) {
        if (handler != null) {
            handler.post(r);
        }
    }


    /**
     * Defines behaviour for changes of the connection status
     */
    private final ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onConnectionStatusChanged(@NotNull ConnectionStatus connectionStatus, @org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onConnectionStatusChanged connectionStatus:" + connectionStatus + " errorCode:" + errorCode);

            runOnUiThread(() -> {

                switch (connectionStatus) {
                    case CONNECTING:
                    case DISCONNECTING:
                        break;
                    case CONNECTED: {
                        cameraHandler.startStream(AbstractCameraActivity.this);
                    }
                    break;
                    case DISCONNECTED: {
                        onDisconnected();
                        finish();
                    }
                    break;
                }
            });
        }
    };

    /**
     * Event listener for enabling or disabling image transformation
     */
    public void toggleTransformation(View view) {
        Switch toggle = (Switch) view;
        applyTransformation = toggle.isChecked();
    }

    abstract void onDisconnected();
}
