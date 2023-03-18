package dlzp.arfuga;

import android.app.Application;
import android.companion.CompanionDeviceManager;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import java.util.List;
import java.util.Objects;

import dlzp.arfuga.N33ble1.N33ble1MonitorService;
import dlzp.arfuga.N33ble1.association.N33ble1AssociationManager;
import dlzp.arfuga.data.DLZPServerClient;

/**
 * Custom Application wrapper that initializes some static structures on application creation.
 * The ApplicationContext should only be used in classes and services that should exist for the
 * entirety of the application lifetime (beyond UI, Activities, and Service lifetimes).
 */
public class ArfugaApp extends Application {
    private static final String LOG_TAG = "ArfugaApp";

    private static ArfugaApp instance;

    public static N33ble1AssociationManager getN33ble1AssociationManager() {
        Objects.requireNonNull(instance);
        Objects.requireNonNull(instance.n33ble1AssociationManager);
        return instance.n33ble1AssociationManager;
    }

    public static DLZPServerClient getDLZPServerClient() {
        Objects.requireNonNull(instance);
        Objects.requireNonNull(instance.dlzpServerClient);
        return instance.dlzpServerClient;
    }

    private N33ble1AssociationManager n33ble1AssociationManager = null;
    private DLZPServerClient dlzpServerClient = null;

    @Override
    public void onCreate() {
        super.onCreate();

        if(instance != null) {
            Log.e(LOG_TAG, "Another instance of ArfugaApp already running?!");
        }
        instance = this;

        n33ble1AssociationManager = new N33ble1AssociationManager(this);
        n33ble1AssociationManager.service();

        dlzpServerClient = new DLZPServerClient(this);
    }
}
