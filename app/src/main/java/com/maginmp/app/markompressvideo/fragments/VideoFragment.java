/*
 * VideoFragment.java
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

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v4.widget.SwipeRefreshLayout;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.AppCompatImageButton;
import android.support.v7.widget.CardView;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.util.SparseBooleanArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;

import com.maginmp.app.markompressvideo.R;
import com.maginmp.app.markompressvideo.database.VideosDataSource;
import com.maginmp.app.markompressvideo.database.VideosDatabaseHelper;
import com.maginmp.app.markompressvideo.objects.VideoObject;
import com.maginmp.app.markompressvideo.services.VideosManagementService;
import com.maginmp.app.markompressvideo.system.Startup;
import com.maginmp.app.markompressvideo.utils.FilesUtils;
import com.maginmp.app.markompressvideo.utils.ResourcesUtils;
import com.maginmp.app.markompressvideo.utils.StringUtils;
import com.maginmp.app.markompressvideo.utils.ThreadsUtils;

import net.cachapa.expandablelayout.ExpandableLayout;

import java.io.ByteArrayInputStream;
import java.text.SimpleDateFormat;

/**
 * Created by MarkGintsburg on 11/11/2016.
 */
public class VideoFragment extends Fragment {
    private static final String TAG = VideoFragment.class.getSimpleName();

