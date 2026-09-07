package com.fishking.core.usecase

import com.fishking.core.model.DailyReview
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

interface DailyReviewRepository {
    /** Live, read-only source used by the journal footer. No summary rows are persisted. */
    fun observe(date: LocalDate): Flow<DailyReview>
}
