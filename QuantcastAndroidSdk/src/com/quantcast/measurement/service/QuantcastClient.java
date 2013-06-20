/**
 * Copyright 2012 Quantcast Corp.
 *
 * This software is licensed under the Quantcast Mobile App Measurement Terms of Service
 * https://www.quantcast.com/learning-center/quantcast-terms/mobile-app-measurement-tos
 * (the “License”). You may not use this file unless (1) you sign up for an account at
 * https://www.quantcast.com and click your agreement to the License and (2) are in
 *  compliance with the License. See the License for the specific language governing
 * permissions and limitations under the License.
 *
 */       
package com.quantcast.measurement.service;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import com.quantcast.settings.GlobalControl;
import com.quantcast.settings.GlobalControlListener;

import java.util.HashSet;
import java.util.Set;

/**
 * Client API for Quantcast Measurement service.
 *
 * This exposes only those methods that may be called by developers using the Quantcast Measurement API.
 */
public class QuantcastClient {

    private static final QuantcastLog.Tag TAG = new QuantcastLog.Tag(QuantcastClient.class);

    /**
     * The maximum number of events that can be uploaded in a single upload.
     */
    public static final int MAX_UPLOAD_SIZE = 200;
    /**
     * The default number of events required to trigger an upload.
     */
    public static final int DEFAULT_UPLOAD_EVENT_COUNT = 100;


    private static MeasurementSession session;
    private static final Object SESSION_LOCK = new Object();

    private static int uploadEventCount = DEFAULT_UPLOAD_EVENT_COUNT;

    private static Set<Integer> activeContexts;

    private static volatile boolean usingSecureConnections = false;

    /**
     * Start a new measurement session. Should be called in the main activity's onCreate method.
     *
     * @param activity               Main Activity using the Quantcast Measurement API
     * @param apiKey                The Quantcast API key that activity for this app should be reported under. Obtain this key from the Quantcast website.
     */
    public static void beginSessionWithApiKey(Activity activity, String apiKey) {
        beginSessionWithApiKeyAndWithUserId(activity, apiKey, null);
    }

    /**
     * Start a new measurement session. Should be called in the main activity's onCreate method.
     *
     * @param activity               Main Activity using the Quantcast Measurement API
     * @param apiKey                The Quantcast API key that activity for this app should be reported under. Obtain this key from the Quantcast website.
     * @param label                 A label for the event.
     */
    public static void beginSessionWithApiKey(Activity activity, String apiKey, String label) {
        beginSessionWithApiKeyAndWithUserId(activity, apiKey, null, label);
    }

    /**
     * Start a new measurement session. Should be called in the main activity's onCreate method.
     *
     * @param activity               Main Activity using the Quantcast Measurement API
     * @param apiKey                The Quantcast API key that activity for this app should be reported under. Obtain this key from the Quantcast website.
     * @param labels                An array of labels for the event.
     */
    public static void beginSessionWithApiKey(Activity activity, String apiKey, String[] labels) {
        beginSessionWithApiKeyAndWithUserId(activity, apiKey, null, labels);
    }

    /**
     * Start a new measurement session. Should be called in the main activity's onCreate method.
     *
     * @param activity               Main Activity using the Quantcast Measurement API
     * @param apiKey                The Quantcast API key that activity for this app should be reported under. Obtain this key from the Quantcast website.
     * @param userId                A consistent identifier for the current user.
     *                              Any user identifier recorded will be save for all future session until it a new user identifier is recorded.
     *                              Record a user identifier of {@link null} should be used for a log out and will remove any saved user identifier.
     */
    public static void beginSessionWithApiKeyAndWithUserId(Activity activity, String apiKey, String userId) {
        beginSessionWithApiKeyAndWithUserId(activity, apiKey, userId, new String[0]);
    }

    /**
     * Start a new measurement session. Should be called in the main activity's onCreate method.
     *
     * @param activity               Main Activity using the Quantcast Measurement API
     * @param apiKey                The Quantcast API key that activity for this app should be reported under. Obtain this key from the Quantcast website.
     * @param userId                A consistent identifier for the current user.
     *                              Any user identifier recorded will be save for all future session until it a new user identifier is recorded.
     *                              Record a user identifier of {@link null} should be used for a log out and will remove any saved user identifier.
     * @param label                 A label for the event.
     */
    public static void beginSessionWithApiKeyAndWithUserId(Activity activity, String apiKey, String userId,  String label) {
        beginSessionWithApiKeyAndWithUserId(activity, apiKey, userId, new String[] { label });
    }

