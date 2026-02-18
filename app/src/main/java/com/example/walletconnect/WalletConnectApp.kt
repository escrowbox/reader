package com.example.walletconnect

import android.app.Application
import org.bouncycastle.jce.provider.BouncyCastleProvider
import java.security.Security
import timber.log.Timber

/**
 * Application класс для Solana EPUB Reader
 * Инициализирует необходимые компоненты для работы с Solana через Mobile Wallet Adapter
 */
class WalletConnectApp : Application() {

    override fun onCreate() {
        super.onCreate()
        
        // Регистрация BouncyCastle для криптографии (Ed25519)
        setupBouncyCastle()
        
        // Инициализация логгера
        Timber.plant(Timber.DebugTree())

        Timber.d("✅✅✅ Solana EPUB Reader инициализирован!")
        Timber.d("📱 Network: Mainnet-Beta")
        Timber.d("📦 Program ID: ${SolanaManager.PROGRAM_ID}")
        Timber.d("🔗 RPC: ${SolanaManager.SOLANA_RPC_URL}")
    }

    /**
     * Регистрирует BouncyCastle как Security Provider.
     * Это необходимо для работы алгоритмов Ed25519 (используются в Solana).
     */
    private fun setupBouncyCastle() {
        try {
            val provider = Security.getProvider(BouncyCastleProvider.PROVIDER_NAME)
            if (provider == null) {
                Security.addProvider(BouncyCastleProvider())
                Timber.d("🔐 BouncyCastle зарегистрирован")
            } else if (provider.javaClass != BouncyCastleProvider::class.java) {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.addProvider(BouncyCastleProvider())
                Timber.d("🔐 BouncyCastle перерегистрирован (обновлен)")
            } else {
                Timber.d("🔐 BouncyCastle уже зарегистрирован")
            }
        } catch (e: Exception) {
            Timber.e(e, "🚨 Ошибка при регистрации BouncyCastle")
        }
    }
}
