package vn.com.frankle.karaokelover;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.res.Configuration;
import android.graphics.Point;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewPager;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;

import com.pushtorefresh.storio.sqlite.StorIOSQLite;
import com.viewpagerindicator.CirclePageIndicator;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.util.List;

import javax.inject.Inject;

import butterknife.BindView;
import butterknife.ButterKnife;
import cn.yangbingqiang.android.parallaxviewpager.ParallaxViewPager;
import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;
import vn.com.frankle.karaokelover.database.entities.ArtistWithKaraoke;
import vn.com.frankle.karaokelover.database.entities.DAOArtist;
import vn.com.frankle.karaokelover.database.entities.DAOHotTrend;
import vn.com.frankle.karaokelover.database.tables.ArtistTable;
import vn.com.frankle.karaokelover.database.tables.HotTrendTable;
import vn.com.frankle.karaokelover.events.EventFinishLoadingHotTrendAndArtist;
import vn.com.frankle.karaokelover.models.ResponseYoutubeSnippetStatistics;
import vn.com.frankle.karaokelover.presenters.KHotArtistAdapter;
import vn.com.frankle.karaokelover.presenters.KPagerAdapterHotKaraokeSong;
import vn.com.frankle.karaokelover.services.ReactiveHelper;
import vn.com.frankle.karaokelover.views.SpaceItemDecoration;

