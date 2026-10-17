package com.example.whatsoff.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.whatsoff.model.ChatMessage
import com.example.whatsoff.model.ConnectionState
import com.example.whatsoff.model.DeliveryStatus
import com.example.whatsoff.ui.theme.DarkBubbleMe
import com.example.whatsoff.ui.theme.DarkBubblePeer
import com.example.whatsoff.ui.theme.LightBubbleMe
import com.example.whatsoff.ui.theme.LightBubblePeer
import com.example.whatsoff.ui.theme.PrimaryGreen
import com.example.whatsoff.ui.theme.StatusOnline
import com.example.whatsoff.viewmodel.ChatViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    vm: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val messages by vm.messages.collectAsState()
    val connectionState by vm.connectionState.collectAsState()
    val fingerprint by vm.sessionFingerprint.collectAsState()
    val peerIsTyping by vm.peerIsTyping.collectAsState()
    val listState = rememberLazyListState()

    var text by remember { mutableStateOf("") }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    var showSecurityDialog by remember { mutableStateOf(false) }

    val peerName = when (val state = connectionState) {
        is ConnectionState.Connected -> state.peerName
        else -> "Peer"
    }

    val peerIp = when (val state = connectionState) {
        is ConnectionState.Connected -> state.peerAddress
        else -> ""
    }

    val isConnected = connectionState is ConnectionState.Connected

    // Auto-scroll to bottom on new message
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable {
                            if (fingerprint != null) showSecurityDialog = true
                        }
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = PrimaryGreen.copy(alpha = 0.2f),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = peerName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (fingerprint != null) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = "Encrypted",
                                        tint = PrimaryGreen,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }

                            if (peerIsTyping) {
                                Text(
                                    text = "typing...",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = PrimaryGreen,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .clip(CircleShape)
                                            .background(if (isConnected) StatusOnline else MaterialTheme.colorScheme.error)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isConnected) {
                                            if (peerIp.isNotBlank()) "Online • $peerIp" else "Online"
                                        } else {
                                            "Disconnected"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isConnected) StatusOnline else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isConnected) {
                            showDisconnectDialog = true
                        } else {
                            onNavigateBack()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (fingerprint != null) {
                        IconButton(onClick = { showSecurityDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = "View Security Fingerprint",
                                tint = PrimaryGreen
                            )
                        }
                    }
                    IconButton(onClick = { showDisconnectDialog = true }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Leave Chat",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            // Disconnection Warning Banner
            if (!isConnected) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Connection lost or closed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(onClick = onNavigateBack) {
                            Text("Back to Devices")
                        }
                    }
                }
            }

            // Security Badge Banner
            if (fingerprint != null) {
                Surface(
                    color = PrimaryGreen.copy(alpha = 0.08f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSecurityDialog = true }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = PrimaryGreen,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "End-to-End Encrypted (ECDH P-256 + AES-GCM) • Tap to verify",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                            color = PrimaryGreen,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Messages Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                if (messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Text(
                                text = "🔒 Direct Peer-to-Peer Encrypted Local Session.\nPersistent offline storage enabled.\nSay hello to start chatting!",
                                style = MaterialTheme.typography.bodySmall,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(messages, key = { it.id }) { message ->
                            ChatBubble(message = message)
                        }
                    }
                }
            }

            // Input Bar
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = text,
                        onValueChange = {
                            text = it
                            vm.sendTyping(it.isNotBlank())
                        },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Type a message...") },
                        maxLines = 5,
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PrimaryGreen,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        shape = CircleShape,
                        color = if (text.isNotBlank() && isConnected) PrimaryGreen else MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.size(48.dp)
                    ) {
                        IconButton(
                            onClick = {
                                if (text.isNotBlank() && isConnected) {
                                    vm.sendMessage(text)
                                    text = ""
                                    vm.sendTyping(false)
                                }
                            },
                            enabled = text.isNotBlank() && isConnected
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (text.isNotBlank() && isConnected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    // Security Verification Dialog
    if (showSecurityDialog && fingerprint != null) {
        AlertDialog(
            onDismissRequest = { showSecurityDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = PrimaryGreen,
                    modifier = Modifier.size(36.dp)
                )
            },
            title = { Text("End-to-End Encryption") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Messages in this session are protected with ephemeral Elliptic-Curve Diffie-Hellman (P-256) and AES-256-GCM authenticated cipher.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    ) {
                        Text(
                            text = fingerprint ?: "",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 2.sp
                            ),
                            color = PrimaryGreen,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Compare this safety number with your peer to verify that no third party is intercepting your connection.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showSecurityDialog = false }) {
                    Text("OK", color = PrimaryGreen, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Leave Chat?") },
            text = { Text("Are you sure you want to disconnect from this peer?") },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        vm.disconnect()
                        onNavigateBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun ChatBubble(message: ChatMessage) {
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val timeFormatter = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(message.timestamp) { timeFormatter.format(Date(message.timestamp)) }

    val bubbleColor = if (message.isMine) {
        if (isDark) DarkBubbleMe else LightBubbleMe
    } else {
        if (isDark) DarkBubblePeer else LightBubblePeer
    }

    val textColor = if (message.isMine) {
        if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    } else {
        if (isDark) Color(0xFFE9EDEF) else Color(0xFF111B21)
    }

    val bubbleShape = if (message.isMine) {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 16.dp,
            bottomEnd = 4.dp
        )
    } else {
        RoundedCornerShape(
            topStart = 16.dp,
            topEnd = 16.dp,
            bottomStart = 4.dp,
            bottomEnd = 16.dp
        )
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (message.isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            shadowElevation = 1.dp,
            modifier = Modifier.widthIn(max = 290.dp)
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                Text(
                    text = message.message,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 15.sp),
                    color = textColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formattedTime,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = textColor.copy(alpha = 0.6f)
                    )

                    if (message.isMine) {
                        Spacer(modifier = Modifier.width(4.dp))
                        when (message.status) {
                            DeliveryStatus.PENDING, DeliveryStatus.SENT -> {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = "Sent",
                                    tint = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(13.dp)
                                )
                            }
                            DeliveryStatus.DELIVERED -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Delivered",
                                    tint = textColor.copy(alpha = 0.6f),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DeliveryStatus.READ -> {
                                Icon(
                                    imageVector = Icons.Default.DoneAll,
                                    contentDescription = "Read",
                                    tint = PrimaryGreen,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            DeliveryStatus.FAILED -> {
                                Text(
                                    text = "!",
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}