package com.example.ui.viewer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.data.model.ModelItem
import com.example.data.model.SavedViewpoint

// Cosmic Slate Theme Colors
val SpaceDark = Color(0xFF0D0F14)
val SpaceCardBg = Color(0xCC131722)
val NeonCyan = Color(0xFF00BFFF)
val NeonGreen = Color(0xFF00FFCC)
val SpaceGrey = Color(0xFF1F2833)
val BrightWhite = Color(0xFFF0F4F8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainViewerScreen(
    viewModel: ModelViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activeModel by viewModel.activeModel.collectAsState()
    val modelsList by viewModel.modelsList.collectAsState()
    val viewpointsList by viewModel.viewpointsList.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // Control Sidebar toggle (highly interactive, collapses for full-bleed Quest immersion)
    var isSidebarOpen by remember { mutableStateOf(true) }
    var showAddViewpointDialog by remember { mutableStateOf(false) }
    var viewpointTitleInput by remember { mutableStateOf("") }

    // HTML/WebGL properties
    val renderMode by viewModel.renderMode.collectAsState()
    val pointSize by viewModel.pointSize.collectAsState()
    val lightIntensity by viewModel.lightIntensity.collectAsState()
    val autoRotate by viewModel.autoRotate.collectAsState()
    val gridVisible by viewModel.gridVisible.collectAsState()
    val backgroundColor by viewModel.backgroundColor.collectAsState()

    // Mesh status
    val verticesCount by viewModel.verticesCount.collectAsState()
    val polygonsCount by viewModel.polygonsCount.collectAsState()
    val cameraPos by viewModel.cameraPosition.collectAsState()
    val cameraTgt by viewModel.cameraTarget.collectAsState()

    // Setup Local File Picker Launcher
    val pickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val resolver = context.contentResolver
            val name = getFileName(resolver, uri) ?: "imported_model"
            val extension = name.substringAfterLast('.', "").lowercase()
            if (extension == "glb" || extension == "gltf" || extension == "ply") {
                viewModel.importModel(context, uri, name, extension)
            } else {
                viewModel.setError("Unsupported format! Please select a .glb, .gltf, or .ply file.")
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(SpaceDark)
    ) {
        // 1. WebGL Interactive 3D Canvas
        ThreeDCanvas(
            viewModel = viewModel,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Translucent Gradient vignette for Tech Atmosphere
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color(0x6607090D)),
                        radius = 1200f
                    )
                )
        )

        // 3. COLLAPSED SIDEBAR TOGGLE (Floating Glass Button)
        if (!isSidebarOpen) {
            FloatingActionButton(
                onClick = { isSidebarOpen = true },
                containerColor = SpaceGrey.copy(alpha = 0.85f),
                contentColor = NeonCyan,
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .testTag("open_sidebar_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Open Sidebar Navigation"
                )
            }
        }

        // 4. MAIN HUD OVERLAY (Top-Center Cockpit Info Panel)
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 20.dp)
                .widthIn(max = 480.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SpaceCardBg)
                .border(1.dp, NeonCyan.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = activeModel?.name ?: "No model active",
                color = BrightWhite,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Vertices",
                        tint = NeonGreen,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "V: ${verticesCount.toLocaleString()}",
                        color = BrightWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.GridOn,
                        contentDescription = "Polygons",
                        tint = NeonCyan,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "P: ${polygonsCount.toLocaleString()}",
                        color = BrightWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = "Position",
                        tint = Color.Magenta,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "XYZ: %.1f, %.1f, %.1f".format(cameraPos[0], cameraPos[1], cameraPos[2]),
                        color = BrightWhite.copy(alpha = 0.7f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // 5. IMMERSIVE COMPASS HUD OVERLAY (Top-Right)
        Card(
            colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "TARGET FOCUS",
                    color = NeonGreen,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "%.1f, %.1f, %.1f".format(cameraTgt[0], cameraTgt[1], cameraTgt[2]),
                    color = BrightWhite,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }

        // 6. ADAPTIVE SIDEBAR (Left Floating Navigation Dashboard Panel)
        AnimatedVisibility(
            visible = isSidebarOpen,
            enter = slideInHorizontally(animationSpec = spring()) { -it } + fadeIn(),
            exit = slideOutHorizontally(animationSpec = spring()) { -it } + fadeOut(),
            modifier = Modifier
                .fillMaxHeight()
                .width(360.dp)
                .align(Alignment.CenterStart)
        ) {
            Surface(
                color = SpaceCardBg,
                modifier = Modifier
                    .fillMaxHeight()
                    .border(
                        width = 1.dp,
                        brush = Brush.horizontalGradient(listOf(Color.Transparent, NeonCyan.copy(alpha = 0.15f))),
                        shape = RoundedCornerShape(0.dp)
                    )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(16.dp)
                ) {
                    // Sidebar Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Navigation,
                                contentDescription = "App Icon",
                                tint = NeonCyan,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "SPATIAL HUB",
                                color = BrightWhite,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp,
                                fontSize = 18.sp
                            )
                        }

                        IconButton(
                            onClick = { isSidebarOpen = false },
                            modifier = Modifier.testTag("close_sidebar_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close settings panel",
                                tint = BrightWhite.copy(alpha = 0.6f)
                            )
                        }
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = NeonCyan.copy(alpha = 0.15f)
                    )

                    // Error Notification Bar
                    errorMessage?.let { err ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0x44FF4444)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .border(1.dp, Color.Red.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error icon",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = err,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(
                                    onClick = { viewModel.setError(null) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Clear error",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Main Scrollable Control Sections
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // SECTION A: 3D MODEL LIBRARY
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "3D WORLD MODEL LIBRARY",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    
                                    // Add Model button
                                    Button(
                                        onClick = { pickerLauncher.launch("*/*") },
                                        colors = ButtonDefaults.buttonColors(containerColor = SpaceGrey),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("import_model_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Add,
                                            contentDescription = "Import",
                                            tint = NeonGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("IMPORT", color = BrightWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Models List
                                if (modelsList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .background(Color(0x05FFFFFF), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No models available. Import one!",
                                            color = BrightWhite.copy(alpha = 0.5f),
                                            fontSize = 11.sp
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        modelsList.forEach { model ->
                                            val isSelected = activeModel?.id == model.id
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clip(RoundedCornerShape(8.dp))
                                                    .background(if (isSelected) SpaceGrey else Color.Transparent)
                                                    .border(
                                                        1.dp,
                                                        if (isSelected) NeonCyan.copy(alpha = 0.4f) else Color.Transparent,
                                                        RoundedCornerShape(8.dp)
                                                    )
                                                    .clickable { viewModel.selectModel(model) }
                                                    .padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = if (model.type == "glb") Icons.Default.FolderOpen else Icons.Default.GridOn,
                                                    contentDescription = "Format",
                                                    tint = if (isSelected) NeonCyan else BrightWhite.copy(alpha = 0.6f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(modifier = Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = model.name,
                                                        color = if (isSelected) NeonGreen else BrightWhite,
                                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                        fontSize = 13.sp,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                    Text(
                                                        text = "${model.type.uppercase()} • ${if (model.isSample) "PRESET SAMPLE" else "LOCAL SCAN"}",
                                                        color = BrightWhite.copy(alpha = 0.45f),
                                                        fontSize = 10.sp
                                                    )
                                                }
                                                if (!model.isSample) {
                                                    IconButton(
                                                        onClick = { viewModel.deleteModel(model) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete model",
                                                            tint = Color.Red.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION B: VIEWPOINTS / HOTSPOT NAVIGATOR
                        item {
                            Column {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "NAVIGATOR VIEWPOINTS",
                                        color = NeonCyan,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 1.sp
                                    )
                                    
                                    Button(
                                        onClick = {
                                            viewpointTitleInput = "Checkpoint ${viewpointsList.size + 1}"
                                            showAddViewpointDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = SpaceGrey),
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        shape = RoundedCornerShape(6.dp),
                                        enabled = activeModel != null,
                                        modifier = Modifier
                                            .height(28.dp)
                                            .testTag("save_viewpoint_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Bookmark,
                                            contentDescription = "Save Viewpoint",
                                            tint = NeonGreen,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("SAVE VIEW", color = BrightWhite, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                if (viewpointsList.isEmpty()) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(80.dp)
                                            .background(Color(0x05FFFFFF), RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "No viewpoints saved. Rotate and save one!",
                                            color = BrightWhite.copy(alpha = 0.5f),
                                            fontSize = 11.sp,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(horizontal = 16.dp)
                                        )
                                    }
                                } else {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        viewpointsList.forEach { vp ->
                                            Card(
                                                colors = CardDefaults.cardColors(containerColor = SpaceGrey.copy(alpha = 0.5f)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .border(1.dp, NeonCyan.copy(alpha = 0.05f), RoundedCornerShape(8.dp))
                                            ) {
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { viewModel.navigateToViewpoint(vp) }
                                                        .padding(10.dp),
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Icon(
                                                        imageVector = Icons.Default.Navigation,
                                                        contentDescription = "Fly",
                                                        tint = NeonGreen,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(10.dp))
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = vp.title,
                                                            color = BrightWhite,
                                                            fontSize = 12.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                            text = "Cam: %.1f, %.1f, %.1f".format(vp.posX, vp.posY, vp.posZ),
                                                            color = BrightWhite.copy(alpha = 0.4f),
                                                            fontSize = 9.sp,
                                                            fontFamily = FontFamily.Monospace
                                                        )
                                                    }
                                                    IconButton(
                                                        onClick = { viewModel.deleteViewpoint(vp) },
                                                        modifier = Modifier.size(24.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = Icons.Default.Delete,
                                                            contentDescription = "Delete viewpoint",
                                                            tint = Color.Red.copy(alpha = 0.7f),
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // SECTION C: WEBGL VIEWPORT ENGINE PROPERTIES
                        item {
                            Column {
                                Text(
                                    text = "WEBGL ENGINE RENDERING",
                                    color = NeonCyan,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Render Mode (Solid Mesh, Wireframe, Points)
                                Text("RENDER LAYER", color = BrightWhite.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val modes = listOf("solid", "wireframe", "points")
                                    modes.forEach { mode ->
                                        val selected = renderMode == mode
                                        Button(
                                            onClick = { viewModel.setRenderMode(mode) },
                                            modifier = Modifier.weight(1f).height(32.dp),
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (selected) NeonCyan else SpaceGrey
                                            ),
                                            contentPadding = PaddingValues(0.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(
                                                text = mode.uppercase(),
                                                color = if (selected) SpaceDark else BrightWhite,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }

                                // Point size slider (For point clouds, PLY scans)
                                if (renderMode == "points" || activeModel?.type == "ply") {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("POINT ATTENUATION SIZE", color = BrightWhite.copy(alpha = 0.7f), fontSize = 11.sp)
                                        Text("%.3f".format(pointSize), color = NeonGreen, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    }
                                    Slider(
                                        value = pointSize,
                                        onValueChange = { viewModel.setPointSize(it) },
                                        valueRange = 0.005f..0.25f,
                                        colors = SliderDefaults.colors(
                                            thumbColor = NeonGreen,
                                            activeTrackColor = NeonGreen.copy(alpha = 0.7f),
                                            inactiveTrackColor = SpaceGrey
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Lighting slider
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("ENVIRONMENT ILLUMINATION", color = BrightWhite.copy(alpha = 0.7f), fontSize = 11.sp)
                                    Text("%.1fx".format(lightIntensity), color = NeonCyan, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                }
                                Slider(
                                    value = lightIntensity,
                                    onValueChange = { viewModel.setLightIntensity(it) },
                                    valueRange = 0.1f..3.0f,
                                    colors = SliderDefaults.colors(
                                        thumbColor = NeonCyan,
                                        activeTrackColor = NeonCyan.copy(alpha = 0.7f),
                                        inactiveTrackColor = SpaceGrey
                                    )
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                // Switch Toggles
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Rotate",
                                            tint = BrightWhite.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("AUTO ROTATION HUB", color = BrightWhite, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = autoRotate,
                                        onCheckedChange = { viewModel.setAutoRotate(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = NeonGreen,
                                            checkedTrackColor = NeonGreen.copy(alpha = 0.4f),
                                            uncheckedThumbColor = SpaceGrey,
                                            uncheckedTrackColor = SpaceGrey.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.GridOn,
                                            contentDescription = "Grid",
                                            tint = BrightWhite.copy(alpha = 0.6f),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("COORDINATE FLOOR GRIDS", color = BrightWhite, fontSize = 12.sp)
                                    }
                                    Switch(
                                        checked = gridVisible,
                                        onCheckedChange = { viewModel.setGridVisible(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = NeonCyan,
                                            checkedTrackColor = NeonCyan.copy(alpha = 0.4f),
                                            uncheckedThumbColor = SpaceGrey,
                                            uncheckedTrackColor = SpaceGrey.copy(alpha = 0.5f)
                                        )
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Background color customisation
                                Text("VIRTUAL BACKGROUND", color = BrightWhite.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    val bgs = listOf(
                                        "#0d0f14" to "Deep Charcoal",
                                        "#050508" to "Onyx Black",
                                        "#0b1e2a" to "Cyber Blue"
                                    )
                                    bgs.forEach { (hex, label) ->
                                        val selected = backgroundColor == hex
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(34.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(Color(android.graphics.Color.parseColor(hex)))
                                                .border(
                                                    1.dp,
                                                    if (selected) NeonGreen else Color.Transparent,
                                                    RoundedCornerShape(6.dp)
                                                )
                                                .clickable { viewModel.setBackgroundColor(hex) }
                                                .padding(4.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                color = if (selected) NeonGreen else BrightWhite.copy(alpha = 0.6f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Bottom info label about Quest 3S
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Designed for Meta Quest 3S VR. Supports Bluetooth Mouse, controller pointers, and touch drag gestures.",
                        color = BrightWhite.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // 7. INTERACTIVE LOADING BLOCKER (with glassmorphic spinning HUD)
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SpaceCardBg),
                    modifier = Modifier
                        .width(220.dp)
                        .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = NeonCyan,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(44.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "PARSING DATA...",
                            color = NeonCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Loading meshes & vertices...",
                            color = BrightWhite.copy(alpha = 0.6f),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialog to save viewpoint
    if (showAddViewpointDialog) {
        Dialog(onDismissRequest = { showAddViewpointDialog = false }) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SpaceGrey),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .width(320.dp)
                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "SAVE CURRENT VIEWPORT",
                        color = NeonCyan,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    OutlinedTextField(
                        value = viewpointTitleInput,
                        onValueChange = { viewpointTitleInput = it },
                        label = { Text("Viewpoint Title", color = BrightWhite.copy(alpha = 0.6f)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = BrightWhite,
                            unfocusedTextColor = BrightWhite,
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = BrightWhite.copy(alpha = 0.2f),
                            focusedLabelColor = NeonCyan
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("viewpoint_title_input")
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Saves coordinates: %.1f, %.1f, %.1f".format(cameraPos[0], cameraPos[1], cameraPos[2]),
                        color = BrightWhite.copy(alpha = 0.5f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { showAddViewpointDialog = false },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = BrightWhite)
                        ) {
                            Text("Cancel", fontSize = 12.sp)
                        }

                        Button(
                            onClick = {
                                if (viewpointTitleInput.isNotBlank()) {
                                    viewModel.saveCurrentViewpoint(viewpointTitleInput)
                                    showAddViewpointDialog = false
                                }
                            },
                            modifier = Modifier.weight(1f).testTag("dialog_save_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                        ) {
                            Text("Save", color = SpaceDark, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// Helper to query filename from ContentResolver safely
private fun getFileName(resolver: ContentResolver, uri: Uri): String? {
    var name: String? = null
    if (uri.scheme == "content") {
        val cursor = resolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
    }
    if (name == null) {
        name = uri.path?.substringAfterLast('/')
    }
    return name
}

// Ext helper for pretty formatting numbers
private fun Int.toLocaleString(): String {
    return java.text.NumberFormat.getIntegerInstance().format(this)
}