public class KActivity_home extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private final String ACTIVITY_NAME = this.getClass().getSimpleName();

    // In this sample app we use dependency injection (DI) to keep the code clean
    // Just remember that it's already configured instance of StorIOSQLite from DbModule
    @Inject
    StorIOSQLite storIOSQLite;

    @NonNull
    private final CompositeSubscription compositeSubscriptionForOnStop = new CompositeSubscription();

    private int mPhysicScreenWidthInDp;
    private int mPhyScreenWidthInPixel;

    @BindView(R.id.layout_main_activity_content)
    LinearLayout mLayoutMainContent;
    @BindView(R.id.cover_container_viewpager)
    ParallaxViewPager mCoverContainer;
    @BindView(R.id.cover_viewpager_indicator)
    CirclePageIndicator mViewpagerIndicator;
    @BindView(R.id.recycleview_hot_artists)
    RecyclerView mRecycleViewHotArtists;
    @BindView(R.id.progressbar_hot_artist)
    ProgressBar mProgressBarHotArtist;
    @BindView(R.id.toolbar)
    Toolbar mToolbar;
    @BindView(R.id.drawer_layout)
    DrawerLayout mDrawerLayout;

    private KPagerAdapterHotKaraokeSong mHotKaraokePagerAdapter;
    private KHotArtistAdapter mHotArtistAdapter = new KHotArtistAdapter(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.layout_activity_home);
        ButterKnife.bind(this);

        KApplication.get(this).appComponent().inject(this);

        // Get screen width in dp
        Configuration configuration = this.getResources().getConfiguration();
        mPhysicScreenWidthInDp = configuration.screenWidthDp; //The current width of the available screen space, in dp units, corresponding to screen width resource qualifier.
        // Get screen width in pixel
        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        mPhyScreenWidthInPixel = size.x;

        setSupportActionBar(mToolbar);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, mDrawerLayout, mToolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        mDrawerLayout.addDrawerListener(toggle);
        toggle.syncState();

        setUpNavigationView();

        setUpViews();

        retrieveHotTrendAndArtistsKaraokes();
    }

    /**
     * Get observable for hot karaoke trends
     *
     * @return
     */
    private Observable<List<ResponseYoutubeSnippetStatistics>> getObservableHotTrend() {
        return storIOSQLite
                .get()
                .listOfObjects(DAOHotTrend.class)
                .withQuery(HotTrendTable.QUERY_ALL)
                .prepare()
                .asRxObservable()
                .subscribeOn(Schedulers.newThread())
                .concatMap(ReactiveHelper::getObsListHotTrend);
    }

    /**
     * Get observable for list of host artists and their karaokes that will be displayed on Home screen
     */
    private Observable<List<ArtistWithKaraoke>> getObservableHotArtistsListWithKaraokes() {
        return storIOSQLite
                .get()
                .listOfObjects(DAOArtist.class)
                .withQuery(ArtistTable.QUERY_ALL)
                .prepare()
                .asRxObservable()
                .subscribeOn(Schedulers.newThread())
                .concatMap(daoArtists -> ReactiveHelper.getListHotArtistWithKarokes(storIOSQLite, daoArtists))
                .concatMap(ReactiveHelper::getListHotKaraokeOfArtist);
    }

    private void retrieveHotTrendAndArtistsKaraokes() {
        Observable<EventFinishLoadingHotTrendAndArtist> networkRequest = Observable.zip(getObservableHotTrend(),
                getObservableHotArtistsListWithKaraokes(), (responseYoutubeSnippetStatisticses, artistWithKaraokes) -> {
                    Log.i(ACTIVITY_NAME, "Finish loading hot trend karaokes and hot artists with their karaokes");
                    return new EventFinishLoadingHotTrendAndArtist(responseYoutubeSnippetStatisticses, artistWithKaraokes);
                });
        // Preventing memory leak (other Observables: Put, Delete emit result once so memory leak won't live long)
        // Because rx.Observable from Get Operation is endless (it watches for changes of tables from query)
        // You can easily create memory leak (in this case you'll leak the Fragment and all it's fields)
        // So please, PLEASE manage your subscriptions
        // We suggest same mechanism via storing all subscriptions that you want to unsubscribe
        // In something like CompositeSubscription and unsubscribe them in appropriate moment of component lifecycle
        compositeSubscriptionForOnStop.add(networkRequest.subscribe(eventFinishLoadingHotTrendAndArtist ->
                KApplication.eventBus.post(eventFinishLoadingHotTrendAndArtist)));
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register eventbus (unregister onDestroy)
        KApplication.eventBus.register(this);
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Unregister eventBus
        KApplication.eventBus.unregister(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        compositeSubscriptionForOnStop.unsubscribe();
    }

    private void setUpViews() {
        // Set up viewpager of hot song content to aspect ration of 16:9
        mCoverContainer.getLayoutParams().height = mPhyScreenWidthInPixel * 9 / 16;

        mRecycleViewHotArtists.setAdapter(mHotArtistAdapter);
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(LinearLayoutManager.VERTICAL);
        mRecycleViewHotArtists.setHasFixedSize(true);
        mRecycleViewHotArtists.setLayoutManager(layoutManager);
        mRecycleViewHotArtists.addItemDecoration(new SpaceItemDecoration(Utils.convertDpToPixel(this, 16), SpaceItemDecoration.VERTICAL));
        setHotArtistLoadingState(true);
    }

    private void setUpNavigationView() {
        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        /**
         * Navigation Drawer width is the minimum (NavDrawerMaxWidth , (ScreenSize — ActionBarSize))
         * where NavDrawerMaxWidth is 320 dp for phones and 400 dp for tablets.
         **/
        navigationView.getLayoutParams().width = Utils.convertDpToPixel(this, Math.min(320, mPhysicScreenWidthInDp - 56));
        navigationView.setNavigationItemSelectedListener(this);
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.kactivity_home, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_search) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(MenuItem item) {
        // Handle navigation view item clicks here.
        int id = item.getItemId();

        if (id == R.id.nav_camera) {
            // Handle the camera action
        } else if (id == R.id.nav_gallery) {

        } else if (id == R.id.nav_slideshow) {

        } else if (id == R.id.nav_manage) {

        } else if (id == R.id.nav_share) {

        } else if (id == R.id.nav_send) {

        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /**
     * ---------------------------------------EVENTs HANDLING-------------------------------------
     */
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onEventFinishLoadingHotTrendAndArtistsKaraokes(EventFinishLoadingHotTrendAndArtist event) {
        mHotKaraokePagerAdapter = new KPagerAdapterHotKaraokeSong(getSupportFragmentManager(), event.getListHotTrendKaraokes());
        mCoverContainer.setAdapter(mHotKaraokePagerAdapter);
        mCoverContainer.setOffscreenPageLimit(4);
        mViewpagerIndicator.setViewPager(mCoverContainer);

        mHotArtistAdapter.updateAdapterData(event.getListHotArtistWithKaraokes());
        setHotArtistLoadingState(false);
    }
    /**-----------------------------------END OF EVENTs HANDLING----------------------------------*/

    /**
     * Switch visiblity of ProgressBar and HotArtist RecyclerView
     *
     * @param loading : true if display progressbar
     */
    private void setHotArtistLoadingState(boolean loading) {
        if (loading) {
            mProgressBarHotArtist.setVisibility(View.VISIBLE);
        } else {
            mProgressBarHotArtist.animate()
                    .alpha(0.0f)
                    .setDuration(500)
                    .setListener(new AnimatorListenerAdapter() {
                        @Override
                        public void onAnimationEnd(Animator animation) {
                            super.onAnimationEnd(animation);
                            mProgressBarHotArtist.setVisibility(View.GONE);
                        }
                    });
        }
    }
}