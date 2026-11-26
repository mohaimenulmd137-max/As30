package com.example.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.studioDataStore by preferencesDataStore(name = "chitraflow_prefs")

class StudioPreferences(private val context: Context) {
    private val keyBanglaUi = booleanPreferencesKey("is_bangla_ui")

    val isBanglaUi: Flow<Boolean> = context.studioDataStore.data.map { prefs ->
        prefs[keyBanglaUi] ?: true
    }

    suspend fun setBanglaUi(enabled: Boolean) {
        context.studioDataStore.edit { it[keyBanglaUi] = enabled }
    }
}

class ProjectRepository(private val dao: ProjectDao) {

    val projectsFlow: Flow<List<StudioProject>> = dao.observeAllProjects().map { list ->
        list.map { ProjectSerializer.fromEntity(it) }
    }

    suspend fun ensureStarterProjectsSeeded() {
        if (dao.countProjects() == 0) {
            PresetCatalog.studioTemplates.forEachIndexed { index, tpl ->
                val proj = tpl.buildProject().copy(
                    lastEditedAt = System.currentTimeMillis() - (index * 3_600_000L)
                )
                dao.upsertProject(ProjectSerializer.toEntity(proj))
            }
        }
    }

    suspend fun getProject(id: String): StudioProject? {
        return dao.getProjectById(id)?.let { ProjectSerializer.fromEntity(it) }
    }

    suspend fun saveProject(project: StudioProject) {
        val updated = project.copy(lastEditedAt = System.currentTimeMillis())
        dao.upsertProject(ProjectSerializer.toEntity(updated))
    }

    suspend fun createBlankProject(name: String, aspectRatio: AspectRatioPreset): StudioProject {
        val project = StudioProject(
            id = UUID.randomUUID().toString(),
            name = name,
            aspectRatio = aspectRatio,
            thumbnailDrawable = "img_studio_hero",
            totalDurationMs = 15_000L
        )
        dao.upsertProject(ProjectSerializer.toEntity(project))
        return project
    }

    suspend fun createFromTemplate(templateId: String): StudioProject {
        val tpl = PresetCatalog.studioTemplates.firstOrNull { it.id == templateId }
            ?: PresetCatalog.studioTemplates.first()
        val project = tpl.buildProject().copy(
            id = UUID.randomUUID().toString(),
            lastEditedAt = System.currentTimeMillis()
        )
        dao.upsertProject(ProjectSerializer.toEntity(project))
        return project
    }

    suspend fun duplicateProject(project: StudioProject): StudioProject {
        val copy = project.copy(
            id = UUID.randomUUID().toString(),
            name = "${project.name} (Copy)",
            lastEditedAt = System.currentTimeMillis()
        )
        dao.upsertProject(ProjectSerializer.toEntity(copy))
        return copy
    }

    suspend fun deleteProject(id: String) {
        dao.deleteProjectById(id)
    }
}
