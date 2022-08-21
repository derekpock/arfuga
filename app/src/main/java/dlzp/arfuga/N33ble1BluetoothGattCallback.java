package dlzp.arfuga;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Looper;
import android.util.Log;

import java.util.LinkedList;
import java.util.UUID;

public class N33ble1BluetoothGattCallback extends BluetoothGattCallback {
    private static final String LOG_TAG = "N33ble1BluetoothGattCallback";

    public static class NullBleComponentException extends Exception {
        NullBleComponentException(String message) {
            super(message);
        }
    }

    public class ChangeRequest {
        private final BluetoothGattDescriptor bluetoothDescriptor;
        private final BluetoothGattCharacteristic bluetoothCharacteristic;
        private final boolean isWrite;
        private int attempts = 0;

        public Runnable onCompletedRunnable = null;

        ChangeRequest(BluetoothGattDescriptor bluetoothDescriptor) {
            this.bluetoothDescriptor = bluetoothDescriptor;
            this.bluetoothCharacteristic = null;
            this.isWrite = true;
        }

        ChangeRequest(BluetoothGattCharacteristic bluetoothCharacteristic, boolean isWrite) {
            this.bluetoothDescriptor = null;
            this.bluetoothCharacteristic = bluetoothCharacteristic;
            this.isWrite = isWrite;
        }

        private boolean doChange() {
            if(attempts > 5) {
                Log.w(LOG_TAG, "Change request reached maximum attempts, not retrying.");
                N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
                return false;
            }
            attempts++;

            try {
                if(bluetoothDescriptor != null && bluetoothCharacteristic == null) {
                    if(isWrite) {
                        gatt.writeDescriptor(bluetoothDescriptor);
                    } else {
                        Log.e(LOG_TAG, "Descriptor reads are not implemented!");
                        return false;
                    }
                } else if (bluetoothCharacteristic != null && bluetoothDescriptor == null) {
                    if(isWrite) {
                        gatt.writeCharacteristic(bluetoothCharacteristic);
                    } else {
                        gatt.readCharacteristic(bluetoothCharacteristic);
                    }
                } else {
                    Log.e(LOG_TAG, "Ambiguous change request cannot be completed!");
                    return false;
                }
            } catch (SecurityException e) {
                N33ble1State.sendAndLogBluetoothPermissionError(context, LOG_TAG, "ChangeRequest.doChange");
                return false;
            }

            return true;
        }

        private void complete() {
            if(onCompletedRunnable != null) {
                onCompletedRunnable.run();
            }
        }
    }

    private void doNextChangeRequest() {
        while(true) {
            final ChangeRequest request = changeRequests.peekFirst();
            if(request == null) {
                return;
            }

            final boolean changeWasRequested = request.doChange();
            if(changeWasRequested) {
                return;
            }

            request.complete();
            changeRequests.pollFirst();
        }
    }

    public void addChangeRequest(ChangeRequest changeRequest) {
        final boolean wasEmpty = changeRequests.isEmpty();
        changeRequests.add(changeRequest);
        if(wasEmpty) {
            doNextChangeRequest();
        }
    }

    private void retryChangeRequest() {
        if (changeRequests.isEmpty()) {
            Log.e(LOG_TAG, "Trying to retry a request but there are no pending requests!");
        } else {
            doNextChangeRequest();
        }
    }

    private void completeChangeRequest() {
        final ChangeRequest changeRequest = changeRequests.pollFirst();
        if(changeRequest == null) {
            Log.e(LOG_TAG, "Trying to complete a request when there are no pending requests!");
        } else {
            N33ble1State.sendIntent(context, N33ble1State.ChangeReceived);
            changeRequest.complete();
            doNextChangeRequest();
        }
    }

    private final Context context;
    private BluetoothGatt gatt = null;
    private final LinkedList<ChangeRequest> changeRequests = new LinkedList<>();

    N33ble1BluetoothGattCallback(Context context) {
        this.context = context;
    }