    /**
     * Start a new measurement session. Should be called in the main activity's onCreate method.
     *
     * @param activity               Main Activity using the Quantcast Measurement API
     * @param apiKey                The Quantcast API key that activity for this app should be reported under. Obtain this key from the Quantcast website.
     * @param userId                A consistent identifier for the current user.
     *                              Any user identifier recorded will be save for all future session until it a new user identifier is recorded.
     *                              Record a user identifier of {@link null} should be used for a log out and will remove any saved user identifier.
     * @param labels                An array of labels for the event.
     */
    public static void beginSessionWithApiKeyAndWithUserId(Activity activity, String apiKey, String userId, String[] labels) {
        synchronized (SESSION_LOCK) {
            if (activeContexts == null) {
                activeContexts = new HashSet<Integer>();
            }
            activeContexts.add(activity.hashCode());

            QuantcastLog.i(TAG, activeContexts.size() + " active contexts.");
            if (session == null) {
                QuantcastLog.i(TAG, "Initializing new session.");
                QuantcastGlobalControlProvider.getProvider(activity).refresh();
                session = new MeasurementSession(apiKey, userId, activity, labels, uploadEventCount, MAX_UPLOAD_SIZE);
                QuantcastLog.i(TAG, "New session initialization complete.");
            }
        }
    }

    static void addActivity(Activity activity) {
        synchronized (SESSION_LOCK) {
            activeContexts.add(activity.hashCode());
        }
    }

