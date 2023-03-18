package dlzp.arfuga.N33ble1.association;

import android.app.Activity;
import android.bluetooth.le.ScanResult;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;

import dlzp.arfuga.R;

/**
 * Receives the result of the user's association request after N33ble1 had been found. If the user
 * accepts, N33ble1 will be internally associated by the CompanionDeviceManager automatically.
 * This will subsequently have the N33ble1AssociationManager to start servicing N33ble1.
 */
public class N33ble1ActivityResultCallback implements ActivityResultCallback<ActivityResult> {
    private static final String LOG_TAG = "N33ble1ActivityResultCallback";

    private final N33ble1AssociationManager associationManager;

    public N33ble1ActivityResultCallback(N33ble1AssociationManager associationManager) {
        this.associationManager = associationManager;
    }

    @Override
    public void onActivityResult(ActivityResult result) {
        if (result.getResultCode() != Activity.RESULT_OK) {
            Log.e(LOG_TAG, "ActivityResult resultCode is not OK: " + result.getResultCode());
            return;
        }

        final Intent data = result.getData();
        if (data == null) {
            Log.e(LOG_TAG, "ActivityResult had unexpected null data");
            return;
        }

        // Use of EXTRA_DEVICE is deprecated, but recommended alternative
        // AssociationInfo.getAssociatedDevice() isn't available until Android 14.
        @SuppressWarnings("deprecation")
        final ScanResult scanResult =
                data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE, ScanResult.class);

        if(scanResult == null) {
            Log.e(LOG_TAG, "ScanResult is null in ActivityResult! Wrong class? Incomplete request?");
            return;
        }

        if(!scanResult.getDevice().getAddress().equals(associationManager.getString(R.string.N33ble1Address))) {
            Log.e(LOG_TAG, "Association completed with unexpected address: " + scanResult.getDevice().getAddress());
            return;
        }

        Log.i(LOG_TAG, "User approved association to " + associationManager.getString(R.string.N33ble1Address));
        associationManager.service();
    }
}
