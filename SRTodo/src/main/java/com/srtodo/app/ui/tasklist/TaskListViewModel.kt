package com.srtodo.app.ui.tasklist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.srtodo.app.SRTodoApplication
import com.srtodo.app.data.Task
import com.srtodo.app.data.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskListViewModel(private val repository: TaskRepository) : ViewModel() {

    val pending: StateFlow<List<Task>> = repository.observePending()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val completed: StateFlow<List<Task>> = repository.observeCompleted()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun add(title: String, dueAt: Long?) = launch { repository.add(title.trim(), dueAt) }

    fun edit(id: Long, title: String, dueAt: Long?) = launch { repository.edit(id, title.trim(), dueAt) }

    fun setDone(id: Long, done: Boolean) = launch {
        if (done) repository.complete(id) else repository.uncomplete(id)
    }

    fun delete(id: Long) = launch { repository.delete(id) }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as SRTodoApplication
                TaskListViewModel(app.container.taskRepository)
            }
        }
    }
}
