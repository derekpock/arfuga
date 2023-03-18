package dlzp.arfuga.N33ble1;

import android.companion.CompanionDeviceService;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.NonNull;

import dlzp.arfuga.R;

/**
 * Signaled by the CompanionDeviceManager whenever N33ble1 enters or leaves range. CDM knows of this
 * class thanks to servicing done by N33ble1AssociationManager.
 *
 * This service does very little. A companion service detached from CDM (and one that can be
 * foregrounded) N33ble1MonitorService does the actual monitoring of N33ble1 when in range.
 */
public class N33ble1CompanionService extends CompanionDeviceService {
    private static final String LOG_TAG = "N33ble1CompanionService";

    @Override
    // This is NOT called when the service is initiated!
    // Even with REQUEST_COMPANION_RUN_IN_BACKGROUND, this does NOT maintain this service's life
    // once this is returned. Is the system binding being removed too early? Bug? Design?
    // https://stackoverflow.com/q/70084826/2511908
    // https://stackoverflow.com/q/65684425/2511908
    // https://issuetracker.google.com/issues/207485313
    //
    // Workaround: foreground a separate "monitor" service when the device is connected.
    // The monitor service will foreground itself, likely requiring/utilizing permission
    // REQUEST_COMPANION_START_FOREGROUND_SERVICES_FROM_BACKGROUND.
    public void onDeviceAppeared(@NonNull String address) {
        if(!address.equals(getString(R.string.N33ble1Address))) {
            Log.e(LOG_TAG, "Unexpected device appear message: " + address);
            return;
        }
        Log.i(LOG_TAG, "N33ble1 appeared");

        startService(new Intent(getApplicationContext(), N33ble1MonitorService.class));
    }

    @Override
    // This doesn't appear to be called at all if a BluetoothGatt connection is connected to the
    // target device when it disappears. This hidden limitation does not appear to affect
    // onDeviceAppeared in any way.
    //  - We're using the BluetoothGatt's "Device Disconnected" notification to gauge when the
    //    device is gone.
    //  - We're un-foregrounding the monitor service when the device is disconnected.
    //  - We're allowing the monitor service to be killed (typically in 60 seconds) of when the
    //    device disappears.
    //  - The 60 second window when the app is in the background plus the reliable onDeviceAppeared
    //    notifications work well together to ensure the monitor service is alive when the device is
    //    present.
    public void onDeviceDisappeared(@NonNull String address) {
        if(!address.equals(getString(R.string.N33ble1Address))) {
            Log.e(LOG_TAG, "Unexpected device disappear message: " + address);
            return;
        }
        Log.i(LOG_TAG, "N33ble1 disappeared");

        stopService(new Intent(getApplicationContext(), N33ble1MonitorService.class));
    }
}