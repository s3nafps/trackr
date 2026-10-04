package com.trackr.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.trackr.app.data.repository.ListRepository
import com.trackr.app.domain.util.YearInReview
import com.trackr.app.domain.util.YearInReviewCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.Year
import javax.inject.Inject

data class WrappedUiState(
    /** Years to pick from, newest first; the current year is always offered. */
    val years: List<Int> = emptyList(),
    val review: YearInReview? = null,
)

@HiltViewModel
class WrappedViewModel @Inject constructor(lists: ListRepository) : ViewModel() {
    private val selected = MutableStateFlow<Int?>(null)

    val state: StateFlow<WrappedUiState> = combine(lists.entries, selected) { entries, pick ->
        val current = Year.now().value
        val years = (YearInReviewCalculator.years(entries) + current).distinct().sortedDescending()
        WrappedUiState(years, YearInReviewCalculator.compute(entries, pick ?: current))
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WrappedUiState())

    fun select(year: Int) {
        selected.value = year
    }
}