    /**
     * Log a user identifier to the service. This will begin a new measurement session, but must be called during another measurement session in order to pull the necessary parameters.
     *
     * @param userId                A consistent identifier for the current user.
     *                              Any user identifier recorded will be save for all future session until it a new user identifier is recorded.
     *                              Record a user identifier of {@link null} should be used for a log out and will remove any saved user identifier.
     */
    public static void recordUserIdentifier(String userId) {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                session = new MeasurementSession(session, userId);
            }
        }
    }

    /**
     * Logs an app-defined event can be arbitrarily defined.
     *
     * @param name                  A string that identifies the event being logged. Hierarchical information can be indicated by using a left-to-right notation with a period as a separator.
     *                              For example, logging one event named "button.left" and another named "button.right" will create three reportable items in Quantcast App Measurement:
     *                              "button.left", "button.right", and "button".
     *                              There is no limit on the cardinality that this hierarchical scheme can create,
     *                              though low-frequency events may not have an audience report on due to the lack of a statistically significant population.
     */
    public static void logEvent(String name) {
        logEvent(name, new String[0]);
    }

    /**
     * Logs an app-defined event can be arbitrarily defined.
     *
     * @param name                  A string that identifies the event being logged. Hierarchical information can be indicated by using a left-to-right notation with a period as a separator.
     *                              For example, logging one event named "button.left" and another named "button.right" will create three reportable items in Quantcast App Measurement:
     *                              "button.left", "button.right", and "button".
     *                              There is no limit on the cardinality that this hierarchical scheme can create,
     *                              though low-frequency events may not have an audience report on due to the lack of a statistically significant population.
     * @param label                 A label for the event.
     */
    public static void logEvent(String name, String label) {
        logEvent(name, new String[] { label });
    }

    /**
     * Logs an app-defined event can be arbitrarily defined.
     *
     * @param name                  A string that identifies the event being logged. Hierarchical information can be indicated by using a left-to-right notation with a period as a separator.
     *                              For example, logging one event named "button.left" and another named "button.right" will create three reportable items in Quantcast App Measurement:
     *                              "button.left", "button.right", and "button".
     *                              There is no limit on the cardinality that this hierarchical scheme can create,
     *                              though low-frequency events may not have an audience report on due to the lack of a statistically significant population.
     * @param labels                An array of labels for the event.
     */
    public static void logEvent(String name, String[] labels) {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                session.logEvent(name, labels);
            }
        }
    }

    /**
     * Logs a pause event as well as evoking some internal maintenance. This should be called in the main activity's onPause method
     * 
     */
    public static void pauseSession() {
        pauseSession(new String[0]);
    }

    /**
     * Logs a pause event as well as evoking some internal maintenance. This should be called in the main activity's onPause method
     * 
     * @param label                 A label for the event.
     */
    public static void pauseSession(String label) {
        pauseSession(new String[] { label });
    }

    /**
     * Logs a pause event as well as evoking some internal maintenance. This should be called in the main activity's onPause method
     * 
     * @param labels                An array of labels for the event.
     */
    public static void pauseSession(String[] labels) {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                session.pause(labels);
            }
        }
    }

    /**
     * Logs a resume event as well as evoking some internal maintenance. This should be called in the main activity's onResume method
     * 
     */
    public static void resumeSession() {
        resumeSession(new String[0]);
    }

    /**
     * Logs a resume event as well as evoking some internal maintenance. This should be called in the main activity's onResume method
     * 
     * @param label                 A label for the event.
     */
    public static void resumeSession(String label) {
        resumeSession(new String[] { label });
    }

    /**
     * Logs a resume event as well as evoking some internal maintenance. This should be called in the main activity's onResume method
     * 
     * @param labels                An array of labels for the event.
     */
    public static void resumeSession(String[] labels) {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                session.resume(labels);
            }
        }
    }

    /**
     * Ends the current measurement session. This will clean up all of the services resources. This should be called in the main activity's onDestroy method.
     * 
     */
    public static void endSession(Activity activity) {
        endSession(activity, new String[0]);
    }

    /**
     * Ends the current measurement session. This will clean up all of the services resources. This should be called in the main activity's onDestroy method.
     * 
     * @param label                 A label for the event.
     */
    public static void endSession(Activity activity, String label) {
        endSession(activity, new String[] { label });
    }

    /**
     * Ends the current measurement session. This will clean up all of the services resources. This should be called in the main activity's onDestroy method.
     * 
     * @param labels                An array of labels for the event.
     */
    public static void endSession(Activity activity, String[] labels) {
        synchronized (SESSION_LOCK) {
            if (activeContexts != null) {
                activeContexts.remove(activity.hashCode());
                QuantcastLog.i(TAG, activeContexts.size() + " active contexts.");
            } else {
                QuantcastLog.i(TAG, "No active contexts.");
            }
            if (activeContexts == null || activeContexts.isEmpty()) {
                if (session != null) {
                    session.end(labels);
                }
                session = null;
            }
        }
    }

    /**
     * Set the number of events required to trigger an upload.
     * This is defaulted to {@link #DEFAULT_UPLOAD_EVENT_COUNT}
     * 
     * @param uploadEventCount The number of events required to trigger an upload.
     * This must be greater than 0 and less than or equal to {@link #MAX_UPLOAD_SIZE}.
     * Invalid values will be ignored.
     */
    public static void setUploadEventCount(int uploadEventCount) {
        if (uploadEventCount > 0 && uploadEventCount <= MAX_UPLOAD_SIZE) {
            synchronized (SESSION_LOCK) {
                QuantcastClient.uploadEventCount = uploadEventCount;
                if (session != null) {
                    session.setUploadEventCount(uploadEventCount);
                }
            }
        } else {
            QuantcastLog.e(TAG, String.format("Illegal Argument: uploadEventCount should be greater than %d and less than or equal to %d.", 0, MAX_UPLOAD_SIZE));
        }
    }

    /**
     * Use this to control whether or not the service should collect location data. You should only enabled location gathering if your app has some location-aware purpose.
     * 
     * @param enableLocationGathering       Set to true to enable location, false to disable
     */
    public static void setEnableLocationGathering(boolean enableLocationGathering) {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                if (enableLocationGathering) {
                    session.startLocationGathering();
                }else{
                    session.stopLocationGathering();
                }
            }else{
                QuantcastLog.e(TAG, "beginSessionWithApiKey must be called before location logging can be enabled. ");
            }
        }
    }

    /**
     * Control whether or not the SDK will secure data uploads using SSl/TLS.
     * 
     * @param usingSecureConnections    Whether or not the SDK will secure data uploads using SSl/TLS.
     */
    public static void setUsingSecureConnections(boolean usingSecureConnections) {
        QuantcastClient.usingSecureConnections = usingSecureConnections;
    }
    
    public static boolean isUsingSecureConnections() {
        return usingSecureConnections;
    }

    /**
     * Show the About Quantcast Screen via {@link Activity#startActivity(Intent)}.
     * 
     * @param activity              The activity to create the About Quantcast Screen Activity. This activity will be returned to when the user is finished.
     */
    public static void showAboutQuantcastScreen(Activity activity) {
        activity.startActivity(new Intent(activity, AboutQuantcastScreen.class));
    }

    /**
     * Can be called to check the opt-out status of the Quantcast Service.
     * If collection is not enabled the user has opted-out.
     * The opt-out status is not guaranteed to be available at the time of the call.
     * Therefore it must be communicated via callback.
     * 
     * @param context               Main Activity using the Quantcast Measurement API
     * @param callback              The action to be taken when the opt-out status is available
     */
    public static void isCollectionEnabled(Context context, final CollectionEnabledCallback callback) {
        QuantcastGlobalControlProvider.getProvider(context).getControl(new GlobalControlListener() {

            @Override
            public void callback(GlobalControl control) {
                callback.callback(control.blockingEventCollection);
            }

        });
    }

    /**
     * Helper callback class to allow for asynchronous reaction to requests for opt-out status
     */
    public static interface CollectionEnabledCallback {

        /**
         * Called when opt-out status is available
         * 
         * @param collectionEnabled             The current opt-out status. If collection is not enabled the user has opted-out.
         */
        public void callback(boolean collectionEnabled);

    }

    /**
     * Allows you to change what logs will actually be reported by Quantcast Measurement Service classes
     * 
     * @param logLevel          The log level for Quantcast Measurement Service classes. This should be one of Log.VERBOSE, Log.DEBUG, Log.INFO, Log.WARN, Log.ERROR
     */
    public static void setLogLevel(int logLevel) {
        QuantcastLog.setLogLevel(logLevel);
    }

    /**
     * Legacy. No longer logs anything
     */
    @Deprecated
    public static void logRefresh() {
        // Do nothing
    }

    /**
     * Legacy. No longer logs anything
     */
    @Deprecated
    public static void logUpdate() {
        // Do nothing
    }

    static final void logLatency(UploadLatency latency) {
        synchronized (SESSION_LOCK) {
            if (session != null) {
                session.logLatency(latency);
            }
        }
    }

}
