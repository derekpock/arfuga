package dlzp.arfuga.N33ble1.association;

import android.bluetooth.le.ScanFilter;
import android.companion.AssociationRequest;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultCaller;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

import dlzp.arfuga.N33ble1.N33ble1MonitorService;
import dlzp.arfuga.R;

/**
 * Manages the association and servicing status of N33ble1. If associated, "servicing" refers to
 * informing the CompanionDeviceManager to signal the N33ble1CompanionService when N33ble1 enters or
 * leaves range. This "informing" needs to be done every application startup (and/or boot).
 */
public class N33ble1AssociationManager extends ContextWrapper {
    private static final String LOG_TAG = "N33ble1AssociationManager";

    @NonNull
    private final CompanionDeviceManager companionDeviceManager;

    public N33ble1AssociationManager(Context base) {
        super(base);
        companionDeviceManager = Objects.requireNonNull((CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE));
    }

    public boolean isAssociated() {
        final List<String> associations = companionDeviceManager.getAssociations();
        Log.v(LOG_TAG, "Associated devices: " + associations);
        return associations.contains(getString(R.string.N33ble1Address));
    }

    public void service() {
        if (!isAssociated()) {
            Log.i(LOG_TAG, "Not servicing N33ble1 as it has not been associated.");
            return;
        }

        // Tell CDM that we want notifications when N33ble1 enters or leaves range.
        companionDeviceManager.startObservingDevicePresence(getString(R.string.N33ble1Address));

        // CompanionDeviceService does not send a notification on startup if the device is already
        // present. Since there is a separate monitor service that manages the bluetooth connection
        // (and will foreground itself as needed), just start the service now and let it die if no/
        // device is present.
        final ComponentName serviceName =
                startService(new Intent(this, N33ble1MonitorService.class));
        if(serviceName == null) {
            Log.e(LOG_TAG, "N33ble1MonitorService not started on startup!");
        }
    }

    public void associate(ActivityResultCaller activityResultCaller) {
        if(isAssociated()) {
            Log.i(LOG_TAG, "Not associating N33ble1 as it is already associated.");
            return;
        }

        // The generated association request below is incredibly strict. We're looking for a
        // specific, hard-coded address. This could be improved to potentially examine multiple
        // candidate devices.
        final ScanFilter scanFilter = new ScanFilter.Builder()
                .setDeviceAddress(getString(R.string.N33ble1Address))
                .build();

        final BluetoothLeDeviceFilter bleDeviceFilter = new BluetoothLeDeviceFilter.Builder()
                .setScanFilter(scanFilter)
                .build();

        final AssociationRequest associationRequest = new AssociationRequest.Builder()
                .addDeviceFilter(bleDeviceFilter)
                .setSingleDevice(true)
                .build();

        // 1. Ask companionDeviceManager to associate a device using the association request above.
        // 2. companionDeviceManagerCallback will receive a callback once a suitable device has been
        //    scanned or if a failure occurred.
        // 3. On success, companionDeviceManagerCallback will ask activityResultCaller (main
        //    activity) to launch an activity to the user to ask them to allow the pairing. This
        //    request is created before hand (now) in activityResultLauncher but launched in
        //    companionDeviceManagerCallback.
        // 4. Once launched, activityResultCallback will be called with the result of the user's
        //    choice.
        // 5. On success, activityResultCallback will verify the confirmed device and in return
        //    call back to this to begin servicing N33ble1.

        final ActivityResultContracts.StartIntentSenderForResult contract =
                new ActivityResultContracts.StartIntentSenderForResult();
        final ActivityResultCallback<ActivityResult> activityResultCallback =
                new N33ble1ActivityResultCallback(this);
        final ActivityResultLauncher<IntentSenderRequest> activityResultLauncher =
                activityResultCaller.registerForActivityResult(contract, activityResultCallback);

        final N33ble1CompanionDeviceManagerCallback companionDeviceManagerCallback =
                new N33ble1CompanionDeviceManagerCallback(activityResultLauncher);
        companionDeviceManager.associate(associationRequest, companionDeviceManagerCallback, null);
    }
}