    public SharedPreferences mSharedPreferences;
    RecyclerView mVideoCardsRecyclerView;
    /**
     * The {@link android.support.v4.widget.SwipeRefreshLayout} that detects swipe gestures and
     * triggers callbacks in the app.
     */
    private SwipeRefreshLayout mSwipeRefreshLayout;
    private Cursor mDisplayedVideosCursor;
    private VideosDataSource mVidDataSrc;
    private VideoCardsAdapter mDisplayedVideosAdapter;
    public SharedPreferences.OnSharedPreferenceChangeListener mPrefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
        @Override
        public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
            Log.v(TAG, key + " Preference was changed");
            if (getActivity() != null) {
                // Listen for refresh state change
                if (key.equals(getString(R.string.keysetting_videos_is_refreshing))) {
                    boolean isRefreshing = mSharedPreferences.getBoolean(key, false);
                    if (!isRefreshing)
                        onRefreshComplete();
                    mSwipeRefreshLayout.setRefreshing(isRefreshing && VideosManagementService.IS_SERVICE_RUNNING);
                }
            }
        }
    };
    private LocalBroadcastManager mBroadcaster;

    private BroadcastReceiver mServiceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int cursorInvalidatePos = intent.getIntExtra(VideosManagementService.BROADCAST_MESSAGE_CURSOR_POS, 0);
            if (cursorInvalidatePos > -1) {
                //invalidateCardByCursorPos(cursorInvalidatePos);

                // This is a patch that should be resolved with a better design
                // It invalidates the entire set because the cursor position differs according
                // the database query.
                invalidateCardByCursorPos(-1);
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mBroadcaster = LocalBroadcastManager.getInstance(getActivity());
    }

    @Override
    public void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver((mServiceReceiver),
                new IntentFilter(VideosManagementService.BROADCAST_RESULT)
        );
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fragment_video, container, false);
        createCardList(view);
        mSwipeRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.swipeRefresh);

        return view;
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        mSharedPreferences.registerOnSharedPreferenceChangeListener(mPrefListener);

        /**
         * Implement {@link SwipeRefreshLayout.OnRefreshListener}. When users do the "swipe to
         * refresh" gesture, SwipeRefreshLayout invokes
         * {@link SwipeRefreshLayout.OnRefreshListener#onRefresh onRefresh()}. In
         * {@link SwipeRefreshLayout.OnRefreshListener#onRefresh onRefresh()}, call a method that
         * refreshes the content. Call the same method in response to the Refresh action from the
         * action bar.
         */
        mSwipeRefreshLayout.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
            @Override
            public void onRefresh() {
                Log.v(TAG, "onRefresh called from SwipeRefreshLayout");
                initiateRefresh();
            }
        });

        mSwipeRefreshLayout.setRefreshing(mSharedPreferences.getBoolean(getString(R.string.keysetting_videos_is_refreshing), false) && VideosManagementService.IS_SERVICE_RUNNING);
    }


    private void initiateRefresh() {
        Log.v(TAG, "initiateRefresh");
        if (VideosManagementService.IS_SERVICE_RUNNING) {
            SharedPreferences.Editor editor = mSharedPreferences.edit();
            editor.putBoolean(getString(R.string.keysetting_videos_is_refreshing), true);
            editor.commit();
        } else
            Toast.makeText(getActivity(), R.string.videos_man_service_off, Toast.LENGTH_LONG).show();
    }

    private void createCardList(View view) {
        mVidDataSrc = new VideosDataSource(getActivity());
        mVidDataSrc.open();

        mVideoCardsRecyclerView = (RecyclerView) view.findViewById(R.id.videoRecyclerView);

        //mVideoCardsRecyclerView.setHasFixedSize(true); // generally the info button changes each card size, so not applicable?
        refreshCardList();
    }

    private void refreshCardList() {
        if (mDisplayedVideosCursor != null && !mDisplayedVideosCursor.isClosed())
            mDisplayedVideosCursor.close();
        mDisplayedVideosCursor = mVidDataSrc.getAllVideos(false, null);
        mDisplayedVideosAdapter = new VideoCardsAdapter(mDisplayedVideosCursor);
        LinearLayoutManager videoCardsLayoutManager = new LinearLayoutManager(getActivity());
        videoCardsLayoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        if (mVideoCardsRecyclerView != null) { //mDisplayedVideosCursor.getCount() > 0 &&
            mVideoCardsRecyclerView.setAdapter(mDisplayedVideosAdapter);
        }

        mVideoCardsRecyclerView.setLayoutManager(videoCardsLayoutManager);
    }

    private void onRefreshComplete() {
        Log.v(TAG, "onRefreshComplete");

        //mDisplayedVideosAdapter.notifyDataSetChanged();
        refreshCardList();
    }

    private void invalidateCardByCursorPos(int position) {
        if (mVidDataSrc == null || mDisplayedVideosAdapter == null)
            return;

        mDisplayedVideosCursor = mVidDataSrc.getAllVideos(false, null);
        mDisplayedVideosAdapter.updateCursor(mDisplayedVideosCursor);
        if (position == -1) {
            //update all
            mDisplayedVideosAdapter.notifyDataSetChanged();
        } else {
            //update a specific id (currently does not work)
            mDisplayedVideosAdapter.notifyItemChanged(position);
        }

    }

    @Override
    public void onPause() {
        LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(mServiceReceiver);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        mDisplayedVideosCursor.close();
        mVidDataSrc.close();
        super.onDestroy();
    }

    public class VideoCardsAdapter extends RecyclerView.Adapter<VideoCardsViewHolder> {
        SparseBooleanArray mIsExpanded = new SparseBooleanArray();
        private Cursor mList;

        public VideoCardsAdapter(Cursor cursor) {
            mList = cursor;
        }

        public void updateCursor(Cursor cursor) {
            mList.close();
            mList = cursor;
        }

        @Override
        public VideoCardsViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            // create a new view
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.video_card, parent, false);
            VideoCardsViewHolder holder = new VideoCardsViewHolder(view);
            return holder;
        }

        @Override
        public void onBindViewHolder(final VideoCardsViewHolder holder, int position) {

            if (!mList.moveToPosition(position)) {
                String err = TAG + " cannot move to cursor pos: " + position;
                Log.e(TAG, err);
                Startup.ERROR_COLLECTOR.addError(err);
            }
            VideoObject video = new VideoObject(mList);
            holder.setmCurrentItem(video);

            holder.getmTitleTextView().setText(video.getmFile().getName());
            long fileSize = video.getmStatus() != VideosDatabaseHelper.STATUS_REVERTED && video.getmStatus() != VideosDatabaseHelper.STATUS_QUEUE ? video.getmTargetFilesize() : video.getmSourceFilesize();
            holder.getmSubTitleTextView().setText(StringUtils.widthAndHeightToDimen(video.getmTargetWidth(), video.getmTargetHeight()) + " \u2022 " + (int) Math.ceil(fileSize / 1048576.0) + " MB \u2022 " + StringUtils.millisToMinAndSec(video.getmDurationMilisec()) + " \u2022 " + StringUtils.getStatusFromInt(holder.getmCardView().getContext(), video.getmStatus()));
            ByteArrayInputStream thumbStream = new ByteArrayInputStream(video.getmThumbnail());
            Bitmap thumbBitmap = BitmapFactory.decodeStream(thumbStream);
            holder.getmThumbnailImageView().setImageBitmap(thumbBitmap);

            // If bu file doesn't exist then src file ize is -1
            boolean isOrigInfoExist = video.getmSourceFilesize() > 0;

            TableLayout moreInfo = holder.getmMoreInfoTable();
            if (moreInfo.getChildCount() != 0)
                moreInfo.removeAllViews();
            String tab = "    ";

            addRow(moreInfo, tab, "");

            addRow(moreInfo, getString(R.string.videos_card_moreinfo_srcvd), "");

            if (isOrigInfoExist)
                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_dimen), StringUtils.widthAndHeightToDimen(video.getmSourceWidth(), video.getmSourceHeight()));

            addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_duration), StringUtils.millisToMinAndSec(video.getmDurationMilisec()));

            if (isOrigInfoExist)
                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_size), String.format("%.1f MB", (float) video.getmSourceFilesize() / 1048576));

            if (video.ismIsRevetable())
                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_file), video.getmBackupFile().getParent());
            else if ((video.getmStatus() & VideosDatabaseHelper.STATUS_QUEUE) == VideosDatabaseHelper.STATUS_QUEUE)
                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_file), video.getmFile().getParent());

            if (isOrigInfoExist)
                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_addedqueue), (new SimpleDateFormat("d MMM yyyy")).format(video.getmAddedToQueueDate()));

            if ((video.getmStatus() & VideosDatabaseHelper.STATUS_DONE) == VideosDatabaseHelper.STATUS_DONE) {
                addRow(moreInfo, tab, ""); //spacer row

                addRow(moreInfo, getString(R.string.videos_card_moreinfo_tarvd), "");

                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_dimen), StringUtils.widthAndHeightToDimen(video.getmTargetWidth(), video.getmTargetHeight()));

                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_size), String.format("%.1f MB", (float) video.getmTargetFilesize() / 1048576));

                addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_file), video.getmFile().getParent());

                if (video.getmProcessedDate().after(video.getmAddedToQueueDate()))
                    addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_proccesed), (new SimpleDateFormat("d MMM yyyy")).format(video.getmProcessedDate()));

                if (video.getmProcessedDate().after(video.getmAddedToQueueDate()))
                    addRow(moreInfo, tab + getString(R.string.videos_card_moreinfo_enctime), StringUtils.millisToMinAndSec(video.getmEncodeTime()));
            }
            addRow(moreInfo, tab, "");

            TypedValue outValue = new TypedValue();
            getResources().getValue(R.dimen.icon_material_black_on_white_alpha_disabled, outValue, true);
            float disabledAlpha = outValue.getFloat();
            outValue = new TypedValue();
            getResources().getValue(R.dimen.icon_material_black_on_white_alpha, outValue, true);
            float enabledAlpha = outValue.getFloat();
            outValue = new TypedValue();
            getResources().getValue(R.dimen.icon_material_color_on_white_alpha, outValue, true);
            float enabledColorAlpha = outValue.getFloat();

            holder.getmBypassButton().setChecked((video.getmStatus() & VideosDatabaseHelper.STATUS_QUEUE) == VideosDatabaseHelper.STATUS_QUEUE);
            if ((video.getmStatus() & VideosDatabaseHelper.STATUS_QUEUE) != VideosDatabaseHelper.STATUS_QUEUE &&
                    (video.getmStatus() & VideosDatabaseHelper.STATUS_BYPASS) != VideosDatabaseHelper.STATUS_BYPASS &&
                    (video.getmStatus() & VideosDatabaseHelper.STATUS_REVERTED) != VideosDatabaseHelper.STATUS_REVERTED) {
                holder.getmBypassButton().setClickable(false);
                holder.getmBypassButton().setAlpha(disabledAlpha);
            } else {
                holder.getmBypassButton().setClickable(true);
                holder.getmBypassButton().setAlpha(enabledColorAlpha);
            }

            if (!video.ismIsRevetable()) {
                holder.getmRevertButton().setClickable(false);
                holder.getmRevertButton().setAlpha(disabledAlpha);
            } else {
                holder.getmRevertButton().setClickable(true);
                holder.getmRevertButton().setAlpha(enabledAlpha);
            }

            if (mIsExpanded.get(position)) {
                holder.getmExpandableLayout().expand(true);

                // Very dirty patch. Apparently the expandable layout has a bug
                // that when recycling views it shows an empty view after expanding the more info.
                // It is fixed when the layout is collapsed and re-expanded.
                holder.getmExpandableLayout().toggle();
                holder.getmExpandableLayout().toggle();
            } else if (holder.getmExpandableLayout().isExpanded())
                holder.getmExpandableLayout().collapse();

        }

        /**
         * Add new row to more info table
         *
         * @param table table
         * @param left  left column
         * @param right right column
         */
        private void addRow(TableLayout table, String left, String right) {
            int padding = ResourcesUtils.dimenResourceToPx(table.getContext(), R.dimen.card_sub_text_left_right_padding);
            TableRow row = new TableRow(table.getContext());
            row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
            TextView tvLeft = new TextView(table.getContext());
            TableRow.LayoutParams lpLeft = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            tvLeft.setLayoutParams(lpLeft);
            tvLeft.setText(left);
            tvLeft.setPadding(padding, padding / 4, padding, padding / 4);
            row.addView(tvLeft);
            TextView tvRight = new TextView(table.getContext());
            TableRow.LayoutParams lpRight = new TableRow.LayoutParams(TableRow.LayoutParams.WRAP_CONTENT, TableRow.LayoutParams.WRAP_CONTENT);
            tvRight.setLayoutParams(lpRight);
            tvRight.setText(right);
            tvRight.setPadding(padding, padding / 4, padding, padding / 4);
            tvRight.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            tvRight.setSingleLine(false);
            tvRight.setMaxEms(10); //do not know why it works this way...
            row.addView(tvRight);
            table.addView(row);
            Log.v(TAG, left + " : " + right);
        }

        @Override
        public int getItemCount() {
            return mList.getCount();
        }
    }

    public class VideoCardsViewHolder extends RecyclerView.ViewHolder {

        private VideoObject mCurrentItem;
        private View mCurrentView;
        private TextView mTitleTextView;
        private TextView mSubTitleTextView;
        private ImageView mThumbnailImageView;
        private SwitchCompat mBypassButton;
        private AppCompatImageButton mMoreInfoButton;
        private AppCompatImageButton mRevertButton;
        private ImageView mPlayVideoButton;
        private ExpandableLayout mExpandableLayout;
        private TableLayout mMoreInfoTable;
        private CardView mCardView;

        public VideoCardsViewHolder(View v) {
            super(v);
            this.setmCurrentView(v);
            mTitleTextView = (TextView) v.findViewById(R.id.videoFileName);
            mSubTitleTextView = (TextView) v.findViewById(R.id.videoFileSubtext);
            mThumbnailImageView = (ImageView) v.findViewById(R.id.videoThumbnailView);
            mBypassButton = (SwitchCompat) v.findViewById(R.id.videos_card_add_remove_queue_switch);
            mRevertButton = (AppCompatImageButton) v.findViewById(R.id.videos_card_revert);
            mMoreInfoButton = (AppCompatImageButton) v.findViewById(R.id.videos_card_info);
            mPlayVideoButton = (ImageView) v.findViewById(R.id.videos_card_play_button);
            mExpandableLayout = (ExpandableLayout) v.findViewById(R.id.videos_card_expandable_layout);
            mMoreInfoTable = (TableLayout) v.findViewById(R.id.video_card_expandable_layout_table);
            mCardView = (CardView) v.findViewById(R.id.card_view);


            mPlayVideoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, "Play " + mCurrentItem.getmFile().getAbsolutePath());
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(mCurrentItem.getmFile().getAbsolutePath()));
                    intent.setDataAndType(Uri.parse(mCurrentItem.getmFile().getAbsolutePath()), "video/*");
                    startActivity(intent);
                }
            });

            mMoreInfoButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final int currentPosition = getAdapterPosition();
                    if (mDisplayedVideosAdapter.mIsExpanded.get(currentPosition))
                        mDisplayedVideosAdapter.mIsExpanded.delete(currentPosition);
                    else
                        mDisplayedVideosAdapter.mIsExpanded.put(currentPosition, true);

                    //inform the adapter to rebind
                    mDisplayedVideosAdapter.notifyItemChanged(currentPosition);
                }
            });

            mBypassButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (((SwitchCompat) v).isChecked()) {
                        mCurrentItem.setmStatus(VideosDatabaseHelper.STATUS_QUEUE);
                    } else {
                        mCurrentItem.setmStatus(VideosDatabaseHelper.STATUS_BYPASS);
                    }

                    final int currentPosition = getAdapterPosition();
                    mVidDataSrc.updateVideoStatus(mCurrentItem.getmStatus(), mCurrentItem.getmId());

                    // Send broadcast
                    String q = "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_QUEUE + ")=" + VideosDatabaseHelper.STATUS_QUEUE + " OR " + "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_RUNNING + ")=" + VideosDatabaseHelper.STATUS_RUNNING + "";
                    Cursor dbFindVideo = mVidDataSrc.getAllVideos(true, q);
                    int cursorCount = dbFindVideo.getCount();
                    ResourcesUtils.broadcastResult(cursorCount, currentPosition, mBroadcaster);
                    dbFindVideo.close();
                }
            });


            mRevertButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Log.v(TAG, mCurrentItem.getmFile().getAbsolutePath() + ": " + mCurrentItem.getmFile().exists());
                    Log.v(TAG, mCurrentItem.getmBackupFile().getAbsolutePath() + ": " + mCurrentItem.getmBackupFile().exists());
                    Log.v(TAG, "Revertable: " + mCurrentItem.ismIsRevetable());

                    // "Are you sure to revert?" dialog
                    AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                    builder.setTitle(R.string.dialog_revert_title)
                            .setMessage(R.string.dialog_revert_msg)
                            .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Log.v(TAG, "Revert ok for " + mCurrentItem.getmFile().getName());
                                    if (mCurrentItem.getmBackupFile().exists()) {
                                        synchronized (ThreadsUtils.SYNC_FILE_OPERATION) {
                                            FilesUtils.swapFiles(mCurrentItem);
                                            FilesUtils.delFile(mCurrentItem.getmBackupFile());
                                            mCurrentItem.setmIsRevetable(false);
                                            mCurrentItem.setmStatus(VideosDatabaseHelper.STATUS_REVERTED);
                                            mVidDataSrc.updateVideoStatus(mCurrentItem.getmStatus(), mCurrentItem.getmId());
                                            mVidDataSrc.updateRevertable(mCurrentItem.ismIsRevetable(), mCurrentItem.getmId());

                                            String q = "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_QUEUE + ")=" + VideosDatabaseHelper.STATUS_QUEUE + " OR " + "(" + VideosDatabaseHelper.COL_VIDEO_STATUS + " & " + VideosDatabaseHelper.STATUS_RUNNING + ")=" + VideosDatabaseHelper.STATUS_RUNNING + "";
                                            Cursor dbFindVideo = mVidDataSrc.getAllVideos(true, q);
                                            int cursorCount = dbFindVideo.getCount();
                                            ResourcesUtils.broadcastResult(cursorCount, mDisplayedVideosCursor.getPosition(), mBroadcaster);
                                            dbFindVideo.close();
                                        }
                                    } else {
                                        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
                                        builder.setTitle(R.string.dialog_revert_notexist_title)
                                                .setMessage(R.string.dialog_revert_notexist_msg)
                                                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                                                    public void onClick(DialogInterface dialog, int id) {
                                                        //Log.v(TAG, "Revert ok for " + mCurrentItem.getmFile().getName());
                                                    }
                                                });
                                        builder.create().show();
                                    }
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface dialog, int id) {
                                    Log.v(TAG, "Revert cancel for " + mCurrentItem.getmFile().getName());
                                }
                            });
                    builder.create().show();
                }
            });


        }

        public TextView getmTitleTextView() {
            return mTitleTextView;
        }

        public void setmTitleTextView(TextView mTitleTextView) {
            this.mTitleTextView = mTitleTextView;
        }

        public TextView getmSubTitleTextView() {
            return mSubTitleTextView;
        }

        public void setmSubTitleTextView(TextView mSubTitleTextView) {
            this.mSubTitleTextView = mSubTitleTextView;
        }

        public ImageView getmThumbnailImageView() {
            return mThumbnailImageView;
        }

        public void setmThumbnailImageView(ImageView mThumbnailImageView) {
            this.mThumbnailImageView = mThumbnailImageView;
        }

        public SwitchCompat getmBypassButton() {
            return mBypassButton;
        }

        public void setmBypassButton(SwitchCompat mBypassButton) {
            this.mBypassButton = mBypassButton;
        }

        public AppCompatImageButton getmRevertButton() {
            return mRevertButton;
        }

        public void setmRevertButton(AppCompatImageButton mRevertButton) {
            this.mRevertButton = mRevertButton;
        }

        public VideoObject getmCurrentItem() {
            return mCurrentItem;
        }

        public void setmCurrentItem(VideoObject mCurrentItem) {
            this.mCurrentItem = mCurrentItem;
        }

        public ImageView getmPlayVideoButton() {
            return mPlayVideoButton;
        }

        public void setmPlayVideoButton(ImageView mPlayVideoButton) {
            this.mPlayVideoButton = mPlayVideoButton;
        }

        public CardView getmCardView() {
            return mCardView;
        }

        public void setmCardView(CardView mCardView) {
            this.mCardView = mCardView;
        }

        public View getmCurrentView() {
            return mCurrentView;
        }

        public void setmCurrentView(View mCurrentView) {
            this.mCurrentView = mCurrentView;
        }

        public TableLayout getmMoreInfoTable() {
            return mMoreInfoTable;
        }

        public void setmMoreInfoTable(TableLayout mMoreInfoTable) {
            this.mMoreInfoTable = mMoreInfoTable;
        }

        public ExpandableLayout getmExpandableLayout() {
            return mExpandableLayout;
        }

        public void setmExpandableLayout(ExpandableLayout mExpandableLayout) {
            this.mExpandableLayout = mExpandableLayout;
        }
    }
}
