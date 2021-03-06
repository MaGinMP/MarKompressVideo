/*
 * FfmpegUtils.java
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

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.DialogInterface;
import android.support.v4.app.NotificationCompat;
import android.util.Log;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;

/**
 * Created by MarkGintsburg on 16/06/2017.
 */

public class FfmpegUtils {

    private static final String TAG = FfmpegUtils.class.getSimpleName();

    public static void showFfmpegUnsupportedDialog(final Activity act) {
        new android.app.AlertDialog.Builder(act)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(act.getString(R.string.dialog_device_not_supported_title))
                .setMessage(act.getString(R.string.dialog_device_not_supported_message))
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        act.finish();
                    }
                })
                .create()
                .show();
    }

    public static void showFfmpegUnsupportedNotification(Context context) {

        int id = MainActivity.NOTIFICATION_ID_ERROR_FFUNSUPPORTED;
        NotificationCompat.Builder mBuilder =
                new NotificationCompat.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                        .setSmallIcon(R.mipmap.ic_notification)
                        .setContentTitle(context.getString(R.string.dialog_device_not_supported_title))
                        .setContentText(context.getString(R.string.dialog_device_not_supported_message));

        try {
            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(id, mBuilder.build());
        }
        catch (Exception e)
        {
            Log.e(TAG, "FFMPEG unsupported!!");
            e.printStackTrace();
        }

    }
}
