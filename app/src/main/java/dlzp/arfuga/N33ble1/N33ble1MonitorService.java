package dlzp.arfuga.N33ble1;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.os.IBinder;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleService;

import org.jetbrains.annotations.NotNull;

import dlzp.arfuga.R;

/**
 * Attempts to create and maintain a BLE connection with N33ble1 when created. While connected, this
 * will foreground itself to stay alive and continue monitoring N33ble1's characteristics through
 * N33ble1BluetoothGattCallback.
 *
 * This service is launched immediately on startup (as long as N33ble1 is associated) because of
 * some scanning limitation of the CompanionDeviceManager on startup. It is likely that this will be
 * quickly destroyed by the Android system if N33ble1 is not in range (and subsequently, this isn't
 * foregrounded).
 *
 * Once N33ble1CompanionService receives a notification from CDM that N33ble1 is in-range or leaves
 * range, this service will be started/stopped by N33ble1CompanionService.
 *
 * In the end, this service should be alive, foregrounded, and monitoring any time N33ble1 is
 * in-range.
 */
public class N33ble1MonitorService extends LifecycleService {
    private static final String LOG_TAG = "N33ble1MonitorService";
    private static final int NotificationIdForeground = 1;

    private static N33ble1MonitorService instance = null;
    public static N33ble1BluetoothGattCallback getBluetoothGattCallbackInstance() {
        if(instance != null && instance.bluetoothGattCallback != null) {
            return instance.bluetoothGattCallback;
        }
        return null;
    }

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case N33ble1State.ResetConnection:
                    Log.i(LOG_TAG, "Resetting connection");
                    disconnectFromN33ble1();
                    connectToN33ble1();
                    break;

                case N33ble1State.DeviceConnected:
                    Log.d(LOG_TAG, "Starting foreground with notification");
                    foregroundSelf();
                    break;

                case N33ble1State.DeviceDisconnected:
                    Log.d(LOG_TAG, "Removing foreground and notification");
                    stopForeground(true);
                    break;

                case N33ble1State.ChangeReceived:
                    if (bleEventHandler == null) {
                        Log.e(LOG_TAG, "BleEventHandler was null on ChangeReceived!");
                    } else {
                        bleEventHandler.onChangeEvent();
                    }
                    break;

                case N33ble1State.BluetoothGattReady:
                    if (bleEventHandler == null) {
                        Log.e(LOG_TAG, "BleEventHandler was null on BluetoothGattReady!");
                    } else {
                        bleEventHandler.onBluetoothGattReady();
                    }
                    break;

                default:
                    Log.e(LOG_TAG, "Unhandled registered broadcast received: " + action);
            }
        }
    }

    private MyBroadcastReceiver myBroadcastReceiver = null;
    private BluetoothGatt bluetoothGatt = null;
    private N33ble1BluetoothGattCallback bluetoothGattCallback = null;
    private N33ble1MonitorBleEventHandler bleEventHandler = null;

    public N33ble1MonitorService() {
        Log.v(LOG_TAG, "Constructed");
    }

    @Override
    public IBinder onBind(@NotNull Intent intent) {
        super.onBind(intent);
        Log.e(LOG_TAG, "Bind called but not implemented");
        throw new UnsupportedOperationException("Not implemented");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.v(LOG_TAG, "Created");

        if(instance != null) {
            Log.e(LOG_TAG, "Service created but a static instance already exists!");
        }
        instance = this;

        myBroadcastReceiver = new MyBroadcastReceiver();
        final IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(N33ble1State.ResetConnection);
        intentFilter.addAction(N33ble1State.DeviceDisconnected);
        intentFilter.addAction(N33ble1State.DeviceConnected);
        intentFilter.addAction(N33ble1State.ChangeReceived);
        intentFilter.addAction(N33ble1State.BluetoothGattReady);
        registerReceiver(myBroadcastReceiver, intentFilter);

        connectToN33ble1();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.v(LOG_TAG, "Destroyed");

        disconnectFromN33ble1();

        if(myBroadcastReceiver != null) {
            unregisterReceiver(myBroadcastReceiver);
            myBroadcastReceiver = null;
        } else {
            Log.e(LOG_TAG, "myBroadcastReceiver null upon service destruction");
        }

        if (instance == null) {
            Log.e(LOG_TAG, "Service destroyed but no static instance exists!");
        }
        instance = null;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.i(LOG_TAG, "Configuration Changed");
    }

    private void foregroundSelf() {
        final NotificationChannel notificationChannel =
                new NotificationChannel(
                        "N33ble1MonitorServiceForeground",
                        "N33ble1 Monitor Running",
                        NotificationManager.IMPORTANCE_MIN
                );

        notificationChannel.setDescription("Notifications displayed when the N33ble1 device is in range and being monitored in the background.");

        final NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        notificationManager.createNotificationChannel(notificationChannel);

        final Notification notification =
                new Notification.Builder(this, notificationChannel.getId())
                .setSmallIcon(R.drawable.ic_notif_arduino)
                .setContentTitle("Arduino Monitor Running")
                .setContentText("N33ble1 is in range and being monitored.")
                .setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
                .build();

        startForeground(NotificationIdForeground, notification);
    }

    private void connectToN33ble1() {
        Log.i(LOG_TAG, "Connecting to N33ble1");

        if(bluetoothGatt != null) {
            Log.w(LOG_TAG, "Bluetooth gatt was not null when connecting to N33ble1, resetting.");
            disconnectFromN33ble1();
        }

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter bluetoothAdapter = bluetoothManager.getAdapter();
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            Log.w(LOG_TAG, "No bluetooth adapter or adapter is offline.");
            N33ble1State.sendIntent(this, N33ble1State.AdapterOffline);
            return;
        }

        if (!BluetoothAdapter.checkBluetoothAddress(getString(R.string.N33ble1Address))) {
            Log.e(LOG_TAG, "Resource N33ble1Address is an invalid BLE address!");
            N33ble1State.sendIntent(this, N33ble1State.InvalidTargetAddress);
            return;
        }

        final BluetoothDevice bluetoothDevice = bluetoothAdapter.getRemoteDevice(getString(R.string.N33ble1Address));
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            Log.w(LOG_TAG, "Bluetooth permissions are not present.");
            N33ble1State.sendIntent(this, N33ble1State.NoBluetoothPermissions);
            return;
        }

        bluetoothGattCallback = new N33ble1BluetoothGattCallback(this);
        bluetoothGatt = bluetoothDevice.connectGatt(this, true, bluetoothGattCallback);
        bleEventHandler = new N33ble1MonitorBleEventHandler(this, bluetoothGattCallback);
    }

    private void disconnectFromN33ble1() {
        bleEventHandler = null;
        try {
            if(bluetoothGatt != null) {
                Log.i(LOG_TAG, "Disconnecting from N33ble1");
                bluetoothGatt.disconnect();
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
        } catch(SecurityException e) {
            N33ble1State.sendAndLogBluetoothPermissionError(this, LOG_TAG, "disconnectFromN33ble1");
        }
        bluetoothGattCallback = null;
    }

}