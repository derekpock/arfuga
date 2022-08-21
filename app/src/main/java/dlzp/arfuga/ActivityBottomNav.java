package dlzp.arfuga;

import android.app.Activity;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.companion.AssociationRequest;
import android.companion.BluetoothLeDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.List;

import dlzp.arfuga.databinding.ActivityBottomNavBinding;

//            ActivityCompat.requestPermissions(this, Manifest.permission.BLUETOOTH_CONNECT, 0);
//    ActivityCompat#requestPermissions
// here to request the missing permissions, and then overriding
//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                                          int[] grantResults)
// to handle the case where the user grants the permission. See the documentation
// for ActivityCompat#requestPermissions for more details.

public class ActivityBottomNav extends AppCompatActivity {
    private static final String LOG_TAG = "ArfugaActivity";

    private static class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            switch (action) {
                case N33ble1State.AdapterOffline:
                    // TODO ask user to enable bluetooth adapter
                    break;
                case N33ble1State.InvalidTargetAddress:
                    Toast.makeText(context, "Invalid BLE Target Address!", Toast.LENGTH_LONG).show();
                    break;
                case N33ble1State.NoBluetoothPermissions:
                    // TODO ask user to provide bluetooth permissions
                    break;
                case N33ble1State.BleServiceError:
                    Toast.makeText(context, "BLE Service Error!", Toast.LENGTH_LONG).show();
                    break;
                case N33ble1State.ChangeReceived:
                    // TODO update UI with new values
                    break;
                case N33ble1State.DeviceConnected:
                    Toast.makeText(context, "Arduino Device Connected", Toast.LENGTH_SHORT).show();
                    break;
                case N33ble1State.DeviceDisconnected:
                    Toast.makeText(context, "Arduino Device Disconnected", Toast.LENGTH_SHORT).show();
                    break;
                case N33ble1State.ResetConnection:
                case DLZPServerClient.GaragePiStatusUpdated:
                case DLZPServerClient.GaragePiError:
                case DLZPServerClient.FuelTrackerStatusUpdated:
                    // These cases are already sufficiently handled via LiveData ui elements.
                    break;

                default:
                    Log.e(LOG_TAG, "Unhandled registered broadcast received: " + action);
            }
        }
    }
    private MyBroadcastReceiver myBroadcastReceiver;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Setup intent receiving
        myBroadcastReceiver = new MyBroadcastReceiver();
        IntentFilter intentFilter = new IntentFilter();
        for(String action : N33ble1State.AllActions) {
            intentFilter.addAction(action);
        }
        for(String action : DLZPServerClient.AllActions) {
            intentFilter.addAction(action);
        }
        registerReceiver(myBroadcastReceiver, intentFilter);

        // Prepare UI
        final ActivityBottomNavBinding binding =
                ActivityBottomNavBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        final AppBarConfiguration appBarConfiguration = new AppBarConfiguration.Builder(
                R.id.navigation_arduino, R.id.navigation_fueltracker, R.id.navigation_garagepi)
                .build();
        final NavController navController =
                Navigation.findNavController(this, R.id.nav_host_fragment_activity_bottom_nav);
        NavigationUI.setupActionBarWithNavController(this, navController, appBarConfiguration);
        NavigationUI.setupWithNavController(binding.navView, navController);

        // TODO dissociate button
        // TODO configure target device menu?

        // Prepare N33ble1 components
        final CompanionDeviceManager companionDeviceManager =
                (CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE);

        final List<String> associations = companionDeviceManager.getAssociations();
        Log.v(LOG_TAG, "Associated devices: " + associations);
        if (associations.contains(getString(R.string.N33ble1Address))) {
            startServicingN33ble1(companionDeviceManager);
        } else {
            associateN33ble1(companionDeviceManager);
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(myBroadcastReceiver);
        super.onDestroy();
    }

    private void startServicingN33ble1(CompanionDeviceManager companionDeviceManager) {
        if (!companionDeviceManager.getAssociations().contains(getString(R.string.N33ble1Address))) {
            Log.e(LOG_TAG, "Cannot start servicing N33ble if it has not been associated!");
            return;
        }

        // Tell CDM that we want notifications when N33ble1 enters or leaves range.
        companionDeviceManager.startObservingDevicePresence(getString(R.string.N33ble1Address));

        // CompanionDeviceService does not send a notification on startup if the device is already
        // present. Since there is a separate monitor service that manages the bluetooth connection
        // (and will foreground itself as needed), just start the service now and let it die if no/
        // device is present.
        startService(new Intent(this, N33ble1MonitorService.class));
    }

    private void associateN33ble1(CompanionDeviceManager companionDeviceManager) {
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

        // TODO do we need to scan for the device first? what UI to show while waiting for device?
        //      low priority - this is really only going to happen once on first install for us
        companionDeviceManager.associate(associationRequest, new CompanionDeviceManager.Callback() {
            @Override
            public void onDeviceFound(IntentSender chooserLauncher) {
                try {
                    // TODO increment / work with requestCode
                    // TODO fix deprecated command
                    startIntentSenderForResult(
                            chooserLauncher, 0, null, 0, 0, 0
                    );
                } catch (IntentSender.SendIntentException e) {
                    Log.e(LOG_TAG, "Failed to send intent: " + e);
                }
            }

            @Override
            public void onFailure(CharSequence error) {
                Log.e(LOG_TAG, "Error in device association: " + error);
            }
        }, null);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode != Activity.RESULT_OK) {
            Log.e(LOG_TAG, "onActivityResult resultCode is not OK: " + resultCode);
            return;
        }

        if (requestCode == 0) {
            if (data == null) {
                Log.e(LOG_TAG, "Request code " + requestCode + " had unexpected null data");
                return;
            }

            final ScanResult scanResult = data.getParcelableExtra(CompanionDeviceManager.EXTRA_DEVICE);
            if(scanResult.getDevice().getAddress().equals(getString(R.string.N33ble1Address))) {
                Log.i(LOG_TAG, "User approved association to " + getString(R.string.N33ble1Address));
                startServicingN33ble1((CompanionDeviceManager) getSystemService(Context.COMPANION_DEVICE_SERVICE));
            } else {
                Log.e(LOG_TAG, "Association completed with unexpected address: " + scanResult.getDevice().getAddress());
            }
        }

        super.onActivityResult(requestCode, resultCode, data);
    }
}
