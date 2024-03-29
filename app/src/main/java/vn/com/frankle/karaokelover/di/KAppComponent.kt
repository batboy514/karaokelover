package vn.com.frankle.karaokelover.di

import com.droidcba.kedditbysteps.di.AppModule
import com.pushtorefresh.storio.sqlite.StorIOSQLite
import dagger.Component
import vn.com.frankle.karaokelover.activities.KActivityPlayVideo
import vn.com.frankle.karaokelover.activities.KActivitySearch
import vn.com.frankle.karaokelover.database.entities.VideoSearchItem
import vn.com.frankle.karaokelover.fragments.KFragmentFavorite
import vn.com.frankle.karaokelover.fragments.KFragmentHome
import javax.inject.Singleton

@Singleton
@Component(modules = arrayOf(
        AppModule::class,
        DatabaseModule::class)
)
interface KAppComponent {
    fun inject(fragmentHome: KFragmentHome)
    fun inject(fragmentFavorite: KFragmentFavorite)
    fun inject(activitySearch: KActivitySearch)
    fun inject(videoSearchItem: VideoSearchItem)
    fun inject(activityPlayVideo: KActivityPlayVideo)

    fun storIOSQLite(): StorIOSQLite
}
