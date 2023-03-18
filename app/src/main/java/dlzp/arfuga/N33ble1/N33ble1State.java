package dlzp.arfuga.N33ble1;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

/**
 * Statically holds some state related to N33ble1 connection. Provides a central place to define
 * intent-focused actions related to N33ble1 connection.
 *
 * TODO The design of this isn't great and should be reworked to avoid static state outside of
 *      ArfugaApp.
 */
public class N33ble1State {
    private static final String LOG_TAG = "N33ble1State";

    public static final String AdapterOffline = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.AdapterOffline";
    public static final String InvalidTargetAddress = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.InvalidTargetAddress";
    public static final String NoBluetoothPermissions = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.NoBluetoothPermissions";
    public static final String BleServiceError = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.BleServiceError";
    public static final String ChangeReceived = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.ChangeReceived";
    public static final String DeviceConnected = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.DeviceConnected";
    public static final String DeviceDisconnected = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.DeviceDisconnected";
    public static final String ResetConnection = "dlzp.arfuga.N33ble1.N33ble1State.intent.action.ResetConnection";

    public static final String[] AllActions = {
            AdapterOffline,
            InvalidTargetAddress,
            NoBluetoothPermissions,
            BleServiceError,
            ChangeReceived,
            DeviceConnected,
            DeviceDisconnected,
            ResetConnection
    };

    public static void sendIntent(Context context, String action) {
        Intent intent = new Intent();
        intent.setAction(action);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(new Intent().setAction(action).setPackage(context.getPackageName()));

        onReceive(intent);
    }

    public static void sendAndLogBluetoothPermissionError(Context context, String logTag, String exceptionTrigger) {
        Log.e(logTag, "Bluetooth permissions denied action attempt: " + exceptionTrigger);
        sendIntent(context, NoBluetoothPermissions);
    }

    private static <T> MutableLiveData<T> InitMutableLiveData(T initialValue) {
        MutableLiveData<T> mutableLiveData = new MutableLiveData<T>();
        mutableLiveData.postValue(initialValue);
        return mutableLiveData;
    }

    private static final MutableLiveData<String> encounteredError = InitMutableLiveData("");
    private static final MutableLiveData<Boolean> deviceConnected = InitMutableLiveData(false);

    public static LiveData<String> getEncounteredError() { return encounteredError; }
    public static LiveData<Boolean> getDeviceConnected() { return deviceConnected; }

    private static void onReceive(Intent intent) {
        final String action = intent.getAction();
        switch (action) {
            case N33ble1State.AdapterOffline:
            case N33ble1State.InvalidTargetAddress:
            case N33ble1State.NoBluetoothPermissions:
            case N33ble1State.BleServiceError:
                encounteredError.postValue(action);
                break;

            case N33ble1State.ChangeReceived:
                break;

            case N33ble1State.DeviceConnected:
                deviceConnected.postValue(true);
                break;
            case N33ble1State.DeviceDisconnected:
                deviceConnected.postValue(false);
                break;

            case N33ble1State.ResetConnection:
                encounteredError.postValue("");
                deviceConnected.postValue(false);
                break;

            default:
                Log.e(LOG_TAG, "Unhandled registered broadcast received: " + action);
        }
    }
}
