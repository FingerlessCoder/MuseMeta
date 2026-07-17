package com.mymusicplayer.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.AlbumEntity
import com.mymusicplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TrackDaoTest {

    private lateinit var database: AppDatabase
    private lateinit var trackDao: TrackDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AppDatabase::class.java
        ).build()
        trackDao = database.trackDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun insertAndRetrieveTrack() = runBlocking {
        val track = TrackEntity(
            title = "Test Song",
            filePath = "/music/test.mp3",
            duration = 200000
        )
        val id = trackDao.insertTrack(track)
        val retrieved = trackDao.getTrackById(id).first()
        assertNotNull(retrieved)
        assertEquals("Test Song", retrieved!!.title)
    }

    @Test
    fun getAllTracksReturnsAllInserted() = runBlocking {
        val track1 = TrackEntity(title = "Song A", filePath = "/a.mp3")
        val track2 = TrackEntity(title = "Song B", filePath = "/b.mp3")
        trackDao.insertTrack(track1)
        trackDao.insertTrack(track2)

        val tracks = trackDao.getAllTracks("name").first()
        assertEquals(2, tracks.size)
    }

    @Test
    fun deleteTrackRemovesIt() = runBlocking {
        val track = TrackEntity(title = "To Delete", filePath = "/delete.mp3")
        val id = trackDao.insertTrack(track)
        trackDao.deleteTrackById(id)

        val retrieved = trackDao.getTrackById(id).first()
        assertNull(retrieved)
    }

    @Test
    fun searchTracksByTitle() = runBlocking {
        val track1 = TrackEntity(title = "Hello World", filePath = "/hello.mp3")
        val track2 = TrackEntity(title = "Goodbye World", filePath = "/bye.mp3")
        trackDao.insertTrack(track1)
        trackDao.insertTrack(track2)

        val results = trackDao.searchTracks("Hello").first()
        assertEquals(1, results.size)
        assertEquals("Hello World", results[0].title)
    }

    @Test
    fun incrementPlayCountUpdatesCount() = runBlocking {
        val track = TrackEntity(title = "Count Test", filePath = "/count.mp3")
        val id = trackDao.insertTrack(track)

        trackDao.incrementPlayCount(id)
        trackDao.incrementPlayCount(id)

        val retrieved = trackDao.getTrackById(id).first()
        assertEquals(2, retrieved!!.playCount)
    }

    @Test
    fun updateRatingChangesRating() = runBlocking {
        val track = TrackEntity(title = "Rating Test", filePath = "/rating.mp3")
        val id = trackDao.insertTrack(track)

        trackDao.updateRating(id, 4)

        val retrieved = trackDao.getTrackById(id).first()
        assertEquals(4, retrieved!!.rating)
    }

    @Test
    fun getTrackIdByPathReturnsCorrectId() = runBlocking {
        val track = TrackEntity(title = "Path Test", filePath = "/unique/path.mp3")
        val id = trackDao.insertTrack(track)

        val foundId = trackDao.getTrackIdByPath("/unique/path.mp3")
        assertEquals(id, foundId)
    }

    @Test
    fun getTrackIdByPathReturnsNullForMissing() = runBlocking {
        val foundId = trackDao.getTrackIdByPath("/nonexistent.mp3")
        assertNull(foundId)
    }

    @Test
    fun filterTracksBySize() = runBlocking {
        val small = TrackEntity(title = "Small", filePath = "/s.mp3", fileSize = 1000)
        val large = TrackEntity(title = "Large", filePath = "/l.mp3", fileSize = 1000000)
        trackDao.insertTrack(small)
        trackDao.insertTrack(large)

        val results = trackDao.filterTracks(minSize = 50000).first()
        assertEquals(1, results.size)
        assertEquals("Large", results[0].title)
    }

    @Test
    fun deleteRemovedTracksCleansUp() = runBlocking {
        val keep = TrackEntity(title = "Keep", filePath = "/keep.mp3")
        val remove = TrackEntity(title = "Remove", filePath = "/remove.mp3")
        trackDao.insertTrack(keep)
        trackDao.insertTrack(remove)

        trackDao.deleteRemovedTracks(listOf("/keep.mp3"))

        val all = trackDao.getAllTracks("name").first()
        assertEquals(1, all.size)
        assertEquals("Keep", all[0].title)
    }
}
