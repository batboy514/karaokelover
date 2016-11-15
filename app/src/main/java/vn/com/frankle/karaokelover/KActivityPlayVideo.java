package vn.com.frankle.karaokelover;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.youtube.player.YouTubeInitializationResult;
import com.google.android.youtube.player.YouTubePlayer;
import com.google.android.youtube.player.YouTubePlayerSupportFragment;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;

import butterknife.BindView;
import butterknife.ButterKnife;
import rx.Observable;
import rx.Subscriber;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import vn.com.frankle.karaokelover.adapters.EndlessRecyclerViewScrollListener;
import vn.com.frankle.karaokelover.adapters.KAdapterComments;
import vn.com.frankle.karaokelover.adapters.viewholders.ViewHolderComment;
import vn.com.frankle.karaokelover.events.EventPrepareRecordingCountdown;
import vn.com.frankle.karaokelover.services.responses.ResponseCommentThreads;
import vn.com.frankle.karaokelover.util.Utils;
import vn.com.frankle.karaokelover.views.recyclerview.InsetDividerDecoration;

public class KActivityPlayVideo extends AppCompatActivity implements KAudioRecord.AudioRecordListener {

    private static final String DEBUG_TAG = KActivityPlayVideo.class.getSimpleName();

    @NonNull
    private final CompositeSubscription compositeSubscriptionForOnStop = new CompositeSubscription();

