/*
 * BatteryLevelAndStateReceiver.java
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

package com.maginmp.app.markompressvideo.receivers;

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.maginmp.app.markompressvideo.services.VideosManagementService;
import com.maginmp.app.markompressvideo.utils.ResourcesUtils;

/**
 * Created by MarkGintsburg on 04/06/2017.
 */

public class BatteryLevelAndStateReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        Toast.makeText(context, "BatteryLevelAndStateReceiver", Toast.LENGTH_LONG).show();

        //todo maybe remove, since in Android O being registered explicitly
        Intent serviceIntent = new Intent(context, VideosManagementService.class);
        context.startService(serviceIntent);


        ResourcesUtils.deviceEncoderStateUpdater(PreferenceManager.getDefaultSharedPreferences(context), intent, context);
    }
}