    private BluetoothGattService getCbService() throws NullBleComponentException {
        if(gatt == null) {
            throw new NullBleComponentException("BluetoothGatt has not yet been set.");
        }

        final BluetoothGattService cbService =
                gatt.getService(UUID.fromString(Constants.CbServiceUuid));
        if (cbService == null) {
            throw new NullBleComponentException("CbService is null, is N33ble1 disconnected?");
        }
        return cbService;
    }

    public BluetoothGattCharacteristic getCharacter(String uuid) throws NullBleComponentException {
        final BluetoothGattCharacteristic character =
                getCbService().getCharacteristic(UUID.fromString(uuid));
        if(character == null) {
            throw new NullBleComponentException("Character from uuid '" + uuid + "' is null, is N33ble1 malformed?");
        }
        return character;
    }

    public BluetoothGattCharacteristic getBoardLedCharacter() throws NullBleComponentException { return getCharacter(Constants.BoardLedCharUuid); }
    public BluetoothGattCharacteristic getButtonLeftCharacter() throws NullBleComponentException { return getCharacter(Constants.ButtonLeftCharUuid); }
    public BluetoothGattCharacteristic getButtonRightCharacter() throws NullBleComponentException { return getCharacter(Constants.ButtonRightCharUuid); }
    public BluetoothGattCharacteristic getButtonLeftHandledCharacter() throws NullBleComponentException { return getCharacter(Constants.ButtonLeftHandledCharUuid); }
    public BluetoothGattCharacteristic getButtonRightHandledCharacter() throws NullBleComponentException { return getCharacter(Constants.ButtonRightHandledCharUuid); }
    public BluetoothGattCharacteristic getButtonLeftLedCharacter() throws NullBleComponentException { return getCharacter(Constants.ButtonLeftLedCharUuid); }
    public BluetoothGattCharacteristic getButtonRightLedCharacter() throws NullBleComponentException { return getCharacter(Constants.ButtonRightLedCharUuid); }

    // TODO need to verify that we are on the main ui thread (or add concurrency checks)
    //      Looper.getMainLooper().getThread() == Thread.currentThread()

    // Consider using indication instead of notify if we encounter read errors / missing notifications.
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")  // false means failure :/
    private boolean registerForNotifications(BluetoothGattCharacteristic character) throws SecurityException {
        gatt.setCharacteristicNotification(character, true);

        final BluetoothGattDescriptor descriptor =
                character.getDescriptor(UUID.fromString(Constants.ClientCharacteristicConfiguration));
        if(descriptor == null) {
            Log.e(LOG_TAG, "When registering for notifications, descriptor is null from a non-null character!");
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
            return false;
        }

        if(!descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)) {
            // Is the provided character supporting notifications?
            Log.e(LOG_TAG, "Unable to set ENABLE_NOTIFICATION_VALUE on descriptor!");
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
            return false;
        }

