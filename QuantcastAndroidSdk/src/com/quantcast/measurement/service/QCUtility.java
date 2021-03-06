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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.UUID;

class QCUtility {


    private static final QCLog.Tag TAG = new QCLog.Tag(QCUtility.class);

    public static final String API_VERSION = "1_1_0";

    private static final long[] HASH_CONSTANTS = {0x811c9dc5, 0xc9dc5118};

    private static final String SHARED_PREFERENCES_NAME = "com.quantcast.measurement.service";
    private static final String INSTALL_ID_PREF_NAME = "applicationId";


    private static final Object APPLICATION_ID_LOCK = new Object();

    private static final String HTTP_SCHEME = "http://";
    private static final String HTTPS_SCHEME = "https://";

    static String applyHash(String string) {
        double[] hashedStrings = new double[HASH_CONSTANTS.length];
        for (int i = 0; i < hashedStrings.length; i++) {
            hashedStrings[i] = applyUserHash(HASH_CONSTANTS[i], string);
        }

        double product = 1;
        for (double hashedString : hashedStrings) {
            product *= hashedString;
        }

        return Long.toHexString(Math.round(Math.abs(product) / 65536d));
    }

    private static long applyUserHash(long hashConstant, String string) {
        for (int i = 0; i < string.length(); i++) {
            int h32 = (int) hashConstant; // javascript only does bit shifting on 32 bits
            h32 ^= string.charAt(i);
            hashConstant = h32;
            hashConstant += (long) (h32 << 1) + (h32 << 4) + (h32 << 7) + (h32 << 8) + (h32 << 24);
        }
        return hashConstant;
    }

    protected static String generateUniqueId() {
        return UUID.randomUUID().toString();
    }

    protected static String getAppName(Context context) {
        String appName = "app";
        ApplicationInfo info = context.getApplicationInfo();
        if (info != null) {
            int nameID = info.labelRes;
            try {
                appName = context.getString(nameID);
            } catch (Resources.NotFoundException e) {
                QCLog.i(TAG, "AppName: Resource not found for " + nameID);
            }
        }
        return appName;
    }

    protected static String getAPIKey(Context context){
        String apiKey = null;
        PackageManager pm = context.getPackageManager();
        if(pm != null){
            try {
                ApplicationInfo ai = pm.getApplicationInfo(context.getPackageName(), PackageManager.GET_META_DATA);
                if(ai != null && ai.metaData != null){
                    apiKey = ai.metaData.getString("com.quantcast.apiKey");
                }
            } catch (PackageManager.NameNotFoundException ignored) {
            }
        }
        return apiKey;
    }

    protected static String getAppInstallId(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        String installId = sharedPreferences.getString(INSTALL_ID_PREF_NAME, null);

        if (installId == null) {
            installId = generateAndSaveAppInstallId(sharedPreferences);
        }

        return installId;
    }

    private static String generateAndSaveAppInstallId(SharedPreferences sharedPreferences) {
        synchronized (APPLICATION_ID_LOCK) {
            String installId = sharedPreferences.getString(INSTALL_ID_PREF_NAME, null);

            if (installId == null) {
                installId = generateUniqueId();
                QCLog.i(TAG, "Saving install id:" + installId + ".");
                Editor editor = sharedPreferences.edit();
                editor.putString(INSTALL_ID_PREF_NAME, installId);
                editor.commit();
            }

            return installId;
        }
    }

    protected static void dumpAppInstallID(Context context) {
        SharedPreferences sharedPreferences = context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
        Editor editor = sharedPreferences.edit();
        editor.remove(INSTALL_ID_PREF_NAME);
        editor.commit();
    }

    protected static String addScheme(String schemelessUrl) {
        return (QuantcastClient.isUsingSecureConnections() ? HTTPS_SCHEME : HTTP_SCHEME) + schemelessUrl;
    }

    protected static String encodeStringArray(String[] values){
        if (values == null || values.length == 0) return null;

        String valueString = null;
        for(String value : values){
            if(value != null){
                try {
                    String encodedValue = URLEncoder.encode(value, "UTF-8");
                    //encodes space with "+" so change it to %20
                    encodedValue = encodedValue.replaceAll("\\+", "%20");
                    if(valueString == null){
                        valueString = encodedValue;
                    }else{
                        valueString += "," + encodedValue;
                    }
                } catch (UnsupportedEncodingException ignored) { }
            }
        }
        return  valueString;
    }

}
