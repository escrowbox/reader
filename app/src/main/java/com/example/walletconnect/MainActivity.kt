package com.example.walletconnect

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.walletconnect.ui.theme.TirtoWritterFontFamily
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.walletconnect.ui.screens.CreateContractScreen
import com.example.walletconnect.ui.screens.EventsScreen
import com.example.walletconnect.ui.screens.EpubReaderScreen
import com.example.walletconnect.ui.screens.InfoScreen
import com.example.walletconnect.ui.screens.SweepBoxesScreen
import com.example.walletconnect.utils.FileManager
import com.example.walletconnect.ui.theme.WalletConnectTheme
import com.example.walletconnect.ui.theme.NeumorphicBackground
import com.example.walletconnect.ui.theme.NeumorphicText
import com.example.walletconnect.ui.theme.NeumorphicTextSecondary
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import timber.log.Timber
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background

/**
 * Главная активность приложения Solana EPUB Reader
 * Использует Compose навигацию и Mobile Wallet Adapter для подключения к Solana кошелькам
 */
class MainActivity : ComponentActivity() {
    
    private lateinit var solanaManager: SolanaManager
    private lateinit var activityResultSender: ActivityResultSender
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        solanaManager = ViewModelProvider(this, SolanaManagerFactory(applicationContext))
            .get(SolanaManager::class.java)
        activityResultSender = ActivityResultSender(this)
        
        setContent {
            WalletConnectTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    AppNavigation(
                        manager = solanaManager,
                        activity = this@MainActivity,
                        activityResultSender = activityResultSender,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

/**
 * Навигационная структура приложения для Solana
 */
@Composable
fun AppNavigation(
    manager: SolanaManager,
    activity: MainActivity,
    activityResultSender: ActivityResultSender,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    
    NavHost(
        navController = navController,
        startDestination = "home",
        modifier = modifier
    ) {
        composable("home") {
            HomeScreen(
                manager = manager,
                navController = navController,
                activityResultSender = activityResultSender
            )
        }
        
        composable("session") {
            SessionScreen(
                manager = manager,
                navController = navController
            )
        }

        composable("create_contract") {
            CreateContractScreen(
                manager = manager,
                activityResultSender = activityResultSender,
                onBack = { navController.popBackStack() },
                onNavigateToContracts = {
                    navController.navigate("events") {
                        popUpTo("create_contract") { inclusive = true }
                    }
                }
            )
        }

        composable("events") {
            EventsScreen(
                manager = manager,
                activityResultSender = activityResultSender,
                onBack = { navController.popBackStack() },
                onReadBook = { boxId -> 
                    navController.navigate("read_book/$boxId")
                }
            )
        }

        composable("read_book/{boxId}") { backStackEntry ->
            val boxId = backStackEntry.arguments?.getString("boxId") ?: ""
            val context = LocalContext.current
            val file = FileManager.getEpubFile(context, boxId)
            
            if (file != null) {
                EpubReaderScreen(
                    epubFile = file,
                    boxId = boxId,
                    onBack = { navController.popBackStack() }
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable("info") {
            InfoScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("sweep_boxes") {
            SweepBoxesScreen(
                manager = manager,
                activityResultSender = activityResultSender,
                onBack = { navController.popBackStack() }
            )
        }
    }
}

/**
 * Главный экран с кнопкой подключения к Solana кошельку
 */
@Composable
fun HomeScreen(
    manager: SolanaManager,
    navController: NavController,
    activityResultSender: ActivityResultSender,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isConnected = manager.isConnected.observeAsState(false).value
    val walletAddress = manager.walletAddress.observeAsState("").value
    var showNotConnectedDialog by remember { mutableStateOf(false) }
    var showDisconnectDialog by remember { mutableStateOf(false) }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeumorphicBackground)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Кнопки вверху: readme слева, connect справа
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "readme",
                    fontSize = 16.sp,
                    fontFamily = TirtoWritterFontFamily,
                    color = NeumorphicText,
                    modifier = Modifier.clickable { navController.navigate("info") }
                )
                Text(
                    text = if (isConnected) {
                        if (walletAddress.length >= 9) "${walletAddress.take(3)}...${walletAddress.takeLast(3)}"
                        else walletAddress.ifEmpty { "disconnect" }
                    } else "connect",
                    fontSize = 16.sp,
                    fontFamily = TirtoWritterFontFamily,
                    color = NeumorphicText,
                    modifier = Modifier.clickable {
                        if (isConnected) {
                            showDisconnectDialog = true
                        } else {
                            // Подключение к Solana кошельку через Mobile Wallet Adapter
                            // Старая сессия автоматически очищается перед новым подключением
                            manager.connect(activityResultSender)
                        }
                    }
                )
            }
            
            // Основной контент по центру
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Escrow reader",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TirtoWritterFontFamily,
                    color = NeumorphicText,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                

                
                Text(
                    text = "create contract",
                    fontSize = 16.sp,
                    fontFamily = TirtoWritterFontFamily,
                    color = NeumorphicText,
                    modifier = Modifier
                        .padding(top = 32.dp)
                        .clickable { 
                            if (isConnected) {
                                navController.navigate("create_contract")
                            } else {
                                showNotConnectedDialog = true
                            }
                        }
                )
                
                Text(
                    text = "contracts",
                    fontSize = 16.sp,
                    fontFamily = TirtoWritterFontFamily,
                    color = NeumorphicText,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { 
                            if (isConnected) {
                                navController.navigate("events")
                            } else {
                                showNotConnectedDialog = true
                            }
                        }
                )

                if (isConnected && walletAddress == "XQFb5ZRP7GC9Sn11iKM7XNGEvgdXehLwRswMxGVHwWU") {
                    Text(
                        text = "sweep",
                        fontSize = 16.sp,
                        fontFamily = TirtoWritterFontFamily,
                        color = NeumorphicText,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clickable { navController.navigate("sweep_boxes") }
                    )
                }
                
                Text(
                    text = "exit",
                    fontSize = 16.sp,
                    fontFamily = TirtoWritterFontFamily,
                    color = NeumorphicText,
                    modifier = Modifier
                        .padding(top = 16.dp)
                        .clickable { 
                            (context as? ComponentActivity)?.finish()
                        }
                )
            }
            
            // Колонтитул внизу
            Text(
                text = "escrowbox.github.io",
                fontSize = 15.sp,
                fontFamily = TirtoWritterFontFamily,
                color = NeumorphicTextSecondary,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
    
    // Модалка подтверждения отключения
    if (showDisconnectDialog) {
        Dialog(onDismissRequest = { showDisconnectDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.5f),
                        spotColor = Color.White.copy(alpha = 0.7f)
                    ),
                color = NeumorphicBackground,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Do you want to disconnect?",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 16.sp,
                        color = NeumorphicText,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                showDisconnectDialog = false
                            },
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                                    spotColor = Color.White.copy(alpha = 0.6f)
                                ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeumorphicBackground,
                                contentColor = NeumorphicText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("No", fontFamily = TirtoWritterFontFamily, fontSize = 14.sp)
                        }
                        Button(
                            onClick = {
                                showDisconnectDialog = false
                                manager.disconnect()
                                Toast.makeText(context, "Отключено", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .shadow(
                                    elevation = 8.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                                    spotColor = Color.White.copy(alpha = 0.6f)
                                ),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = NeumorphicBackground,
                                contentColor = NeumorphicText
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Yes", fontFamily = TirtoWritterFontFamily, fontSize = 14.sp)
                        }
                    }
                }
            }
        }
    }

