package com.arjanvlek.oxygenupdater.installation.automatic;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.arjanvlek.oxygenupdater.ApplicationData;
import com.arjanvlek.oxygenupdater.R;
import com.arjanvlek.oxygenupdater.domain.SystemVersionProperties;
import com.arjanvlek.oxygenupdater.internal.Utils;
import com.arjanvlek.oxygenupdater.internal.logger.Logger;
import com.arjanvlek.oxygenupdater.settings.SettingsManager;
import com.arjanvlek.oxygenupdater.views.MainActivity;

import static com.arjanvlek.oxygenupdater.ApplicationData.NO_OXYGEN_OS;

public class VerifyInstallationReceiver extends BroadcastReceiver {

    private static final int NOTIFICATION_ID = 79243095;
    private static final String TAG = "VerifyInstallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            SettingsManager settingsManager = new SettingsManager(context);
            if (settingsManager.getPreference(SettingsManager.PROPERTY_VERIFY_SYSTEM_VERSION_ON_REBOOT, false) && intent.getAction() != null && intent.getAction().equals("android.intent.action.BOOT_COMPLETED")) {
                settingsManager.savePreference(SettingsManager.PROPERTY_VERIFY_SYSTEM_VERSION_ON_REBOOT, false);

                SystemVersionProperties properties = new SystemVersionProperties(false);

                // Don't check on unsupported devices.
                if (properties.getOxygenOSVersion().equals(NO_OXYGEN_OS) || properties.getOxygenOSOTAVersion().equals(NO_OXYGEN_OS)) {
                    return;
                }

                String oldOxygenOSVersion = settingsManager.getPreference(SettingsManager.PROPERTY_OLD_SYSTEM_VERSION, "");
                String targetOxygenOSVersion = settingsManager.getPreference(SettingsManager.PROPERTY_TARGET_SYSTEM_VERSION, "");
                String currentOxygenOSVersion = properties.getOxygenOSOTAVersion();

                if (oldOxygenOSVersion.isEmpty() || targetOxygenOSVersion.isEmpty() || currentOxygenOSVersion.isEmpty()) {
                    displayFailureNotification(context, context.getString(R.string.install_verify_error_unable_to_verify));
                    logFailure(context, oldOxygenOSVersion, targetOxygenOSVersion, currentOxygenOSVersion, "ERR_CHECK_FAILED");
                } else if (currentOxygenOSVersion.equals(oldOxygenOSVersion)) {
                    displayFailureNotification(context, context.getString(R.string.install_verify_error_nothing_installed));
                    logFailure(context, oldOxygenOSVersion, targetOxygenOSVersion, currentOxygenOSVersion, "ERR_INSTALL_FAILED");
                } else if (!currentOxygenOSVersion.equals(targetOxygenOSVersion)) {
                    displayFailureNotification(context, context.getString(R.string.install_verify_error_wrong_version_installed));
                    logFailure(context, oldOxygenOSVersion, targetOxygenOSVersion, currentOxygenOSVersion, "ERR_WRONG_OS_INSTALLED");
                } else {
                    displaySuccessNotification(context, properties.getOxygenOSVersion());
                    logSuccess(context, oldOxygenOSVersion, targetOxygenOSVersion, currentOxygenOSVersion);
                }
            }

        } catch (Throwable e) {
            Logger.logError(false, TAG, "Failed to check if update was successfully installed: ", e);
        }
    }

    private void displaySuccessNotification(Context context, String oxygenOSVersion) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ApplicationData.PUSH_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_done)
                .setOngoing(false)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setContentTitle(context.getString(R.string.install_verify_success_title))
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(context.getString(R.string.install_verify_success_message, oxygenOSVersion))
                )
                .setContentText(context.getString(R.string.install_verify_success_message, oxygenOSVersion))
                .setCategory(Notification.CATEGORY_STATUS);

        NotificationManager manager = (NotificationManager) Utils.getSystemService(context, Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void displayFailureNotification(Context context, String errorMessage) {
        PendingIntent contentIntent = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), 0);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, ApplicationData.PUSH_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_failed)
                .setOngoing(false)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_HIGH)
                .setStyle(new NotificationCompat.BigTextStyle()
                        .bigText(errorMessage)
                )
                .setContentTitle(context.getString(R.string.install_verify_error_title))
                .setContentText(errorMessage)
                .setCategory(Notification.CATEGORY_STATUS);

        NotificationManager manager = (NotificationManager) Utils.getSystemService(context, Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, builder.build());
    }

    private void logSuccess(Context context, String startOs, String destinationOs, String currentOs) {
        Intent logIntent = buildLogIntent(context, startOs, destinationOs, currentOs);
        logIntent.putExtra(RootInstallLogger.INTENT_STATUS, InstallationStatus.FINISHED);

        context.startService(logIntent);
    }

    private void logFailure(Context context, String startOs, String destinationOs, String currentOs, String reason) {
        Intent logIntent = buildLogIntent(context, startOs, destinationOs, currentOs);
        logIntent.putExtra(RootInstallLogger.INTENT_STATUS, InstallationStatus.FAILED);
        logIntent.putExtra(RootInstallLogger.INTENT_FAILURE_REASON, reason);

        context.startService(logIntent);
    }

    private Intent buildLogIntent(Context context, String startOs, String destinationOs, String currentOs) {
        SettingsManager settingsManager = new SettingsManager(context);

        Intent logIntent = new Intent(context, RootInstallLogger.class);

        logIntent.putExtra(RootInstallLogger.INTENT_INSTALL_ID, settingsManager.getPreference(SettingsManager.PROPERTY_INSTALLATION_ID, "<INVALID>"));
        logIntent.putExtra(RootInstallLogger.INTENT_START_OS, startOs);
        logIntent.putExtra(RootInstallLogger.INTENT_DESTINATION_OS, destinationOs);
        logIntent.putExtra(RootInstallLogger.INTENT_CURR_OS, currentOs);

        return logIntent;
    }
}
