package com.mymusicplayer.domain.usecase

import com.mymusicplayer.data.db.dao.TrackDao
import com.mymusicplayer.data.db.entity.TrackEntity
import kotlinx.coroutines.flow.Flow

class SmartSortUseCase constructor(
    private val trackDao: TrackDao
) {
    operator fun invoke(): Flow<List<TrackEntity>> {
        val now = System.currentTimeMillis()
        val decayDays = 86_400_000L * 30
        return trackDao.getSmartSortedTracks(
            playCountWeight = 0.4,
            recencyWeight = 0.35,
            ratingWeight = 0.25,
            now = now,
            decayDays = decayDays
        )
    }
}
