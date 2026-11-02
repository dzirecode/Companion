package com.dzire.eduseva360.features.students

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class StudentViewModel @Inject constructor(
    private val repository: StudentRepository
) : ViewModel() {
    val students: StateFlow<List<Student>> = repository.students.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )
    val syncStatus: StateFlow<String> = repository.syncStatus
    private val _operationMessage = MutableStateFlow<String?>(null)
    val operationMessage = _operationMessage.asStateFlow()
    fun clearOperationMessage() { _operationMessage.value = null }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refreshFromCloud() }
    }

    fun save(student: Student) {
        viewModelScope.launch {
            _operationMessage.value = null
            runCatching { repository.save(student) }
                .onSuccess { _operationMessage.value = "Student saved successfully." }
                .onFailure { _operationMessage.value = it.message ?: "Unable to save student." }
        }
    }
}
