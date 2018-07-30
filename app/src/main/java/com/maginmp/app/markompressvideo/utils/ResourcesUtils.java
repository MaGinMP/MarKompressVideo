/*
 * ResourcesUtils.java
 *
 * MarKompressVideo
 * Copyright (c) 2017. Mark Gintsburg
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.maginmp.app.markompressvideo.utils;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.BatteryManager;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.view.View;
import android.view.ViewParent;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.services.VideosManagementService;

import static java.lang.Math.round;

/**
 * Created by MarkGintsburg on 16/06/2017.
 */

public class ResourcesUtils {

    private static final String TAG = ResourcesUtils.class.getSimpleName();

    /**
     * Find the parent of the view by an id
     *
     * @param v   view
     * @param res id to find
     * @return the requested parent or null
     */
    public static ViewParent findParentById(View v, int res) {
        return findParentById(v.getParent(), res);
    }

    /**
     * A recursive helper function to {@Link findParentById}
     *
     * @param vp  this level parent
     * @param res id to find
     * @return the requested parent or one level up or null
     */
    private static ViewParent findParentById(ViewParent vp, int res) {
        if (vp == null)
            return null;
        if (((View) vp).getId() == res)
            return vp;
        return findParentById(vp.getParent(), res);
    }

    /**
     * Translates DIMEN to pixels
     *
     * @param context
     * @param resource id of the DIMEN
     * @return value in pixel
     */
    public static int dimenResourceToPx(Context context, int resource) {
        return (int) context.getResources().getDimension(resource);
    }

    /**
     * Updates the receivers if the device state is ready to start encoding. call this method each time
     * that there is a change (battery, settings) that may impact on device encode readiness
     *
     * @param mSharedPreferences shared preferences
     * @param intent             for now null (see comments inside)
     * @param context            context
     */
    @SuppressWarnings("ParameterCanBeLocal")
    public static synchronized void deviceEncoderStateUpdater(SharedPreferences mSharedPreferences, @Nullable Intent intent, Context context) {

        boolean isServiceEnabledFromSettings = mSharedPreferences.getBoolean(context.getString(R.string.keysetting_enable_service), true);
        int freeSpace = (int) (MainActivity.MKV_DIRECTORY.getFreeSpace() / (1024 * 1024));
        boolean isCharging = false;
        float batteryLvl = 0;

        // For some reason providing an intent from Receiver does not work
        //if (intent == null) // In case method called not from StateReceiver
        //{
        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        intent = context.registerReceiver(null, ifilter);
        //}

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);

        batteryLvl = level / (float) scale;

        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL;

        SharedPreferences.Editor editor = mSharedPreferences.edit();
        if (isServiceEnabledFromSettings && isCharging && batteryLvl > MainActivity.MINIMAL_BATTERY_LEVEL) {
            if (freeSpace < MainActivity.MINIMAL_SUGGESTED_STORAGE_MB) {
                // TODO warning notification only?
            }
            editor.putBoolean(context.getString(R.string.keysetting_is_device_state_encoding_ready), true);

        } else {
            editor.putBoolean(context.getString(R.string.keysetting_is_device_state_encoding_ready), false);
        }

        editor.commit();
    }

    /**
     * After finishing encoding, broadcast the results to update badge videos fragment
     *
     * @param inQueueCount     updated videos in queue or -1 if unchanged
     * @param updatedCursorPos the cursor position to update the list or -1 if update all (for now provide -1 always)
     * @param broadcaster
     */
    public static synchronized void broadcastResult(int inQueueCount, int updatedCursorPos, LocalBroadcastManager broadcaster) {
        Intent intent = new Intent(VideosManagementService.BROADCAST_RESULT);
        intent.putExtra(VideosManagementService.BROADCAST_MESSAGE_QUEUE_COUNT, inQueueCount);
        intent.putExtra(VideosManagementService.BROADCAST_MESSAGE_CURSOR_POS, updatedCursorPos);
        broadcaster.sendBroadcast(intent);
    }


    public static long hoursToMilis(float hours) {
        return round(hours * 60 * 60 * 1000);
    }


    /* Not needed: For now checking using a static variable is enough
    public static boolean isVideosManagementServiceRunning(Activity activity)
    {
        return VideosManagementService.IS_SERVICE_RUNNING && PermissionsUtils.IsServiceRunning(activity, VideosManagementService.class);
    }

    private boolean IsServiceRunning(Class<?> serviceClass, Activity activity) {
        ActivityManager manager = (ActivityManager) activity.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }
    */
}
