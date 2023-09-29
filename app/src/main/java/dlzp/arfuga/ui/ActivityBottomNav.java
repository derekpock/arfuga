package dlzp.arfuga.ui;

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

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;
import androidx.navigation.ui.AppBarConfiguration;
import androidx.navigation.ui.NavigationUI;

import java.util.List;

import dlzp.arfuga.ArfugaApp;
import dlzp.arfuga.N33ble1.N33ble1MonitorService;
import dlzp.arfuga.N33ble1.N33ble1State;
import dlzp.arfuga.R;
import dlzp.arfuga.data.DLZPServerClient;
import dlzp.arfuga.databinding.ActivityBottomNavBinding;

//            ActivityCompat.requestPermissions(this, Manifest.permission.BLUETOOTH_CONNECT, 0);
//    ActivityCompat#requestPermissions
// here to request the missing permissions, and then overriding
//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                                          int[] grantResults)
// to handle the case where the user grants the permission. See the documentation
// for ActivityCompat#requestPermissions for more details.

/**
 * Core UI Activity, this prepares the tab UI fragments and Toasts events received from
 * N33ble1State. Also will trigger association automatically on app startup.
 *
 * TODO Toasting could be moved to its own class.
 * TODO Bluetooth permission, association configuration, and other less-hardcoding of Android
 *      components.
 */
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
                case N33ble1State.BluetoothGattReady:
                case N33ble1State.BluetoothGattError:
                case DLZPServerClient.GaragePiStatusUpdated:
                case DLZPServerClient.GaragePiError:
                case DLZPServerClient.FuelTrackerStatusUpdated:
                    // These cases are already sufficiently handled via LiveData ui elements or they
                    // are not ui desirable.
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

        // Have the N33ble1AssociationManager try to associate if we aren't associated already.
        // Noop if already associated - we may be already connected to the device in that case.
        ArfugaApp.getN33ble1AssociationManager().associate(this);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(myBroadcastReceiver);
        super.onDestroy();
    }
}
