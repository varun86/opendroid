package com.opendroid.ai.ui.screens

import android.Manifest
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.opendroid.ai.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(onFinished: () -> Unit) {
    val context = LocalContext.current
    
    // Core permissions status state
    var recordAudioGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.RECORD_AUDIO)) }
    var locationGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.ACCESS_FINE_LOCATION)) }
    var smsGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.SEND_SMS)) }
    var phoneGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.CALL_PHONE)) }
    var contactsGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.READ_CONTACTS)) }
    var calendarGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.READ_CALENDAR)) }
    var cameraGranted by remember { mutableStateOf(checkPerm(context, Manifest.permission.CAMERA)) }
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                checkPerm(context, Manifest.permission.POST_NOTIFICATIONS)
            } else {
                true
            }
        )
    }
    var storageGranted by remember { mutableStateOf(hasStoragePermission(context)) }
    var accessibilityGranted by remember { mutableStateOf(isAccessibilityServiceEnabled(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                accessibilityGranted = isAccessibilityServiceEnabled(context)
                recordAudioGranted = checkPerm(context, Manifest.permission.RECORD_AUDIO)
                locationGranted = checkPerm(context, Manifest.permission.ACCESS_FINE_LOCATION)
                smsGranted = checkPerm(context, Manifest.permission.SEND_SMS)
                phoneGranted = checkPerm(context, Manifest.permission.CALL_PHONE)
                contactsGranted = checkPerm(context, Manifest.permission.READ_CONTACTS)
                calendarGranted = checkPerm(context, Manifest.permission.READ_CALENDAR)
                cameraGranted = checkPerm(context, Manifest.permission.CAMERA)
                notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    checkPerm(context, Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    true
                }
                storageGranted = hasStoragePermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        recordAudioGranted = it
    }
    val locationLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        locationGranted = it
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        smsGranted = it
    }
    val phoneLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        phoneGranted = it
    }
    val contactsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        contactsGranted = it
    }
    val calendarLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        calendarGranted = it
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        cameraGranted = it
    }
    val notificationsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsGranted = it
    }
    val legacyStorageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        storageGranted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true &&
                permissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("OpenDroid Onboarding", color = AccentNeonGreen, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DarkBackground)
            )
        },
        containerColor = DarkBackground
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
        ) {
            Text(
                text = "Welcome to OpenDroid",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "We need permissions to act as your autonomous device operator. Grant the following items:",
                fontSize = 14.sp,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    PermissionCard(
                        title = "Microphone",
                        desc = "Needed for wake word and speech recognition.",
                        granted = recordAudioGranted,
                        onGrant = { audioLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                    )
                }
                item {
                    PermissionCard(
                        title = "Location",
                        desc = "Needed to fetch weather, directions, and maps.",
                        granted = locationGranted,
                        onGrant = { locationLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                    )
                }
                item {
                    PermissionCard(
                        title = "SMS & Telephony",
                        desc = "Needed to read and send messages, and place calls.",
                        granted = smsGranted && phoneGranted,
                        onGrant = {
                            smsLauncher.launch(Manifest.permission.SEND_SMS)
                            phoneLauncher.launch(Manifest.permission.CALL_PHONE)
                        }
                    )
                }
                item {
                    PermissionCard(
                        title = "Contacts & Calendar",
                        desc = "Needed to resolve recipient names and manage events.",
                        granted = contactsGranted && calendarGranted,
                        onGrant = {
                            contactsLauncher.launch(Manifest.permission.READ_CONTACTS)
                            calendarLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    )
                }
                item {
                    PermissionCard(
                        title = "Camera",
                        desc = "Needed for image input and vision capabilities.",
                        granted = cameraGranted,
                        onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) }
                    )
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    item {
                        PermissionCard(
                            title = "Notifications",
                            desc = "Needed to post system notifications and service status.",
                            granted = notificationsGranted,
                            onGrant = { notificationsLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        )
                    }
                }
                item {
                    PermissionCard(
                        title = "Storage / Files Access",
                        desc = "Needed for agent to list, read, write, and delete files.",
                        granted = storageGranted,
                        onGrant = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                try {
                                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                } catch (e: Exception) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION).apply {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(intent)
                                }
                            } else {
                                legacyStorageLauncher.launch(
                                    arrayOf(
                                        Manifest.permission.READ_EXTERNAL_STORAGE,
                                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                                    )
                                )
                            }
                        }
                    )
                }
                item {
                    PermissionCard(
                        title = "Accessibility Service",
                        desc = "Enables full agent screen automation (clicks & inputs).",
                        granted = accessibilityGranted,
                        onGrant = {
                            context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            })
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val sharedPrefs = context.getSharedPreferences("opendroid_prefs", android.content.Context.MODE_PRIVATE)
                    sharedPrefs.edit().putBoolean("onboarding_completed", true).apply()
                    onFinished()
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = AccentNeonGreen, contentColor = DarkBackground),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Proceed to OpenDroid Agent", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onGrant: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBackground)
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, fontSize = 12.sp, color = TextSecondary)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Button(
            onClick = onGrant,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (granted) BorderColor else AccentNeonGreen,
                contentColor = if (granted) TextSecondary else DarkBackground
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(if (granted) "Granted" else "Grant", fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun checkPerm(context: Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
}

private fun hasStoragePermission(context: Context): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        android.os.Environment.isExternalStorageManager()
    } else {
        checkPerm(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                checkPerm(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    if (com.opendroid.ai.accessibility.OpenDroidAccessibilityService.getInstance() != null) {
        return true
    }
    val expectedComponentName = android.content.ComponentName(context, com.opendroid.ai.accessibility.OpenDroidAccessibilityService::class.java).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServices)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        if (componentNameString.equals(expectedComponentName, ignoreCase = true)) {
            return true
        }
    }
    return false
}
