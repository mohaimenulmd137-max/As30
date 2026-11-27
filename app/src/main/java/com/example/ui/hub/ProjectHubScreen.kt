package com.example.ui.hub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.engine.VideoExportEngine
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.PresetCatalog
import com.example.domain.model.StudioProject
import com.example.ui.theme.AmberDocumentary
import com.example.ui.theme.CyanPlayhead
import com.example.ui.theme.EmeraldAudio
import com.example.ui.theme.StudioBorder
import com.example.ui.theme.StudioObsidian
import com.example.ui.theme.StudioSurface
import com.example.ui.theme.StudioSurfaceElevated
import com.example.ui.theme.StudioSurfaceHigh
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ProjectHubScreen(
    viewModel: ProjectHubViewModel,
    onOpenProject: (String) -> Unit
) {
    val projects by viewModel.projects.collectAsStateWithLifecycle()
    val isBangla by viewModel.isBanglaUi.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()

    var showNewProjectDialog by remember { mutableStateOf(false) }
    var showAutoDocDialog by remember { mutableStateOf(false) }
    var showGuideDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<StudioProject?>(null) }

    val filteredProjects = remember(projects, searchQuery) {
        if (searchQuery.isBlank()) projects
        else projects.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StudioObsidian),
        contentAlignment = Alignment.TopCenter
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentPadding = PaddingValues(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // 1. Atmospheric Studio Hero Header
            item {
                StudioHeroHeader(
                    isBangla = isBangla,
                    projectCount = projects.size,
                    onToggleLanguage = { viewModel.toggleLanguage() },
                    onOpenGuide = { showGuideDialog = true },
                    onNewBlankProject = { showNewProjectDialog = true },
                    onAutoDocumentary = { showAutoDocDialog = true }
                )
            }

            // 2. Graphic & Documentary Templates
            item {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AmberDocumentary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isBangla) "প্রি-সেট গ্রাফিক ও ডকুমেন্টারি টেমপ্লেট" else "Motion & Documentary Templates",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(PresetCatalog.studioTemplates, key = { it.id }) { tpl ->
                            TemplateCard(
                                template = tpl,
                                isBangla = isBangla,
                                onClick = {
                                    viewModel.createFromTemplate(tpl.id) { newId ->
                                        onOpenProject(newId)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // 3. Search & Drafts Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (isBangla) "সেভড প্রজেক্ট ও ড্রাফট (${filteredProjects.size})" else "Project Gallery & Drafts (${filteredProjects.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary
                        )
                        Surface(
                            color = StudioSurfaceElevated,
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldAudio)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isBangla) "লোকাল অটো-সেভ" else "Local Auto-Save",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldAudio
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.updateSearchQuery(it) },
                        placeholder = {
                            Text(
                                text = if (isBangla) "প্রজেক্টের নাম খুঁজুন..." else "Search saved drafts...",
                                color = TextMuted,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = TextMuted)
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = CyanPlayhead,
                            unfocusedBorderColor = StudioBorder,
                            focusedContainerColor = StudioSurface,
                            unfocusedContainerColor = StudioSurface,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("search_projects_input")
                    )
                }
            }

            // 4. Draft Gallery Cards
            if (filteredProjects.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        color = StudioSurface,
                        shape = RoundedCornerShape(18.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder)
                    ) {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.MovieCreation,
                                contentDescription = null,
                                tint = CyanPlayhead,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = if (isBangla) "কোনো ড্রাফট পাওয়া যায়নি" else "No drafts found",
                                style = MaterialTheme.typography.titleMedium,
                                color = TextPrimary
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isBangla) "উপরের + বাটনে চাপ দিয়ে নতুন প্রজেক্ট শুরু করুন" else "Tap New Blank Project above to start editing",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            } else {
                items(filteredProjects, key = { it.id }) { project ->
                    ProjectDraftCard(
                        project = project,
                        isBangla = isBangla,
                        onOpen = { onOpenProject(project.id) },
                        onRename = { renameTarget = project },
                        onDuplicate = { viewModel.duplicateProject(project) },
                        onDelete = { viewModel.deleteProject(project.id) }
                    )
                }
            }
        }
    }

    if (showNewProjectDialog) {
        NewProjectDialog(
            isBangla = isBangla,
            onDismiss = { showNewProjectDialog = false },
            onCreate = { name, ratio ->
                showNewProjectDialog = false
                viewModel.createBlankProject(name, ratio) { newId ->
                    onOpenProject(newId)
                }
            }
        )
    }

    if (showAutoDocDialog) {
        com.example.ui.editor.AutoDocumentaryBuilderDialog(
            isBangla = isBangla,
            onDismiss = { showAutoDocDialog = false },
            onGenerate = { topic, note ->
                showAutoDocDialog = false
                viewModel.createAutoDocumentaryProject(topic, note) { newId ->
                    onOpenProject(newId)
                }
            }
        )
    }

    if (showGuideDialog) {
        com.example.ui.editor.StudioGuideDialog(
            isBangla = isBangla,
            onDismiss = { showGuideDialog = false }
        )
    }

    renameTarget?.let { target ->
        RenameProjectDialog(
            currentName = target.name,
            isBangla = isBangla,
            onDismiss = { renameTarget = null },
            onConfirm = { newName ->
                viewModel.renameProject(target, newName)
                renameTarget = null
            }
        )
    }
}

