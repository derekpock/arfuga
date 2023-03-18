package dlzp.arfuga;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String LOG_TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // No need to do anything here. Just receiving the intent has started ArfugaApp and has
        // notified CompanionDeviceManager that we're interested in watching for N33ble1 to come in
        // range.
        Log.i(LOG_TAG, "BootReceiver received intent: " + intent.getAction());
    }
}