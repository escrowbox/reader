package com.example.walletconnect

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.walletconnect.ui.hooks.TxStatus
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender
import com.solana.mobilewalletadapter.clientlib.ConnectionIdentity
import com.solana.mobilewalletadapter.clientlib.MobileWalletAdapter
import com.solana.mobilewalletadapter.clientlib.Solana
import com.solana.mobilewalletadapter.clientlib.TransactionResult
import com.solana.publickey.SolanaPublicKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import timber.log.Timber
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.funkatronics.encoders.Base58
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters
import com.example.walletconnect.utils.BoxMetadataStore

/**
 * SolanaManager - управляет подключением к Solana кошельку и взаимодействием с Escrow программой.
 * 
 * Program ID: 6Qz6EaxsD6LZewhM5NAw8ZkHTFcEju2XUAkbnpj9ZeAW
 * Network: Solana Mainnet
 */
class SolanaManager(private val context: Context) : ViewModel() {

    private val _isConnected = MutableLiveData(false)
    val isConnected: LiveData<Boolean> = _isConnected

    private val _walletAddress = MutableLiveData("")
    val walletAddress: LiveData<String> = _walletAddress

    private val _errorMessage = MutableLiveData("")
    val errorMessage: LiveData<String> = _errorMessage

    private val _balancesLoading = MutableLiveData(false)
    val balancesLoading: LiveData<Boolean> = _balancesLoading

    private val _nativeSolBalance = MutableLiveData("")
    val nativeEthBalance: LiveData<String> = _nativeSolBalance  // Оставляем имя для совместимости с UI

    private val _transactionStatus = MutableLiveData("")
    val transactionStatus: LiveData<String> = _transactionStatus

    private val _txStatus = MutableStateFlow(TxStatus.IDLE)
    val txStatusFlow: StateFlow<TxStatus> = _txStatus.asStateFlow()

    private var pendingTxSignature: String? = null
    private var currentPendingContractId: String? = null
    private var currentBoxPda: ByteArray? = null
    private var currentOpeningBoxId: String? = null

    private val _boxCreatedEvents = MutableLiveData<List<BoxCreatedEvent>>(emptyList())
    val boxCreatedEvents: LiveData<List<BoxCreatedEvent>> = _boxCreatedEvents

    private val _boxOpenedEvents = MutableLiveData<List<BoxOpenedEvent>>(emptyList())
    val boxOpenedEvents: LiveData<List<BoxOpenedEvent>> = _boxOpenedEvents
    
    // Сырой ответ RPC для отладки
    private val _rawRpcResponse = MutableLiveData<String>("")
    val rawRpcResponse: LiveData<String> = _rawRpcResponse

    private val _pendingContracts = MutableLiveData<List<PendingContract>>(emptyList())
    val pendingContracts: LiveData<List<PendingContract>> = _pendingContracts

    private val _openingBoxId = MutableLiveData<String?>(null)
    val openingBoxId: LiveData<String?> = _openingBoxId

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // SharedPreferences для сохранения сессии
    private val prefs = context.getSharedPreferences("solana_wallet_session", Context.MODE_PRIVATE)

    // ConnectionIdentity - используется для создания MobileWalletAdapter
    private val connectionIdentity = ConnectionIdentity(
        identityUri = IDENTITY_URI,
        iconUri = ICON_URI,
        identityName = IDENTITY_NAME
    )
    
    private var authToken: String? = null
    private var connectedPublicKey: ByteArray? = null
    
    init {
        // Восстанавливаем сессию при старте (пользователь остается подключенным после перезапуска)
        restoreSession()
        
        // Загружаем pending контракты
        loadPendingContracts()
    }

    /**
     * Событие создания бокса
     */
    data class BoxCreatedEvent(
        val sender: String,
        val id: String,
        val deadline: BigInteger,
        val amount: BigInteger,
        val transactionHash: String,
        val blockNumber: BigInteger
    )

    /**
     * Событие открытия бокса
     */
    data class BoxOpenedEvent(
        val sender: String,
        val id: String,
        val transactionHash: String,
        val blockNumber: BigInteger
    )

    /**
     * Pending контракт
     */
    data class PendingContract(
        val id: String,
        val deadline: BigInteger,
        val amount: BigInteger,
        val txHash: String?,
        val timestamp: Long = System.currentTimeMillis()
    )

    companion object {
        // Solana Mainnet RPC
        const val SOLANA_RPC_URL = "https://api.mainnet-beta.solana.com"
        
        // Escrow Program ID
        const val PROGRAM_ID = "6Qz6EaxsD6LZewhM5NAw8ZkHTFcEju2XUAkbnpj9ZeAW"
        
        // System Program ID
        const val SYSTEM_PROGRAM_ID = "11111111111111111111111111111111"
        
        // SPL Token Program ID
        const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
        
        // Associated Token Account Program ID
        const val ASSOCIATED_TOKEN_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
        
        // Metaplex Token Metadata Program ID
        const val METADATA_PROGRAM_ID = "metaqbxxUerdq28cj1RbAWkYQm3ybzjb6a8bt518x1s"
        
        // Количество знаков после запятой для UI
        const val UI_FRACTION_DIGITS = 6
        
        // Lamports в одном SOL
        const val LAMPORTS_PER_SOL = 1_000_000_000L

        // Identity URI - Максимально уникальный URI для избежания кэша Phantom
        // Используем timestamp чтобы Phantom точно воспринял как новое приложение
        val IDENTITY_URI: Uri = Uri.parse("https://escrowbox.github.io/")
        const val IDENTITY_NAME = "Escrow reader"  
        // ICON_URI - относительный путь для favicon
        val ICON_URI: Uri = Uri.parse("favicon.ico")
    }

    private val httpClient: OkHttpClient by lazy {
        OkHttpClient.Builder().build()
    }

    private val programIdBytes: ByteArray by lazy {
        Base58.decode(PROGRAM_ID)
    }

    private val systemProgramIdBytes: ByteArray by lazy {
        Base58.decode(SYSTEM_PROGRAM_ID)
    }
    
    private val tokenProgramIdBytes: ByteArray by lazy {
        Base58.decode(TOKEN_PROGRAM_ID)
    }
    
    private val associatedTokenProgramIdBytes: ByteArray by lazy {
        Base58.decode(ASSOCIATED_TOKEN_PROGRAM_ID)
    }

    /**
     * Подключается к кошельку через Mobile Wallet Adapter
     * Должен вызываться из Main потока
     */
    fun connect(sender: ActivityResultSender) {
        mainScope.launch {
            try {
                Timber.d("🔌 Начало подключения к кошельку")
                Timber.d("   Identity URI: $IDENTITY_URI")
                Timber.d("   Identity Name: $IDENTITY_NAME")
                Timber.d("   Icon URI: $ICON_URI")
                Timber.d("   Chain: НЕ УКАЗАН (по умолчанию должен быть mainnet)")
                
                // ВСЕГДА очищаем старую сессию перед новым подключением
                // Это гарантирует, что каждый кошелек получает чистую авторизацию
                Timber.d("🔄 Очистка старой сессии перед подключением")
                authToken = null
                connectedPublicKey = null
                clearSession()
                
                _transactionStatus.postValue("Подключение к кошельку...")

                // Создаем НОВЫЙ экземпляр MobileWalletAdapter для каждого подключения
                // ВАЖНО: устанавливаем blockchain = Solana.Mainnet чтобы transact() 
                // автоматически авторизовал с chain = "solana:mainnet"
                val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                    blockchain = Solana.Mainnet
                }
                
                // transact() сам вызывает authorize() с blockchain.fullName = "solana:mainnet"
                // Результат авторизации передается в блок как параметр authResult
                val result = walletAdapter.transact(sender) { authResult ->
                    authResult
                }

                Timber.d("📱 Получен результат от кошелька: ${result.javaClass.simpleName}")
                
                when (result) {
                    is TransactionResult.Success -> {
                        val authResult = result.payload
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        
                        val address = Base58.encodeToString(authResult.publicKey)
                        Timber.d("✅ Успешная авторизация!")
                        Timber.d("   Адрес: $address")
                        Timber.d("   AuthToken: ${authResult.authToken.take(20)}...")
                        
                        _walletAddress.postValue(address)
                        _isConnected.postValue(true)
                        _errorMessage.postValue("")
                        _transactionStatus.postValue("")
                        
                        // Сохраняем сессию для восстановления после ребилда
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        refreshBalances()
                    }
                    is TransactionResult.Failure -> {
                        Timber.e("❌ TransactionResult.Failure")
                        Timber.e("   Сообщение: ${result.e.message}")
                        Timber.e("   Тип: ${result.e.javaClass.simpleName}")
                        Timber.e("   Причина: ${result.e.cause?.message}")
                        result.e.printStackTrace()
                        _errorMessage.postValue("Ошибка: ${result.e.message}")
                        _transactionStatus.postValue("")
                    }
                    is TransactionResult.NoWalletFound -> {
                        Timber.e("❌ TransactionResult.NoWalletFound")
                        _errorMessage.postValue("Кошелек не найден")
                        _transactionStatus.postValue("")
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "❌ Исключение при подключении")
                _errorMessage.postValue("Ошибка: ${e.message}")
                _transactionStatus.postValue("")
            }
        }
    }

    /**
     * Отключается от кошелька
     */
    fun disconnect() {
        _isConnected.postValue(false)
        _walletAddress.postValue("")
        authToken = null
        connectedPublicKey = null
        clearBalances()
        clearSession()
        // Timber.d("🔌 Отключен от кошелька")
    }

    /**
     * Сохраняет сессию в SharedPreferences
     */
    private fun saveSession(publicKey: ByteArray, authToken: String, address: String) {
        try {
            prefs.edit().apply {
                putString("public_key", Base58.encodeToString(publicKey))
                putString("auth_token", authToken)
                putString("wallet_address", address)
                putBoolean("is_connected", true)
                apply()
            }
            // Timber.d("💾 Сессия сохранена")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка сохранения сессии")
        }
    }

    /**
     * Восстанавливает сессию из SharedPreferences
     */
    private fun restoreSession() {
        try {
            val isConnected = prefs.getBoolean("is_connected", false)
            // Timber.d("🔍 Восстановление сессии: isConnected=$isConnected")
            if (!isConnected) {
                // Timber.d("⚠️ Сессия не сохранена в SharedPreferences")
                return
            }

            val publicKeyStr = prefs.getString("public_key", null)
            val savedAuthToken = prefs.getString("auth_token", null)
            val address = prefs.getString("wallet_address", null)
            
            // Timber.d("🔍 Данные из SharedPreferences:")
            // Timber.d("   publicKey: ${if (publicKeyStr != null) "есть" else "null"}")
            // Timber.d("   authToken: ${if (savedAuthToken != null) "есть (${savedAuthToken.take(20)}...)" else "null"}")
            // Timber.d("   address: $address")

            if (publicKeyStr == null || savedAuthToken == null || address == null) {
                Timber.w("⚠️ Не все данные сессии найдены в SharedPreferences")
                return
            }

            connectedPublicKey = Base58.decode(publicKeyStr)
            authToken = savedAuthToken
            _walletAddress.postValue(address)
            _isConnected.postValue(true)
            
            // Timber.d("✅ Сессия восстановлена: $address")
            // Timber.d("   authToken в памяти: ${if (authToken != null) "есть (${authToken!!.take(20)}...)" else "null"}")
            refreshBalances()
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка восстановления сессии")
            clearSession()
        }
    }

    /**
     * Очищает сохраненную сессию
     */
    private fun clearSession() {
        try {
            prefs.edit().clear().apply()
            // Timber.d("🗑️ Сессия очищена")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка очистки сессии")
        }
    }
    
    /**
     * Сохраняет pending контракты в SharedPreferences.
     * Принимает список напрямую, чтобы избежать race condition при использовании postValue.
     * (postValue обновляет LiveData.value асинхронно, поэтому чтение .value сразу после postValue
     * вернёт старое значение)
     */
    private fun savePendingContracts(contracts: List<PendingContract>) {
        try {
            val json = org.json.JSONArray()
            
            contracts.forEach { pending ->
                val obj = org.json.JSONObject().apply {
                    put("id", pending.id)
                    put("deadline", pending.deadline.toString())
                    put("amount", pending.amount.toString())
                    put("txHash", pending.txHash ?: "")
                    put("timestamp", pending.timestamp)
                }
                json.put(obj)
            }
            
            prefs.edit().putString("pending_contracts", json.toString()).apply()
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка сохранения pending")
        }
    }
    
    /**
     * Загружает pending контракты из SharedPreferences
     */
    private fun loadPendingContracts() {
        try {
            val jsonStr = prefs.getString("pending_contracts", null) ?: return
            val json = org.json.JSONArray(jsonStr)
            val contracts = mutableListOf<PendingContract>()
            
            val now = System.currentTimeMillis()
            val maxAgeMs = 10 * 60 * 1000L // 10 минут — на Solana blockhash живёт ~60 сек, 10 мин с запасом
            
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val timestamp = obj.getLong("timestamp")
                
                // Автоочистка: пропускаем pending контракты старше 10 минут
                if (now - timestamp > maxAgeMs) {
                    Timber.d("🗑️ Автоочистка устаревшего pending контракта (возраст: ${(now - timestamp) / 60000} мин)")
                    continue
                }
                
                val contract = PendingContract(
                    id = obj.getString("id"),
                    deadline = BigInteger(obj.getString("deadline")),
                    amount = BigInteger(obj.getString("amount")),
                    txHash = obj.getString("txHash").takeIf { it.isNotEmpty() },
                    timestamp = timestamp
                )
                contracts.add(contract)
            }
            
            _pendingContracts.postValue(contracts)
            
            // Пересохраняем, если какие-то контракты были удалены при автоочистке
            if (contracts.size < json.length()) {
                savePendingContracts(contracts)
                Timber.d("🗑️ Автоочистка: удалено ${json.length() - contracts.size} устаревших pending контрактов")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка загрузки pending")
        }
    }

    fun getSelectedAddress(): String = _walletAddress.value ?: ""

    /**
     * Обновляет баланс SOL
     */
    fun refreshBalances() {
        val address = getSelectedAddress()
        if (address.isBlank()) return

        _balancesLoading.postValue(true)
        scope.launch {
            try {
                val balance = getBalance(address)
                val sol = formatSol(balance, UI_FRACTION_DIGITS)
                _nativeSolBalance.postValue("$sol SOL")
                
                // Загружаем боксы пользователя (из локального хранилища)
                fetchUserBoxes()
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка обновления баланса")
                _errorMessage.postValue("Ошибка RPC: ${e.message}")
            } finally {
                _balancesLoading.postValue(false)
            }
        }
    }

