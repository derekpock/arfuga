package dlzp.arfuga;

import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

public class DLZPServerClient {
    private static final String LOG_TAG = "DLZPServerClient";
    private static final String PreferencesName = "dlzp.arfuga.DLZPServerClient.preferences";
    private static final String PreferencesGaragePiEnabled = "dlzp.arfuga.DLZPServerClient.preferences.GaragePiEnabled";
    public static final String FuelTrackerStatusValueSuccess = "Success!";

    public static final String GaragePiStatusUpdated = "dlzp.arfuga.DLZPServerClient.intent.action.GaragePiStatus";
    public static final String GaragePiError = "dlzp.arfuga.DLZPServerClient.intent.action.GaragePiCmdError";
    public static final String FuelTrackerStatusUpdated = "dlzp.arfuga.DLZPServerClient.intent.action.FuelTrackerStatusUpdated";

    public static final String ExtraErrorInfo = "dlzp.arfuga.DLZPServerClient.intent.extra.ErrorInfo";

    public static final String[] AllActions = {
            GaragePiStatusUpdated,
            GaragePiError,
            FuelTrackerStatusUpdated
    };

    private static DLZPServerClient instance = null;
    public static DLZPServerClient getInstance(Context context) {
        if(instance == null) {
            instance = new DLZPServerClient(context);
        }

        return instance;
    }

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Context context;
    private final MutableLiveData<String> garagePiStatus = new MutableLiveData<>();
    private final MutableLiveData<String> garagePiErrorInfo = new MutableLiveData<>();
    private final MutableLiveData<String> fuelTrackerStatus = new MutableLiveData<>();
    private boolean garagePiEnabled;

    public LiveData<String> getGaragePiStatus() { return garagePiStatus; }
    public LiveData<String> getGaragePiErrorInfo() { return garagePiErrorInfo; }
    public LiveData<String> getFuelTrackerStatus() { return fuelTrackerStatus; }

    private DLZPServerClient(Context context) {
        this.context = context.getApplicationContext();
        garagePiEnabled = this.context
                .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .getBoolean(PreferencesGaragePiEnabled, false);
        garagePiStatus.postValue(garagePiEnabled ? "not connected" : "locally disabled");

        // TODO consistency?
        garagePiErrorInfo.postValue("");
        fuelTrackerStatus.postValue("Not Connected");
    }

    // TODO make this more detailed / not just a string for UI to break apart
    // TODO led needs to warn on these values?
    private void updateGaragePiStatus(String newStatus, String errorDescription) {
        garagePiStatus.postValue(newStatus);
        garagePiErrorInfo.postValue(errorDescription);

        context.sendBroadcast(new Intent()
                .setAction(GaragePiStatusUpdated)
                .setPackage(context.getPackageName())
        );

        if(!errorDescription.isEmpty()) {
            context.sendBroadcast(new Intent()
                    .setAction(GaragePiError)
                    .setPackage(context.getPackageName())
                    .putExtra(ExtraErrorInfo, errorDescription)
            );
        }
    }

    // TODO expand this with more info as well?
    private void updateFuelTrackerStatus(String newStatus) {
        fuelTrackerStatus.postValue(newStatus);

        context.sendBroadcast(new Intent()
                .setAction(FuelTrackerStatusUpdated)
                .setPackage(context.getPackageName())
        );
    }

    public boolean toggleGaragePiLocallyEnabled() {
        garagePiEnabled = !garagePiEnabled;
        context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
                .edit().putBoolean(PreferencesGaragePiEnabled, garagePiEnabled).apply();
        garagePiStatus.postValue(garagePiEnabled ? "not connected" : "locally disabled");
        Log.i(LOG_TAG, "GaragePi is now locally " + (garagePiEnabled ? "enabled" : "disabled"));
        return garagePiEnabled;
    }

