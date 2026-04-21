package com.gpslogger;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

public class GpsService extends Service {

    private static final String TAG = "GpsLogger";
    private static final String CHANNEL_ID = "gps_logger_channel";
    private static final int NOTIFICATION_ID = 1;
    private static final long INTERVAL_MS = 10_000L; // 10 secondes

    private FusedLocationProviderClient fusedClient;
    private LocationCallback locationCallback;
    private PowerManager.WakeLock wakeLock;
    private JSONArray logArray;
    private File outputFile;
    private String outputFileName;
    private SimpleDateFormat isoFormat;

    @Override
    public void onCreate() {
        super.onCreate();

        // Format ISO 8601 pour les timestamps
        isoFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        isoFormat.setTimeZone(TimeZone.getTimeZone("UTC"));

        // Initialise le fichier JSON dans /sdcard/GpsLogger/
        initOutputFile();

        // WakeLock pour que le CPU ne dorme pas
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GpsLogger::WakeLock");
        wakeLock.acquire();

        // Démarre en foreground avec notification discrète
        createNotificationChannel();
        Notification notif = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GPS Logger")
                .setContentText("Enregistrement en cours...")
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true)
                .setSilent(true)
                .build();
        startForeground(NOTIFICATION_ID, notif);

        // Configure le client GPS
        fusedClient = LocationServices.getFusedLocationProviderClient(this);
        startLocationUpdates();
    }

    private void initOutputFile() {
        // Dossier Documents/GpsLogger/ accessible via Gestionnaire de fichiers
        File dir = new File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            "GpsLogger"
        );
        if (!dir.exists()) dir.mkdirs();

        // Nom du fichier avec date de démarrage
        String startDate = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(new Date());
        outputFileName = "gps_log_" + startDate + ".json";
        outputFile = new File(dir, outputFileName);

        logArray = new JSONArray();
        Log.i(TAG, "Fichier de log : " + outputFile.getAbsolutePath());
    }

    private void startLocationUpdates() {
        LocationRequest request = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, INTERVAL_MS)
                .setMinUpdateIntervalMillis(INTERVAL_MS)
                .setWaitForAccurateLocation(false)
                .build();

        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult result) {
                if (result == null) return;
                Location loc = result.getLastLocation();
                if (loc != null) {
                    appendLocation(loc);
                }
            }
        };

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, Looper.getMainLooper());
        } catch (SecurityException e) {
            Log.e(TAG, "Permission GPS manquante", e);
        }
    }

    private void appendLocation(Location loc) {
        try {
            JSONObject entry = new JSONObject();
            entry.put("timestamp", isoFormat.format(new Date(loc.getTime())));

            // Latitude : ND si non disponible ou invalide
            if (loc.getLatitude() == 0.0 && loc.getLongitude() == 0.0) {
                entry.put("latitude", "ND");
                entry.put("longitude", "ND");
            } else {
                entry.put("latitude", loc.getLatitude());
                entry.put("longitude", loc.getLongitude());
            }

            entry.put("altitude_m", loc.hasAltitude() ? loc.getAltitude() : "ND");
            entry.put("accuracy_m", loc.hasAccuracy() ? loc.getAccuracy() : "ND");
            entry.put("speed_ms", loc.hasSpeed() ? loc.getSpeed() : "ND");
            entry.put("bearing_deg", loc.hasBearing() ? loc.getBearing() : "ND");
            entry.put("provider", loc.getProvider() != null ? loc.getProvider() : "ND");

            logArray.put(entry);
            writeToFile();

            Log.d(TAG, "Position enregistrée : " + loc.getLatitude() + ", " + loc.getLongitude());
        } catch (JSONException e) {
            Log.e(TAG, "Erreur JSON", e);
        }
    }

    private void writeToFile() {
        try (FileWriter writer = new FileWriter(outputFile, false)) {
            writer.write(logArray.toString(2)); // indentation de 2 espaces
        } catch (IOException | JSONException e) {
            Log.e(TAG, "Erreur écriture fichier", e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // START_STICKY : le service redémarre automatiquement si tué
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (fusedClient != null && locationCallback != null) {
            fusedClient.removeLocationUpdates(locationCallback);
        }
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        // Sauvegarde finale
        writeToFile();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null; // Service non lié
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "GPS Logger",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Service d'enregistrement GPS");
            channel.setSound(null, null);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