    @BindView(R.id.content_kactivity_play_video)
    RelativeLayout mContentLayout;
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.btn_recording)
    ImageButton btnRecord;
    @BindView(R.id.layout_ready)
    RelativeLayout mLayoutCountdown;
    @BindView(R.id.tv_countdown)
    TextView mTvCountdown;
    @BindView(R.id.btn_record)
    FloatingActionButton fabRecord;
    @BindView(R.id.timer_recording)
    TextView mTvTimerRecord;
    @BindView(R.id.saved_filename)
    TextView mTvSavedFilename;
    @BindView(R.id.recyclerview_comments)
    RecyclerView mRecyclerViewComment;
    @BindView(R.id.progressbar_comment)
    ProgressBar mProgressBarComment;
    @BindView(R.id.layout_record)
    RelativeLayout mLayoutRecord;
    @BindView(R.id.layout_play_video)
    RelativeLayout mLayoutPlayVideo;

    // App SharePreference
    KSharedPreference mAppSharePrefs = new KSharedPreference();
    EndlessRecyclerViewScrollListener onScrollListener;
    private KAdapterComments mAdapterComment;
    //    private GLAudioVisualizationView mAudioRecordVisualization;
    private String mCurrentVideoId;
    private String mCurrentVideoTitle;
    private String mCurrentSavedFilename;
    private String mCurrentCommentPageToken;
    private boolean mFavoriteState;
    private YouTubePlayerSupportFragment mYoutubePlayerFragment;
    //    private KAudioRecordDbmHandler mAudioDbmHandler = new KAudioRecordDbmHandler();
    private KAudioRecord mRecorder;
    // Handling record timer
    private int recorderSecondsElapsed = 0;
    private Handler handler = new Handler();
    private YouTubePlayer mYoutubePlayer;
    private YouTubePlayer.PlayerStateChangeListener playerStateChangeListener = new YouTubePlayer.PlayerStateChangeListener() {
        @Override
        public void onLoading() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlayerStateChangeListener - onLoading");
        }

        @Override
        public void onLoaded(String s) {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlayerStateChangeListener - onLoaded");
        }

        @Override
        public void onAdStarted() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlayerStateChangeListener - onAdStared");
        }

        @Override
        public void onVideoStarted() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlayerStateChangeListener - OnVideoStarted");
        }

        @Override
        public void onVideoEnded() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlayerStateChangeListener - OnVideoEnded");
        }

        @Override
        public void onError(YouTubePlayer.ErrorReason errorReason) {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlayerStateChangeListener - OnError");
        }
    };
    private YouTubePlayer.PlaybackEventListener mPlaybackEventListener = new YouTubePlayer.PlaybackEventListener() {
        @Override
        public void onPlaying() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlaybackEventListener - onPlaying");
        }

        @Override
        public void onPaused() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlaybackEventListener - onPaused");
        }

        @Override
        public void onStopped() {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlaybackEventListener - onStopped");
        }

        @Override
        public void onBuffering(boolean b) {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlaybackEventListener - onBuffering");
        }

        @Override
        public void onSeekTo(int i) {
            Log.i(DEBUG_TAG, "YouTubePlayer.PlaybackEventListener - onSeekTo");
        }
    };
    private YouTubePlayer.OnInitializedListener onInitializedListener = new YouTubePlayer.OnInitializedListener() {
        @Override
        public void onInitializationSuccess(YouTubePlayer.Provider provider, YouTubePlayer youTubePlayer, boolean b) {
            mYoutubePlayer = youTubePlayer;
            mYoutubePlayer.setPlayerStateChangeListener(playerStateChangeListener);
            mYoutubePlayer.setPlaybackEventListener(mPlaybackEventListener);
            mYoutubePlayer.loadVideo(mCurrentVideoId);
            mYoutubePlayer.play();
        }

        @Override
        public void onInitializationFailure(YouTubePlayer.Provider provider, YouTubeInitializationResult youTubeInitializationResult) {

        }
    };
    /**
     * Task to update timer
     */
    private Runnable updateTimer = new Runnable() {
        @Override
        public void run() {
            runOnUiThread(() -> mTvTimerRecord.setText(Utils.formatSeconds(recorderSecondsElapsed)));
            recorderSecondsElapsed++;
            handler.postDelayed(this, 1000);
        }
    };
    private View.OnClickListener onRecordClickListener = view -> onRecordButtonClick();

    private void switchRecordButton(boolean recording) {
        if (recording) {
            btnRecord.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.btn_stop_record));
        } else {
            btnRecord.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.btn_recording));
        }
    }

    /**
     * Handle record button click event
     */
    private void onRecordButtonClick() {
        if (!mRecorder.mIsRecording.get()) {
            mCurrentSavedFilename = mCurrentVideoTitle + ".wav";

            if (!Utils.isAvailableFilename(mCurrentSavedFilename)) {
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setMessage(getResources().getString(R.string.msg_filename_existed));
                builder.setNegativeButton("NO", (dialog, which) -> dialog.cancel());
                builder.setPositiveButton("YES", (dialog, which) -> new DeleteRecordedFileTask().execute());
                builder.create().show();
            } else {
                new PrepareRecordingTask().execute();
            }
        } else {
            Log.d(DEBUG_TAG, "Stop recording...");
            switchRecordButton(false);
            mRecorder.stop();
            mYoutubePlayer.pause();
//            mAudioDbmHandler.stopVisualizer();
            stopRecordingTimer();
            buildPostRecordDialog();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_kplay_video);

        ButterKnife.bind(this);

        // Initialzie recorder
        mRecorder = new KAudioRecord(this);

        mCurrentVideoTitle = getIntent().getStringExtra("title");
        mCurrentVideoId = getIntent().getStringExtra("videoid");

        setupViews();

        loadVideoComments();
    }

    private void loadVideoComments() {
        Observable<ResponseCommentThreads> obsComment = KApplication.rxYoutubeAPIService.getVideoComments(mCurrentVideoId);
        compositeSubscriptionForOnStop.add(obsComment.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseCommentThreads>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(KActivityPlayVideo.this, "Error getting comment", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(ResponseCommentThreads responseCommentThreads) {
                        handleCommentResults(responseCommentThreads);
                    }
                }));
    }

    private void handleCommentResults(ResponseCommentThreads responseCommentThreads) {
        if (responseCommentThreads.getCommentThreads().size() > 0) {
            toggleLoadingCommentLayout();

            mCurrentCommentPageToken = responseCommentThreads.getNextPageToken();
            if (mCurrentCommentPageToken == null || mCurrentCommentPageToken.isEmpty()) {
                mAdapterComment.setEndlessScroll(false);
                onScrollListener.setLoadMoreEnable(false);
            }
            mAdapterComment.addDataItems(responseCommentThreads.getCommentThreads());
        } else {
            Toast.makeText(this, "No comment.", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Initialize activity's view on create
     */
    private void setupViews() {

        setSupportActionBar(mToolbar);
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(mCurrentVideoTitle);

        //Setup youtube player view
        mYoutubePlayerFragment = (YouTubePlayerSupportFragment) getSupportFragmentManager().findFragmentById(R.id.youtube_player);
        mYoutubePlayerFragment.initialize(Constants.YOUTUBE_API_KEY, onInitializedListener);

        fabRecord.setOnClickListener(onRecordClickListener);
        btnRecord.setOnClickListener(onRecordClickListener);

//        mAudioRecordVisualization = new GLAudioVisualizationView.Builder(this)
//                .setLayersCount(1)
//                .setWavesCount(6)
//                .setWavesHeight(R.dimen.wave_height)
//                .setWavesFooterHeight(R.dimen.footer_height)
//                .setBubblesPerLayer(20)
//                .setBubblesSize(R.dimen.bubble_size)
//                .setBubblesRandomizeSize(true)
//                .setBackgroundColor(ContextCompat.getColor(this, R.color.colorPrimary))
//                .setLayerColors(new int[]{ContextCompat.getColor(this, R.color.colorAccentBlur)})
//                .build();
//        mAudioRecordVisualization.linkTo(mAudioDbmHandler);
//
//        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
//        layoutParams.addRule(RelativeLayout.BELOW, R.id.youtube_player);
//        layoutParams.addRule(RelativeLayout.SYSTEM_UI_FLAG_VISIBLE, View.INVISIBLE);
//        mContentLayout.addView(mAudioRecordVisualization, 1, layoutParams);

        //Setup comment view
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecyclerViewComment.setLayoutManager(layoutManager);
        mRecyclerViewComment.addItemDecoration(new InsetDividerDecoration(
                ViewHolderComment.class,
                getResources().getDimensionPixelSize(R.dimen.divider_height),
                getResources().getDimensionPixelSize(R.dimen.keyline_1),
                ContextCompat.getColor(this, R.color.divider_light)));
        mAdapterComment = new KAdapterComments(this);
//        Observable<ZingArtist> obsItemClickListener = mAdapter.onItemClickListener();
//        compositeSubscriptionForOnStop.add(obsItemClickListener.subscribe(this::handleArtistItemClick));
        mRecyclerViewComment.setAdapter(mAdapterComment);
        onScrollListener = new EndlessRecyclerViewScrollListener(layoutManager) {
            @Override
            public void onLoadMore(int page, int totalItemCount) {
                loadMoreComments(totalItemCount);
            }
        };
        mRecyclerViewComment.addOnScrollListener(onScrollListener);
    }

    private void loadMoreComments(int totalItemCount) {
        Observable<ResponseCommentThreads> obsCommentMore = KApplication.rxYoutubeAPIService.getVideoCommentsNext(mCurrentVideoId, mCurrentCommentPageToken);
        compositeSubscriptionForOnStop.add(obsCommentMore.subscribeOn(Schedulers.newThread())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(new Subscriber<ResponseCommentThreads>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {
                        Toast.makeText(KActivityPlayVideo.this, "Error getting more comment!", Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onNext(ResponseCommentThreads responseCommentThreads) {
                        mCurrentCommentPageToken = responseCommentThreads.getNextPageToken();
                        if (mCurrentCommentPageToken == null || mCurrentCommentPageToken.isEmpty()) {
                            mAdapterComment.setEndlessScroll(false);
                            onScrollListener.setLoadMoreEnable(false);
                        }
                        mAdapterComment.addDataItems(responseCommentThreads.getCommentThreads());
                    }
                }));
    }

    /**
     * Build a dialog that appear when Stop recording button is clicked
     */
    private void buildPostRecordDialog() {
        AlertDialog.Builder postRecDialgBuilder = new AlertDialog.Builder(this);

        LayoutInflater inflater = this.getLayoutInflater();
        View actionDialog = inflater.inflate(R.layout.dialog_post_recording, null);
        postRecDialgBuilder.setView(actionDialog);
        LinearLayout actionEditRecord = (LinearLayout) actionDialog.findViewById(R.id.action_edit_recording);
        actionEditRecord.setOnClickListener(view -> {
            Intent intent = new Intent(this, KActivityMyRecording.class);
            startActivity(intent);
            KActivityPlayVideo.this.finish();
        });
        postRecDialgBuilder.create().show();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.kactivity_play_video, menu);
        // Setup for favorite icon
        MenuItem favoriteMenu = menu.findItem(R.id.menu_favourite);

        mFavoriteState = mAppSharePrefs.isInFavoriteList(this, mCurrentVideoId);
        if (!mFavoriteState) {
            favoriteMenu.setIcon(R.drawable.drawable_menu_favourite);
        } else {
            favoriteMenu.setIcon(R.drawable.drawable_menu_favourite_added);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int menuId = item.getItemId();
        switch (menuId) {
            case R.id.menu_favourite:
                handleFavoriteClick(item);
                break;
        }

        return true;
    }

//    /**
//     * Preparing audio file (download from youtubeinmp3.com) of current Youtube video
//     */
//    private void prepareAudioFile() {
//        Intent intentDownloadAudio = new Intent(this, YoutubeAudioDownloadService.class);
//        intentDownloadAudio.putExtra("videoId", mCurrentVideoId);
//        intentDownloadAudio.putExtra("title", mCurrentVideoTitle);
//        startService(intentDownloadAudio);
//    }

//    private void showPreparingDownloadDialog() {
//        mProgressPrepare = new ProgressDialog(KActivityPlayVideo.this);
//        mProgressPrepare.setMessage("Preparing beat file...");
//        mProgressPrepare.show();
//    }

//    /**
//     * Show a progress dialog when starting downloading audio file
//     */
//    private void showDownloadProgressDialog() {
//        if (mProgressPrepare.isShowing()) {
//            mProgressPrepare.dismiss();
//        }
//        mProgressDownloadDialog = new ProgressDialog(KActivityPlayVideo.this);
//        mProgressDownloadDialog.setIndeterminate(false);
//        mProgressDownloadDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
//        mProgressDownloadDialog.setCancelable(false);
//        mProgressDownloadDialog.setMax(100);
//        mProgressDownloadDialog.setTitle("Downloading beat file");
//        mProgressDownloadDialog.setButton(DialogInterface.BUTTON_NEGATIVE, "Cancel", (dialogInterface, i) -> {
//        });
//        mProgressDownloadDialog.show();
//    }

//    /**
//     * Event : preparing to download beat file (waiting for server to convert youtube video into mp3 file)
//     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEventPreparingDownloadAudio(EventDownloadAudioPreparing event) {
//        showPreparingDownloadDialog();
//    }

//    /**
//     * Event : conversion is completed. Beat file is ready to be downloaded.
//     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEventStartDownloadAudio(EventDownloadAudioStart event) {
//        showDownloadProgressDialog();
//    }

//    /**
//     * Receive event download progress -> update progress dialog
//     *
//     * @param event : event object contains information about progress
//     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEventDownloadProgress(EventDownloadAudioProgress event) {
//        mProgressDownloadDialog.setProgress(event.getProgress());
//        if (event.getProgress() < 100) {
//            mProgressDownloadDialog.setTitle(String.format(Locale.ENGLISH, "Downloaded (%d/%d) MB", event.getDownloadedSize(), event.getTotalFileSize()));
//        }
//    }
//
//    /**
//     * Event: download beat file completed, start recording user's voice
//     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEventDownloadAudioCompleted(EventDownloadAudioCompleted event) {
//        mProgressDownloadDialog.setProgress(100);
//        mProgressDownloadDialog.setTitle("Finished preparing beat file successfully.");
//        mProgressDownloadDialog.dismiss();
//    }
//
//    /**
//     * Event indicate that has to load a webview to download beat file
//     */
//    @Subscribe(threadMode = ThreadMode.MAIN)
//    public void onEventDownloadAudioError(EventDownloadAudioError event) {
//        mProgressDownloadDialog.dismiss();
//        new AlertDialog.Builder(this)
//                .setTitle("Error")
//                .setMessage("Internal problem when preparing beat file. Please try again!")
//                .setPositiveButton("OK", (dialogInterface, i) -> dialogInterface.dismiss())
//                .show();
//    }

    /**
     * Handle favorite click event
     * If this video is already in the favorite list, remove it.
     * Otherwise, add it to the current favorite video list and change the icon of Favorite button
     *
     * @param favoriteMenuItem : menu item
     */
    private void handleFavoriteClick(MenuItem favoriteMenuItem) {
        boolean isInFavoriteList = mAppSharePrefs.isInFavoriteList(this, mCurrentVideoId);
        if (!isInFavoriteList) {// Not in the favorite list -> add it
            mAppSharePrefs.addFavorites(this, mCurrentVideoId);

            favoriteMenuItem.setIcon(R.drawable.drawable_menu_favourite_added);
            Toast.makeText(this, "Added to the favorite list", Toast.LENGTH_SHORT).show();
        } else {// Currently in favorite list -> remove it
            mAppSharePrefs.removeFavorite(this, mCurrentVideoId);

            favoriteMenuItem.setIcon(R.drawable.drawable_menu_favourite);
            Toast.makeText(this, "Removed to the favorite list", Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Event of running prepare recording countdown
     *
     * @param event
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventPrepareCountdownRunning(EventPrepareRecordingCountdown event) {
        int current = event.getCurrentValue();
        Log.d(DEBUG_TAG, "Countdown: value = " + current);

        if (current == 0) {
            mTvCountdown.setText("READY!!!");
        } else {
            mTvCountdown.setText(String.valueOf(current));
        }
    }

    /**
     * Start updating recording timer
     */
    private void startRecordingTimer() {
        handler.post(updateTimer);
    }

    /**
     * Stop updating recording timer
     */
    private void stopRecordingTimer() {
        handler.removeCallbacks(updateTimer);
    }

    /**
     * Callback to get sample when recording
     *
     * @param data : sample data
     */
    @Override
    public void onAudioRecordDataReceived(byte[] data) {
        /* TO-DO: handle recording sample data */
    }

    /**
     * Callback when recording get errors
     */
    @Override
    public void onAudioRecordError() {

    }

    /**
     * Start recording voice
     */
    private void startRecording() {
        if (mYoutubePlayer != null && !mYoutubePlayer.isPlaying()) {
//            mCurrentSavedFilename = Utils.getAutoFilename();
            switchRecordButton(true);

            /*Auto configure audio output volume (try to prevent too loud beat)*/
            configAudioVolume();

            mYoutubePlayer.play();
            mTvSavedFilename.setText(mCurrentSavedFilename);
//            mAudioRecordVisualization.setVisibility(View.VISIBLE);

            mRecorder.start(mCurrentSavedFilename);
            startRecordingTimer();
        }
    }

    /**
     * If user record directly, this lead to a problem that the beat volume is too loud and user's
     * voice volume is too low -> pre-configure audio volume, user may change later if they find it's
     * not suitable
     */
    private void configAudioVolume() {
        // This value's result maybe vary between devices
        // Just a start number, user may change volume if it's too loud or too small
        float percent = 0.4f;

        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
        int autoVol = (int) (maxVol * percent);
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, autoVol, 0);
    }

    @Override
    protected void onPause() {
        super.onPause();
//        mAudioRecordVisualization.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
//        mAudioRecordVisualization.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(DEBUG_TAG, "OnStop-------------------------");
        KApplication.Companion.getEventBus().unregister(this);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(DEBUG_TAG, "OnStart-------------------------");
        KApplication.Companion.getEventBus().register(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        compositeSubscriptionForOnStop.unsubscribe();
//        mAudioRecordVisualization.release();
    }

    @Override
    public void onBackPressed() {
        Log.d(DEBUG_TAG, "onBackPressed");

        boolean mCurrentState = mAppSharePrefs.isInFavoriteList(this, mCurrentVideoId);
        if (mCurrentState != mFavoriteState) {
            // User change state of favorite
            Intent updateFavoriteList = new Intent();
            updateFavoriteList.putExtra("video_id", mCurrentVideoId);
            setResult(Activity.RESULT_OK, updateFavoriteList);
        }
        super.onBackPressed();
    }

    private void toggleLoadingCommentLayout() {
        if (mProgressBarComment.getVisibility() == View.VISIBLE) {
            mProgressBarComment.setVisibility(View.GONE);
            mRecyclerViewComment.setVisibility(View.VISIBLE);
        } else {
            mProgressBarComment.setVisibility(View.VISIBLE);
            mRecyclerViewComment.setVisibility(View.GONE);
        }
    }

    private void toggleLayoutPlayRecord() {
        if (mLayoutPlayVideo.getVisibility() == View.VISIBLE) {
            mLayoutPlayVideo.setVisibility(View.GONE);
            mLayoutRecord.setVisibility(View.VISIBLE);
        } else {
            mLayoutPlayVideo.setVisibility(View.VISIBLE);
            mLayoutRecord.setVisibility(View.GONE);
        }
    }

    /**
     * Display a prepare screen before starting recording
     */
    private class PrepareRecordingTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (mYoutubePlayer != null) {
                if (mYoutubePlayer.isPlaying()) {
                    mYoutubePlayer.pause();
                }
                mYoutubePlayer.seekToMillis(0);
            }

            mLayoutCountdown.setVisibility(View.VISIBLE);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 3; i >= 0; --i) {
                Log.d(DEBUG_TAG, "Starting countdown: current = " + i);
                KApplication.Companion.getEventBus().post(new EventPrepareRecordingCountdown(i));
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);

            mLayoutCountdown.setVisibility(View.INVISIBLE);
            toggleLayoutPlayRecord();

            // Start recording voice
            startRecording();
        }
    }

    /**
     * Task to delete existed recorded file before starting recording
     */
    private class DeleteRecordedFileTask extends AsyncTask<Void, Void, Boolean> {

        ProgressDialog deleteProgessDialog = new ProgressDialog(KActivityPlayVideo.this);

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            deleteProgessDialog.setMessage("Deleting old recorded file...");
            deleteProgessDialog.setIndeterminate(true);
            deleteProgessDialog.show();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            File recordFileDir = new File(KApplication.Companion.getRECORDING_DIRECTORY_URI());
            File recordedFile = new File(recordFileDir, mCurrentSavedFilename);

            return recordedFile.exists() && recordedFile.delete();
        }

        @Override
        protected void onPostExecute(Boolean result) {
            super.onPostExecute(result);

            if (result) {
                deleteProgessDialog.dismiss();
                new PrepareRecordingTask().execute();
            } else {
                Toast.makeText(KActivityPlayVideo.this, getResources().getString(R.string.toast_delete_file_error), Toast.LENGTH_SHORT).show();
            }
        }
    }
}
