package dlzp.arfuga.ui;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import dlzp.arfuga.ArfugaApp;
import dlzp.arfuga.Constants;
import dlzp.arfuga.data.DLZPServerClient;
import dlzp.arfuga.databinding.FragmentGaragepiBinding;

/**
 * Builds UI fragment for presenting the status of GaragePi to the user as well as numerous buttons
 * for operating GaragePi. This communicates with DLZPServerClient on most button presses.
 */
public class GaragePiFragment extends Fragment {

    private final Handler handler = new Handler(Looper.getMainLooper());
    private FragmentGaragepiBinding binding;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentGaragepiBinding.inflate(inflater, container, false);

        binding.butDisable.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdDisable));
        binding.butEnable.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdEnable));
        binding.butQuiet.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdQuiet));
        binding.butLoud.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdLoud));
        binding.butOpen.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdOpen));
        binding.butClose.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdClose));
        binding.butToggle.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdToggle));
        binding.butTimed.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdTimed));
        binding.butStatus.setOnClickListener((View) -> sendCmd(Constants.GaragePiCmdStatus));

        binding.butDisenable.setOnClickListener((View) -> {
            setFieldsEnabled(false);
            handler.postDelayed(() -> setFieldsEnabled(true), 500);
            ArfugaApp.getDLZPServerClient().toggleGaragePiLocallyEnabled();
        });

        ArfugaApp.getDLZPServerClient()
                .getGaragePiStatus()
                .observe(getViewLifecycleOwner(), binding.labStatus::setText);

        ArfugaApp.getDLZPServerClient()
                .getGaragePiErrorInfo()
                .observe(getViewLifecycleOwner(), (String errorInfo) -> {
                    if(!errorInfo.isEmpty()) {
                        Toast.makeText(getContext(), errorInfo, Toast.LENGTH_LONG).show();
                    }
                });

        return binding.getRoot();
    }

    @Override
    public void onDestroyView() {
        handler.removeCallbacksAndMessages(null);
        binding = null;
        super.onDestroyView();
    }

    private void sendCmd(String cmd) {
        setFieldsEnabled(false);
        handler.postDelayed(() -> setFieldsEnabled(true), 500);
        ArfugaApp.getDLZPServerClient().sendGaragePiCmd(cmd);
    }

    private void setFieldsEnabled(boolean enabled) {
        binding.butDisable.setEnabled(enabled);
        binding.butEnable.setEnabled(enabled);
        binding.butQuiet.setEnabled(enabled);
        binding.butLoud.setEnabled(enabled);
        binding.butOpen.setEnabled(enabled);
        binding.butClose.setEnabled(enabled);
        binding.butToggle.setEnabled(enabled);
        binding.butTimed.setEnabled(enabled);
        binding.butDisenable.setEnabled(enabled);
        binding.butStatus.setEnabled(enabled);
    }
}