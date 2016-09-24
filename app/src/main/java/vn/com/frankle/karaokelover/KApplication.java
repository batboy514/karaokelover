package vn.com.frankle.karaokelover;

import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.greenrobot.eventbus.EventBus;

import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.gson.GsonConverterFactory;
import rx.schedulers.Schedulers;
import vn.com.frankle.karaokelover.database.DbModule;
import vn.com.frankle.karaokelover.services.YoutubeAPIEndpointInterface;
import vn.com.frankle.karaokelover.services.YoutubeAudioMp3APIInterface;
import vn.com.frankle.karaokelover.services.ZingMp3APIEndpointInterface;

/**
 * Created by duclm on 7/18/2016.
 */

public class KApplication extends Application {

    public static final String BASE_URL = "http://192.168.0.2:8080/";
    public static final String YOUTUBE_BASE_URL = "https://www.googleapis.com/youtube/v3/";
    public static final String YOUTUBE_MP3_AUDIO_URL = "http://www.youtubeinmp3.com/";
    public static final String ZING_MP3_BASE_URL = "http://api.mp3.zing.vn/api/";
    public static final String RECORDING_DIRECTORY_URI = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Karaoke Lover/";

    private static Context context;

    public static EventBus eventBus;

    private Retrofit rxYoutubeAPI;
    private Retrofit rxAudioMP3API;
    private Retrofit rxZingMp3API;
    private Retrofit youtubeInMp3;

    public static YoutubeAPIEndpointInterface rxYoutubeAPIService;
    public static YoutubeAudioMp3APIInterface youtubeInMp3APIService;
    public static ZingMp3APIEndpointInterface rxZingMp3APIService;

    @Nullable
    private volatile KAppComponent appComponent;

    @Override
    public void onCreate() {
        super.onCreate();
        KApplication.context = getApplicationContext();

        eventBus = new EventBus();

        OkHttpClient.Builder builder = new OkHttpClient().newBuilder();
        builder.readTimeout(10, TimeUnit.SECONDS);
        builder.connectTimeout(10, TimeUnit.SECONDS);

        HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor();
        interceptor.setLevel(HttpLoggingInterceptor.Level.BASIC);
        builder.addInterceptor(interceptor);
        OkHttpClient client = builder.build();

        rxYoutubeAPI = new Retrofit.Builder()
                .client(client)
                .baseUrl(YOUTUBE_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .build();
        rxYoutubeAPIService = rxYoutubeAPI.create(YoutubeAPIEndpointInterface.class);

        rxZingMp3API = new Retrofit.Builder()
                .client(client)
                .baseUrl(ZING_MP3_BASE_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .addCallAdapterFactory(RxJavaCallAdapterFactory.createWithScheduler(Schedulers.io()))
                .build();
        rxZingMp3APIService = rxZingMp3API.create(ZingMp3APIEndpointInterface.class);

        rxAudioMP3API = new Retrofit.Builder()
                .client(client)
                .baseUrl(YOUTUBE_MP3_AUDIO_URL)
                .build();
        youtubeInMp3APIService = rxAudioMP3API.create(YoutubeAudioMp3APIInterface.class);
    }

    @NonNull
    public static KApplication get(@NonNull Context context) {
        return (KApplication) context.getApplicationContext();
    }

    public static YoutubeAPIEndpointInterface getRxYoutubeAPIService() {
        return rxYoutubeAPIService;
    }

    public static YoutubeAudioMp3APIInterface getYoutubeInMp3APIService() {
        return youtubeInMp3APIService;
    }

    public static ZingMp3APIEndpointInterface getRxZingMp3APIService() {
        return rxZingMp3APIService;
    }

    @NonNull
    public KAppComponent appComponent() {
        if (appComponent == null) {
            synchronized (KApplication.class) {
                if (appComponent == null) {
                    appComponent = createAppComponent();
                }
            }
        }

        //noinspection ConstantConditions
        return appComponent;
    }

    @NonNull
    private KAppComponent createAppComponent() {
        return DaggerKAppComponent
                .builder()
                .kAppModule(new KAppModule(this))
                .dbModule(new DbModule())
                .build();
    }
}
