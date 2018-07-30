/*
 * PermissionsUtils.java
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
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.util.Log;
import android.widget.Toast;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by MarkGintsburg on 05/12/2016.
 */

public class PermissionsUtils {

    private static final String TAG = PermissionsUtils.class.getSimpleName();


    public static boolean CheckPermissionsGrantedSimple(Context context) {
        int[] permissionCheck = new int[MainActivity.APP_PERMISSIONS.length];
        boolean wasNotApproved = false;

        for (int i = 0; i < permissionCheck.length; i++) {
            permissionCheck[i] = ContextCompat.checkSelfPermission(context, MainActivity.APP_PERMISSIONS[i]);
            if (permissionCheck[i] == PackageManager.PERMISSION_DENIED) {
                wasNotApproved = true;
            }
        }

        return !wasNotApproved;
    }

    public static void requestPermissions(Activity activity, String[] permissions, int requestCode) {
        int[] permissionCheck = new int[permissions.length];
        boolean[] permissionRational = new boolean[permissions.length];
        List<String> needToApproveList = new ArrayList<>();
        boolean wasNotApproved = false;
        boolean neverAskedForPermissions = true;
        for (int i = 0; i < permissionCheck.length; i++) {
            permissionCheck[i] = ContextCompat.checkSelfPermission(activity, permissions[i]);
            permissionRational[i] = ActivityCompat.shouldShowRequestPermissionRationale(activity, permissions[i]);
            if (permissionCheck[i] == PackageManager.PERMISSION_DENIED) {
                wasNotApproved = true;
                needToApproveList.add(permissions[i]);
            }
            if (permissionRational[i])
                neverAskedForPermissions = false;
        }

        if (neverAskedForPermissions && wasNotApproved) {
            ActivityCompat.requestPermissions(activity, permissions, requestCode);
        } else if (wasNotApproved) {
            String[] strArr = new String[needToApproveList.size()];
            needToApproveList.toArray(strArr);
            showPermissionExplanationDialog(strArr, activity, requestCode);
        } else
            Log.i(TAG, "PermissionsUtils are good");

    }

    public static void showPermissionExplanationDialog(final String[] permissions, final Activity activity, final int requestCode) {
        AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle(R.string.dialog_permissions_title)
                .setMessage(R.string.dialog_permissions_msg)
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        ActivityCompat.requestPermissions(activity, permissions, requestCode);
                    }
                });
        builder.create().show();
    }

    public static void onRequestPermissionsResult(
            Activity activity,
            int staticRequestCode,
            int requestCode,
            String permissions[],
            int[] grantResults) {

        boolean fullyApproved = grantResults.length > 0;

        if (requestCode == staticRequestCode)
            for (int i = 0; i < grantResults.length; i++)
                if (grantResults[i] == PackageManager.PERMISSION_DENIED)
                    fullyApproved = false;

        if (fullyApproved) {
            Log.i(TAG, "PermissionsUtils are good");
        } else {
            Toast.makeText(activity, R.string.toast_permissions_not_accepted, Toast.LENGTH_LONG).show();
            Log.i(TAG, "PermissionsUtils are bad");
            // Exit app
            activity.finishAffinity();
        }
    }

}