    /**
     * Получает баланс аккаунта через RPC
     */
    private suspend fun getBalance(address: String): Long = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getBalance")
            put("params", JSONArray().apply {
                put(address)
            })
        }

        val request = Request.Builder()
            .url(SOLANA_RPC_URL)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val result = JSONObject(responseBody)
        
        if (result.has("error")) {
            throw Exception(result.getJSONObject("error").getString("message"))
        }
        
        result.getJSONObject("result").getLong("value")
    }

    /**
     * Данные о токене: баланс и decimals
     */
    data class TokenInfo(
        val balance: Long,      // raw amount (без учета decimals)
        val decimals: Int,      // количество знаков после запятой
        val uiAmount: Double    // форматированный баланс
    )
    
    /**
     * Получает баланс токена и decimals для указанного mint address
     * @param ownerAddress адрес владельца
     * @param mintAddress адрес mint токена
     * @return TokenInfo или null если токен не найден
     */
    suspend fun getTokenBalance(ownerAddress: String, mintAddress: String): TokenInfo? = withContext(Dispatchers.IO) {
        try {
            // Получаем ATA (Associated Token Account) для владельца и mint
            val ownerBytes = Base58.decode(ownerAddress)
            val mintBytes = Base58.decode(mintAddress)
            val ataBytes = getAssociatedTokenAddress(ownerBytes, mintBytes)
                ?: return@withContext null
            val ataAddress = Base58.encodeToString(ataBytes)
            
            Timber.d("🔍 Получение баланса токена: owner=$ownerAddress, mint=$mintAddress, ata=$ataAddress")
            
            // Получаем данные ATA аккаунта
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getTokenAccountBalance")
                put("params", JSONArray().apply {
                    put(ataAddress)
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                // Токен аккаунт не существует - баланс 0
                Timber.d("⚠️ Token account не существует, возвращаем баланс 0")
                // Нужно получить decimals от mint
                val decimals = getTokenDecimals(mintAddress)
                return@withContext TokenInfo(0L, decimals, 0.0)
            }
            
            val value = result.getJSONObject("result").getJSONObject("value")
            val amount = value.getString("amount").toLong()
            val decimals = value.getInt("decimals")
            val uiAmount = value.optDouble("uiAmount", 0.0)
            
            Timber.d("✅ Баланс токена: $amount (decimals: $decimals, ui: $uiAmount)")
            
            TokenInfo(amount, decimals, uiAmount)
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения баланса токена")
            null
        }
    }
    
    /**
     * Получает decimals для mint токена
     */
    suspend fun getTokenDecimals(mintAddress: String): Int = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getAccountInfo")
                put("params", JSONArray().apply {
                    put(mintAddress)
                    put(JSONObject().apply {
                        put("encoding", "jsonParsed")
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext 9 // Default to 9 (like SOL)
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                return@withContext 9
            }
            
            val value = result.getJSONObject("result").optJSONObject("value")
                ?: return@withContext 9
            val data = value.optJSONObject("data")
                ?: return@withContext 9
            val parsed = data.optJSONObject("parsed")
                ?: return@withContext 9
            val info = parsed.optJSONObject("info")
                ?: return@withContext 9
            
            info.optInt("decimals", 9)
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения decimals токена")
            9 // Default to 9
        }
    }

    /**
     * Получает последний blockhash для транзакции
     */
    private suspend fun getLatestBlockhash(): String = withContext(Dispatchers.IO) {
        val json = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", 1)
            put("method", "getLatestBlockhash")
            put("params", JSONArray())
        }

        val request = Request.Builder()
            .url(SOLANA_RPC_URL)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        val response = httpClient.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("Empty response")
        val result = JSONObject(responseBody)
        
        if (result.has("error")) {
            throw Exception(result.getJSONObject("error").getString("message"))
        }
        
        result.getJSONObject("result").getJSONObject("value").getString("blockhash")
    }

    /**
     * Вычисляет PDA для бокса
     * seeds = [b"box", sender.key, id.as_ref()]
     */
    fun findBoxPda(senderPubkey: ByteArray, idPubkey: ByteArray): Pair<ByteArray, Int>? {
        val seeds = listOf(
            "box".toByteArray(),
            senderPubkey,
            idPubkey
        )
        return findProgramAddress(seeds, programIdBytes)
    }
    
    /**
     * Вычисляет PDA для token бокса
     * seeds = [b"token_box", sender.key, id.as_ref()]
     */
    fun findTokenBoxPda(senderPubkey: ByteArray, idPubkey: ByteArray): Pair<ByteArray, Int>? {
        val seeds = listOf(
            "token_box".toByteArray(),
            senderPubkey,
            idPubkey
        )
        return findProgramAddress(seeds, programIdBytes)
    }
    
    /**
     * Вычисляет PDA для vault authority
     * seeds = [b"vault", token_box_pda.as_ref()]
     */
    fun findVaultPda(tokenBoxPda: ByteArray): Pair<ByteArray, Int>? {
        val seeds = listOf(
            "vault".toByteArray(),
            tokenBoxPda
        )
        return findProgramAddress(seeds, programIdBytes)
    }
    
    /**
     * Вычисляет Associated Token Address
     * Формула: PDA([owner, TOKEN_PROGRAM_ID, mint], ATA_PROGRAM_ID)
     */
    fun getAssociatedTokenAddress(owner: ByteArray, mint: ByteArray): ByteArray? {
        val seeds = listOf(
            owner,
            tokenProgramIdBytes,
            mint
        )
        val result = findProgramAddress(seeds, associatedTokenProgramIdBytes)
        return result?.first
    }

    /**
     * Находит PDA (Program Derived Address)
     * Реализация алгоритма findProgramAddress
     */
    private fun findProgramAddress(seeds: List<ByteArray>, programId: ByteArray): Pair<ByteArray, Int>? {
        for (bump in 255 downTo 0) {
            try {
                val seedsWithBump = seeds + listOf(byteArrayOf(bump.toByte()))
                val address = createProgramAddress(seedsWithBump, programId)
                if (address != null && !isOnCurve(address)) {
                    return Pair(address, bump)
                }
            } catch (e: Exception) {
                continue
            }
        }
        return null
    }

    /**
     * Создает адрес программы из seeds
     */
    private fun createProgramAddress(seeds: List<ByteArray>, programId: ByteArray): ByteArray? {
        val buffer = ByteBuffer.allocate(seeds.sumOf { it.size } + programId.size + "ProgramDerivedAddress".length)
        seeds.forEach { buffer.put(it) }
        buffer.put(programId)
        buffer.put("ProgramDerivedAddress".toByteArray())
        
        val digest = SHA256Digest()
        val hash = ByteArray(32)
        digest.update(buffer.array(), 0, buffer.position())
        digest.doFinal(hash, 0)
        
        return hash
    }

    /**
     * Проверяет, находится ли точка на кривой ed25519
     * PDA должен быть OFF curve
     */
    private fun isOnCurve(publicKey: ByteArray): Boolean {
        if (publicKey.size != 32) return true // Неверный размер - считаем on curve
        
        try {
            // Пытаемся создать Ed25519 публичный ключ из байтов
            // Если получится - точка ON curve, если ошибка - OFF curve
            val keyParams = Ed25519PublicKeyParameters(publicKey)
            return true // Успешно создали - точка ON curve
        } catch (e: Exception) {
            // Не удалось создать ключ - точка OFF curve (подходит для PDA)
            return false
        }
    }

    /**
     * Добавляет pending контракт синхронно
     */
    fun addPendingContractSync(id: String, deadlineDays: Int, amount: BigInteger) {
        // Timber.d("➕ Добавление pending: ${id.take(20)}..., deadline=$deadlineDays days, amount=$amount")
        
        val current = _pendingContracts.value ?: emptyList()
        
        // Проверяем, не существует ли уже pending контракт с таким ID
        if (current.any { it.id.equals(id, ignoreCase = true) }) {
            // Timber.d("⚠️ Pending контракт с таким ID уже существует, пропускаем добавление")
            currentPendingContractId = id
            return
        }
        
        val pending = PendingContract(
            id = id,
            deadline = BigInteger.valueOf(deadlineDays.toLong()),
            amount = amount,
            txHash = null
        )
        
        val updated = current + pending
        _pendingContracts.value = updated
        currentPendingContractId = id
        
        // Сохраняем в SharedPreferences
        savePendingContracts(updated)
        
        // Timber.d("✅ Pending добавлен. Всего: ${_pendingContracts.value?.size ?: 0}")
    }

    /**
     * Удаляет pending контракт
     */
    fun removePendingContract(id: String) {
        // Timber.d("🗑️ Удаление pending: ${id.take(20)}...")
        val current = _pendingContracts.value ?: emptyList()
        val filtered = current.filter { !it.id.equals(id, ignoreCase = true) }
        _pendingContracts.postValue(filtered)
        
        // Сохраняем отфильтрованный список напрямую, а не через чтение LiveData.value,
        // т.к. postValue асинхронный и .value ещё содержит старые данные
        savePendingContracts(filtered)
        
        // Timber.d("✅ Осталось pending: ${filtered.size}")
    }

    /**
     * Отправляет транзакцию createBox
     * 
     * @param id Публичный ключ бокса (сгенерированный)
     * @param deadlineDays Количество дней до дедлайна
     * @param amount Количество SOL в lamports
     */
    fun sendCreateBoxWithStatus(
        id: String,
        deadlineDays: Int,
        amount: BigInteger,
        sender: ActivityResultSender
    ) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Кошелек не подключен")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
            return
        }

        _txStatus.value = TxStatus.SIGNING
        
        // Сначала подготавливаем данные в IO потоке
        scope.launch {
            try {
                // Timber.d("🚀🚀🚀 НАЧАЛО sendCreateBoxWithStatus 🚀🚀🚀")
                // Timber.d("   id=$id, deadlineDays=$deadlineDays, amount=$amount")
                _transactionStatus.postValue("Создание транзакции...")
                
                val senderPubkeyBytes = Base58.decode(owner)
                val idPubkeyBytes = Base58.decode(id)
                
                // Вычисляем PDA для бокса
                val boxPdaResult = findBoxPda(senderPubkeyBytes, idPubkeyBytes)
                    ?: throw Exception("Не удалось вычислить PDA для бокса")
                val (boxPdaBytes, bump) = boxPdaResult
                
                // Сохраняем boxPda для проверки после подтверждения
                currentBoxPda = boxPdaBytes
                
                // Timber.d("📦 Box PDA: ${Base58.encodeToString(boxPdaBytes)}, bump: $bump")
                
                // Создаем instruction data для CreateBox
                // variant (1 byte) + id (32 bytes) + deadline_days (2 bytes) + amount (8 bytes)
                val instructionData = ByteBuffer.allocate(1 + 32 + 2 + 8).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put(1) // CreateBox variant
                    put(idPubkeyBytes)
                    putShort(deadlineDays.toShort())
                    putLong(amount.toLong())
                }
                
                // Получаем blockhash
                val blockhash = getLatestBlockhash()
                // Timber.d("📋 Blockhash: $blockhash")
                
                // Создаем сериализованную транзакцию вручную
                val serializedTx = buildTransaction(
                    feePayer = senderPubkeyBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(senderPubkeyBytes, isSigner = true, isWritable = true),
                                AccountMeta(boxPdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(systemProgramIdBytes, isSigner = false, isWritable = false)
                            ),
                            data = instructionData.array()
                        )
                    )
                )
                
                Timber.d("🔐🔐🔐 ПЕРЕД ПОДПИСАНИЕМ ТРАНЗАКЦИИ 🔐🔐🔐")
                Timber.d("   Sender: $owner")
                Timber.d("   Box PDA: ${Base58.encodeToString(boxPdaBytes)}")
                Timber.d("   Deadline: $deadlineDays days")
                Timber.d("   Amount: $amount lamports")
                Timber.d("   Transaction size: ${serializedTx.size} bytes")
                
                // ДИАГНОСТИКА: Симулируем транзакцию через RPC перед отправкой в кошелёк
                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("❌ СИМУЛЯЦИЯ ПРОВАЛИЛАСЬ: $simError")
                    _errorMessage.postValue("Simulation failed: $simError")
                    // Продолжаем всё равно, чтобы показать ошибку в кошельке тоже
                } else {
                    Timber.d("✅ Симуляция успешна, отправляем в кошелёк")
                }
                
                _transactionStatus.postValue("Подписание в кошельке...")
                
                // Переключаемся на Main поток для вызова transact()
                withContext(Dispatchers.Main) {
                    // Timber.d("📤📤📤 ОТПРАВКА CreateBox ТРАНЗАКЦИИ 📤📤📤")
                    // Timber.d("   Размер: ${serializedTx.size} bytes")
                    // Timber.d("   Base58: ${Base58.encodeToString(serializedTx).take(100)}...")
                    // Timber.d("   Sender: ${owner}")
                    // Timber.d("   Box PDA: ${Base58.encodeToString(boxPdaBytes)}")
                    // Timber.d("   Deadline: $deadlineDays days")
                    // Timber.d("   Amount: $amount lamports")
                    
                    // Проверяем состояние сессии ПЕРЕД вызовом transact()
                    // Timber.d("🚀 СОСТОЯНИЕ СЕССИИ ПЕРЕД ПОДПИСАНИЕМ:")
                    // Timber.d("   isConnected: ${_isConnected.value}")
                    // Timber.d("   authToken: ${if (authToken != null) "есть (${authToken!!.take(20)}...)" else "null"}")
                    // Timber.d("   connectedPublicKey: ${if (connectedPublicKey != null) "есть" else "null"}")
                    
                    // Создаем экземпляр MobileWalletAdapter с blockchain = Solana.Mainnet
                    // transact() автоматически вызывает authorize с chain = "solana:mainnet"
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        // transact() уже авторизовал с chain = "solana:mainnet"
                        // Сохраняем данные сессии из автоматической авторизации
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        
                        // Timber.d("📝 Вызов signTransactions() для получения подписи")
                        try {
                            val txResult = signTransactions(arrayOf(serializedTx))
                            // Timber.d("✅ signTransactions вернул результат")
                            txResult
                        } catch (e: Exception) {
                            Timber.e(e, "❌ Ошибка в signTransactions")
                            throw e
                        }
                    }
                    
                    // Timber.d("🔍 Тип результата: ${signResult.javaClass.simpleName}")
                    
                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTransactions = signResult.payload.signedPayloads
                            // Timber.d("✅ TransactionResult.Success, количество подписанных транзакций: ${signedTransactions.size}")
                            
                            if (signedTransactions.isNotEmpty()) {
                                val signedTx = signedTransactions.first()
                                // Timber.d("📤 Отправляем подписанную транзакцию в Solana, размер: ${signedTx.size} bytes")
                                
                                // Отправляем подписанную транзакцию через RPC
                                scope.launch {
                                    try {
                                        _transactionStatus.postValue("Отправка транзакции...")
                                        
                                        // Ждем 2 секунды чтобы приложение вернулось в foreground
                                        // Timber.d("⏳ Ожидание возврата приложения в foreground...")
                                        delay(2000)
                                        
                                        // Проверяем интернет
                                        if (!isNetworkAvailable()) {
                                            throw Exception("Нет подключения к интернету")
                                        }
                                        // Timber.d("✅ Интернет доступен")
                                        
                                        // Пробуем отправить транзакцию с несколькими попытками
                                        var signature: String? = null
                                        var lastError: Exception? = null
                                        
                        for (attempt in 1..3) {
                            try {
                                // if (attempt > 1) Timber.d("📡 Попытка $attempt из 3")
                                signature = sendRawTransaction(signedTx)
                                // Timber.d("✅ Транзакция отправлена")
                                break // Успешно
                            } catch (e: Exception) {
                                lastError = e
                                val errorMsg = e.message?.take(100) ?: e.javaClass.simpleName
                                Timber.e("⚠️ Попытка $attempt: $errorMsg")
                                
                                if (attempt < 3) {
                                    delay(2000L)
                                }
                            }
                        }
                                        
                                        if (signature != null) {
                                            // Timber.d("✅ Транзакция отправлена: $signature")
                                            
                                            pendingTxSignature = signature
                                            _txStatus.value = TxStatus.MINING
                                            _transactionStatus.postValue("Транзакция отправлена!")
                                            
                                            waitForConfirmation(signature)
                                        } else {
                                            throw lastError ?: Exception("Не удалось отправить транзакцию")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "❌ Ошибка отправки транзакции")
                                        _txStatus.value = TxStatus.ERROR
                                        
                                        val errorMsg = when {
                                            e.message?.contains("UnknownHostException") == true || 
                                            e.message?.contains("No address associated with hostname") == true ->
                                                "Нет подключения к интернету"
                                            else -> "Ошибка отправки: ${e.message}"
                                        }
                                        
                                        _errorMessage.postValue(errorMsg)
                                        _transactionStatus.postValue(errorMsg)
                                        
                                        // Удаляем pending контракт при ошибке отправки
                                        currentPendingContractId?.let { removePendingContract(it) }
                                        currentPendingContractId = null
                                        currentBoxPda = null
                                        
                                        resetTxStatusAfterDelay()
                                    }
                                }
                            } else {
                                Timber.w("⚠️ Нет подписанных транзакций в результате")
                                throw Exception("Кошелек не вернул подписанную транзакцию")
                            }
                        }
                        is TransactionResult.Failure -> {
                            Timber.e("❌ TransactionResult.Failure: ${signResult.e.message}")
                            Timber.e("   Тип ошибки: ${signResult.e.javaClass.simpleName}")
                            throw Exception("Транзакция отклонена: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            Timber.e("❌ TransactionResult.NoWalletFound")
                            throw Exception("Кошелек не найден")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка создания бокса")
                _txStatus.value = TxStatus.ERROR
                _errorMessage.postValue("Ошибка: ${e.message}")
                _transactionStatus.postValue("")
                
                currentPendingContractId?.let { removePendingContract(it) }
                currentPendingContractId = null
                currentBoxPda = null
                
                resetTxStatusAfterDelay()
            }
        }
    }

    /**
     * Отправляет транзакцию createBoxToken для SPL токенов
     * 
     * @param id Публичный ключ бокса (сгенерированный)
     * @param deadlineDays Количество дней до дедлайна
     * @param amount Количество токенов
     * @param mintAddress Адрес mint токена
     * @param decimals Decimals токена (опционально, будет получено автоматически если не указано)
     * @param symbol Символ токена (опционально)
     */
    fun sendCreateBoxTokenWithStatus(
        id: String,
        deadlineDays: Int,
        amount: BigInteger,
        mintAddress: String,
        sender: ActivityResultSender,
        decimals: Int? = null,
        symbol: String? = null
    ) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Кошелек не подключен")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
            return
        }

        _txStatus.value = TxStatus.SIGNING
        
        scope.launch {
            try {
                Timber.d("🚀 НАЧАЛО sendCreateBoxTokenWithStatus")
                Timber.d("   id=$id, deadlineDays=$deadlineDays, amount=$amount, mint=$mintAddress")
                _transactionStatus.postValue("Создание token транзакции...")
                
                // Получаем метаданные токена (decimals и symbol)
                Timber.d("🔍 Получение метаданных для mint: $mintAddress")
                val metadata = getTokenMetadata(mintAddress)
                Timber.d("🔍 Результат getTokenMetadata: name=${metadata?.name}, symbol=${metadata?.symbol}, uri=${metadata?.uri}")
                
                // Сохраняем decimals токена
                val tokenDecimals = decimals ?: getMintDecimals(mintAddress)
                if (tokenDecimals != null) {
                    BoxMetadataStore.setDecimals(context, id, tokenDecimals)
                    Timber.d("✅ Сохранены decimals=$tokenDecimals для boxId=$id, mint=$mintAddress")
                } else {
                    Timber.e("❌ Не удалось получить decimals для mint=$mintAddress")
                }
                
                // Сохраняем symbol токена из метаданных
                val tokenSymbol = metadata?.symbol ?: symbol
                if (tokenSymbol != null) {
                    BoxMetadataStore.setSymbol(context, id, tokenSymbol)
                    Timber.d("✅ Сохранен symbol=$tokenSymbol для boxId=$id, mint=$mintAddress")
                } else {
                    Timber.e("❌ Не удалось получить symbol для mint=$mintAddress (metadata?.symbol=${metadata?.symbol}, param symbol=$symbol)")
                }
                
                val senderPubkeyBytes = Base58.decode(owner)
                val idPubkeyBytes = Base58.decode(id)
                val mintBytes = Base58.decode(mintAddress)
                
                // Вычисляем TokenBox PDA
                val tokenBoxPdaResult = findTokenBoxPda(senderPubkeyBytes, idPubkeyBytes)
                    ?: throw Exception("Не удалось вычислить TokenBox PDA")
                val (tokenBoxPdaBytes, _) = tokenBoxPdaResult
                
                // Вычисляем Vault PDA
                val vaultPdaResult = findVaultPda(tokenBoxPdaBytes)
                    ?: throw Exception("Не удалось вычислить Vault PDA")
                val (vaultAuthorityBytes, _) = vaultPdaResult
                
                // Вычисляем ATA для sender
                val senderAtaBytes = getAssociatedTokenAddress(senderPubkeyBytes, mintBytes)
                    ?: throw Exception("Не удалось вычислить Sender ATA")
                
                // Вычисляем ATA для vault
                val vaultAtaBytes = getAssociatedTokenAddress(vaultAuthorityBytes, mintBytes)
                    ?: throw Exception("Не удалось вычислить Vault ATA")
                
                Timber.d("📦 TokenBox PDA: ${Base58.encodeToString(tokenBoxPdaBytes)}")
                Timber.d("📦 Vault Authority: ${Base58.encodeToString(vaultAuthorityBytes)}")
                Timber.d("📦 Sender ATA: ${Base58.encodeToString(senderAtaBytes)}")
                Timber.d("📦 Vault ATA: ${Base58.encodeToString(vaultAtaBytes)}")
                
                // Сохраняем TokenBox PDA для последующей проверки в блокчейне
                currentBoxPda = tokenBoxPdaBytes
                
                // Создаем instruction data для CreateBoxToken
                // variant (1 byte) + id (32 bytes) + deadline_days (2 bytes) + amount (8 bytes)
                val instructionData = ByteBuffer.allocate(1 + 32 + 2 + 8).apply {
                    order(ByteOrder.LITTLE_ENDIAN)
                    put(4) // CreateBoxToken variant
                    put(idPubkeyBytes)
                    putShort(deadlineDays.toShort())
                    putLong(amount.toLong())
                }
                
                // Получаем blockhash
                val blockhash = getLatestBlockhash()
                
                // Создаем сериализованную транзакцию
                // Accounts: [sender, sender_token_account, token_box_pda, vault_ata, mint,
                //           vault_authority, token_program, associated_token_program, system_program]
                val serializedTx = buildTransaction(
                    feePayer = senderPubkeyBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(senderPubkeyBytes, isSigner = true, isWritable = true),
                                AccountMeta(senderAtaBytes, isSigner = false, isWritable = true),
                                AccountMeta(tokenBoxPdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(vaultAtaBytes, isSigner = false, isWritable = true),
                                AccountMeta(mintBytes, isSigner = false, isWritable = false),
                                AccountMeta(vaultAuthorityBytes, isSigner = false, isWritable = false),
                                AccountMeta(tokenProgramIdBytes, isSigner = false, isWritable = false),
                                AccountMeta(associatedTokenProgramIdBytes, isSigner = false, isWritable = false),
                                AccountMeta(systemProgramIdBytes, isSigner = false, isWritable = false)
                            ),
                            data = instructionData.array()
                        )
                    )
                )
                
                Timber.d("🔐🔐🔐 ПЕРЕД ПОДПИСАНИЕМ TOKEN ТРАНЗАКЦИИ 🔐🔐🔐")
                Timber.d("   Sender: $owner")
                Timber.d("   Mint: $mintAddress")
                Timber.d("   TokenBox PDA: ${Base58.encodeToString(tokenBoxPdaBytes)}")
                Timber.d("   Vault Authority: ${Base58.encodeToString(vaultAuthorityBytes)}")
                Timber.d("   Sender ATA: ${Base58.encodeToString(senderAtaBytes)}")
                Timber.d("   Vault ATA: ${Base58.encodeToString(vaultAtaBytes)}")
                Timber.d("   Deadline: $deadlineDays days")
                Timber.d("   Amount: $amount")
                Timber.d("   Transaction size: ${serializedTx.size} bytes")
                
                // ДИАГНОСТИКА: Симулируем транзакцию через RPC перед отправкой в кошелёк
                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("❌ СИМУЛЯЦИЯ TOKEN ТРАНЗАКЦИИ ПРОВАЛИЛАСЬ: $simError")
                    _errorMessage.postValue("Simulation failed: $simError")
                } else {
                    Timber.d("✅ Симуляция token транзакции успешна")
                }
                
                _transactionStatus.postValue("Подписание в кошельке...")
                
                // Переключаемся на Main поток для вызова transact()
                withContext(Dispatchers.Main) {
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        // transact() уже авторизовал с chain = "solana:mainnet"
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        
                        signTransactions(arrayOf(serializedTx))
                    }
                    
                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTransactions = signResult.payload.signedPayloads
                            
                            if (signedTransactions.isNotEmpty()) {
                                val signedTx = signedTransactions.first()
                                
                                scope.launch {
                                    try {
                                        _transactionStatus.postValue("Отправка транзакции...")
                                        delay(2000)
                                        
                                        if (!isNetworkAvailable()) {
                                            throw Exception("Нет подключения к интернету")
                                        }
                                        
                                        var signature: String? = null
                                        var lastError: Exception? = null
                                        
                                        for (attempt in 1..3) {
                                            try {
                                                signature = sendRawTransaction(signedTx)
                                                break
                                            } catch (e: Exception) {
                                                lastError = e
                                                if (attempt < 3) delay(2000L)
                                            }
                                        }
                                        
                                        if (signature != null) {
                                            pendingTxSignature = signature
                                            _txStatus.value = TxStatus.MINING
                                            _transactionStatus.postValue("Транзакция отправлена!")
                                            
                                            waitForTokenConfirmation(signature, id, mintAddress)
                                        } else {
                                            throw lastError ?: Exception("Не удалось отправить транзакцию")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "❌ Ошибка отправки token транзакции")
                                        _txStatus.value = TxStatus.ERROR
                                        _errorMessage.postValue("Ошибка отправки: ${e.message}")
                                        _transactionStatus.postValue("")
                                        
                                        // Удаляем pending контракт при ошибке отправки
                                        currentPendingContractId?.let { removePendingContract(it) }
                                        currentPendingContractId = null
                                        currentBoxPda = null
                                        
                                        resetTxStatusAfterDelay()
                                    }
                                }
                            } else {
                                throw Exception("Кошелек не вернул подписанную транзакцию")
                            }
                        }
                        is TransactionResult.Failure -> {
                            throw Exception("Транзакция отклонена: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            throw Exception("Кошелек не найден")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка создания token бокса")
                _txStatus.value = TxStatus.ERROR
                _errorMessage.postValue("Ошибка: ${e.message}")
                _transactionStatus.postValue("")
                
                currentPendingContractId?.let { removePendingContract(it) }
                currentPendingContractId = null
                currentBoxPda = null
                
                resetTxStatusAfterDelay()
            }
        }
    }
    
    /**
     * Ожидает подтверждения token транзакции
     */
    private suspend fun waitForTokenConfirmation(signature: String, boxId: String, mintAddress: String) {
        try {
            var confirmed = false
            var attempts = 0
            val maxAttempts = 60
            
            while (!confirmed && attempts < maxAttempts) {
                delay(2000)
                
                val status = getTransactionStatus(signature)
                if (status != null) {
                    confirmed = true
                    
                    _txStatus.value = TxStatus.SUCCESS
                    
                    // Сохраняем метаданные токен-бокса
                    BoxMetadataStore.addBox(context, boxId)
                    BoxMetadataStore.setIsToken(context, boxId, true)
                    BoxMetadataStore.setMint(context, boxId, mintAddress)
                    BoxMetadataStore.setStatus(context, boxId, BoxMetadataStore.BoxStatus.ACTIVE)
                    
                    // Проверяем, появился ли TokenBox в блокчейне и добавляем в список событий
                    currentBoxPda?.let { tokenBoxPdaBytes ->
                        checkAndAddBoxFromBlockchain(tokenBoxPdaBytes, signature)
                    }
                    
                    // Удаляем pending контракт
                    currentPendingContractId?.let { removePendingContract(it) }
                    currentPendingContractId = null
                    currentBoxPda = null
                    
                    refreshBalances()
                }
                
                attempts++
            }
            
            if (!confirmed) {
                Timber.w("⏱ Token транзакция не подтверждена за 120 сек — blockhash протух")
                _txStatus.value = TxStatus.SUCCESS
                
                // Удаляем pending контракт — транзакция уже точно не пройдёт
                currentPendingContractId?.let { removePendingContract(it) }
                currentPendingContractId = null
                currentBoxPda = null
            }
            
            resetTxStatusAfterDelay()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка при ожидании подтверждения token транзакции")
            _txStatus.value = TxStatus.ERROR
            
            // Удаляем pending контракт при ошибке
            currentPendingContractId?.let { removePendingContract(it) }
            currentPendingContractId = null
            currentBoxPda = null
            
            resetTxStatusAfterDelay()
        }
    }

    /**
     * Открывает бокс (получает SOL обратно)
     */
    fun openBox(context: Context, boxId: String, sender: ActivityResultSender) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Кошелек не подключен")
            return
        }

        _openingBoxId.postValue(boxId)
        currentOpeningBoxId = boxId
        _errorMessage.postValue("") // Очищаем предыдущую ошибку

        scope.launch {
            try {
                _transactionStatus.postValue("Проверка состояния бокса...")
                
                val senderPubkeyBytes = Base58.decode(owner)
                val idPubkeyBytes = Base58.decode(boxId)
                
                // Вычисляем PDA для бокса
                val boxPdaResult = findBoxPda(senderPubkeyBytes, idPubkeyBytes)
                    ?: throw Exception("Не удалось вычислить PDA для бокса")
                val (boxPdaBytes, _) = boxPdaResult
                
                // Проверяем состояние бокса перед открытием
                val boxPdaString = Base58.encodeToString(boxPdaBytes)
                val accountData = getAccountInfo(boxPdaString)
                
                if (accountData != null) {
                    val box = parseBoxAccount(accountData, boxPdaString)
                    if (box != null) {
                        // Проверяем, не открыт ли уже бокс
                        if (box.deadline == BigInteger.ZERO || box.amount == BigInteger.ZERO) {
                            Timber.w("⚠️ Бокс уже открыт: deadline=${box.deadline}, amount=${box.amount}")
                            _errorMessage.postValue("Бокс уже открыт")
                            _transactionStatus.postValue("")
                            _openingBoxId.postValue(null)
                            currentOpeningBoxId = null
                            return@launch
                        }
                        
                        // Проверяем, не истек ли дедлайн
                        val currentTime = System.currentTimeMillis() / 1000
                        if (box.deadline.toLong() <= currentTime) {
                            Timber.w("⚠️ Дедлайн истек: deadline=${box.deadline}, currentTime=$currentTime")
                            _errorMessage.postValue("Дедлайн истек. Используйте SweepBox")
                            _transactionStatus.postValue("")
                            _openingBoxId.postValue(null)
                            currentOpeningBoxId = null
                            return@launch
                        }
                    } else {
                        Timber.w("⚠️ Не удалось распарсить данные бокса")
                        // Продолжаем попытку открытия, возможно бокс не существует
                    }
                } else {
                    Timber.w("⚠️ Бокс не найден в блокчейне")
                    _errorMessage.postValue("Бокс не найден в блокчейне")
                    _transactionStatus.postValue("")
                    _openingBoxId.postValue(null)
                    currentOpeningBoxId = null
                    return@launch
                }
                
                _transactionStatus.postValue("Подготовка транзакции...")
                
                // Создаем instruction data для OpenBox
                // variant (1 byte)
                val instructionData = byteArrayOf(2) // OpenBox variant
                
                Timber.d("📦 OpenBox: boxId=$boxId, owner=$owner")
                Timber.d("   boxPda=${Base58.encodeToString(boxPdaBytes)}")
                
                // Получаем blockhash
                val blockhash = getLatestBlockhash()
                Timber.d("   blockhash=$blockhash")
                
                // Создаем сериализованную транзакцию
                val serializedTx = buildTransaction(
                    feePayer = senderPubkeyBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(boxPdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(senderPubkeyBytes, isSigner = true, isWritable = true)
                            ),
                            data = instructionData
                        )
                    )
                )
                
                // Симулируем транзакцию перед отправкой
                _transactionStatus.postValue("Симуляция транзакции...")
                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("❌ СИМУЛЯЦИЯ OpenBox ПРОВАЛИЛАСЬ: $simError")
                    _errorMessage.postValue("Симуляция OpenBox: $simError")
                    // Продолжаем всё равно - кошелек может обработать по-другому
                } else {
                    Timber.d("✅ Симуляция OpenBox успешна")
                }
                
                _transactionStatus.postValue("Подписание в кошельке...")
                
                // Переключаемся на Main поток для вызова transact()
                withContext(Dispatchers.Main) {
                    Timber.d("📤 Отправка OpenBox транзакции в кошелек")
                    
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        // transact() уже авторизовал с chain = "solana:mainnet"
                        Timber.d("📝 OpenBox: авторизация через transact()")
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        
                        signTransactions(arrayOf(serializedTx))
                    }
                    
                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTransactions = signResult.payload.signedPayloads
                            Timber.d("✅ OpenBox подписан, количество транзакций: ${signedTransactions.size}")
                            
                            if (signedTransactions.isNotEmpty()) {
                                val signedTx = signedTransactions.first()
                                Timber.d("📤 Отправка подписанной OpenBox транзакции, размер: ${signedTx.size} bytes")
                                
                                // Отправляем подписанную транзакцию через RPC с retry
                                scope.launch {
                                    try {
                                        _transactionStatus.postValue("Отправка транзакции...")
                                        
                                        // Задержка перед отправкой для стабильности
                                        delay(1000)
                                        
                                        var signature: String? = null
                                        var lastError: Exception? = null
                                        
                                        for (attempt in 1..3) {
                                            try {
                                                Timber.d("📤 OpenBox sendRawTransaction попытка $attempt/3")
                                                signature = sendRawTransaction(signedTx)
                                                Timber.d("✅ OpenBox транзакция отправлена: $signature")
                                                break
                                            } catch (e: Exception) {
                                                lastError = e
                                                Timber.e(e, "❌ OpenBox sendRawTransaction попытка $attempt/3 провалилась")
                                                if (attempt < 3) {
                                                    delay(2000L)
                                                }
                                            }
                                        }
                                        
                                        if (signature == null) {
                                            throw lastError ?: Exception("Не удалось отправить транзакцию после 3 попыток")
                                        }
                                        
                                        _transactionStatus.postValue("Транзакция отправлена!")
                                        
                                        // Сохраняем статус WIN для бокса
                                        BoxMetadataStore.setStatus(context, boxId, BoxMetadataStore.BoxStatus.WIN)
                                        
                                        // СРАЗУ добавляем событие в boxOpenedEvents для мгновенного обновления UI
                                        val openedEvent = BoxOpenedEvent(
                                            sender = owner,
                                            id = boxId,
                                            transactionHash = signature,
                                            blockNumber = BigInteger.ZERO
                                        )
                                        val currentOpenedEvents = _boxOpenedEvents.value ?: emptyList()
                                        if (!currentOpenedEvents.any { it.id == boxId }) {
                                            _boxOpenedEvents.postValue(currentOpenedEvents + openedEvent)
                                        }
                                        
                                        // Сбрасываем состояние загрузки
                                        _openingBoxId.postValue(null)
                                        currentOpeningBoxId = null
                                        
                                        delay(2000)
                                        _transactionStatus.postValue("")
                                        refreshBalances()
                                    } catch (e: Exception) {
                                        Timber.e(e, "❌ Ошибка отправки OpenBox (все попытки)")
                                        _transactionStatus.postValue("")
                                        _errorMessage.postValue("Ошибка OpenBox: ${e.message}")
                                        _openingBoxId.postValue(null)
                                        currentOpeningBoxId = null
                                    }
                                }
                            } else {
                                throw Exception("Кошелек не вернул подписанную транзакцию")
                            }
                        }
                        is TransactionResult.Failure -> {
                            Timber.e("❌ OpenBox транзакция отклонена: ${signResult.e.message}")
                            throw Exception("Транзакция отклонена: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            Timber.e("❌ Кошелек не найден для OpenBox")
                            throw Exception("Кошелек не найден")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка открытия бокса")
                _transactionStatus.postValue("")
                _errorMessage.postValue("Ошибка: ${e.message}")
                _openingBoxId.postValue(null)
                currentOpeningBoxId = null
            }
        }
    }

    /**
     * Открывает token бокс (получает SPL токены обратно)
     */
    fun openBoxToken(context: Context, boxId: String, sender: ActivityResultSender) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Кошелек не подключен")
            return
        }

        _openingBoxId.postValue(boxId)
        currentOpeningBoxId = boxId
        _errorMessage.postValue("") // Очищаем предыдущую ошибку

        scope.launch {
            try {
                _transactionStatus.postValue("Проверка состояния token бокса...")
                
                val senderPubkeyBytes = Base58.decode(owner)
                val idPubkeyBytes = Base58.decode(boxId)
                
                // Получаем mint address из метаданных
                val mintAddress = BoxMetadataStore.getMint(context, boxId)
                    ?: throw Exception("Mint address не найден для бокса")
                val mintBytes = Base58.decode(mintAddress)
                
                // Вычисляем TokenBox PDA
                val tokenBoxPdaResult = findTokenBoxPda(senderPubkeyBytes, idPubkeyBytes)
                    ?: throw Exception("Не удалось вычислить TokenBox PDA")
                val (tokenBoxPdaBytes, _) = tokenBoxPdaResult
                
                // Вычисляем Vault PDA
                val vaultPdaResult = findVaultPda(tokenBoxPdaBytes)
                    ?: throw Exception("Не удалось вычислить Vault PDA")
                val (vaultAuthorityBytes, _) = vaultPdaResult
                
                // Вычисляем ATA для vault
                val vaultAtaBytes = getAssociatedTokenAddress(vaultAuthorityBytes, mintBytes)
                    ?: throw Exception("Не удалось вычислить Vault ATA")
                
                // Вычисляем ATA для recipient (sender)
                val recipientAtaBytes = getAssociatedTokenAddress(senderPubkeyBytes, mintBytes)
                    ?: throw Exception("Не удалось вычислить Recipient ATA")
                
                // Проверяем состояние token box
                val tokenBoxPdaString = Base58.encodeToString(tokenBoxPdaBytes)
                val accountData = getAccountInfo(tokenBoxPdaString)
                
                if (accountData == null) {
                    _errorMessage.postValue("Token бокс не найден в блокчейне")
                    _transactionStatus.postValue("")
                    _openingBoxId.postValue(null)
                    currentOpeningBoxId = null
                    return@launch
                }
                
                _transactionStatus.postValue("Подготовка транзакции...")
                
                // Создаем instruction data для OpenBoxToken
                val instructionData = byteArrayOf(5) // OpenBoxToken variant
                
                Timber.d("📦 OpenBoxToken: boxId=$boxId, mint=$mintAddress")
                Timber.d("   tokenBoxPda=${Base58.encodeToString(tokenBoxPdaBytes)}")
                Timber.d("   vaultAta=${Base58.encodeToString(vaultAtaBytes)}")
                Timber.d("   recipientAta=${Base58.encodeToString(recipientAtaBytes)}")
                Timber.d("   vaultAuthority=${Base58.encodeToString(vaultAuthorityBytes)}")
                
                // Получаем blockhash
                val blockhash = getLatestBlockhash()
                
                // Accounts: [token_box_pda, vault_ata, recipient_token_account, sender,
                //           vault_authority, token_program]
                val serializedTx = buildTransaction(
                    feePayer = senderPubkeyBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(tokenBoxPdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(vaultAtaBytes, isSigner = false, isWritable = true),
                                AccountMeta(recipientAtaBytes, isSigner = false, isWritable = true),
                                AccountMeta(senderPubkeyBytes, isSigner = true, isWritable = true),
                                AccountMeta(vaultAuthorityBytes, isSigner = false, isWritable = false),
                                AccountMeta(tokenProgramIdBytes, isSigner = false, isWritable = false)
                            ),
                            data = instructionData
                        )
                    )
                )
                
                // Симулируем транзакцию перед отправкой
                _transactionStatus.postValue("Симуляция транзакции...")
                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("❌ СИМУЛЯЦИЯ OpenBoxToken ПРОВАЛИЛАСЬ: $simError")
                    _errorMessage.postValue("Симуляция OpenBoxToken: $simError")
                } else {
                    Timber.d("✅ Симуляция OpenBoxToken успешна")
                }
                
                _transactionStatus.postValue("Подписание в кошельке...")
                
                withContext(Dispatchers.Main) {
                    Timber.d("📤 Отправка OpenBoxToken транзакции в кошелек")
                    
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        // transact() уже авторизовал с chain = "solana:mainnet"
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        
                        signTransactions(arrayOf(serializedTx))
                    }
                    
                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTransactions = signResult.payload.signedPayloads
                            Timber.d("✅ OpenBoxToken подписан, количество: ${signedTransactions.size}")
                            
                            if (signedTransactions.isNotEmpty()) {
                                val signedTx = signedTransactions.first()
                                
                                scope.launch {
                                    try {
                                        _transactionStatus.postValue("Отправка транзакции...")
                                        delay(1000)
                                        
                                        var signature: String? = null
                                        var lastError: Exception? = null
                                        
                                        for (attempt in 1..3) {
                                            try {
                                                Timber.d("📤 OpenBoxToken sendRawTransaction попытка $attempt/3")
                                                signature = sendRawTransaction(signedTx)
                                                Timber.d("✅ OpenBoxToken транзакция отправлена: $signature")
                                                break
                                            } catch (e: Exception) {
                                                lastError = e
                                                Timber.e(e, "❌ OpenBoxToken sendRawTransaction попытка $attempt/3 провалилась")
                                                if (attempt < 3) delay(2000L)
                                            }
                                        }
                                        
                                        if (signature == null) {
                                            throw lastError ?: Exception("Не удалось отправить транзакцию после 3 попыток")
                                        }
                                        
                                        _transactionStatus.postValue("Транзакция отправлена!")
                                        
                                        // Сохраняем статус WIN для бокса
                                        BoxMetadataStore.setStatus(context, boxId, BoxMetadataStore.BoxStatus.WIN)
                                        
                                        // Добавляем событие
                                        val openedEvent = BoxOpenedEvent(
                                            sender = owner,
                                            id = boxId,
                                            transactionHash = signature,
                                            blockNumber = BigInteger.ZERO
                                        )
                                        val currentOpenedEvents = _boxOpenedEvents.value ?: emptyList()
                                        if (!currentOpenedEvents.any { it.id == boxId }) {
                                            _boxOpenedEvents.postValue(currentOpenedEvents + openedEvent)
                                        }
                                        
                                        _openingBoxId.postValue(null)
                                        currentOpeningBoxId = null
                                        
                                        delay(2000)
                                        _transactionStatus.postValue("")
                                        refreshBalances()
                                    } catch (e: Exception) {
                                        Timber.e(e, "❌ Ошибка отправки OpenBoxToken (все попытки)")
                                        _transactionStatus.postValue("")
                                        _errorMessage.postValue("Ошибка OpenBoxToken: ${e.message}")
                                        _openingBoxId.postValue(null)
                                        currentOpeningBoxId = null
                                    }
                                }
                            } else {
                                throw Exception("Кошелек не вернул подписанную транзакцию")
                            }
                        }
                        is TransactionResult.Failure -> {
                            throw Exception("Транзакция отклонена: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            throw Exception("Кошелек не найден")
                        }
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка открытия token бокса")
                _transactionStatus.postValue("")
                _errorMessage.postValue("Ошибка: ${e.message}")
                _openingBoxId.postValue(null)
                currentOpeningBoxId = null
            }
        }
    }

    /**
     * Внутренний класс для инструкции
     */
    data class Instruction(
        val programId: ByteArray,
        val accounts: List<AccountMeta>,
        val data: ByteArray
    )

    /**
     * Внутренний класс для метаданных аккаунта
     */
    data class AccountMeta(
        val pubkey: ByteArray,
        val isSigner: Boolean,
        val isWritable: Boolean
    )

    /**
     * Строит сериализованную транзакцию
     */
    private fun buildTransaction(
        feePayer: ByteArray,
        recentBlockhash: ByteArray,
        instructions: List<Instruction>
    ): ByteArray {
        // Собираем все уникальные аккаунты
        val accountsMap = linkedMapOf<String, AccountMeta>()
        
        // Fee payer всегда первый
        accountsMap[Base58.encodeToString(feePayer)] = AccountMeta(feePayer, true, true)
        
        // Добавляем аккаунты из инструкций
        instructions.forEach { instruction ->
            instruction.accounts.forEach { acc ->
                val key = Base58.encodeToString(acc.pubkey)
                val existing = accountsMap[key]
                if (existing != null) {
                    // Объединяем флаги
                    accountsMap[key] = AccountMeta(
                        acc.pubkey,
                        existing.isSigner || acc.isSigner,
                        existing.isWritable || acc.isWritable
                    )
                } else {
                    accountsMap[key] = acc
                }
            }
            
            // Добавляем program id
            val programKey = Base58.encodeToString(instruction.programId)
            if (!accountsMap.containsKey(programKey)) {
                accountsMap[programKey] = AccountMeta(instruction.programId, false, false)
            }
        }
        
        val accounts = accountsMap.values.toList()
        
        // Сортируем: signers+writable, signers+readonly, non-signers+writable, non-signers+readonly
        val sortedAccounts = accounts.sortedWith(compareBy(
            { !it.isSigner },
            { !it.isWritable }
        ))
        
        // Считаем количество подписантов
        val numSigners = sortedAccounts.count { it.isSigner }
        val numWritableSigners = sortedAccounts.count { it.isSigner && it.isWritable }
        val numReadonlySigners = numSigners - numWritableSigners
        val numWritableNonSigners = sortedAccounts.count { !it.isSigner && it.isWritable }
        val numReadonlyNonSigners = sortedAccounts.size - numSigners - numWritableNonSigners
        
        // Создаем индекс аккаунтов
        val accountIndex = sortedAccounts.mapIndexed { index, acc -> 
            Base58.encodeToString(acc.pubkey) to index 
        }.toMap()
        
        // Строим message
        val messageBuffer = ByteBuffer.allocate(4096)
        messageBuffer.order(ByteOrder.LITTLE_ENDIAN)
        
        // Header
        messageBuffer.put(numSigners.toByte())
        messageBuffer.put(numReadonlySigners.toByte())
        messageBuffer.put(numReadonlyNonSigners.toByte())
        
        // Account keys (compact array)
        writeCompactU16(messageBuffer, sortedAccounts.size)
        sortedAccounts.forEach { messageBuffer.put(it.pubkey) }
        
        // Recent blockhash
        messageBuffer.put(recentBlockhash)
        
        // Instructions (compact array)
        writeCompactU16(messageBuffer, instructions.size)
        instructions.forEach { instruction ->
            // Program id index
            val programIndex = accountIndex[Base58.encodeToString(instruction.programId)]!!
            messageBuffer.put(programIndex.toByte())
            
            // Account indices (compact array)
            writeCompactU16(messageBuffer, instruction.accounts.size)
            instruction.accounts.forEach { acc ->
                val accIndex = accountIndex[Base58.encodeToString(acc.pubkey)]!!
                messageBuffer.put(accIndex.toByte())
            }
            
            // Data (compact array)
            writeCompactU16(messageBuffer, instruction.data.size)
            messageBuffer.put(instruction.data)
        }
        
        val messageBytes = ByteArray(messageBuffer.position())
        messageBuffer.flip()
        messageBuffer.get(messageBytes)
        
        // Транзакция в формате Solana: compact-array подписей + message
        val txBuffer = ByteBuffer.allocate(1 + 64 * numSigners + messageBytes.size)
        writeCompactU16(txBuffer, numSigners) // Количество подписей
        repeat(numSigners) {
            txBuffer.put(ByteArray(64)) // Placeholder для подписи (все нули)
        }
        txBuffer.put(messageBytes)
        
        val txBytes = ByteArray(txBuffer.position())
        txBuffer.flip()
        txBuffer.get(txBytes)
        
        return txBytes
    }

    /**
     * Записывает compact u16 в буфер
     */
    private fun writeCompactU16(buffer: ByteBuffer, value: Int) {
        if (value < 128) {
            buffer.put(value.toByte())
        } else if (value < 16384) {
            buffer.put((value and 0x7f or 0x80).toByte())
            buffer.put((value shr 7).toByte())
        } else {
            buffer.put((value and 0x7f or 0x80).toByte())
            buffer.put((value shr 7 and 0x7f or 0x80).toByte())
            buffer.put((value shr 14).toByte())
        }
    }

    /**
     * Ожидает подтверждения транзакции
     */
    private suspend fun waitForConfirmation(signature: String) {
        try {
            var confirmed = false
            var attempts = 0
            val maxAttempts = 60
            
            while (!confirmed && attempts < maxAttempts) {
                delay(2000)
                
                val status = getTransactionStatus(signature)
                if (status != null) {
                    confirmed = true
                    // Timber.d("✅ Транзакция подтверждена!")
                    
                    _txStatus.value = TxStatus.SUCCESS
                    
                    // Проверяем, появился ли Box в блокчейне
                    currentBoxPda?.let { boxPdaBytes ->
                        checkAndAddBoxFromBlockchain(boxPdaBytes, signature)
                    }
                    
                    // Удаляем pending контракт
                    currentPendingContractId?.let { boxId ->
                        removePendingContract(boxId)
                    }
                    currentPendingContractId = null
                    currentBoxPda = null
                    
                    refreshBalances()
                }
                
                attempts++
            }
            
            if (!confirmed) {
                Timber.w("⏱ Транзакция не подтверждена за 120 сек — blockhash протух, транзакция не пройдёт")
                _txStatus.value = TxStatus.SUCCESS
                
                // Удаляем pending контракт — на Solana blockhash живёт ~60 сек,
                // если за 120 сек не подтвердилось, транзакция уже точно не пройдёт
                currentPendingContractId?.let { boxId ->
                    removePendingContract(boxId)
                }
                currentPendingContractId = null
                currentBoxPda = null
            }
            
            resetTxStatusAfterDelay()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка при ожидании подтверждения")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
        }
    }
    
    /**
     * Проверяет существование Box аккаунта в блокчейне и добавляет событие
     */
    private suspend fun checkAndAddBoxFromBlockchain(boxPdaBytes: ByteArray, signature: String) = withContext(Dispatchers.IO) {
        try {
            val boxPdaString = Base58.encodeToString(boxPdaBytes)
            // Timber.d("🔍 Проверка Box аккаунта в блокчейне: $boxPdaString")
            
            // Пробуем несколько раз, так как аккаунт может появиться с задержкой
            var found = false
            for (attempt in 1..10) {
                val accountData = getAccountInfo(boxPdaString)
                
                if (accountData != null) {
                    // Timber.d("✅ Box найден в блокчейне на попытке $attempt")
                    
                    // Парсим данные из аккаунта
                    val box = parseBoxAccount(accountData, boxPdaString)
                    if (box != null) {
                        // Обновляем transactionHash из подписи
                        val boxWithTxHash = box.copy(transactionHash = signature)
                        
                        // Получаем slot для сортировки
                        var slot: Long = 0L
                        val txSlot = getTransactionSlot(signature)
                        if (txSlot != null) {
                            slot = txSlot
                            // Timber.d("📅 Slot для нового бокса ${boxWithTxHash.id}: $slot")
                        }
                        
                        val boxWithSlot = boxWithTxHash.copy(blockNumber = BigInteger.valueOf(slot))
                        
                        val currentEvents = _boxCreatedEvents.value ?: emptyList()
                        // Проверяем, нет ли уже такого события
                        if (!currentEvents.any { it.id == boxWithSlot.id }) {
                            // Добавляем бокс в метаданные для сортировки и статусов
                            BoxMetadataStore.addBox(context, boxWithSlot.id)
                            BoxMetadataStore.setStatus(context, boxWithSlot.id, BoxMetadataStore.BoxStatus.ACTIVE)
                            
                            // Сохраняем сумму депозита
                            if (boxWithSlot.amount != BigInteger.ZERO) {
                                BoxMetadataStore.setAmount(context, boxWithSlot.id, boxWithSlot.amount)
                            }
                            
                            // Добавляем новый бокс и сортируем по slot
                            val updatedEvents = (currentEvents + boxWithSlot).sortedByDescending { it.blockNumber.toLong() }
                            _boxCreatedEvents.postValue(updatedEvents)
                            // Timber.d("✅ BoxCreatedEvent добавлен из блокчейна: ${boxWithSlot.id}, slot=$slot")
                        } else {
                            // Обновляем сумму для существующего бокса, если её еще нет
                            val savedAmount = BoxMetadataStore.getAmount(context, boxWithSlot.id)
                            if (savedAmount == null && boxWithSlot.amount != BigInteger.ZERO) {
                                BoxMetadataStore.setAmount(context, boxWithSlot.id, boxWithSlot.amount)
                            }
                            // Timber.d("⚠️ BoxCreatedEvent уже существует: ${boxWithSlot.id}")
                        }
                    }
                    
                    found = true
                    break
                } else {
                    // Timber.d("⏳ Box еще не появился в блокчейне (попытка $attempt/10)")
                    if (attempt < 10) {
                        delay(1000) // Ждем 1 секунду перед следующей попыткой
                    }
                }
            }
            
            if (!found) {
                Timber.w("⚠️ Box не найден в блокчейне после 10 попыток")
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка проверки Box в блокчейне")
        }
    }
    
    /**
     * Получает данные аккаунта через getAccountInfo
     */
    private suspend fun getAccountInfo(accountPubkey: String): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getAccountInfo")
                put("params", JSONArray().apply {
                    put(accountPubkey)
                    put(JSONObject().apply {
                        put("encoding", "base64")
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null

            val result = JSONObject(responseBody)
            if (result.has("error")) {
                val error = result.getJSONObject("error")
                Timber.e("❌ RPC ошибка getAccountInfo: ${error.optString("message", "Unknown error")}")
                return@withContext null
            }

            val resultObj = result.getJSONObject("result")
            
            // Если аккаунт не существует, value будет null
            if (resultObj.isNull("value")) {
                Timber.d("ℹ️ Аккаунт $accountPubkey не найден (value=null)")
                return@withContext null
            }
            
            val accountInfo = resultObj.getJSONObject("value")
            
            if (accountInfo.isNull("data")) {
                return@withContext null
            }
            
            val dataArray = accountInfo.getJSONArray("data")
            val dataBase64 = dataArray.getString(0)
            
            return@withContext Base64.decode(dataBase64, Base64.DEFAULT)
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения данных аккаунта $accountPubkey")
            null
        }
    }

    /**
     * Получает метаданные токена из Token Extensions (Token-2022)
     * Если метаданные не найдены, создает fallback с сокращенным адресом mint
     * @param mintAddress Адрес mint токена
     * @return TokenMetadata с сокращенным адресом как символом
     */
    private suspend fun getTokenExtensionsMetadata(mintAddress: String): TokenMetadata? = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 Попытка получить метаданные из Token Extensions для mint: $mintAddress")
            
            val accountData = getAccountInfo(mintAddress)
            if (accountData == null || accountData.size < 82) {
                Timber.w("⚠️ Не удалось получить mint account data, используем fallback")
                // Используем первые 4 символа mint адреса как символ
                val fallbackSymbol = mintAddress.take(4).uppercase()
                Timber.d("✅ Создан fallback symbol: $fallbackSymbol")
                return@withContext TokenMetadata(
                    name = "Unknown Token",
                    symbol = fallbackSymbol,
                    uri = ""
                )
            }
            
            Timber.d("🔍 Размер mint account: ${accountData.size} bytes")
            
            // Для Token-2022 с метаданными размер будет > 82 bytes
            // Для стандартного SPL Token используем fallback
            if (accountData.size <= 82) {
                Timber.w("⚠️ Стандартный SPL Token без метаданных, используем fallback")
                val fallbackSymbol = mintAddress.take(4).uppercase()
                Timber.d("✅ Создан fallback symbol: $fallbackSymbol")
                return@withContext TokenMetadata(
                    name = "SPL Token",
                    symbol = fallbackSymbol,
                    uri = ""
                )
            }
            
            // Если есть extensions, но мы не можем их распарсить, используем fallback
            Timber.w("⚠️ Token-2022 с extensions, но парсинг не реализован, используем fallback")
            val fallbackSymbol = mintAddress.take(4).uppercase()
            Timber.d("✅ Создан fallback symbol для Token-2022: $fallbackSymbol")
            return@withContext TokenMetadata(
                name = "Token-2022",
                symbol = fallbackSymbol,
                uri = ""
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения метаданных, используем fallback")
            val fallbackSymbol = mintAddress.take(4).uppercase()
            return@withContext TokenMetadata(
                name = "Unknown",
                symbol = fallbackSymbol,
                uri = ""
            )
        }
    }
    
    /**
     * Получает информацию о mint токена (decimals)
     * @param mintAddress Адрес mint токена
     * @return Количество decimals токена или null при ошибке
     */
    suspend fun getMintDecimals(mintAddress: String): Int? = withContext(Dispatchers.IO) {
        try {
            val accountData = getAccountInfo(mintAddress)
            if (accountData == null || accountData.size < 45) {
                Timber.e("❌ Не удалось получить данные mint account или неверный размер")
                return@withContext null
            }
            
            // Структура Mint account:
            // Offset 44: decimals (u8)
            val decimals = accountData[44].toInt() and 0xFF
            Timber.d("✅ Получены decimals для mint $mintAddress: $decimals")
            
            return@withContext decimals
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения decimals для mint $mintAddress")
            null
        }
    }
    
    /**
     * Данные метаданных токена
     */
    data class TokenMetadata(
        val name: String,
        val symbol: String,
        val uri: String
    )
    
    /**
     * Вычисляет PDA для метаданных токена (Metaplex Token Metadata)
     * @param mintAddress Адрес mint токена
     * @return PDA для метаданных или null при ошибке
     */
    private fun findMetadataPda(mintAddress: String): ByteArray? {
        return try {
            val mintBytes = Base58.decode(mintAddress)
            val metadataProgramBytes = Base58.decode(METADATA_PROGRAM_ID)
            
            val seeds = listOf(
                "metadata".toByteArray(Charsets.UTF_8),
                metadataProgramBytes,
                mintBytes
            )
            
            val pda = findProgramAddress(seeds, metadataProgramBytes)
            pda?.first
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка вычисления Metadata PDA для mint $mintAddress")
            null
        }
    }
    
    /**
     * Получает метаданные токена (name, symbol, uri) через Metaplex Token Metadata Program
     * Если Metaplex метаданные не найдены, пытается получить из Token Extensions (Token-2022)
     * @param mintAddress Адрес mint токена
     * @return TokenMetadata или null при ошибке
     */
    suspend fun getTokenMetadata(mintAddress: String): TokenMetadata? = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 Начало получения метаданных для mint: $mintAddress")
            
            // Сначала пытаемся получить из Metaplex Token Metadata
            val metadataPda = findMetadataPda(mintAddress)
            if (metadataPda == null) {
                Timber.w("⚠️ Не удалось вычислить Metadata PDA для mint $mintAddress, пробуем Token Extensions")
                return@withContext getTokenExtensionsMetadata(mintAddress)
            }
            
            val metadataPdaAddress = Base58.encodeToString(metadataPda)
            Timber.d("🔍 Metadata PDA адрес: $metadataPdaAddress")
            
            val accountData = getAccountInfo(metadataPdaAddress)
            
            if (accountData == null) {
                Timber.w("⚠️ Metaplex Metadata account не найден, пробуем Token Extensions")
                return@withContext getTokenExtensionsMetadata(mintAddress)
            }
            
            Timber.d("🔍 Размер данных Metadata account: ${accountData.size} bytes")
            
            if (accountData.size < 100) {
                Timber.w("⚠️ Неверный размер Metadata account: ${accountData.size} < 100, пробуем Token Extensions")
                return@withContext getTokenExtensionsMetadata(mintAddress)
            }
            
            // Структура Metadata account (упрощенная):
            // Offset 0: key (1 byte) - должен быть 4 (Metadata V1)
            // Offset 1: update_authority (32 bytes)
            // Offset 33: mint (32 bytes)
            // Offset 65: name (String) - 4 bytes длина + строка
            // Затем symbol (String) - 4 bytes длина + строка
            // Затем uri (String) - 4 bytes длина + строка
            
            val buffer = ByteBuffer.wrap(accountData).order(ByteOrder.LITTLE_ENDIAN)
            
            Timber.d("🔍 Начало парсинга Metadata account, размер buffer: ${buffer.remaining()} bytes")
            
            // Проверяем key
            val key = buffer.get().toInt() and 0xFF
            Timber.d("🔍 Key в Metadata account: $key (ожидается 4)")
            if (key != 4) {
                Timber.e("❌ Неверный key в Metadata account: $key (ожидается 4)")
                return@withContext null
            }
            
            // Пропускаем update_authority (32 bytes) и mint (32 bytes)
            buffer.position(65)
            Timber.d("🔍 Позиция buffer после пропуска update_authority и mint: ${buffer.position()}")
            
            // Читаем name
            val nameLength = buffer.int
            Timber.d("🔍 Длина name: $nameLength")
            if (nameLength < 0 || nameLength > 1000) {
                Timber.e("❌ Неверная длина name: $nameLength")
                return@withContext null
            }
            val nameBytes = ByteArray(nameLength)
            buffer.get(nameBytes)
            val name = String(nameBytes, Charsets.UTF_8).trim('\u0000', ' ')
            Timber.d("🔍 Name: '$name'")
            
            // Читаем symbol
            val symbolLength = buffer.int
            Timber.d("🔍 Длина symbol: $symbolLength")
            if (symbolLength < 0 || symbolLength > 1000) {
                Timber.e("❌ Неверная длина symbol: $symbolLength")
                return@withContext null
            }
            val symbolBytes = ByteArray(symbolLength)
            buffer.get(symbolBytes)
            val symbol = String(symbolBytes, Charsets.UTF_8).trim('\u0000', ' ')
            Timber.d("🔍 Symbol: '$symbol'")
            
            // Читаем uri
            val uriLength = buffer.int
            Timber.d("🔍 Длина uri: $uriLength")
            if (uriLength < 0 || uriLength > 1000) {
                Timber.e("❌ Неверная длина uri: $uriLength")
                return@withContext null
            }
            val uriBytes = ByteArray(uriLength)
            buffer.get(uriBytes)
            val uri = String(uriBytes, Charsets.UTF_8).trim('\u0000', ' ')
            Timber.d("🔍 URI: '$uri'")
            
            Timber.d("✅ Получены метаданные для mint $mintAddress: name=$name, symbol=$symbol, uri=$uri")
            
            return@withContext TokenMetadata(name, symbol, uri)
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения Metaplex метаданных для mint $mintAddress")
            Timber.e("❌ Тип ошибки: ${e.javaClass.simpleName}, сообщение: ${e.message}")
            Timber.w("⚠️ Используем fallback метаданные")
            // Используем fallback с сокращенным адресом mint
            val fallbackSymbol = mintAddress.take(4).uppercase()
            TokenMetadata(
                name = "Unknown Token",
                symbol = fallbackSymbol,
                uri = ""
            )
        }
    }

    /**
     * Симулирует транзакцию через RPC для диагностики
     * Возвращает ошибку если симуляция провалилась, null если успешна
     */
    private suspend fun simulateTransaction(serializedTx: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val txBase64 = Base64.encodeToString(serializedTx, Base64.NO_WRAP)
            Timber.d("🔬 СИМУЛЯЦИЯ ТРАНЗАКЦИИ")
            Timber.d("   Размер: ${serializedTx.size} bytes")
            Timber.d("   Base64 (первые 100): ${txBase64.take(100)}...")
            
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "simulateTransaction")
                put("params", JSONArray().apply {
                    put(txBase64)
                    put(JSONObject().apply {
                        put("encoding", "base64")
                        put("commitment", "confirmed")
                        put("sigVerify", false) // Не проверяем подписи
                        put("replaceRecentBlockhash", true) // Используем свежий blockhash
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .header("Content-Type", "application/json")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            Timber.d("🌐 Отправка симуляции к $SOLANA_RPC_URL")
            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext "Empty response"
            
            Timber.d("📥 Ответ симуляции: $responseBody")
            
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                val error = result.getJSONObject("error")
                return@withContext "RPC error: ${error.optString("message", "Unknown")}"
            }
            
            val simResult = result.getJSONObject("result")
            val value = simResult.optJSONObject("value")
            
            if (value == null) {
                return@withContext "No simulation result"
            }
            
            val simError = value.optJSONObject("err")
            if (simError != null && simError.length() > 0) {
                val logs = value.optJSONArray("logs")
                val logsStr = if (logs != null) {
                    (0 until logs.length()).map { logs.getString(it) }.joinToString("\n")
                } else {
                    "No logs"
                }
                Timber.e("❌ Симуляция провалилась!")
                Timber.e("   Error: $simError")
                Timber.e("   Logs:\n$logsStr")
                return@withContext "Simulation failed: $simError\nLogs:\n$logsStr"
            }
            
            Timber.d("✅ Симуляция успешна!")
            val logs = value.optJSONArray("logs")
            if (logs != null) {
                Timber.d("   Logs:")
                for (i in 0 until logs.length()) {
                    Timber.d("      ${logs.getString(i)}")
                }
            }
            
            null // Успех
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка симуляции")
            "Exception: ${e.message}"
        }
    }

    /**
     * Отправляет подписанную транзакцию через RPC
     */
    private suspend fun sendRawTransaction(signedTx: ByteArray): String = withContext(Dispatchers.IO) {
        try {
            val txBase58 = Base58.encodeToString(signedTx)
            // Timber.d("📤 Отправка через $SOLANA_RPC_URL")
            Timber.d("   Размер транзакции: ${signedTx.size} bytes")
            Timber.d("   Base58 (первые 50): ${txBase58.take(50)}...")
            
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "sendTransaction")
                put("params", JSONArray().apply {
                    put(txBase58)
                    put(JSONObject().apply {
                        put("encoding", "base58")
                        put("skipPreflight", false)
                        put("preflightCommitment", "confirmed")
                    })
                })
            }

            Timber.d("📡 Создание HTTP запроса...")
            val requestBody = json.toString().toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .header("Content-Type", "application/json")
                .post(requestBody)
                .build()

            Timber.d("🌐 Отправка HTTP запроса к $SOLANA_RPC_URL")
            val response = httpClient.newCall(request).execute()
            
            Timber.d("📥 Получен ответ, код: ${response.code}")
            val responseBody = response.body?.string() 
                ?: throw Exception("Empty response from RPC, code: ${response.code}")
            
            Timber.d("📥 RPC ответ: $responseBody")
            
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                val error = result.getJSONObject("error")
                val errorMsg = error.optString("message", "Unknown RPC error")
                val errorCode = error.optInt("code", 0)
                Timber.e("❌ RPC ошибка: code=$errorCode, message=$errorMsg")
                
                // Извлекаем программные логи из данных ошибки
                val errorData = error.optJSONObject("data")
                if (errorData != null) {
                    val logs = errorData.optJSONArray("logs")
                    if (logs != null) {
                        val logsStr = (0 until logs.length()).joinToString("\n") { logs.getString(it) }
                        Timber.e("📋 Программные логи:\n$logsStr")
                        
                        // Ищем строку с ошибкой программы
                        val programError = (0 until logs.length())
                            .map { logs.getString(it) }
                            .lastOrNull { it.contains("failed") || it.contains("Error") || it.contains("error") }
                        if (programError != null) {
                            throw Exception("$errorMsg | $programError")
                        }
                    }
                    Timber.e("📋 Данные ошибки: $errorData")
                }
                
                throw Exception("RPC error: $errorMsg")
            }
            
            val signature = result.getString("result")
            Timber.d("✅ RPC вернул signature: $signature")
            signature
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка sendRawTransaction")
            throw e
        }
    }

    /**
     * Получает статус транзакции через RPC
     */
    private suspend fun getTransactionStatus(signature: String): Boolean? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getSignatureStatuses")
                put("params", JSONArray().apply {
                    put(JSONArray().apply { put(signature) })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            
            // ОПТИМИЗАЦИЯ: Не сохраняем rawRpcResponse при каждом запросе - это вызывает recompositions
            // Убираем для производительности скролла
            
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                return@withContext null
            }
            
            val value = result.getJSONObject("result").getJSONArray("value")
            if (value.length() > 0 && !value.isNull(0)) {
                val status = value.getJSONObject(0)
                val err = status.optJSONObject("err")
                err == null // null = успешно
            } else {
                null // еще не подтверждено
            }
        } catch (e: Exception) {
            Timber.e(e, "Ошибка получения статуса транзакции")
            null
        }
    }
    
    /**
     * Получает slot транзакции через getSignatureStatuses
     */
    private suspend fun getTransactionSlot(signature: String): Long? = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getSignatureStatuses")
                put("params", JSONArray().apply {
                    put(JSONArray().apply { put(signature) })
                    put(JSONObject().apply {
                        put("searchTransactionHistory", true)
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext null
            
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                return@withContext null
            }
            
            val value = result.getJSONObject("result").getJSONArray("value")
            if (value.length() > 0 && !value.isNull(0)) {
                val status = value.getJSONObject(0)
                if (status.has("slot")) {
                    return@withContext status.getLong("slot")
                }
            }
            null
        } catch (e: Exception) {
            Timber.e(e, "Ошибка получения slot транзакции")
            null
        }
    }
    
    /**
     * Получает список подписей транзакций для адреса
     */
    private suspend fun getSignaturesForAddress(address: String, limit: Int = 1): List<String> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getSignaturesForAddress")
                put("params", JSONArray().apply {
                    put(address)
                    put(JSONObject().apply {
                        put("limit", limit)
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            
            val result = JSONObject(responseBody)
            
            if (result.has("error")) {
                return@withContext emptyList()
            }
            
            val signatures = result.getJSONObject("result").getJSONArray("value")
            val signatureList = mutableListOf<String>()
            for (i in 0 until signatures.length()) {
                val sigObj = signatures.getJSONObject(i)
                val signature = sigObj.getString("signature")
                signatureList.add(signature)
            }
            signatureList
        } catch (e: Exception) {
            Timber.e(e, "Ошибка получения подписей для адреса")
            emptyList()
        }
    }

    /**
     * Получает полные данные транзакции через RPC
     */
    private suspend fun getTransactionData(signature: String) = scope.launch(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getTransaction")
                put("params", JSONArray().apply {
                    put(signature)
                    put(JSONObject().apply {
                        put("encoding", "jsonParsed")
                        put("maxSupportedTransactionVersion", 0)
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@launch
            
            // Сохраняем сырой ответ для отображения
            _rawRpcResponse.postValue(responseBody)
            Timber.d("📥 Получены данные транзакции: ${responseBody.take(200)}...")
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения данных транзакции")
        }
    }

    /**
     * Проверяет доступность сети
     */
    private fun isNetworkAvailable(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            
            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            
            Timber.d("🌐 Проверка сети: hasInternet=$hasInternet, isValidated=$isValidated")
            hasInternet && isValidated
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка проверки сети")
            false
        }
    }

    /**
     * Загружает боксы пользователя из блокчейна Solana
     */
    private fun fetchUserBoxes() {
        val userAddress = getSelectedAddress()
        if (userAddress.isBlank()) return
        
        Timber.d("📦 Загрузка боксов для $userAddress из блокчейна")
        scope.launch {
            try {
                fetchBoxCreatedEventsFromBlockchain(userAddress)
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка загрузки боксов из блокчейна")
            }
        }
    }

    /**
     * Загружает события создания боксов из блокчейна Solana
     */
    fun fetchBoxCreatedEvents() {
        val userAddress = getSelectedAddress()
        if (userAddress.isBlank()) return
        
        scope.launch {
            try {
                fetchBoxCreatedEventsFromBlockchain(userAddress)
            } catch (e: Exception) {
                Timber.e(e, "❌ Ошибка загрузки событий из блокчейна")
            }
        }
    }
    
    /**
     * Suspend версия для загрузки событий с ожиданием результата
     * Возвращает true если загрузка завершилась успешно
     */
    suspend fun fetchBoxCreatedEventsAsync(): Boolean {
        val userAddress = getSelectedAddress()
        if (userAddress.isBlank()) return false
        
        return try {
            fetchBoxCreatedEventsFromBlockchain(userAddress)
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка загрузки событий из блокчейна")
            false
        }
    }
    
    /**
     * Загружает события создания боксов из блокчейна через getProgramAccounts
     */
    private suspend fun fetchBoxCreatedEventsFromBlockchain(userAddress: String) = withContext(Dispatchers.IO) {
        try {
            // Получаем SOL боксы (80 bytes)
            val solAccounts = getProgramAccounts(userAddress, dataSize = 80)
            // Получаем Token боксы (112 bytes)
            val tokenAccounts = getProgramAccounts(userAddress, dataSize = 112)
            
            Timber.d("📦 SOL аккаунтов: ${solAccounts.size}, Token аккаунтов: ${tokenAccounts.size}")
            
            val events = mutableListOf<BoxCreatedEvent>()
            val currentTime = System.currentTimeMillis() / 1000
            
            // Парсим SOL боксы
            for (account in solAccounts) {
                try {
                    val box = parseBoxAccount(account.data, account.pubkey)
                    if (box != null) {
                        events.add(box)
                        processBoxMetadata(box, currentTime, isToken = false, mintAddress = null)
                    }
                } catch (e: Exception) {
                    // Пропускаем ошибочные аккаунты
                }
            }
            
            // Парсим Token боксы
            for (account in tokenAccounts) {
                try {
                    val tokenBox = parseTokenBoxAccount(account.data, account.pubkey)
                    if (tokenBox != null) {
                        events.add(tokenBox.event)
                        processBoxMetadata(tokenBox.event, currentTime, isToken = true, mintAddress = tokenBox.mint)
                    }
                } catch (e: Exception) {
                    // Пропускаем ошибочные аккаунты
                }
            }
            
            if (events.isEmpty()) {
                Timber.d("📭 Аккаунтов не найдено")
                return@withContext
            }
            
            // Сортируем по времени создания из метаданных (новые сверху)
            val sortedEvents = events.sortedByDescending { event ->
                BoxMetadataStore.getCreatedAt(context, event.id) ?: 0L
            }
            
            // ОПТИМИЗАЦИЯ: Обновляем только если список действительно изменился
            // Используем более строгую проверку для предотвращения лишних обновлений
            val currentEvents = _boxCreatedEvents.value ?: emptyList()
            val currentIds = currentEvents.map { it.id }.toSet()
            val newIds = sortedEvents.map { it.id }.toSet()
            
            // Обновляем только если:
            // 1. Размер изменился ИЛИ
            // 2. Набор ID изменился ИЛИ
            // 3. Это первая загрузка (currentEvents пустой)
            if (currentEvents.isEmpty() || 
                currentEvents.size != sortedEvents.size || 
                currentIds != newIds) {
                // Дополнительная проверка: не обновляем если списки идентичны
                if (currentEvents != sortedEvents) {
                    _boxCreatedEvents.postValue(sortedEvents)
                }
            }
            
            // Удаляем pending контракты, которые уже появились в блокчейне
            val currentPending = _pendingContracts.value ?: emptyList()
            if (currentPending.isNotEmpty()) {
                val remainingPending = currentPending.filter { pending ->
                    // Оставляем только те pending контракты, которых еще нет в блокчейне
                    !newIds.contains(pending.id)
                }
                
                // Обновляем список pending контрактов если что-то изменилось
                if (remainingPending.size != currentPending.size) {
                    _pendingContracts.postValue(remainingPending)
                    savePendingContracts(remainingPending)
                    // Timber.d("🗑️ Удалено подтвержденных pending контрактов: ${currentPending.size - remainingPending.size}")
                }
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка загрузки боксов из блокчейна")
            throw e
        }
    }
    
    /**
     * Получает аккаунты программы через getProgramAccounts с фильтрацией по sender
     * @param dataSize размер данных для фильтрации (80 для Box, 112 для TokenBox)
     */
    private suspend fun getProgramAccounts(userAddress: String, dataSize: Int = 80): List<ProgramAccount> = withContext(Dispatchers.IO) {
        try {
            Timber.d("🔍 Запрос getProgramAccounts для программы: $PROGRAM_ID, пользователь: $userAddress, dataSize: $dataSize")
            
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getProgramAccounts")
                put("params", JSONArray().apply {
                    put(PROGRAM_ID)
                    put(JSONObject().apply {
                        put("encoding", "base64")
                        // Добавляем фильтры для оптимизации запроса
                        put("filters", JSONArray().apply {
                            // Фильтр по размеру данных
                            put(JSONObject().apply {
                                put("dataSize", dataSize)
                            })
                            // Фильтр по sender (первые 32 байта структуры Box/TokenBox)
                            put(JSONObject().apply {
                                put("memcmp", JSONObject().apply {
                                    put("offset", 0)  // sender находится в начале структуры
                                    put("bytes", userAddress)  // адрес пользователя в Base58
                                })
                            })
                        })
                    })
                })
            }

            val requestBody = json.toString()
            Timber.d("📤 RPC запрос: $requestBody")

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(requestBody.toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()
            
            // ОПТИМИЗАЦИЯ: Убираем лишние логи и обновления rawRpcResponse для производительности
            // Timber.d("📥 RPC ответ (первые 500 символов): ${responseBody.take(500)}")
            // _rawRpcResponse.postValue(formatJson(responseBody))
            
            val result = JSONObject(responseBody)
            if (result.has("error")) {
                val error = result.getJSONObject("error")
                val errorMessage = error.optString("message", "Unknown error")
                val errorCode = error.optInt("code", -1)
                Timber.e("❌ RPC ошибка getProgramAccounts: code=$errorCode, message=$errorMessage")
                return@withContext emptyList()
            }
            
            if (!result.has("result")) {
                Timber.e("❌ RPC ответ не содержит 'result'")
                return@withContext emptyList()
            }
            
            // В getProgramAccounts result может быть массивом напрямую или объектом с полем value
            val accountsArray = when {
                result.get("result") is JSONArray -> {
                    // Формат: {"result": [{...}, {...}]}
                    result.getJSONArray("result")
                }
                result.get("result") is JSONObject -> {
                    // Формат: {"result": {"value": [{...}, {...}]}}
                    val resultObj = result.getJSONObject("result")
                    if (resultObj.has("value")) {
                        resultObj.getJSONArray("value")
                    } else {
                        Timber.e("❌ RPC result объект не содержит 'value'")
                        return@withContext emptyList()
                    }
                }
                else -> {
                    Timber.e("❌ Неизвестный формат result в RPC ответе")
                    return@withContext emptyList()
                }
            }
            
            Timber.d("📋 Получено аккаунтов из RPC: ${accountsArray.length()}")
            val accounts = mutableListOf<ProgramAccount>()
            
            for (i in 0 until accountsArray.length()) {
                try {
                    val accountObj = accountsArray.getJSONObject(i)
                    val pubkey = accountObj.getString("pubkey")
                    val account = accountObj.getJSONObject("account")
                    
                    // Данные могут быть массивом [base64String, "base64"] или строкой
                    val dataBytes = when {
                        account.has("data") && account.get("data") is JSONArray -> {
                            // Формат массива: ["base64String", "base64"]
                            val dataArray = account.getJSONArray("data")
                            val dataBase64 = dataArray.getString(0)
                            Base64.decode(dataBase64, Base64.DEFAULT)
                        }
                        account.has("data") && account.get("data") is String -> {
                            // Формат строки: "base64String"
                            val data = account.getString("data")
                            Base64.decode(data, Base64.DEFAULT)
                        }
                        else -> {
                            Timber.w("⚠️ Неизвестный формат данных для аккаунта $pubkey")
                            continue
                        }
                    }
                    
                    Timber.d("📦 Обработка аккаунта: pubkey=$pubkey, dataSize=${dataBytes.size} bytes")
                    
                    // RPC уже отфильтровал аккаунты по размеру 80 bytes и sender
                    accounts.add(ProgramAccount(
                        pubkey = pubkey,
                        data = dataBytes
                    ))
                } catch (e: Exception) {
                    Timber.e(e, "⚠️ Ошибка обработки аккаунта: ${e.message}")
                    Timber.e("   Stack trace: ${e.stackTrace.take(3).joinToString("\n")}")
                }
            }
            
            Timber.d("📋 Получено аккаунтов пользователя $userAddress (размер = $dataSize bytes): ${accounts.size}")
            accounts
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка получения аккаунтов программы")
            emptyList()
        }
    }
    
    /**
     * Парсит данные Box из аккаунта (Borsh deserialization)
     */
    private fun parseBoxAccount(data: ByteArray, pubkey: String): BoxCreatedEvent? {
        try {
            Timber.d("🔍 Парсинг Box аккаунта: pubkey=$pubkey, размер данных: ${data.size} bytes")
            
            // Проверяем минимальный размер (80 bytes для Box)
            if (data.size < 80) {
                Timber.w("⚠️ Недостаточно данных в аккаунте: ${data.size} байт (нужно минимум 80)")
                // Показываем первые байты для отладки
                val preview = data.take(20).joinToString(" ") { "%02X".format(it) }
                Timber.w("   Первые 20 байт (hex): $preview...")
                return null
            }
            
            val buffer = java.nio.ByteBuffer.wrap(data)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            
            // Структура Box (Borsh):
            // sender: Pubkey (32 bytes)
            // id: Pubkey (32 bytes)
            // deadline: i64 (8 bytes) - Unix timestamp
            // amount: u64 (8 bytes) - в lamports
            
            val senderBytes = ByteArray(32)
            buffer.get(senderBytes)
            val sender = Base58.encodeToString(senderBytes)
            
            val idBytes = ByteArray(32)
            buffer.get(idBytes)
            val id = Base58.encodeToString(idBytes)
            
            val deadline = buffer.long // i64 - уже Unix timestamp!
            val amount = buffer.long.toBigInteger() // u64
            
            Timber.d("📦 Успешно распарсен Box: pubkey=$pubkey")
            Timber.d("   id=$id")
            Timber.d("   sender=$sender")
            Timber.d("   deadline=$deadline (${if (deadline > 0) java.util.Date(deadline * 1000) else "0"})")
            Timber.d("   amount=$amount lamports")
            
            // ВРЕМЕННО: показываем все боксы, даже закрытые (для отладки)
            // Позже можно вернуть проверку: if (deadline == 0L || amount == BigInteger.ZERO) return null
            if (deadline == 0L && amount == BigInteger.ZERO) {
                Timber.d("⚠️ Бокс закрыт (deadline=0, amount=0), но показываем для отладки")
            }
            
            return BoxCreatedEvent(
                sender = sender,
                id = id,
                deadline = BigInteger.valueOf(deadline),
                amount = amount,
                transactionHash = "", // Не хранится в аккаунте
                blockNumber = BigInteger.ZERO
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка парсинга Box аккаунта $pubkey: ${e.message}")
            Timber.e("   Stack trace: ${e.stackTrace.take(5).joinToString("\n")}")
            return null
        }
    }
    
    /**
     * Результат парсинга TokenBox
     */
    private data class TokenBoxParsed(
        val event: BoxCreatedEvent,
        val mint: String
    )
    
    /**
     * Парсит данные TokenBox из аккаунта (Borsh deserialization)
     */
    private fun parseTokenBoxAccount(data: ByteArray, pubkey: String): TokenBoxParsed? {
        try {
            // Проверяем минимальный размер (112 bytes для TokenBox)
            if (data.size < 112) {
                return null
            }
            
            val buffer = java.nio.ByteBuffer.wrap(data)
            buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)
            
            // Структура TokenBox (Borsh):
            // sender: Pubkey (32 bytes)
            // id: Pubkey (32 bytes)
            // deadline: i64 (8 bytes) - Unix timestamp
            // amount: u64 (8 bytes) - в token units
            // mint: Pubkey (32 bytes)
            
            val senderBytes = ByteArray(32)
            buffer.get(senderBytes)
            val sender = Base58.encodeToString(senderBytes)
            
            val idBytes = ByteArray(32)
            buffer.get(idBytes)
            val id = Base58.encodeToString(idBytes)
            
            val deadline = buffer.long
            val amount = buffer.long.toBigInteger()
            
            val mintBytes = ByteArray(32)
            buffer.get(mintBytes)
            val mint = Base58.encodeToString(mintBytes)
            
            Timber.d("📦 Успешно распарсен TokenBox: id=$id, mint=$mint")
            
            return TokenBoxParsed(
                event = BoxCreatedEvent(
                    sender = sender,
                    id = id,
                    deadline = BigInteger.valueOf(deadline),
                    amount = amount,
                    transactionHash = "",
                    blockNumber = BigInteger.ZERO
                ),
                mint = mint
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ Ошибка парсинга TokenBox аккаунта $pubkey")
            return null
        }
    }
    
    /**
     * Обрабатывает метаданные бокса
     */
    private fun processBoxMetadata(box: BoxCreatedEvent, currentTime: Long, isToken: Boolean, mintAddress: String?) {
        // Добавляем бокс в метаданные если его еще нет
        if (BoxMetadataStore.getCreatedAt(context, box.id) == null) {
            BoxMetadataStore.addBox(context, box.id)
        }
        
        // Сохраняем тип бокса (SOL или token)
        if (isToken) {
            BoxMetadataStore.setIsToken(context, box.id, true)
            mintAddress?.let { mint ->
                BoxMetadataStore.setMint(context, box.id, mint)
                
                // Получаем и сохраняем метаданные токена, если их еще нет
                val needsDecimals = BoxMetadataStore.getDecimals(context, box.id) == null
                val needsSymbol = BoxMetadataStore.getSymbol(context, box.id) == null
                
                Timber.d("🔍 processBoxMetadata для boxId=${box.id}, mint=$mint: needsDecimals=$needsDecimals, needsSymbol=$needsSymbol")
                
                if (needsDecimals || needsSymbol) {
                    scope.launch {
                        // Получаем метаданные токена (symbol)
                        if (needsSymbol) {
                            Timber.d("🔍 Получение метаданных токена для mint=$mint")
                            val metadata = getTokenMetadata(mint)
                            Timber.d("🔍 Результат: symbol=${metadata?.symbol}")
                            if (metadata?.symbol != null) {
                                BoxMetadataStore.setSymbol(context, box.id, metadata.symbol)
                                Timber.d("✅ Сохранен symbol=${metadata.symbol} для бокса ${box.id}, mint=$mint")
                            } else {
                                Timber.e("❌ Не удалось получить symbol для бокса ${box.id}, mint=$mint")
                            }
                        }
                        
                        // Получаем decimals токена
                        if (needsDecimals) {
                            val decimals = getMintDecimals(mint)
                            if (decimals != null) {
                                BoxMetadataStore.setDecimals(context, box.id, decimals)
                                Timber.d("✅ Сохранены decimals=$decimals для бокса ${box.id}, mint=$mint")
                            } else {
                                Timber.e("❌ Не удалось получить decimals для бокса ${box.id}, mint=$mint")
                            }
                        }
                    }
                }
            }
        }
        
        // Сохраняем сумму депозита
        val savedAmount = BoxMetadataStore.getAmount(context, box.id)
        if (savedAmount == null && box.amount != BigInteger.ZERO) {
            BoxMetadataStore.setAmount(context, box.id, box.amount)
        }
        
        // Автоматически обновляем статусы
        val savedStatus = BoxMetadataStore.getStatus(context, box.id)
        
        when {
            box.deadline.toLong() == 0L && box.amount == BigInteger.ZERO -> {
                if (savedStatus == null || savedStatus == BoxMetadataStore.BoxStatus.ACTIVE) {
                    BoxMetadataStore.setStatus(context, box.id, BoxMetadataStore.BoxStatus.WIN)
                }
            }
            box.deadline.toLong() < currentTime -> {
                if (savedStatus == null || savedStatus == BoxMetadataStore.BoxStatus.ACTIVE) {
                    BoxMetadataStore.setStatus(context, box.id, BoxMetadataStore.BoxStatus.LOSE)
                }
            }
            else -> {
                if (savedStatus == null) {
                    BoxMetadataStore.setStatus(context, box.id, BoxMetadataStore.BoxStatus.ACTIVE)
                }
            }
        }
    }
    
    /**
     * Форматирует JSON для читаемого отображения
     */
    private fun formatJson(jsonString: String): String {
        return try {
            // Пытаемся отформатировать JSON с отступами
            val jsonObj = JSONObject(jsonString)
            val formatted = formatJsonPretty(jsonObj, 0)
            // Ограничиваем размер (первые 5000 символов)
            if (formatted.length > 5000) {
                formatted.take(5000) + "\n\n... (truncated, total length: ${formatted.length} chars)"
            } else {
                formatted
            }
        } catch (e: Exception) {
            // Если не JSON или ошибка форматирования, возвращаем как есть (ограниченный)
            if (jsonString.length > 5000) {
                jsonString.take(5000) + "\n\n... (truncated, total length: ${jsonString.length} chars)"
            } else {
                jsonString
            }
        }
    }
    
    /**
     * Рекурсивно форматирует JSON с отступами
     */
    private fun formatJsonPretty(json: Any, indent: Int): String {
        val indentStr = "  ".repeat(indent)
        val nextIndent = indent + 1
        val nextIndentStr = "  ".repeat(nextIndent)
        
        return when (json) {
            is JSONObject -> {
                val keys = json.keys()
                if (!keys.hasNext()) {
                    return "{}"
                }
                val entries = mutableListOf<String>()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val value = json.get(key)
                    entries.add("$nextIndentStr\"$key\": ${formatJsonPretty(value, nextIndent)}")
                }
                "{\n${entries.joinToString(",\n")}\n$indentStr}"
            }
            is JSONArray -> {
                if (json.length() == 0) {
                    return "[]"
                }
                val items = (0 until json.length()).map { i ->
                    "$nextIndentStr${formatJsonPretty(json.get(i), nextIndent)}"
                }
                "[\n${items.joinToString(",\n")}\n$indentStr]"
            }
            is String -> "\"$json\""
            is Number -> json.toString()
            is Boolean -> json.toString()
            JSONObject.NULL -> "null"
            else -> json.toString()
        }
    }
    
    /**
     * Данные аккаунта программы
     */
    private data class ProgramAccount(
        val pubkey: String,
        val data: ByteArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            
            other as ProgramAccount
            
            if (pubkey != other.pubkey) return false
            if (!data.contentEquals(other.data)) return false
            
            return true
        }
        
        override fun hashCode(): Int {
            var result = pubkey.hashCode()
            result = 31 * result + data.contentHashCode()
            return result
        }
    }

    /**
     * Заглушка для совместимости с UI
     */
    fun fetchBoxOpenedEvents() {
        // Не используется в Solana версии
    }

    /**
     * Сброс статуса транзакции через задержку
     */
    private fun resetTxStatusAfterDelay() {
        scope.launch {
            delay(3000)
            _txStatus.value = TxStatus.IDLE
            _transactionStatus.postValue("")
            pendingTxSignature = null
        }
    }

    /**
     * Очищает балансы
     */
    private fun clearBalances() {
        _balancesLoading.postValue(false)
        _nativeSolBalance.postValue("")
        _boxCreatedEvents.postValue(emptyList())
        _boxOpenedEvents.postValue(emptyList())
        _pendingContracts.postValue(emptyList())
    }

    // ==================== SWEEP FUNCTIONALITY ====================

    /**
     * Данные истекшего SOL бокса
     */
    data class ExpiredBox(
        val pubkey: String,
        val sender: String,
        val id: String,
        val deadline: Long,
        val amount: BigInteger,
        val isToken: Boolean,
        val mint: String? = null
    )

    private val _expiredBoxes = MutableLiveData<List<ExpiredBox>>(emptyList())
    val expiredBoxes: LiveData<List<ExpiredBox>> = _expiredBoxes

    private val _sweepLoading = MutableLiveData(false)
    val sweepLoading: LiveData<Boolean> = _sweepLoading

    /**
     * Вычисляет PDA для program state
     * seeds = [b"program_state"]
     */
    fun findProgramStatePda(): Pair<ByteArray, Int>? {
        val seeds = listOf("program_state".toByteArray())
        return findProgramAddress(seeds, programIdBytes)
    }

    private val _programStateExists = MutableLiveData<Boolean?>(null)
    val programStateExists: LiveData<Boolean?> = _programStateExists

    /**
     * Проверяет, существует ли program_state PDA (т.е. вызвана ли Initialize)
     */
    fun checkProgramStateExists() {
        scope.launch {
            try {
                val pdaResult = findProgramStatePda() ?: run {
                    _programStateExists.postValue(false)
                    return@launch
                }
                val pdaAddress = Base58.encodeToString(pdaResult.first)
                val data = getAccountInfo(pdaAddress)
                _programStateExists.postValue(data != null && data.size >= 32)
                if (data != null) {
                    val authorityBytes = data.copyOfRange(0, 32)
                    val authorityAddress = Base58.encodeToString(authorityBytes)
                    Timber.d("Program state exists. Authority: $authorityAddress")
                } else {
                    Timber.d("Program state NOT initialized")
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking program state")
                _programStateExists.postValue(false)
            }
        }
    }

    /**
     * Отправляет транзакцию Initialize - записывает authority в program_state PDA
     *
     * Accounts:
     * 0. [writable, signer] Authority account
     * 1. [writable] Program state PDA
     * 2. [] System program
     */
    fun sendInitializeWithStatus(sender: ActivityResultSender) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Wallet not connected")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
            return
        }

        _txStatus.value = TxStatus.SIGNING

        scope.launch {
            try {
                val authorityBytes = Base58.decode(owner)

                val programStatePdaResult = findProgramStatePda()
                    ?: throw Exception("Failed to derive program state PDA")
                val (programStatePdaBytes, _) = programStatePdaResult

                Timber.d("Initialize: authority=$owner, PDA=${Base58.encodeToString(programStatePdaBytes)}")

                // Instruction data: variant byte = 0 (Initialize)
                val instructionData = byteArrayOf(0)

                val blockhash = getLatestBlockhash()

                val serializedTx = buildTransaction(
                    feePayer = authorityBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(authorityBytes, isSigner = true, isWritable = true),
                                AccountMeta(programStatePdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(systemProgramIdBytes, isSigner = false, isWritable = false)
                            ),
                            data = instructionData
                        )
                    )
                )

                Timber.d("Initialize tx built, size=${serializedTx.size}")

                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("Initialize simulation failed: $simError")
                    _errorMessage.postValue("Simulation failed: $simError")
                }

                withContext(Dispatchers.Main) {
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        signTransactions(arrayOf(serializedTx))
                    }

                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTxs = signResult.payload.signedPayloads
                            if (signedTxs.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        delay(2000)
                                        var signature: String? = null
                                        var lastError: Exception? = null
                                        for (attempt in 1..3) {
                                            try {
                                                signature = sendRawTransaction(signedTxs.first())
                                                break
                                            } catch (e: Exception) {
                                                lastError = e
                                                if (attempt < 3) delay(2000)
                                            }
                                        }
                                        if (signature != null) {
                                            Timber.d("Initialize tx sent: $signature")
                                            _txStatus.value = TxStatus.MINING

                                            var confirmed = false
                                            var attempts = 0
                                            while (!confirmed && attempts < 60) {
                                                delay(2000)
                                                val status = getTransactionStatus(signature)
                                                if (status != null) {
                                                    confirmed = true
                                                    Timber.d("Initialize confirmed!")
                                                    _txStatus.value = TxStatus.SUCCESS
                                                    _programStateExists.postValue(true)
                                                }
                                                attempts++
                                            }
                                            if (!confirmed) {
                                                _txStatus.value = TxStatus.SUCCESS
                                                _programStateExists.postValue(true)
                                            }
                                            resetTxStatusAfterDelay()
                                        } else {
                                            throw lastError ?: Exception("Failed to send transaction")
                                        }
                                    } catch (e: Exception) {
                                        Timber.e(e, "Initialize send error")
                                        _txStatus.value = TxStatus.ERROR
                                        _errorMessage.postValue("Send error: ${e.message}")
                                        resetTxStatusAfterDelay()
                                    }
                                }
                            }
                        }
                        is TransactionResult.Failure -> {
                            throw Exception("Transaction rejected: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            throw Exception("Wallet not found")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "Initialize error")
                _txStatus.value = TxStatus.ERROR
                _errorMessage.postValue("Error: ${e.message}")
                resetTxStatusAfterDelay()
            }
        }
    }

    /**
     * Загружает все истекшие боксы из блокчейна (без фильтрации по sender)
     */
    fun fetchAllExpiredBoxes() {
        _sweepLoading.postValue(true)
        scope.launch {
            try {
                val expiredList = mutableListOf<ExpiredBox>()
                val currentTime = System.currentTimeMillis() / 1000

                // Загружаем SOL боксы (dataSize=80) без фильтра sender
                val solAccounts = getAllProgramAccounts(dataSize = 80)
                for (account in solAccounts) {
                    try {
                        if (account.data.size < 80) continue
                        val buffer = java.nio.ByteBuffer.wrap(account.data)
                        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

                        val senderBytes = ByteArray(32)
                        buffer.get(senderBytes)
                        val sender = Base58.encodeToString(senderBytes)

                        val idBytes = ByteArray(32)
                        buffer.get(idBytes)
                        val id = Base58.encodeToString(idBytes)

                        val deadline = buffer.long
                        val amount = buffer.long.toBigInteger()

                        if (deadline != 0L && deadline < currentTime && amount > BigInteger.ZERO) {
                            expiredList.add(ExpiredBox(
                                pubkey = account.pubkey,
                                sender = sender,
                                id = id,
                                deadline = deadline,
                                amount = amount,
                                isToken = false
                            ))
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing SOL box ${account.pubkey}")
                    }
                }

                // Загружаем Token боксы (dataSize=112) без фильтра sender
                val tokenAccounts = getAllProgramAccounts(dataSize = 112)
                for (account in tokenAccounts) {
                    try {
                        if (account.data.size < 112) continue
                        val buffer = java.nio.ByteBuffer.wrap(account.data)
                        buffer.order(java.nio.ByteOrder.LITTLE_ENDIAN)

                        val senderBytes = ByteArray(32)
                        buffer.get(senderBytes)
                        val sender = Base58.encodeToString(senderBytes)

                        val idBytes = ByteArray(32)
                        buffer.get(idBytes)
                        val id = Base58.encodeToString(idBytes)

                        val deadline = buffer.long
                        val amount = buffer.long.toBigInteger()

                        val mintBytes = ByteArray(32)
                        buffer.get(mintBytes)
                        val mint = Base58.encodeToString(mintBytes)

                        if (deadline != 0L && deadline < currentTime && amount > BigInteger.ZERO) {
                            expiredList.add(ExpiredBox(
                                pubkey = account.pubkey,
                                sender = sender,
                                id = id,
                                deadline = deadline,
                                amount = amount,
                                isToken = true,
                                mint = mint
                            ))
                        }
                    } catch (e: Exception) {
                        Timber.e(e, "Error parsing Token box ${account.pubkey}")
                    }
                }

                Timber.d("Found ${expiredList.size} expired boxes (${solAccounts.size} SOL accounts, ${tokenAccounts.size} Token accounts)")
                _expiredBoxes.postValue(expiredList.sortedBy { it.deadline })
            } catch (e: Exception) {
                Timber.e(e, "Error fetching expired boxes")
            } finally {
                _sweepLoading.postValue(false)
            }
        }
    }

    /**
     * Получает ВСЕ аккаунты программы заданного размера (без фильтра sender)
     */
    private suspend fun getAllProgramAccounts(dataSize: Int): List<ProgramAccount> = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", 1)
                put("method", "getProgramAccounts")
                put("params", JSONArray().apply {
                    put(PROGRAM_ID)
                    put(JSONObject().apply {
                        put("encoding", "base64")
                        put("filters", JSONArray().apply {
                            put(JSONObject().apply {
                                put("dataSize", dataSize)
                            })
                        })
                    })
                })
            }

            val request = Request.Builder()
                .url(SOLANA_RPC_URL)
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val responseBody = response.body?.string() ?: return@withContext emptyList()

            val result = JSONObject(responseBody)
            if (result.has("error")) {
                Timber.e("RPC error getAllProgramAccounts: ${result.getJSONObject("error").optString("message")}")
                return@withContext emptyList()
            }

            val accountsArray = when {
                result.get("result") is JSONArray -> result.getJSONArray("result")
                result.get("result") is JSONObject -> {
                    val resultObj = result.getJSONObject("result")
                    if (resultObj.has("value")) resultObj.getJSONArray("value")
                    else return@withContext emptyList()
                }
                else -> return@withContext emptyList()
            }

            val accounts = mutableListOf<ProgramAccount>()
            for (i in 0 until accountsArray.length()) {
                try {
                    val accountObj = accountsArray.getJSONObject(i)
                    val pubkey = accountObj.getString("pubkey")
                    val account = accountObj.getJSONObject("account")

                    val dataBytes = when {
                        account.has("data") && account.get("data") is JSONArray -> {
                            val dataArray = account.getJSONArray("data")
                            Base64.decode(dataArray.getString(0), Base64.DEFAULT)
                        }
                        account.has("data") && account.get("data") is String -> {
                            Base64.decode(account.getString("data"), Base64.DEFAULT)
                        }
                        else -> continue
                    }
                    accounts.add(ProgramAccount(pubkey = pubkey, data = dataBytes))
                } catch (e: Exception) {
                    continue
                }
            }
            accounts
        } catch (e: Exception) {
            Timber.e(e, "Error in getAllProgramAccounts")
            emptyList()
        }
    }

    /**
     * Отправляет транзакцию SweepBox (SOL) - забирает средства из просроченного бокса
     *
     * Accounts:
     * 0. [] Program state PDA
     * 1. [writable] Box PDA account
     * 2. [writable] Authority account (signer)
     */
    fun sendSweepBoxWithStatus(
        boxPubkey: String,
        sender: ActivityResultSender
    ) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Wallet not connected")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
            return
        }

        _txStatus.value = TxStatus.SIGNING

        scope.launch {
            try {
                val authorityBytes = Base58.decode(owner)
                val boxPdaBytes = Base58.decode(boxPubkey)

                val programStatePdaResult = findProgramStatePda()
                    ?: throw Exception("Failed to derive program state PDA")
                val (programStatePdaBytes, _) = programStatePdaResult

                // Instruction data: variant byte = 3 (SweepBox)
                val instructionData = byteArrayOf(3)

                val blockhash = getLatestBlockhash()

                val serializedTx = buildTransaction(
                    feePayer = authorityBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(programStatePdaBytes, isSigner = false, isWritable = false),
                                AccountMeta(boxPdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(authorityBytes, isSigner = true, isWritable = true)
                            ),
                            data = instructionData
                        )
                    )
                )

                Timber.d("SweepBox tx built: boxPDA=$boxPubkey, size=${serializedTx.size}")

                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("SweepBox simulation failed: $simError")
                    _errorMessage.postValue("Simulation failed: $simError")
                }

                withContext(Dispatchers.Main) {
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        signTransactions(arrayOf(serializedTx))
                    }

                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTxs = signResult.payload.signedPayloads
                            if (signedTxs.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        delay(2000)
                                        val signature = sendRawTransaction(signedTxs.first())
                                        Timber.d("SweepBox tx sent: $signature")
                                        _txStatus.value = TxStatus.MINING
                                        waitForSweepConfirmation(signature)
                                    } catch (e: Exception) {
                                        Timber.e(e, "SweepBox send error")
                                        _txStatus.value = TxStatus.ERROR
                                        _errorMessage.postValue("Send error: ${e.message}")
                                        resetTxStatusAfterDelay()
                                    }
                                }
                            }
                        }
                        is TransactionResult.Failure -> {
                            throw Exception("Transaction rejected: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            throw Exception("Wallet not found")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SweepBox error")
                _txStatus.value = TxStatus.ERROR
                _errorMessage.postValue("Error: ${e.message}")
                resetTxStatusAfterDelay()
            }
        }
    }

    /**
     * Отправляет транзакцию SweepBoxToken - забирает токены из просроченного token бокса
     *
     * Accounts:
     * 0. [] Program state PDA
     * 1. [writable] TokenBox PDA account
     * 2. [writable] Vault ATA
     * 3. [writable] Authority token account (ATA)
     * 4. [signer] Authority
     * 5. [] Vault authority PDA
     * 6. [] Token program
     */
    fun sendSweepBoxTokenWithStatus(
        boxPubkey: String,
        mintAddress: String,
        sender: ActivityResultSender
    ) {
        val owner = getSelectedAddress()
        if (owner.isBlank()) {
            _errorMessage.postValue("Wallet not connected")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
            return
        }

        _txStatus.value = TxStatus.SIGNING

        scope.launch {
            try {
                val authorityBytes = Base58.decode(owner)
                val tokenBoxPdaBytes = Base58.decode(boxPubkey)
                val mintBytes = Base58.decode(mintAddress)

                val programStatePdaResult = findProgramStatePda()
                    ?: throw Exception("Failed to derive program state PDA")
                val (programStatePdaBytes, _) = programStatePdaResult

                val vaultPdaResult = findVaultPda(tokenBoxPdaBytes)
                    ?: throw Exception("Failed to derive vault PDA")
                val (vaultAuthorityBytes, _) = vaultPdaResult

                val vaultAta = getAssociatedTokenAddress(vaultAuthorityBytes, mintBytes)
                    ?: throw Exception("Failed to derive vault ATA")

                val authorityAta = getAssociatedTokenAddress(authorityBytes, mintBytes)
                    ?: throw Exception("Failed to derive authority ATA")

                // Instruction data: variant byte = 6 (SweepBoxToken)
                val instructionData = byteArrayOf(6)

                val blockhash = getLatestBlockhash()

                val serializedTx = buildTransaction(
                    feePayer = authorityBytes,
                    recentBlockhash = Base58.decode(blockhash),
                    instructions = listOf(
                        Instruction(
                            programId = programIdBytes,
                            accounts = listOf(
                                AccountMeta(programStatePdaBytes, isSigner = false, isWritable = false),
                                AccountMeta(tokenBoxPdaBytes, isSigner = false, isWritable = true),
                                AccountMeta(vaultAta, isSigner = false, isWritable = true),
                                AccountMeta(authorityAta, isSigner = false, isWritable = true),
                                AccountMeta(authorityBytes, isSigner = true, isWritable = true),
                                AccountMeta(vaultAuthorityBytes, isSigner = false, isWritable = false),
                                AccountMeta(tokenProgramIdBytes, isSigner = false, isWritable = false)
                            ),
                            data = instructionData
                        )
                    )
                )

                Timber.d("SweepBoxToken tx built: tokenBoxPDA=$boxPubkey, mint=$mintAddress, size=${serializedTx.size}")

                val simError = simulateTransaction(serializedTx)
                if (simError != null) {
                    Timber.e("SweepBoxToken simulation failed: $simError")
                    _errorMessage.postValue("Simulation failed: $simError")
                }

                withContext(Dispatchers.Main) {
                    val walletAdapter = MobileWalletAdapter(connectionIdentity).apply {
                        blockchain = Solana.Mainnet
                    }
                    val signResult = walletAdapter.transact(sender) { authResult ->
                        authToken = authResult.authToken
                        connectedPublicKey = authResult.publicKey
                        val address = Base58.encodeToString(authResult.publicKey)
                        saveSession(authResult.publicKey, authResult.authToken, address)
                        _isConnected.postValue(true)
                        _walletAddress.postValue(address)
                        signTransactions(arrayOf(serializedTx))
                    }

                    when (signResult) {
                        is TransactionResult.Success -> {
                            val signedTxs = signResult.payload.signedPayloads
                            if (signedTxs.isNotEmpty()) {
                                scope.launch {
                                    try {
                                        delay(2000)
                                        val signature = sendRawTransaction(signedTxs.first())
                                        Timber.d("SweepBoxToken tx sent: $signature")
                                        _txStatus.value = TxStatus.MINING
                                        waitForSweepConfirmation(signature)
                                    } catch (e: Exception) {
                                        Timber.e(e, "SweepBoxToken send error")
                                        _txStatus.value = TxStatus.ERROR
                                        _errorMessage.postValue("Send error: ${e.message}")
                                        resetTxStatusAfterDelay()
                                    }
                                }
                            }
                        }
                        is TransactionResult.Failure -> {
                            throw Exception("Transaction rejected: ${signResult.e.message}")
                        }
                        is TransactionResult.NoWalletFound -> {
                            throw Exception("Wallet not found")
                        }
                    }
                }
            } catch (e: Exception) {
                Timber.e(e, "SweepBoxToken error")
                _txStatus.value = TxStatus.ERROR
                _errorMessage.postValue("Error: ${e.message}")
                resetTxStatusAfterDelay()
            }
        }
    }

    /**
     * Ожидает подтверждения sweep транзакции и обновляет список expired boxes
     */
    private suspend fun waitForSweepConfirmation(signature: String) {
        try {
            var confirmed = false
            var attempts = 0

            while (!confirmed && attempts < 60) {
                delay(2000)
                val status = getTransactionStatus(signature)
                if (status != null) {
                    confirmed = true
                    Timber.d("Sweep transaction confirmed: $signature")
                    _txStatus.value = TxStatus.SUCCESS
                    refreshBalances()
                    fetchAllExpiredBoxes()
                }
                attempts++
            }

            if (!confirmed) {
                Timber.w("Sweep transaction not confirmed within 120s")
                _txStatus.value = TxStatus.SUCCESS
            }
            resetTxStatusAfterDelay()
        } catch (e: Exception) {
            Timber.e(e, "Error waiting for sweep confirmation")
            _txStatus.value = TxStatus.ERROR
            resetTxStatusAfterDelay()
        }
    }

    // ==================== END SWEEP FUNCTIONALITY ====================

    /**
     * Форматирует lamports в SOL
     */
    private fun formatSol(lamports: Long, fractionDigits: Int): String {
        val sol = BigDecimal(lamports).divide(BigDecimal(LAMPORTS_PER_SOL), fractionDigits, RoundingMode.DOWN)
        return sol.stripTrailingZeros().toPlainString()
    }

    override fun onCleared() {
        super.onCleared()
        scope.cancel()
        mainScope.cancel()
    }
}

/**
 * Factory для создания SolanaManager с передачей Context
 */
class SolanaManagerFactory(private val context: Context) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SolanaManager::class.java)) {
            return SolanaManager(context) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
