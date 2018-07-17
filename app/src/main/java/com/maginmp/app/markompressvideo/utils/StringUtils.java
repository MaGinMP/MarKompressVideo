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

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

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

    /**
     * Translates ContentValues to String ready to be embed in metadata
     *
     * @param contentValues The content values to embed
     * @param keysToConvert The keys to embed
     * @return String ready to embed
     */
    @NonNull
    public static String contentValuesToString(ContentValues contentValues, String[] keysToConvert) {
        StringBuilder sb = new StringBuilder();
        sb.append(MainActivity.MKV_METADATA_STAMP[MainActivity.MKV_METADATA_STAMP_IDX]);
        for (int i = 0; i < keysToConvert.length; i++) {
            sb.append(MainActivity.MKV_METADATA_DELIMITER);
            sb.append(keysToConvert[i]);
            sb.append("=");
            sb.append(contentValues.getAsString(keysToConvert[i]));
        }
        sb.append(MainActivity.MKV_METADATA_DELIMITER);
        return sb.toString();
    }

    /**
     * Convert embed MKV metadata string to ContentValues
     *
     * @param str           The relevant metadata part (for example after "comment="). Must start with MainActivity.MKV_METADATA_STAMP
     * @param KeysToExtract Which keys to extract
     * @return ContentValues
     */
    @Nullable
    public static ContentValues StringToContentValues(String str, String[] KeysToExtract) {
        String[] strSplit = str.split(Pattern.quote(MainActivity.MKV_METADATA_DELIMITER_READ));
        if (strSplit == null || strSplit.length == 0)
            return null;

        if (!Arrays.asList(MainActivity.MKV_METADATA_STAMP).contains(strSplit[0]))
            return null;

        ContentValues contentValues = new ContentValues();

        for (int i = 0; i < strSplit.length; i++) {
            String[] strSplitSplit = strSplit[i].split(Pattern.quote("\\="), 2);
            if (strSplitSplit != null && strSplitSplit.length == 2 && Arrays.asList(KeysToExtract).contains(strSplitSplit[0]))
                contentValues.put(strSplitSplit[0], strSplitSplit[1]);
        }

        return contentValues;
    }


    /**
     * Removes an element from an array. Note: not for big arrays (might be inefficient)
     *
     * @param arr     The array to remove the element from
     * @param element The element to remove
     * @param <T>     Any object sub type
     * @return The new array without the element
     */
    public static <T> T[] removeElementFromArray(T[] arr, T element) {
        if (arr == null)
            return null;
        List<T> arrList = new LinkedList<>(Arrays.asList(arr));
        arrList.remove(element);
        return (T[]) arrList.toArray(new CharSequence[arrList.size()]);
        //todo Avoid this cast, otherwise will crash on non charseq!
    }
}
