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

import dlzp.arfuga.Constants;
import dlzp.arfuga.DLZPServerClient;
import dlzp.arfuga.databinding.FragmentGaragepiBinding;

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
            DLZPServerClient.getInstance(getContext()).toggleGaragePiLocallyEnabled();
        });

        DLZPServerClient
                .getInstance(getContext())
                .getGaragePiStatus()
                .observe(getViewLifecycleOwner(), binding.labStatus::setText);

        DLZPServerClient
                .getInstance(getContext())
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
        DLZPServerClient.getInstance(getContext()).sendGaragePiCmd(cmd);
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