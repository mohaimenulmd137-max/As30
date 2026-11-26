package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.editor.EditorScreen
import com.example.ui.editor.EditorViewModel
import com.example.ui.hub.ProjectHubScreen
import com.example.ui.hub.ProjectHubViewModel
import com.example.ui.theme.ChitraFlowTheme

const val ROUTE_HUB = "project_hub"
const val ROUTE_EDITOR = "editor_workspace"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            ChitraFlowTheme {
                ChitraFlowApp()
            }
        }
    }
}

@Composable
fun ChitraFlowApp() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = ROUTE_HUB
    ) {
        composable(ROUTE_HUB) {
            val hubViewModel: ProjectHubViewModel = viewModel()
            ProjectHubScreen(
                viewModel = hubViewModel,
                onOpenProject = { projectId ->
                    navController.navigate("$ROUTE_EDITOR/$projectId")
                }
            )
        }
        composable(
            route = "$ROUTE_EDITOR/{projectId}",
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId") ?: return@composable
            val editorViewModel: EditorViewModel = viewModel()
            EditorScreen(
                projectId = projectId,
                viewModel = editorViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