@Composable
private fun StudioHeroHeader(
    isBangla: Boolean,
    projectCount: Int,
    onToggleLanguage: () -> Unit,
    onOpenGuide: () -> Unit,
    onNewBlankProject: () -> Unit,
    onAutoDocumentary: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(24.dp))
            .border(1.dp, StudioBorder, RoundedCornerShape(24.dp))
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_studio_hero),
            contentDescription = "ChitraFlow Studio Banner",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .matchParentSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xBB0A0C12),
                            Color(0xDD0A0C12),
                            Color(0xF50A0C12)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(listOf(CyanPlayhead, AmberDocumentary))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MovieCreation,
                            contentDescription = null,
                            tint = StudioObsidian,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "ChitraFlow Studio",
                            style = MaterialTheme.typography.headlineSmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (isBangla) "প্রো মোশন গ্রাফিক্স ও ডকুমেন্টারি এডিটর" else "Pro Motion Graphics & Documentary Suite",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        onClick = onOpenGuide,
                        color = StudioSurfaceElevated.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.testTag("hub_guide_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = "User Guide",
                                tint = AmberDocumentary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (isBangla) "গাইড" else "Guide",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }

                    Surface(
                        onClick = onToggleLanguage,
                        color = StudioSurfaceElevated.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                        modifier = Modifier.testTag("language_toggle_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = "Toggle Language",
                                tint = CyanPlayhead,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isBangla) "বাংলা" else "EN",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextPrimary
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onNewBlankProject,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CyanPlayhead,
                        contentColor = StudioObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("new_blank_project_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBangla) "নতুন প্রজেক্ট" else "Blank Project",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Button(
                    onClick = onAutoDocumentary,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AmberDocumentary,
                        contentColor = StudioObsidian
                    ),
                    shape = RoundedCornerShape(14.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("auto_doc_hub_button")
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isBangla) "অটো ডকুমেন্টারি" else "Auto Doc",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Surface(
                    color = StudioSurfaceElevated.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
                    modifier = Modifier.height(48.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = AmberDocumentary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "$projectCount",
                            style = MaterialTheme.typography.titleSmall,
                            color = TextPrimary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(
    template: PresetCatalog.StudioTemplate,
    isBangla: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .width(236.dp)
            .testTag("template_card_${template.id}")
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                Image(
                    painter = painterResource(id = VideoExportEngine.resolveDrawableId(template.thumbnailDrawable)),
                    contentDescription = template.titleEn,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC0A0C12)))
                        )
                )
                Surface(
                    color = CyanPlayhead.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                ) {
                    Text(
                        text = template.aspectRatio.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioObsidian,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (isBangla) template.titleBn else template.titleEn,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = template.subtitleBn,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun ProjectDraftCard(
    project: StudioProject,
    isBangla: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val dateLabel = remember(project.lastEditedAt) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(project.lastEditedAt))
    }

    Surface(
        onClick = onOpen,
        color = StudioSurface,
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, StudioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .testTag("project_card_${project.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(98.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(StudioSurfaceHigh)
            ) {
                Image(
                    painter = painterResource(id = VideoExportEngine.resolveDrawableId(project.thumbnailDrawable)),
                    contentDescription = project.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Surface(
                    color = Color(0xBB0A0C12),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                ) {
                    Text(
                        text = formatSec(project.totalDurationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextPrimary,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = project.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        color = StudioSurfaceElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "${project.aspectRatio.label} · ${project.previewResolution.label}",
                            style = MaterialTheme.typography.labelSmall,
                            color = CyanPlayhead,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = if (isBangla) "${project.clips.size} লেয়ার" else "${project.clips.size} layers",
                        style = MaterialTheme.typography.labelSmall,
                        color = AmberDocumentary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = dateLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            }

            Box {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.testTag("project_menu_${project.id}")
                ) {
                    Icon(
                        imageVector = Icons.Outlined.MoreVert,
                        contentDescription = "Project options",
                        tint = TextSecondary
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = StudioSurfaceElevated
                ) {
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "নাম পরিবর্তন" else "Rename", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null, tint = CyanPlayhead) },
                        onClick = {
                            menuExpanded = false
                            onRename()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ডুপ্লিকেট করুন" else "Duplicate", color = TextPrimary) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = AmberDocumentary) },
                        onClick = {
                            menuExpanded = false
                            onDuplicate()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(if (isBangla) "ডিলিট করুন" else "Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NewProjectDialog(
    isBangla: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String, AspectRatioPreset) -> Unit
) {
    var name by remember { mutableStateOf(if (isBangla) "আমার ডকুমেন্টারি প্রজেক্ট" else "My Documentary Edit") }
    var selectedRatio by remember { mutableStateOf(AspectRatioPreset.PORTRAIT_9_16) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = {
            Text(
                text = if (isBangla) "নতুন স্টুডিও প্রজেক্ট" else "Create Studio Project",
                color = TextPrimary,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(if (isBangla) "প্রজেক্টের নাম" else "Project Title") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyanPlayhead,
                        unfocusedBorderColor = StudioBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (isBangla) "ক্যানভাস অনুপাত (Aspect Ratio)" else "Canvas Aspect Ratio",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AspectRatioPreset.entries.forEach { preset ->
                        FilterChip(
                            selected = selectedRatio == preset,
                            onClick = { selectedRatio = preset },
                            label = { Text(preset.label) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = CyanPlayhead,
                                selectedLabelColor = StudioObsidian,
                                labelColor = TextSecondary
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(name, selectedRatio) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian),
                modifier = Modifier.testTag("confirm_new_project_button")
            ) {
                Text(if (isBangla) "স্টুডিও খুলুন" else "Open Studio", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
            }
        }
    )
}

@Composable
private fun RenameProjectDialog(
    currentName: String,
    isBangla: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(currentName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = StudioSurfaceElevated,
        title = { Text(if (isBangla) "প্রজেক্টের নাম পরিবর্তন" else "Rename Project", color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = CyanPlayhead,
                    unfocusedBorderColor = StudioBorder,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(name) },
                colors = ButtonDefaults.buttonColors(containerColor = CyanPlayhead, contentColor = StudioObsidian)
            ) {
                Text(if (isBangla) "সেভ" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(if (isBangla) "বাতিল" else "Cancel", color = TextSecondary)
            }
        }
    )
}

private fun formatSec(ms: Long): String {
    val totalSec = (ms / 1000L).coerceAtLeast(0L)
    val mins = totalSec / 60
    val secs = totalSec % 60
    return "%02d:%02d".format(mins, secs)
}
