package com.example.alist.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.alist.data.local.ServerProfile
import java.net.URL

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginView(viewModel: HomeViewModel, uiState: HomeUiState) {
    var aliasName by remember { mutableStateOf("") }
    var isHttps by remember { mutableStateOf(true) }
    var host by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("443") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    
    var historyExpanded by remember { mutableStateOf(false) }

    val primaryColor = Color(0xFF4C45E5)
    val backgroundColor = Color(0xFFF8F9FA)
    val inputBackgroundColor = Color(0xFFF3F4F6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))
        
        // App Icon Placeholder (Rounded square with primary color)
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryColor),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Storage, // Using storage icon as placeholder
                contentDescription = "App Icon",
                tint = Color.White,
                modifier = Modifier.size(36.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = "Remote Storage Manager",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF1E293B)
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "SUPPORT ALIST AND OPENLIST SERVERS",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF94A3B8),
            letterSpacing = 1.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))

        // History Dropdown
        if (uiState.profiles.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = "History",
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "SELECT FROM HISTORY",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF94A3B8),
                        letterSpacing = 1.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                
                ExposedDropdownMenuBox(
                    expanded = historyExpanded,
                    onExpandedChange = { historyExpanded = it }
                ) {
                    val currentProfile = uiState.currentProfile
                    val displayText = if (currentProfile != null) {
                        if (currentProfile.aliasName.isNotBlank()) currentProfile.aliasName else currentProfile.serverUrl
                    } else {
                        "Select a server..."
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(horizontal = 16.dp, vertical = 14.dp)
                            .menuAnchor(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current: $displayText",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color(0xFF334155)
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color(0xFF94A3B8)
                            )
                        }
                    }

                    ExposedDropdownMenu(
                        expanded = historyExpanded,
                        onDismissRequest = { historyExpanded = false }
                    ) {
                        uiState.profiles.forEach { profile ->
                            val title = if (profile.aliasName.isNotBlank()) profile.aliasName else profile.username
                            val subtitle = profile.serverUrl
                            
                            DropdownMenuItem(
                                text = { 
                                    Column {
                                        Text(
                                            text = title,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp,
                                            color = Color(0xFF1E293B)
                                        )
                                        Text(
                                            text = subtitle,
                                            fontSize = 11.sp,
                                            color = Color(0xFF94A3B8)
                                        )
                                    }
                                },
                                onClick = {
                                    historyExpanded = false
                                    aliasName = profile.aliasName
                                    username = profile.username
                                    // Parse URL
                                    try {
                                        val url = URL(profile.serverUrl)
                                        isHttps = url.protocol.equals("https", ignoreCase = true)
                                        host = url.host
                                        port = if (url.port != -1) url.port.toString() else if (isHttps) "443" else "80"
                                    } catch (e: Exception) {
                                        host = profile.serverUrl.replace("https://", "").replace("http://", "")
                                    }
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = { viewModel.deleteProfile(profile) }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Delete,
                                            contentDescription = "Delete",
                                            tint = Color(0xFFCBD5E1),
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // General Info
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "GENERAL INFO",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            CustomTextField(
                value = aliasName,
                onValueChange = { aliasName = it },
                icon = Icons.Outlined.Label,
                placeholder = "Alias Name (e.g. NAS)"
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))

        // Server Config
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SERVER CONFIG",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                if (isHttps) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "SSL",
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "SSL",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            
            // White card for server details
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.White)
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // 1. Protocol Toggle (Compact Icon Box)
                    Box(
                        modifier = Modifier
                            .width(44.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isHttps) Color(0xFF10B981).copy(alpha = 0.1f) else inputBackgroundColor)
                            .clickable {
                                isHttps = !isHttps
                                if (isHttps && port == "80") port = "443"
                                if (!isHttps && port == "443") port = "80"
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isHttps) Icons.Outlined.Lock else Icons.Outlined.LockOpen,
                            contentDescription = "Toggle Protocol",
                            tint = if (isHttps) Color(0xFF10B981) else Color(0xFF94A3B8),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // 2. Host (Takes up all remaining space)
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(inputBackgroundColor)
                            .padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = "Host",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            if (host.isEmpty()) {
                                Text(
                                    text = "Domain or IP",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 14.sp
                                )
                            }
                            BasicTextField(
                                value = host,
                                onValueChange = { host = it },
                                textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium),
                                singleLine = true,
                                cursorBrush = SolidColor(primaryColor),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                    
                    // 3. Port (Fixed tight width, pure numbers)
                    Row(
                        modifier = Modifier
                            .width(56.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .background(inputBackgroundColor)
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = port,
                            onValueChange = { port = it },
                            textStyle = TextStyle(fontSize = 14.sp, color = Color(0xFF334155), fontWeight = FontWeight.Medium),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            cursorBrush = SolidColor(primaryColor),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Username
        CustomTextField(
            value = username,
            onValueChange = { username = it },
            icon = Icons.Outlined.Person,
            placeholder = "Username"
        )
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // Password
        CustomTextField(
            value = password,
            onValueChange = { password = it },
            icon = Icons.Outlined.Lock,
            placeholder = "Password",
            isPassword = true
        )
        
        Spacer(modifier = Modifier.height(24.dp))

        // Connect Button
        Button(
            onClick = {
                val protocol = if (isHttps) "https" else "http"
                val serverUrl = "$protocol://$host:$port"
                val name = if (aliasName.isBlank()) host else aliasName
                viewModel.testLoginAndFetch(name, serverUrl, username, password)
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            enabled = !uiState.isLoading && host.isNotBlank() && username.isNotBlank(),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = primaryColor,
                disabledContainerColor = primaryColor.copy(alpha = 0.5f)
            )
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("Connect Now", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }

        if (uiState.error != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = uiState.error!!,
                color = MaterialTheme.colorScheme.error,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        Text(
            text = "V 0.1.3",
            color = Color(0xFFCBD5E1),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
fun CustomTextField(
    value: String,
    onValueChange: (String) -> Unit,
    icon: ImageVector,
    placeholder: String,
    isPassword: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFFCBD5E1),
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        
        Box(modifier = Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontSize = 14.sp, 
                    color = Color(0xFF334155), 
                    fontWeight = FontWeight.Medium
                ),
                singleLine = true,
                visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions = if (isPassword) KeyboardOptions(keyboardType = KeyboardType.Password) else KeyboardOptions.Default,
                cursorBrush = SolidColor(Color(0xFF4C45E5)),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
