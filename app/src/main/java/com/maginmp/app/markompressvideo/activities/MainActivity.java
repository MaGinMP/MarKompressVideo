/*
 * MainActivity.java
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

package com.maginmp.app.markompressvideo.activities;

import android.Manifest;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.annotation.IdRes;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.database.VideosDataSource;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;
import com.maginmp.app.markompressvideo.fragments.InfoFragment;
import com.maginmp.app.markompressvideo.fragments.SettingsFragment;
import com.maginmp.app.markompressvideo.fragments.VideoFragment;
import com.maginmp.app.markompressvideo.services.VideosManagementService;
import com.maginmp.app.markompressvideo.utils.FfmpegUtils;
import com.maginmp.app.markompressvideo.utils.PermissionsUtils;
import com.roughike.bottombar.BottomBar;
import com.roughike.bottombar.BottomBarTab;
import com.roughike.bottombar.OnTabSelectListener;
import com.stephentuso.welcome.WelcomeHelper;

import java.io.File;

public class MainActivity extends AppCompatActivity implements OnTabSelectListener {

    public static final String[] APP_PERMISSIONS = {
            Manifest.permission.INTERNET,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.RECEIVE_BOOT_COMPLETED,
            Manifest.permission.WAKE_LOCK,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    public static final int APP_PERMISSIONS_REQUEST_CODE = 1;
    public static final String[] VIDEO_FILE_EXTENSIONS = {".mp4", ".mov"};
    public static final String MKV_NAME = "MKV";
    public static final File MKV_DIRECTORY = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM), MainActivity.MKV_NAME);
    public static final String[] MKV_METADATA_STAMP = {"MarKompress"}; //If name changed, add to array. Needed for metadata parse
    public static final int MKV_METADATA_STAMP_IDX = 0; //The index of the currently chosen MainActivity.MKV_METADATA_STAMP
    public static final String MKV_METADATA_DELIMITER = ";;";
    public static final String MKV_METADATA_DELIMITER_READ = "\\;\\;"; //ffmpeg adds escape chars
    public static final int NOTIFICATION_ID_ERROR_COLLECTOR = 3;
    public static final int NOTIFICATION_ID_ERROR_FFUNSUPPORTED = 2;
    public static final int NOTIFICATION_ID_FG_SERVICE = 1;
    public static final String NOTIFICATION_CHANNEL_ID = "com.maginmp.app.markompressvideo.services.notifications";
    public static final float MINIMAL_BATTERY_LEVEL = 0.95f; // 95%
    public static final int MINIMAL_SUGGESTED_STORAGE_MB = 1024; // 1GB
    private static final String TAG = MainActivity.class.getSimpleName();
    private SharedPreferences mSharedPreferences;

    private BottomBar mBottomBar;
    private BottomBarTab mTabVideos;
    private BottomBarTab mTabSettings;
    private BottomBarTab mTabInfo;
    private TextView mTitleBar;

    private Fragment mVideosFragment;
    private Fragment mSettingsFragment;
    private Fragment mInfoFragment;

    private final BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int queueCount = intent.getIntExtra(VideosManagementService.BROADCAST_MESSAGE_QUEUE_COUNT, 0);

            // If service sent a broadcast that number of in queue videos changed (queueCount positive)
            if (mTabVideos != null && queueCount > -1) {
                // update or remove badge
                if (queueCount == 0)
                    mTabVideos.removeBadge();
                else
                    mTabVideos.setBadgeCount(queueCount);
            }
        }
    };
    private WelcomeHelper mWelcomeScreen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "MKV started");
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(this);
        PermissionsUtils.requestPermissions(this, APP_PERMISSIONS, APP_PERMISSIONS_REQUEST_CODE);
        setContentView(R.layout.activity_main);

        initUi(savedInstanceState);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register a receiver for service broadcast
        LocalBroadcastManager.getInstance(this).registerReceiver((mServiceReceiver),
                new IntentFilter(VideosManagementService.BROADCAST_RESULT)
        );
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.i(TAG, "MKV resumed");

        // Set dir chooser in setting
        if (mSettingsFragment instanceof SettingsFragment)
            ((SettingsFragment) mSettingsFragment).setListPreferenceData();

        updateBadgeCountFromDb();

        initService();
    }

    /**
     * Reads from DB all in queue videos and updates badge
     */
    private void updateBadgeCountFromDb() {
        VideosDataSource videosDataSource = new VideosDataSource(this);
        videosDataSource.open();
        int count = videosDataSource.getCount(VideosDatabaseHelper.STATUS_QUEUE);
        if (count == 0)
            mTabVideos.removeBadge();
        else
            mTabVideos.setBadgeCount(count);
        videosDataSource.close();
    }


    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String permissions[],
            int[] grantResults) {

        PermissionsUtils.onRequestPermissionsResult(this, APP_PERMISSIONS_REQUEST_CODE, requestCode, permissions, grantResults);
        if (mSettingsFragment instanceof SettingsFragment)
            ((SettingsFragment) mSettingsFragment).setListPreferenceData();
    }


    private void initUi(Bundle savedInstanceState) {
        mWelcomeScreen = new WelcomeHelper(this, WelcomeScreenActivity.class);
        mWelcomeScreen.show(savedInstanceState);

        mBottomBar = findViewById(R.id.bottomBar);
        mBottomBar.setDefaultTab(R.id.tab_settings);
        mTabVideos = mBottomBar.getTabWithId(R.id.tab_videos);
        mTabSettings = mBottomBar.getTabWithId(R.id.tab_settings);
        mTabInfo = mBottomBar.getTabWithId(R.id.tab_info);

        mTitleBar = findViewById(R.id.appTitleBar);


        mVideosFragment = new VideoFragment();
        mSettingsFragment = new SettingsFragment();
        mInfoFragment = new InfoFragment();

        mBottomBar.setOnTabSelectListener(this);
        mTabVideos.setBadgeHidesWhenActive(false);

    }

    private void initService() {
        if (!mSharedPreferences.getBoolean(getString(R.string.keysetting_is_ffmpeg_supported), true)) {
            FfmpegUtils.showFfmpegUnsupportedDialog(this);
        } else {
            Intent intent = new Intent(this, VideosManagementService.class);
            startService(intent);
        }
    }

    @Override
    public void onTabSelected(@IdRes int tabId) {
        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);

        if (tabId == R.id.tab_videos) {
            transaction.replace(R.id.contentContainer, mVideosFragment);
        } else if (tabId == R.id.tab_settings) {
            transaction.replace(R.id.contentContainer, mSettingsFragment);
        } else if (tabId == R.id.tab_info) {
            transaction.replace(R.id.contentContainer, mInfoFragment);
        }

        transaction.addToBackStack(null);
        transaction.commit();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mWelcomeScreen.onSaveInstanceState(outState);
    }


    public void forceShowSplash(View v) {
        Log.v(TAG, "Showing splash");
        mWelcomeScreen = new WelcomeHelper(this, WelcomeScreenActivity.class);
        mWelcomeScreen.forceShow();
    }

    @Override
    protected void onStop() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mServiceReceiver);
        super.onStop();
    }
}

