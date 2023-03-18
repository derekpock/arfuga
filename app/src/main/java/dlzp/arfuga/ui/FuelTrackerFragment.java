package dlzp.arfuga.ui;

import android.annotation.SuppressLint;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import java.text.SimpleDateFormat;
import java.util.Date;

import dlzp.arfuga.ArfugaApp;
import dlzp.arfuga.data.DLZPServerClient;
import dlzp.arfuga.databinding.FragmentFueltrackerBinding;

/**
 * Builds UI fragment for user to input fuel refill information and sends signals to transmit the
 * user's info to the FuelTrackerServer when done.
 */
public class FuelTrackerFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        final FragmentFueltrackerBinding binding =
                FragmentFueltrackerBinding.inflate(inflater, container, false);

        binding.buttonSendInfo.setOnClickListener((View) -> {
            setFieldsEnabled(binding, false);
            handler.postDelayed(() -> setFieldsEnabled(binding, true), 500);

            sendInfo(binding);
        });

        ArfugaApp.getDLZPServerClient()
                .getFuelTrackerStatus()
                .observe(getViewLifecycleOwner(), (String newStatus) -> {
                    if(newStatus.equals(DLZPServerClient.FuelTrackerStatusValueSuccess)) {
                        binding.inputGallons.setText("");
                        binding.inputPrice.setText("");
                        binding.inputRange.setText("");
                        binding.inputMpgEst.setText("");
                        binding.inputAveSpeed.setText("");
                        binding.inputDriveTime.setText("");
                        binding.inputMileage.setText("");
                    }
                    binding.labelStatus.setText(newStatus);
                });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        super.onDestroyView();
    }

    private void setFieldsEnabled(FragmentFueltrackerBinding binding, boolean enabled) {
        binding.inputGallons.setEnabled(enabled);
        binding.inputPrice.setEnabled(enabled);
        binding.inputRange.setEnabled(enabled);
        binding.inputMpgEst.setEnabled(enabled);
        binding.inputAveSpeed.setEnabled(enabled);
        binding.inputDriveTime.setEnabled(enabled);
        binding.inputMileage.setEnabled(enabled);
        binding.buttonSendInfo.setEnabled(enabled);
    }

    private void sendInfo(FragmentFueltrackerBinding binding) {
        String gallons = binding.inputGallons.getText().toString();
        String ppg = binding.inputPrice.getText().toString();
        String milesEst = binding.inputRange.getText().toString();
        String mpgEst = binding.inputMpgEst.getText().toString();
        String mphAve = binding.inputAveSpeed.getText().toString();
        String timeDriving = binding.inputDriveTime.getText().toString();
        String miles = binding.inputMileage.getText().toString();

        @SuppressLint("SimpleDateFormat") final String timeStamp =
                new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new Date());

        int timeDrivingInt;
        if(timeDriving.contains(":")) {
            String[] splitTime = timeDriving.split(":");
            try {
                int hours = Integer.parseInt(splitTime[0]);
                int minutes = Integer.parseInt(splitTime[1]);
                timeDrivingInt = minutes + (hours * 60);
            } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                timeDrivingInt = 0;
            }
        } else {
            try {
                timeDrivingInt = Integer.parseInt(timeDriving);
            } catch (NumberFormatException e) {
                timeDrivingInt = 0;
            }
        }

        timeDriving = timeDrivingInt + "";

        if(gallons.isEmpty())
            gallons = "0";

        if(ppg.isEmpty())
            ppg = "0";

        if(milesEst.isEmpty())
            milesEst = "0";

        if(mpgEst.isEmpty())
            mpgEst = "0";

        if(mphAve.isEmpty())
            mphAve = "0";

        if(miles.isEmpty())
            miles = "0";

        final String message = "NEWDATA:" +
                timeStamp + "\t" +
                gallons + "\t" +
                ppg + "\t" +
                milesEst + "\t" +
                mpgEst + "\t" +
                mphAve + "\t" +
                timeDriving + "\t" +
                miles;

        ArfugaApp.getDLZPServerClient().sendFuelTrackerMessage(message);
    }
}