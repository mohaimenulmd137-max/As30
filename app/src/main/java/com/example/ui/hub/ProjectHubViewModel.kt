package com.example.ui.hub

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ProjectRepository
import com.example.data.local.StudioDatabase
import com.example.data.local.StudioPreferences
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.StudioProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectHubViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ProjectRepository(StudioDatabase.getInstance(application).projectDao())
    private val preferences = StudioPreferences(application)

    val projects: StateFlow<List<StudioProject>> = repository.projectsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isBanglaUi: StateFlow<Boolean> = preferences.isBanglaUi
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        viewModelScope.launch {
            repository.ensureStarterProjectsSeeded()
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleLanguage() {
        viewModelScope.launch {
            preferences.setBanglaUi(!isBanglaUi.value)
        }
    }

    fun createBlankProject(
        name: String,
        aspectRatio: AspectRatioPreset,
        onCreated: (String) -> Unit
    ) {
        viewModelScope.launch {
            val created = repository.createBlankProject(
                name = name.ifBlank { "New Studio Project" },
                aspectRatio = aspectRatio
            )
            onCreated(created.id)
        }
    }

    fun createFromTemplate(templateId: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val created = repository.createFromTemplate(templateId)
            onCreated(created.id)
        }
    }

    fun createAutoDocumentaryProject(topic: String, scriptNote: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val doc = com.example.domain.model.PresetCatalog.buildAutoInvestigativeDocumentary(topic, scriptNote)
            repository.saveProject(doc)
            onCreated(doc.id)
        }
    }

    fun renameProject(project: StudioProject, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            repository.saveProject(project.copy(name = newName.trim()))
        }
    }

    fun duplicateProject(project: StudioProject) {
        viewModelScope.launch {
            repository.duplicateProject(project)
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            repository.deleteProject(projectId)
        }
    }
}
