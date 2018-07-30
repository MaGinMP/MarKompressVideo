/*
 * BootCompletedReceiver.java
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

/**
 * Created by MarkGintsburg on 11/12/2016.
 */

import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.maginmp.app.markompressvideo.services.VideosManagementService;

public class BootCompletedReceiver extends BroadcastReceiver {
    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!VideosManagementService.IS_SERVICE_RUNNING) {
            Intent serviceIntent = new Intent(context, VideosManagementService.class);
            context.startService(serviceIntent);
        }
    }
}