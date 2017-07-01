/*
 * StringUtils.java
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
import android.content.pm.ApplicationInfo;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Created by MarkGintsburg on 16/06/2017.
 */

public class StringUtils {

    private static final String TAG = StringUtils.class.getSimpleName();

    /**
     * Translates "WidthxHeight" to [w,h] array
     *
     * @param dimenStr string of "WidthxHeight" form (like "1920x1080")
     * @return array of [w , h]
     */
    public static int[] dimenToWidthAndHeight(String dimenStr) {
        int[] widthHeightRes = new int[2];
        String[] splittedStr = dimenStr.toLowerCase().split("x");
        try {
            widthHeightRes[0] = Integer.parseInt(splittedStr[0]);
            widthHeightRes[1] = Integer.parseInt(splittedStr[1]);
        } catch (NumberFormatException e) {
            widthHeightRes[0] = 0;
            widthHeightRes[1] = 0;
        }
        return widthHeightRes;
    }

    /**
     * Create "WidthxHeight" from [w,h] array
     *
     * @param w width
     * @param h height
     * @return string of "WidthxHeight" form (like "1920x1080")
     */
    public static String widthAndHeightToDimen(int w, int h) {
        return w + "x" + h;
    }

    /**
     * Translate milis to string of "mm:ss"
     *
     * @param millis time in millis
     * @return string of "mm:ss"
     */
    public static String millisToMinAndSec(long millis) {
        return String.format(Locale.US, "%02d:%02d",
                TimeUnit.MILLISECONDS.toMinutes(millis),
                TimeUnit.MILLISECONDS.toSeconds(millis) -
                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(millis))
        );
    }

    public static String getApplicationName(Context context) {
        ApplicationInfo applicationInfo = context.getApplicationInfo();
        int stringId = applicationInfo.labelRes;
        return stringId == 0 ? applicationInfo.nonLocalizedLabel.toString() : context.getString(stringId);
    }

    /**
     * Bit level status extract from video status int. (hazard: if status int contains more than 1 status, only one of them will be returned)
     *
     * @param context
     * @param status
     * @return a friendly string of status meaning
     */
    public static String getStatusFromInt(Context context, int status) {
        String statusStr = "";
        if ((status & VideosDatabaseHelper.STATUS_QUEUE) != 0)
            statusStr = context.getString(R.string.videos_card_status_queue);
        else if (((status & VideosDatabaseHelper.STATUS_DONE) != 0))
            statusStr = context.getString(R.string.videos_card_status_done);
        else if (((status & VideosDatabaseHelper.STATUS_BYPASS) != 0))
            statusStr = context.getString(R.string.videos_card_status_bypass);
        else if (((status & VideosDatabaseHelper.STATUS_RUNNING) != 0))
            statusStr = context.getString(R.string.videos_card_status_running);
        else if (((status & VideosDatabaseHelper.STATUS_REVERTED) != 0))
            statusStr = context.getString(R.string.videos_card_status_reverted);
        else if (((status & VideosDatabaseHelper.STATUS_DELETED) != 0))
            statusStr = context.getString(R.string.videos_card_status_deleted);

        return statusStr;
    }


}
