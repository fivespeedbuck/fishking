package com.fishking.core.usecase

import com.fishking.core.database.FishKingDatabase

class FishKingContainer(database: FishKingDatabase) {
    val homeRepository: HomeRepository = RoomHomeRepository(database)
    val habitRepository: HabitRepository = RoomHabitRepository(database)
    val lifeRepository: LifeRepository = RoomLifeRepository(database, homeRepository)
    val journalRepository: JournalRepository = RoomJournalRepository(database)
    val tagRepository: TagRepository = RoomTagRepository(database)
    val dailyReviewRepository: DailyReviewRepository = RoomDailyReviewRepository(
        homeRepository = homeRepository,
        habitRepository = habitRepository,
    )
}
