/*
 * Startup.java
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

import android.app.Application;

import com.maginmp.app.markompressvideo.activities.MainActivity;

import java.io.File;

/**
 * Created by MarkGintsburg on 30/05/2017.
 */

public class Startup extends Application {

    public static final ErrorCollector ERROR_COLLECTOR = ErrorCollector.getErrorCollectorIstance();

    @Override
    public void onCreate() {
        ERROR_COLLECTOR.setNotificationId(MainActivity.NOTIFICATION_ID_ERROR_COLLECTOR);
        ERROR_COLLECTOR.setLogFile((new File(MainActivity.MKV_DIRECTORY, "log.txt")).getAbsolutePath());
        super.onCreate();
    }
}
