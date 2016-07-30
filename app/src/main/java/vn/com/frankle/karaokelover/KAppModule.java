package vn.com.frankle.karaokelover;

import android.content.Context;
import android.support.annotation.NonNull;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;

@Module
public class KAppModule {

    @NonNull
    private final KApplication app;

    KAppModule(@NonNull KApplication app) {
        this.app = app;
    }

    @Provides
    @NonNull
    @Singleton
    Context provideContext() {
        return app;
    }
}
