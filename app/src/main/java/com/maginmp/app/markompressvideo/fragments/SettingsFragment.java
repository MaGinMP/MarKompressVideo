/*
 * SettingsFragment.java
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

package com.maginmp.app.markompressvideo.fragments;

import android.os.Bundle;
import android.os.Environment;
import android.preference.MultiSelectListPreference;
import android.preference.PreferenceFragment;
import android.util.Log;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.activities.MainActivity;
import com.maginmp.app.markompressvideo.utils.FilesUtils;
import com.maginmp.app.markompressvideo.utils.PermissionsUtils;
import com.maginmp.app.markompressvideo.utils.StringUtils;

import java.io.File;

/**
 * Created by MarkGintsburg on 11/11/2016.
 */
public class SettingsFragment extends PreferenceFragment {

    private static final String TAG = SettingsFragment.class.getSimpleName();
    private MultiSelectListPreference mMslp;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.fragment_settings);
        mMslp = (MultiSelectListPreference) findPreference(getString(R.string.keysetting_file_directories));
    }

    /**
     * Adds all dirs in DCIM to dir chooser
     */
    public void setListPreferenceData() {
        File dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM);

        if (dcimDir != null) {
            CharSequence[] dirs = FilesUtils.listAllDirectories(dcimDir);

            //remove mkv from search path
            dirs = StringUtils.removeElementFromArray(dirs, MainActivity.MKV_NAME);

            CharSequence[] entries = null;
            CharSequence[] entryValues = null;
            if (dirs != null) {
                entries = dirs.clone();
                entryValues = dirs.clone();
            }
            mMslp.setEntries(entries);
            mMslp.setEntryValues(entryValues);

            if (entries == null || entries.length == 0)
                Log.i(TAG, "DCIM directory is empty");
        } else {
            if (PermissionsUtils.CheckPermissionsGrantedSimple(getActivity())) {
                Log.e(TAG, "MultiSelectListPreference permission issue");
            } else {
                Log.e(TAG, "MultiSelectListPreference IO issue - non permission");
            }
        }
    }


//    @Override
//    public View onCreateView(LayoutInflater inflater, ViewGroup container,
//                             Bundle savedInstanceState) {
//        // Inflate the layout for this fragment
//        //return inflater.inflate(R.layout.fragment_settings, container, false);
//        return addPreferencesFromResource(R.xml.fragment_settings);
//    }
}
