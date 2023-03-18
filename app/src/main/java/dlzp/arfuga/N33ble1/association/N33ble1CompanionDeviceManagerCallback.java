package dlzp.arfuga.N33ble1.association;

import android.companion.CompanionDeviceManager;
import android.content.IntentSender;
import android.os.Looper;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.annotation.Nullable;

/**
 * Awaits and ferries the result of a CompanionDeviceManager association scan to a UI Intent to ask
 * the user for association confirmation once N33ble1 has been discovered.
 */
public class N33ble1CompanionDeviceManagerCallback extends CompanionDeviceManager.Callback {
    private static final String LOG_TAG = "N33ble1CompanionDeviceManagerCallback";

    private final ActivityResultLauncher<IntentSenderRequest> activityResultLauncher;

    N33ble1CompanionDeviceManagerCallback(ActivityResultLauncher<IntentSenderRequest> activityResultLauncher) {
        this.activityResultLauncher = activityResultLauncher;
    }

    @Override
    public void onAssociationPending(IntentSender chooserLauncher) {
        if(Looper.getMainLooper().getThread() != Thread.currentThread()) {
            Log.e(LOG_TAG, "onAssociationPending not called on main thread!");
        }

        if(activityResultLauncher == null) {
            Log.e(LOG_TAG, "No activity result launcher for finalizing association!");
            return;
        }

        activityResultLauncher.launch(new IntentSenderRequest.Builder(chooserLauncher).build());
    }

    @Override
    public void onFailure(@Nullable CharSequence error) {
        Log.e(LOG_TAG, "Error in device association: " + error);
    }
}