package dlzp.arfuga.N33ble1;

import android.bluetooth.BluetoothGattCharacteristic;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.lifecycle.LifecycleOwner;

import java.util.function.Consumer;

import dlzp.arfuga.ArfugaApp;
import dlzp.arfuga.Constants;

/**
 * This specifically handles ChangeReceived intent-actions that N33ble1MonitorService receives.
 * Based on BLE characteristics changed, this will perform the action necessary. For example, when a
 * button press has been confirmed, this will signal to the necessary components or clients to
 * perform the action the user expects when the button is pressed.
 *
 * TODO Assess this for improvements. There is no second-button functionality right now. This may
 *      need a better connection to the UI.
 */
public class N33ble1MonitorBleEventHandler {
    private static final String LOG_TAG = "N33ble1MonitorBleEventHandler";

    private final Context context;
    private final N33ble1BluetoothGattCallback bluetoothGattCallback;
    private final Handler handlerLedLeft = new Handler(Looper.getMainLooper());

    // If LifecycleOwner is needed in the future, re-add it as argument from N33ble1MonitorService.
    N33ble1MonitorBleEventHandler(Context applicationContext, N33ble1BluetoothGattCallback bluetoothGattCallback) {
        this.context = applicationContext;
        this.bluetoothGattCallback = bluetoothGattCallback;
    }

    public void onBluetoothGattReady() {
        try {
            // Read initial values of button and buttonHandled characters.
            bluetoothGattCallback.addChangeRequest(bluetoothGattCallback.new ChangeRequest(bluetoothGattCallback.getButtonLeftCharacter(), false));
            bluetoothGattCallback.addChangeRequest(bluetoothGattCallback.new ChangeRequest(bluetoothGattCallback.getButtonRightCharacter(), false));
            bluetoothGattCallback.addChangeRequest(bluetoothGattCallback.new ChangeRequest(bluetoothGattCallback.getButtonLeftHandledCharacter(), false));
            bluetoothGattCallback.addChangeRequest(bluetoothGattCallback.new ChangeRequest(bluetoothGattCallback.getButtonRightHandledCharacter(), false));

            // Set left and right leds to ON when first connecting.
            setButtonLed(true, Constants.LedTimingIgnore, 0, 0, 0);
            setButtonLed(false, Constants.LedTimingIgnore, 0, 0, 0);

            // Set board rgb led to blue when first connecting.
            byte[] lights = new byte[]{(byte) 0, (byte) 0, (byte) 0, (byte) 255};
            BluetoothGattCharacteristic boardLed = bluetoothGattCallback.getBoardLedCharacter();
            boardLed.setValue(lights);
            bluetoothGattCallback.addChangeRequest(bluetoothGattCallback.new ChangeRequest(boardLed, true));
        } catch (N33ble1BluetoothGattCallback.NullBleComponentException e) {
            Log.e(LOG_TAG, e.getMessage());
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
        }
    }

    public void onChangeEvent() {
        try {
            checkAndProcessButtonChanged(
                    bluetoothGattCallback.getButtonLeftCharacter(),
                    bluetoothGattCallback.getButtonLeftHandledCharacter(),
                    this::onButtonLeftHandledConfirmed);

            checkAndProcessButtonChanged(
                    bluetoothGattCallback.getButtonRightCharacter(),
                    bluetoothGattCallback.getButtonRightHandledCharacter(),
                    this::onButtonRightHandledConfirmed);

        } catch (N33ble1BluetoothGattCallback.NullBleComponentException e) {
            Log.e(LOG_TAG, e.getMessage());
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
        }
    }
    private void checkAndProcessButtonChanged(
            BluetoothGattCharacteristic buttonCharacter,
            BluetoothGattCharacteristic buttonHandledCharacter,
            Consumer<Integer> onChangeConfirmed) {
        final byte[] buttonValue = buttonCharacter.getValue();
        final byte[] buttonHandledValue = buttonHandledCharacter.getValue();

        if(buttonValue == null || buttonHandledValue == null) {
            // Kind-of noisy as INFO because this is expected on first connection as we send out
            // ChangeEvents on every BLE first-read.
            Log.d(LOG_TAG, "ButtonCharacter or ButtonHandledCharacter is not yet read.");
            return;
        }

        if(buttonValue.length != 1) {
            Log.e(LOG_TAG, "ButtonCharacter " + buttonCharacter.getUuid() + " is of an unexpected length: " + buttonValue.length);
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
            return;
        }

        if(buttonHandledValue.length != 1) {
            Log.e(LOG_TAG, "ButtonHandledCharacter " + buttonHandledCharacter.getUuid() + " is of an unexpected length: " + buttonHandledValue.length);
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
            return;
        }

        final int currentSequence = buttonValue[0] & 0b00111111;
        final int handledSequence = buttonHandledValue[0] & 0b00111111;

        if(currentSequence == handledSequence) {
            Log.d(LOG_TAG, "Current button press for " + buttonCharacter.getUuid() + " already handled: " + currentSequence);
            return;
        }

        buttonHandledCharacter.setValue(buttonValue);
        final N33ble1BluetoothGattCallback.ChangeRequest changeRequest =
                bluetoothGattCallback.new ChangeRequest(buttonHandledCharacter, true);

        final int pressType = (buttonValue[0] >> 6) & 0b00000011;
        changeRequest.onCompletedRunnable = () -> onChangeConfirmed.accept(pressType);

        bluetoothGattCallback.addChangeRequest(changeRequest);
    }

