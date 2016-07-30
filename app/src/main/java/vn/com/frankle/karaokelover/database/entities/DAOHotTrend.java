package vn.com.frankle.karaokelover.database.entities;

import android.support.annotation.NonNull;

import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteColumn;
import com.pushtorefresh.storio.sqlite.annotations.StorIOSQLiteType;

import vn.com.frankle.karaokelover.database.tables.ArtistTable;
import vn.com.frankle.karaokelover.database.tables.HotTrendTable;
import vn.com.frankle.karaokelover.database.tables.KaraokeTable;

/**
 * Created by duclm on 7/28/2016.
 */
@StorIOSQLiteType(table = HotTrendTable.TABLE)
public class DAOHotTrend {
    /**
     * If object was not inserted into db, id will be null
     */
    @StorIOSQLiteColumn(name = HotTrendTable.COLUMN_ID, key = true)
    int id;

    @StorIOSQLiteColumn(name = HotTrendTable.COLUMN_VIDEOID)
    String video_id;

    // leave default constructor for AutoGenerated code!
    DAOHotTrend() {
    }

    private DAOHotTrend(int id, @NonNull String video_id) {
        this.id = id;
        this.video_id = video_id;
    }

    @NonNull
    public static DAOHotTrend newHotTrend(int id, @NonNull String video_id) {
        return new DAOHotTrend(id, video_id);
    }

    public String getVideoId() {
        return video_id;
    }

    public void setVideoId(String video_id) {
        this.video_id = video_id;
    }
}