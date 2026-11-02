package com.dzire.eduseva360.features.attendance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dzire.eduseva360.features.students.Student
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AttendanceViewModel @Inject constructor(
    private val repository: AttendanceRepository
) : ViewModel() {
    val attendanceDate: String = LocalDate.now().toString()
    val records: StateFlow<List<AttendanceRecord>> = repository.observeForDate(attendanceDate).stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList()
    )
    private val _message = MutableStateFlow<String?>(null)
    val message = _message.asStateFlow()
    private val _saving = MutableStateFlow(false)
    val saving = _saving.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            runCatching { repository.refreshFromCloud(attendanceDate) }
                .onFailure { _message.value = it.message ?: "Unable to refresh attendance." }
        }
    }

    fun save(students: List<Student>, selected: Map<String, AttendanceStatus>) {
        viewModelScope.launch {
            _saving.value = true
            _message.value = null
            var saved = 0
            var failure: String? = null
            for (student in students) {
                val status = selected[student.id] ?: continue
                runCatching { repository.save(student, attendanceDate, status) }
                    .onSuccess { saved++ }
                    .onFailure { failure = it.message ?: "Some attendance records could not sync." }
            }
            _message.value = if (failure == null) "Saved attendance for $saved student(s)." else "Saved $saved record(s). $failure"
            _saving.value = false
        }
    }
}
