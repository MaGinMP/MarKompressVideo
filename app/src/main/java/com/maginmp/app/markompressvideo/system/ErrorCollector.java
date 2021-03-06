/*
 * ErrorCollector.java
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

package com.maginmp.app.markompressvideo.system;

import android.app.NotificationManager;
import android.content.Context;
import android.support.v4.app.NotificationCompat;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.maginmp.app.markompressvideo.BuildConfig;
import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.utils.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Created by MarkGintsburg on 29/05/2017.
 */

public class ErrorCollector {
    private static final ErrorCollector INSTANCE = new ErrorCollector();
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final long LOG_FILE_MAX_SIZE = 10000000;
    private String mLogFilePath;
    private int mNotificationId = -1;
    private final List<String> mErrorList = new ArrayList<>();
    private ErrorCollector() {
    }

    public static ErrorCollector getErrorCollectorIstance() {
        return INSTANCE;
    }

    public void setNotificationId(int id) {
        mNotificationId = id;
    }

    public void addError(String error, Context context) {
        addErrorIfNotExist(error, context);
    }

    private void addErrorIfNotExist(String error, Context context) {
        if (!mErrorList.contains(error))
            mErrorList.add(error);
        appendToLogFile(error);
        if (context != null)
            showNotification(context);
    }

    public void removeAllErrors(Context context) {
        mErrorList.clear();
        hideNotification(context);
    }

    public void removeError(String error, Context context) {
        mErrorList.remove(error);
        if (mErrorList.isEmpty())
            hideNotification(context);
    }

    public boolean isErrorInList(String error) {
        return mErrorList.contains(error);
    }

    public void hideNotification(Context context) {
        NotificationManager mNotificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        mNotificationManager.cancel(mNotificationId);
    }

    public void showNotification(Context context) {
        if (mNotificationId > 0 && !mErrorList.isEmpty()) {
            NotificationCompat.Builder mBuilder =
                    new NotificationCompat.Builder(context, MainActivity.NOTIFICATION_CHANNEL_ID)
                            .setSmallIcon(R.mipmap.ic_notification)
                            .setStyle(new NotificationCompat.BigTextStyle()
                                    .bigText(context.getString(R.string.notification_error_send_to_dev) + " " + TextUtils.join(";\t", mErrorList)))
                            .setContentTitle(StringUtils.getApplicationName(context) + " | " + context.getString(R.string.notification_error_title))
                            .setContentText(context.getString(R.string.notification_error_send_to_dev));

            NotificationManager mNotificationManager =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            mNotificationManager.notify(mNotificationId, mBuilder.build());
        }
    }

    public void setLogFile(String path) {
        mLogFilePath = path;
    }

    synchronized private void appendToLogFile(String msg) {
        if (msg != null && mLogFilePath != null) {
            BufferedWriter buf = null;
            try {
                //BufferedWriter for performance, true to set append to file flag
                if ((new File(mLogFilePath)).length() > LOG_FILE_MAX_SIZE)
                    buf = new BufferedWriter(new FileWriter(mLogFilePath, false));
                else
                    buf = new BufferedWriter(new FileWriter(mLogFilePath, true));
                buf.append(new Date().toString() + ": " + msg);
                buf.newLine();
            } catch (IOException e) {
                // Problem writing log file
                Log.e(TAG, "Cannot write log file " + msg);
                e.printStackTrace();
            } finally {
                if (buf != null)
                    try {
                        buf.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }
        }
    }

    public static void debugToast(String msg, Context context)
    {
        if (BuildConfig.DEBUG)
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    public static void debugLog(String tag, String msg)
    {
        if (BuildConfig.DEBUG)
            Log.v(tag, msg);
    }

}