    // TODO: delaySeconds is only implemented for LEFT at the moment!
    private void setButtonLed(boolean isLeft, byte timing, int firstSeqInt, int secondSeqInt, int delaySeconds) {
        final byte firstSeq = (byte)firstSeqInt;
        final byte secondSeq = (byte)secondSeqInt;

        if((firstSeq & 0b11111000) != 0) {
            Log.e(LOG_TAG, "Cannot set first led sequence to " + firstSeq + "; too large!");
            return;
        }

        if((secondSeq & 0b11111000) != 0) {
            Log.e(LOG_TAG, "Cannot set second led sequence to " + secondSeq + "; too large!");
            return;
        }

        Log.i(LOG_TAG, "Setting " + (isLeft ? "left" : "right") +
                        " led - timing: " + Byte.toUnsignedInt(timing) +
                        ", firstSeq: " + firstSeqInt +
                        ", secondSeq: " + delaySeconds +
                        ", delay: " + delaySeconds);

        try {
            final BluetoothGattCharacteristic ledCharacter =
                    (isLeft ? bluetoothGattCallback.getButtonLeftLedCharacter()
                            : bluetoothGattCallback.getButtonRightLedCharacter());
            final byte[] newValue = {(byte)(timing | ((secondSeq << 3) & 0b00111000) | (firstSeq & 0b00000111))};

            ledCharacter.setValue(newValue);
            bluetoothGattCallback.addChangeRequest(
                    bluetoothGattCallback.new ChangeRequest(ledCharacter, true));
        } catch (N33ble1BluetoothGattCallback.NullBleComponentException e) {
            Log.e(LOG_TAG, e.getMessage());
            N33ble1State.sendIntent(context, N33ble1State.BleServiceError);
        }

        if(isLeft && delaySeconds != 0) {
            handlerLedLeft.removeCallbacksAndMessages(null);
            handlerLedLeft.postDelayed(() -> {
                setButtonLed(true, Constants.LedTimingIgnore, 0, 0, 0);
            }, delaySeconds * 1000L);
        }
    }

    // TODO improved led management (hmm...)
    // TODO UI shows debug values for LED and button (+ handled?)
    private void onButtonLeftHandledConfirmed(int pressType) {
        Log.i(LOG_TAG, "Verified left button press handle write, doing operation.");
        switch (pressType) {
            case Constants.ButtonPressTypePressSingle: {
                Log.i(LOG_TAG, "Button pressed once");
                final boolean sendSuccess = ArfugaApp
                        .getDLZPServerClient()
                        .sendGaragePiCmd(Constants.GaragePiCmdStatus);
                if (sendSuccess) {
                    setButtonLed(true, Constants.LedTimingIgnore, 0, 0, 0);
                    // TODO implement status get and UI
//                    DLZPServerClient.getInstance(context).getGaragePiStatus().observe(lifecycleOwner, new Observer<String>() {
//                        @Override
//                        public void onChanged(String status) {
//                            if(status.contains(""))
//                        }
//                    });
                } else {
                    setButtonLed(true, Constants.LedTimingShort, 3, 3, 12);
                }
            } break;

            case Constants.ButtonPressTypePressDouble: {
                Log.i(LOG_TAG, "Button pressed twice");
                final boolean sendSuccess = ArfugaApp
                        .getDLZPServerClient()
                        .sendGaragePiCmd(Constants.GaragePiCmdTimed);
                if (sendSuccess) {
                    setButtonLed(true, Constants.LedTimingLong, 1, 1, 35);
                } else {
                    setButtonLed(true, Constants.LedTimingShort, 3, 3, 12);
                }
            } break;

            case Constants.ButtonPressTypeHoldShort: {
                Log.i(LOG_TAG, "Button held short");
                final boolean sendSuccess = ArfugaApp
                        .getDLZPServerClient()
                        .sendGaragePiCmd(Constants.GaragePiCmdToggle);
                if (sendSuccess) {
                    setButtonLed(true, Constants.LedTimingLong, 1, 2, 12);
                } else {
                    setButtonLed(true, Constants.LedTimingShort, 3, 3, 12);
                }
            } break;

            case Constants.ButtonPressTypeHoldLong: {
                Log.i(LOG_TAG, "Button held long");
                final boolean isEnabled = ArfugaApp
                        .getDLZPServerClient()
                        .toggleGaragePiLocallyEnabled();
                if (isEnabled) {
                    setButtonLed(true, Constants.LedTimingLong, 3, 2, 12);
                } else {
                    setButtonLed(true, Constants.LedTimingLong, 3, 3, 12);
                }
            } break;
        }
    }

    private void onButtonRightHandledConfirmed(int pressType) {
        Log.i(LOG_TAG, "Verified right button press handle write, doing operation.");
        switch (pressType) {
            case Constants.ButtonPressTypePressSingle:
                Log.i(LOG_TAG, "Button pressed once");
                // TODO send pause/play
                break;

            case Constants.ButtonPressTypePressDouble:
                Log.i(LOG_TAG, "Button pressed twice");
                // TODO send skip/next
                break;

            case Constants.ButtonPressTypeHoldShort:
                Log.i(LOG_TAG, "Button held short");
                // TODO
                break;

            case Constants.ButtonPressTypeHoldLong:
                Log.i(LOG_TAG, "Button held long");
                // TODO
                break;
        }
    }
}