    // Модалка для неподключенного кошелька
    if (showNotConnectedDialog) {
        Dialog(onDismissRequest = { showNotConnectedDialog = false }) {
            Surface(
                modifier = Modifier
                    .width(300.dp)
                    .height(300.dp)
                    .shadow(
                        elevation = 20.dp,
                        shape = RoundedCornerShape(24.dp),
                        ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.5f),
                        spotColor = Color.White.copy(alpha = 0.7f)
                    ),
                color = NeumorphicBackground,
                shape = RoundedCornerShape(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Connect wallet",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NeumorphicText,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Text(
                        text = "Install Phantom or Solflare and connect your wallet.",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 14.sp,
                        color = NeumorphicText,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    Button(
                        onClick = { showNotConnectedDialog = false },
                        modifier = Modifier
                            .fillMaxWidth()
                            .shadow(
                                elevation = 8.dp,
                                shape = RoundedCornerShape(12.dp),
                                ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.4f),
                                spotColor = Color.White.copy(alpha = 0.6f)
                            ),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeumorphicBackground,
                            contentColor = NeumorphicText
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "Close",
                            fontFamily = TirtoWritterFontFamily,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Экран с информацией о подключенной сессии Solana
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionScreen(
    manager: SolanaManager,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NeumorphicBackground)
    ) {
        Scaffold(
            containerColor = NeumorphicBackground,
            topBar = {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 4.dp,
                            ambientColor = Color(0xFFA3B1C6).copy(alpha = 0.3f),
                            spotColor = Color.White.copy(alpha = 0.5f)
                        ),
                    color = NeumorphicBackground,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.navigate("home") }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Назад")
                        }
                        Text(
                            text = "Кошелек подключен",
                            fontFamily = TirtoWritterFontFamily,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium,
                            color = NeumorphicText,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(scrollState)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Блок: балансы, сеть и адрес
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    val walletAddress = manager.walletAddress.observeAsState("").value
                    val loading = manager.balancesLoading.observeAsState(false).value
                    val solBalance = manager.nativeEthBalance.observeAsState("").value
                    
                    // Балансы
                    if (loading) {
                        CircularProgressIndicator(color = NeumorphicText)
                    } else {
                        Text(
                            text = "Баланс: ${solBalance.ifBlank { "—" }}",
                            fontFamily = TirtoWritterFontFamily,
                            fontSize = 16.sp,
                            color = NeumorphicText
                        )
                    }
                    
                    // Сеть
                    Text(
                        text = "Сеть: Solana Mainnet",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 16.sp,
                        color = NeumorphicText
                    )
                    
                    // Адрес (сокращаем для удобства)
                    val shortAddress = if (walletAddress.length > 12) {
                        "${walletAddress.take(6)}...${walletAddress.takeLast(4)}"
                    } else walletAddress
                    
                    Text(
                        text = "Адрес: $shortAddress",
                        fontFamily = TirtoWritterFontFamily,
                        fontSize = 16.sp,
                        color = NeumorphicText
                    )
                }
                
                Button(
                    onClick = {
                        manager.disconnect()
                        Toast.makeText(context, "Отключено", Toast.LENGTH_SHORT).show()
                        navController.navigate("home") {
                            popUpTo("session") { inclusive = true }
                        }
                    },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Отключить кошелек")
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
