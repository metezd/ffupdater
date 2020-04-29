package de.marmaro.krt.ffupdater.notification;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import de.marmaro.krt.ffupdater.App;
import de.marmaro.krt.ffupdater.download.AppUpdate;
import de.marmaro.krt.ffupdater.MainActivity;
import de.marmaro.krt.ffupdater.R;
import de.marmaro.krt.ffupdater.settings.SettingsHelper;

import static androidx.work.ExistingPeriodicWorkPolicy.REPLACE;

/**
 * This class will call the {@link WorkManager} to check regularly for app updates in the background.
 * When an app update is available, a notification will be created and displayed.
 */
public class NotificationCreator extends Worker {
    private static final String CHANNEL_ID = "update_notification_channel_id";
    private static final int REQUEST_CODE_START_MAIN_ACTIVITY = 2;
    private static final String WORK_MANAGER_KEY = "update_checker";

    public NotificationCreator(@NonNull Context context, @NonNull WorkerParameters workerParams) {
        super(context, workerParams);
    }

    /**
     * Register NotificationCreator for regularly update checks.
     * If NotificationCreator is already registered, the already registered NotificationCreator will be replaced.
     * If pref_check_interval (from default shared preferences) is less or equal 0, NotificationCreator will be unregistered.
     *
     * @param context necessary context for accessing default shared preferences and using {@link WorkManager}.
     */
    public static void register(Context context) {
        register(context, SettingsHelper.isAutomaticCheck(context), SettingsHelper.getCheckInterval(context));
    }

    /**
     * Register NotificationCreator for regularly update checks.
     * If NotificationCreator is already registered, the already registered NotificationCreator will be replaced.
     * If pref_check_interval (from default shared preferences) is less or equal 0, NotificationCreator will be unregistered.
     *
     * @param context            necessary context using {@link WorkManager}.
     * @param repeatEveryMinutes check for app update every x minutes
     */
    public static void register(Context context, boolean automaticCheckInBackground, int repeatEveryMinutes) {
        if (!automaticCheckInBackground) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_MANAGER_KEY);
            return;
        }

        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build();

        PeriodicWorkRequest saveRequest =
                new PeriodicWorkRequest.Builder(NotificationCreator.class, repeatEveryMinutes, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .build();

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_MANAGER_KEY, REPLACE, saveRequest);
    }

    /**
     * This method will be called by the WorkManager regularly.
     *
     * @return every method call will return a Result successfully.
     */
    @NonNull
    @Override
    public Result doWork() {
        Log.d("NotificationCreator", "doWork() executed");
        AppUpdate appUpdate = AppUpdate.create(getApplicationContext().getPackageManager());
        Set<App> disableApps = SettingsHelper.getDisableApps(getApplicationContext());
        appUpdate.checkUpdatesForInstalledApps(disableApps, null, () -> {
            if (appUpdate.areUpdatesForInstalledAppsAvailable()) {
                createNotification();
            }
        });
        return Result.success();
    }

    @NonNull
    private NotificationManager getNotificationManager() {
        Context context = getApplicationContext();
        return Objects.requireNonNull(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));
    }

    private void createNotification() {
        Context context = getApplicationContext();
        NotificationCompat.Builder builder;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createNotificationChannel();
            builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        } else {
            //noinspection deprecation
            builder = new NotificationCompat.Builder(context);
        }

        Intent intent = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, REQUEST_CODE_START_MAIN_ACTIVITY, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification notification = builder.setSmallIcon(R.mipmap.transparent, 0)
                .setLargeIcon(BitmapFactory.decodeResource(context.getResources(), R.mipmap.ic_launcher))
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text))
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build();

        getNotificationManager().notify(1, notification);
    }

    /**
     * This method will create a notification channel for the "update notification".
     * Reason: Since Android 9 notification can only be created with an existing notification channel.
     */
    @RequiresApi(api = Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                getApplicationContext().getString(R.string.update_notification_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(getApplicationContext().getString(R.string.update_notification_channel_description));
        getNotificationManager().createNotificationChannel(channel);
    }
}