        addChangeRequest(new ChangeRequest(descriptor));
        return true;
    }

    private void ensureGattMatch(BluetoothGatt gatt) {
        if(this.gatt == null) {
            this.gatt = gatt;
        } else if (this.gatt != gatt) {
            Log.e(LOG_TAG, "BluetoothGatt received from callback is mismatching!");
        }

        if(Looper.getMainLooper().getThread() != Thread.currentThread()) {
            Log.e(LOG_TAG, "BluetoothGatt callback processed not on the main thread!");
        }
    }

    @Override
    public void onConnectionStateChange(BluetoothGatt gatt, int status,
                                        int newState) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e(LOG_TAG, "BluetoothGatt encountered error on connection state change: " + status);
                N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
                return;
            }

            switch (newState) {
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.i(LOG_TAG, "Disconnected from N33ble1");
                    N33ble1State.sendIntent(context, N33ble1State.DeviceDisconnected);
                    break;

                case BluetoothProfile.STATE_CONNECTING:
                    Log.d(LOG_TAG, "Connecting to N33ble1");
                    break;

                case BluetoothProfile.STATE_CONNECTED:
                    Log.i(LOG_TAG, "Connected to N33ble1");
                    N33ble1State.sendIntent(context, N33ble1State.DeviceConnected);

                    try {
                        gatt.discoverServices();
                    } catch (SecurityException e) {
                        N33ble1State.sendAndLogBluetoothPermissionError(context, LOG_TAG, "onConnectionStateChange");
                    }
                    break;

                case BluetoothProfile.STATE_DISCONNECTING:
                    Log.d(LOG_TAG, "Disconnecting from N33ble1");
                    break;

                default:
                    Log.e(LOG_TAG, "Unknown onConnectionStateChange newState: " + newState);
            }
        });
    }

    @Override
    public void onServicesDiscovered(BluetoothGatt gatt, int status) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);

            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.w(LOG_TAG, "BluetoothGatt encountered error when discovering services: " + status);
                N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
                return;
            }

            // Note, character values are not known at this point. We either need to register for
            // changes and wait for a change, or just request a character read and wait for the read
            // result callback.

            try {
                // Register for character change notifications for the button characters.
                if (!registerForNotifications(getButtonLeftCharacter())) {
                    return;
                }

                if (!registerForNotifications(getButtonRightCharacter())) {
                    return;
                }

                // Read initial values of button and buttonHandled characters.
                addChangeRequest(new ChangeRequest(getButtonLeftCharacter(), false));
                addChangeRequest(new ChangeRequest(getButtonRightCharacter(), false));
                addChangeRequest(new ChangeRequest(getButtonLeftHandledCharacter(), false));
                addChangeRequest(new ChangeRequest(getButtonRightHandledCharacter(), false));

                // Read initial values of board and button led characters.
                // We may have more use for this in the future (keep alive?) but right now we just put
                // this value on the UI as a point of entrance + debug.
//                addChangeRequest(new ChangeRequest(getBoardLedCharacter(), false));
                addChangeRequest(new ChangeRequest(getButtonLeftLedCharacter(), false));
                addChangeRequest(new ChangeRequest(getButtonRightLedCharacter(), false));

                byte[] lights = new byte[]{(byte)0, (byte)255, (byte) 0, (byte)0};
                BluetoothGattCharacteristic boardLed = getBoardLedCharacter();
                boardLed.setValue(lights);
                addChangeRequest(new ChangeRequest(boardLed, true));

            } catch (NullBleComponentException e) {
                Log.e(LOG_TAG, e.getMessage());
                N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
            } catch (SecurityException e) {
                N33ble1State.sendAndLogBluetoothPermissionError(context, LOG_TAG, "onServicesDiscovered");
            }
        });
    }

    @Override
    public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic,
                                     int status) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.d(LOG_TAG, "Character read - uuid: " + characteristic.getUuid() + " status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completeChangeRequest();
            } else {
                retryChangeRequest();
            }
        });
    }

    @Override
    public void onCharacteristicWrite(BluetoothGatt gatt,
                                      BluetoothGattCharacteristic characteristic, int status) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.d(LOG_TAG, "Character written - uuid: " + characteristic.getUuid() + " status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completeChangeRequest();
            } else {
                retryChangeRequest();
            }
        });
    }

    @Override
    public void onCharacteristicChanged(BluetoothGatt gatt,
                                        BluetoothGattCharacteristic characteristic) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.d(LOG_TAG, "Character changed - uuid: " + characteristic.getUuid());
            N33ble1State.sendIntent(context, N33ble1State.ChangeReceived);
        });
    }

    @Override
    public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                 int status) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.e(LOG_TAG, "Descriptor read changes are not implemented! uuid: " + descriptor.getUuid() + " status: " + status);
        });
    }

    @Override
    public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor,
                                  int status) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.d(LOG_TAG, "Descriptor written: uuid " + descriptor.getUuid() + " status: " + status);
            if (status == BluetoothGatt.GATT_SUCCESS) {
                completeChangeRequest();
            } else {
                retryChangeRequest();
            }
        });
    }

    @Override
    public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.e(LOG_TAG, "Reliable write changes are not implemented! status: " + status);
        });
    }

    @Override
    public void onServiceChanged(BluetoothGatt gatt) {
        context.getMainExecutor().execute(() -> {
            ensureGattMatch(gatt);
            Log.i(LOG_TAG, "Service changed, rediscovering.");
            try {
                gatt.discoverServices();
            } catch (SecurityException e) {
                N33ble1State.sendAndLogBluetoothPermissionError(context, LOG_TAG, "onServiceChanged");
            }
        });
    }
}
