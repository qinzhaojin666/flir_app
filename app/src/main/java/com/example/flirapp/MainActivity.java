package com.example.flirapp;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import com.flir.thermalsdk.ErrorCode;
import com.flir.thermalsdk.androidsdk.ThermalSdkAndroid;
import com.flir.thermalsdk.live.CommunicationInterface;
import com.flir.thermalsdk.live.Identity;
import com.flir.thermalsdk.live.connectivity.ConnectionStatus;
import com.flir.thermalsdk.live.connectivity.ConnectionStatusListener;
import com.flir.thermalsdk.live.discovery.DiscoveryEventListener;
import com.flir.thermalsdk.log.ThermalLog;

import org.jetbrains.annotations.NotNull;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private CameraHandler cameraHandler;
    private Identity connectedIdentity = null;


    /**
     * Executed when activity is created
     * @param savedInstanceState -
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set logging level (DEBUG or NONE)
        ThermalLog.LogLevel enableLoggingInDebug = BuildConfig.DEBUG ? ThermalLog.LogLevel.DEBUG : ThermalLog.LogLevel.NONE;

        // Initialize SDK
        ThermalSdkAndroid.init(getApplicationContext(), enableLoggingInDebug);

        cameraHandler = new CameraHandler();

        startDiscovery();
    }



    /**
     * Start camera discovery
     */
    private void startDiscovery() {
        cameraHandler.startDiscovery(cameraDiscoveryListener, discoveryStatusListener);
    }

    /**
     * Stop camera discovery
     */
    private void stopDiscovery() {
        cameraHandler.stopDiscovery(discoveryStatusListener);
    }

    /**
     * Callback for discovery status
     */
    private final CameraHandler.DiscoveryStatus discoveryStatusListener = new CameraHandler.DiscoveryStatus() {
        @Override
        public void started() {
            showMessage.showOnUI("Starting discovery.");
        }

        @Override
        public void stopped() {
            showMessage.showOnUI("Stopped discovery.");
        }
    };

    /**
     * Callback for camera discovered
     */
    private final DiscoveryEventListener cameraDiscoveryListener = new DiscoveryEventListener() {

        @Override
        public void onCameraFound(Identity identity) {
            Log.d(TAG, "onCameraFound identity:" + identity);

            // If in debug mode, connect to emulator
            if (BuildConfig.DEBUG && cameraHandler.isEmulator(identity)) {
                connect(identity);
            }
            // Otherwise, only connect to camera
            else if (cameraHandler.isCamera(identity)) {
                connect(identity);
            }
        }

        @Override
        public void onDiscoveryError(CommunicationInterface communicationInterface, ErrorCode errorCode) {
            Log.d(TAG, "onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);

            runOnUiThread(() -> {
                showMessage.show("onDiscoveryError communicationInterface:" + communicationInterface + " errorCode:" + errorCode);
            });
        }
    };

    /**
     * Connect to a Camera
     */
    private void connect(Identity identity) {

        // Stop discovery when connected
        cameraHandler.stopDiscovery(discoveryStatusListener);


        // Already connected
        if (connectedIdentity != null) {
            Log.d(TAG, "connect(), already connected to a camera!");
            showMessage.showOnUI("connect(), already connected to a camera!");
            return;
        }

        // No camera available
        if (identity == null) {
            Log.d(TAG, "connect(), no camera available!");
            showMessage.showOnUI("connect(), no camera available!");
            return;
        }

        // If no errors, connect to camera
        showMessage.showOnUI("connecting to " + identity);
        connectedIdentity = identity;
        cameraHandler.connect(identity, connectionStatusListener);
    }

    /**
     * Disconnect from a camera
     */
    private void disconnect() {
        Log.d(TAG, "disconnect() called with: connectedIdentity = [" + connectedIdentity + "]");
        connectedIdentity = null;
        cameraHandler.disconnect();

        // Start discovery when disconnected
        startDiscovery();
    }

    private final ConnectionStatusListener connectionStatusListener = new ConnectionStatusListener() {
        @Override
        public void onConnectionStatusChanged(@NotNull ConnectionStatus connectionStatus, @org.jetbrains.annotations.Nullable ErrorCode errorCode) {
            Log.d(TAG, "onConnectionStatusChanged connectionStatus:" + connectionStatus + " errorCode:" + errorCode);

            runOnUiThread(() -> {

                switch (connectionStatus) {
                    case CONNECTING: break;
                    case CONNECTED: {
                        showMessage.showOnUI("Connected to camera!");
                    }
                    break;
                    case DISCONNECTING:
                        break;
                    case DISCONNECTED: {
                        disconnect();
                        showMessage.showOnUI("Disconnected from camera!");
                    }
                    break;
                }
            });
        }
    };

    /**
     * Show message on the screen
     */
    interface ShowMessage {
        void show(String message);
        void showOnUI(String message);
    }

    private ShowMessage showMessage = new ShowMessage() {
        @Override
        public void show(String message) {
            Toast.makeText(MainActivity.this, message, Toast.LENGTH_SHORT).show();
            TextView view = (TextView) findViewById(R.id.textBox);
            view.setText(view.getText() + "\n" + message);
        }

        @Override
        public void showOnUI(String message) {
            runOnUiThread(() -> show(message));
        }
    };
}
