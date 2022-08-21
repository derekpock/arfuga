package dlzp.arfuga.ui;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import dlzp.arfuga.Constants;
import dlzp.arfuga.N33ble1BluetoothGattCallback;
import dlzp.arfuga.N33ble1MonitorService;
import dlzp.arfuga.N33ble1State;
import dlzp.arfuga.databinding.FragmentArduinoBinding;

public class ArduinoFragment extends Fragment {
    private static final String LOG_TAG = "ArduinoFragment";

    private class MyBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if(action.equals(N33ble1State.ChangeReceived)) {
                onChangeReceived();
            } else {
                Log.e(LOG_TAG, "Unhandled registered broadcast received: " + action);
            }
        }
    }

    private FragmentArduinoBinding binding;
    private MyBroadcastReceiver broadcastReceiver = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        final FragmentActivity fragmentActivity = getActivity();
        if(fragmentActivity != null) {
            broadcastReceiver = new MyBroadcastReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(N33ble1State.ChangeReceived);
            fragmentActivity.registerReceiver(broadcastReceiver, intentFilter);
        } else {
            Log.e(LOG_TAG, "This doesn't have a parent activity?!");
        }

        binding = FragmentArduinoBinding.inflate(inflater, container, false);

        onChangeReceived();

        N33ble1State.getDeviceConnected().observe(getViewLifecycleOwner(), (Boolean isConnected) -> {
            binding.buiconConnected.setVisibility(isConnected ? View.VISIBLE : View.INVISIBLE);
            binding.buiconDisconnected.setVisibility(!isConnected ? View.VISIBLE : View.INVISIBLE);

            if(!isConnected) {
                forgetValues();
            }
        });

        N33ble1State.getEncounteredError().observe(getViewLifecycleOwner(), (String errorDescription) -> {
            if(errorDescription.isEmpty()) {
                binding.valErrorDescription.setText("No Errors");
            } else {
                binding.valErrorDescription.setText(errorDescription);
            }
        });

        binding.butResetConnection.setOnClickListener((View) ->
                N33ble1State.sendIntent(this.getActivity().getApplicationContext(), N33ble1State.ResetConnection));

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        if(broadcastReceiver != null) {
            final FragmentActivity fragmentActivity = getActivity();
            if(fragmentActivity != null) {
                fragmentActivity.unregisterReceiver(broadcastReceiver);
            } else {
                Log.e(LOG_TAG, "BroadcastReceiver is not null but this doesn't have a parent activity?!?!");
            }
            broadcastReceiver = null;
        }
        binding = null;
        super.onDestroyView();
    }

    private void forgetValues() {
        final TextView[] valueViews = new TextView[]{
                binding.valButtonLeft,
                binding.valButtonLeftHandled,
                binding.valButtonLeftLed,
                binding.valButtonRight,
                binding.valButtonRightHandled,
                binding.valButtonRightLed,
                binding.valLedColor,
                binding.valLedExternal
        };

        for(TextView view : valueViews) {
            view.setText("unknown");
        }
    }

    private void setButtonText(N33ble1BluetoothGattCallback bluetoothGattCallback, String charUuid, TextView viewValue) {
        try {
            final byte[] bytes = bluetoothGattCallback.getCharacter(charUuid).getValue();
            if(bytes == null) {
                viewValue.setText("null");
            } else if (bytes.length != 1) {
                viewValue.setText("unexp len " + bytes.length);
            } else {
                final byte currentSequence = (byte)(bytes[0] & 0b00111111);
                final byte pressType = (byte)((bytes[0] >> 6) & 0b00000011);
                viewValue.setText(Byte.toUnsignedInt(pressType) + " " + Byte.toUnsignedInt(currentSequence));
            }
        } catch(N33ble1BluetoothGattCallback.NullBleComponentException e) {
            viewValue.setText("char not present");
        }

    }

    private void setSingleLedFromByte(byte b, TextView viewValue) {
        final byte sequenceA = (byte)(b & 0b00000111);
        final byte sequenceB = (byte)((b >> 3) & 0b00000111);
        final byte timing = (byte)((b >> 6) & 0b00000011);
        viewValue.setText(Byte.toUnsignedInt(timing) + " " + Byte.toUnsignedInt(sequenceB) + " " + Byte.toUnsignedInt(sequenceA));
    }

    private void setSingleLedFromChar(N33ble1BluetoothGattCallback bluetoothGattCallback, String charUuid, TextView viewValue) {
        try {
            final byte[] bytes = bluetoothGattCallback.getCharacter(charUuid).getValue();
            if(bytes == null) {
                viewValue.setText("null");
            } else if (bytes.length != 1) {
                viewValue.setText("unexp len " + bytes.length);
            } else {
                setSingleLedFromByte(bytes[0], viewValue);
            }
        } catch (N33ble1BluetoothGattCallback.NullBleComponentException e) {
            viewValue.setText("char not present");
        }
    }

    private void setBoardLeds(N33ble1BluetoothGattCallback bluetoothGattCallback) {
        try {
            final byte[] bytes = bluetoothGattCallback.getBoardLedCharacter().getValue();
            if(bytes == null) {
                binding.valLedColor.setText("null");
                binding.valLedExternal.setText("null");
            } else if (bytes.length != 4) {
                binding.valLedColor.setText("unexp len " + bytes.length);
                binding.valLedExternal.setText("unexp len " + bytes.length);
            } else {
                final byte red = bytes[1];
                final byte green = bytes[2];
                final byte blue = bytes[3];

                binding.valLedColor.setText("R" + Byte.toUnsignedInt(red) + " G" + Byte.toUnsignedInt(green) + " B" + Byte.toUnsignedInt(blue));
                setSingleLedFromByte(bytes[0], binding.valLedExternal);
            }
        } catch (N33ble1BluetoothGattCallback.NullBleComponentException e) {
            binding.valLedColor.setText("char not present");
            binding.valLedExternal.setText("char not present");
        }
    }

    private void onChangeReceived() {
        final N33ble1BluetoothGattCallback bluetoothGattCallback =
                N33ble1MonitorService.getBluetoothGattCallbackInstance();
        if(bluetoothGattCallback == null) {
            forgetValues();
        } else {
            setButtonText(bluetoothGattCallback, Constants.ButtonLeftCharUuid, binding.valButtonLeft);
            setButtonText(bluetoothGattCallback, Constants.ButtonLeftHandledCharUuid, binding.valButtonLeftHandled);

            setSingleLedFromChar(bluetoothGattCallback, Constants.ButtonLeftLedCharUuid, binding.valButtonLeftLed);

            setButtonText(bluetoothGattCallback, Constants.ButtonRightCharUuid, binding.valButtonRight);
            setButtonText(bluetoothGattCallback, Constants.ButtonRightHandledCharUuid, binding.valButtonRightHandled);

            setSingleLedFromChar(bluetoothGattCallback, Constants.ButtonRightLedCharUuid, binding.valButtonRightLed);

            setBoardLeds(bluetoothGattCallback);
        }
    }
}