    public boolean sendGaragePiCmd(String garagePiCommand) {
        if(!garagePiEnabled) {
            Log.i(LOG_TAG, "Not sending command '" + garagePiCommand + "'; GaragePi is locally disabled.");
            updateGaragePiStatus(
                    "locally disabled - skipped " + garagePiCommand,
                    "Command aborted\nGaragePi is locally disabled");
            return false;
        }

        try {
            executorService.submit(() -> {
                try {
                    Log.d(LOG_TAG, "Connecting to GaragePiServer...");
                    updateGaragePiStatus("connecting to server", "");
                    final SSLSocket socket =
                            (SSLSocket) SSLSocketFactory
                                    .getDefault()
                                    .createSocket(
                                            context.getString(R.string.HttpHost),
                                            context.getResources().getInteger(R.integer.HttpGaragePiPort)
                                    );

                    if (!HttpsURLConnection
                            .getDefaultHostnameVerifier()
                            .verify(context.getString(R.string.HttpHost), socket.getSession())) {
                        throw new SSLHandshakeException("");
                    }

                    final String body = context.getString(R.string.HttpKey) + "\t" + garagePiCommand;
                    final OutputStream out = socket.getOutputStream();
                    out.write(body.getBytes());
                    out.flush();


                    Log.d(LOG_TAG, "Sent GaragePi command " + garagePiCommand + ", waiting for response...");
                    updateGaragePiStatus("sent request: " + garagePiCommand, "");
                    final int readSize = 10240;
                    final byte[] responseBytes = new byte[10240];
                    final int bytesRead = socket.getInputStream().read(responseBytes);

                    if(bytesRead >= readSize) {
                        Log.w(LOG_TAG, "More data to read? Bytes read matched max read size!");
                    }
                    final String response =
                            new String(responseBytes)
                                    .substring(0, bytesRead)
                                    .replace("\t", " - ");


                    Log.d(LOG_TAG, "Got GaragePi response: " + response);
                    if(response.trim().isEmpty()) {
                        updateGaragePiStatus("empty response: " + garagePiCommand, "");
                    } else {
                        updateGaragePiStatus(response, "");
                    }

                    socket.close();
                } catch (SSLHandshakeException e) {
                    Log.w(LOG_TAG, "GaragePi encountered SSLHandshakeException: " + e);
                    updateGaragePiStatus("SSLHandshakeException", e.toString());
                } catch (UnknownHostException e) {
                    Log.w(LOG_TAG, "GaragePi encountered UnknownHostException: " + e);
                    updateGaragePiStatus("UnknownHostException", e.toString());
                } catch (IOException e) {
                    Log.w(LOG_TAG, "GaragePi encountered IOException: " + e);
                    updateGaragePiStatus("IOException", e.toString());
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e(LOG_TAG, "GaragePi encountered RejectedExecutionException: " + e);
            updateGaragePiStatus("RejectedExecutionException", e.toString());
            return false;
        }
        return true;
    }

    public void sendFuelTrackerMessage(String message) {
        try {
            executorService.submit(() -> {
                try {
                    Log.d(LOG_TAG, "Connecting to FuelTrackerServer...");
                    updateFuelTrackerStatus("Connecting to Server...");
                    final Socket clientSocket =
                            new Socket(
                                    context.getString(R.string.HttpHost),
                                    context.getResources().getInteger(R.integer.HttpFuelTrackerPort)
                            );

                    final PrintWriter out = new PrintWriter(clientSocket.getOutputStream(), true);
                    final BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

                    out.println(message);
                    out.flush();


                    Log.d(LOG_TAG, "Sent FuelTracker message: " + message + ", waiting for response...");
                    boolean finishedCleanly = false;
                    String input;
                    while (!finishedCleanly && (input = in.readLine()) != null) {
                        Log.d(LOG_TAG, "FuelTracker received: " + input);
                        if (input.equals("STILLTHERE?")) {
                            out.println("YESSTILLHERE!");
                        } else if (input.startsWith("GOODBYE!")) {
                            out.println("GOODBYE!");
                        } else if (input.toLowerCase().startsWith("received")) {
                            out.println("GOODBYE!");
                            out.flush();
                            finishedCleanly = true;
                        } else if (!input.equals("HELLO!")) {
                            Log.e(LOG_TAG, "FuelTracker received unexpected input: " + input);
                        }
                    }

                    if(finishedCleanly) {
                        Log.d(LOG_TAG, "FuelTracker request completed.");
                        updateFuelTrackerStatus(FuelTrackerStatusValueSuccess);
                    } else {
                        Log.w(LOG_TAG, "FuelTracker connection aborted abruptly.");
                        updateFuelTrackerStatus("Connection Aborted");
                    }

                    in.close();
                    out.close();
                    clientSocket.close();
                } catch (UnknownHostException e) {
                    Log.w(LOG_TAG, "FuelTracker encountered UnknownHostException: " + e);
                    updateFuelTrackerStatus("UnknownHostException");
                } catch (IOException e) {
                    Log.w(LOG_TAG, "FuelTracker encountered IOException: " + e);
                    updateFuelTrackerStatus("IOException");
                }
            });
        } catch (RejectedExecutionException e) {
            Log.e(LOG_TAG, "GaragePi encountered RejectedExecutionException: " + e);
            updateFuelTrackerStatus("RejectedExecutionException");
        }
    }
}
