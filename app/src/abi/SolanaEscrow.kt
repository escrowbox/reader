package com.example.walletconnect.abi

/**
 * Информация о Solana Escrow программе
 * 
 * Развёрнута на Solana Devnet
 */
object SolanaEscrow {
    
    /**
     * Program ID задеплоенной программы
     */
    const val PROGRAM_ID = "6Qz6EaxsD6LZewhM5NAw8ZkHTFcEju2XUAkbnpj9ZeAW"
    
    /**
     * ProgramData Address
     */
    const val PROGRAM_DATA_ADDRESS = "8N23KspfFScvkabe1DmWZzTauDx5ZzX1TJhUvAMh94aT"
    
    /**
     * Authority (owner of the program)
     */
    const val AUTHORITY = "8jzfUmkKvJ1z8zpPg6FE3hHq9n3VqxwsuWohr1RRRxLU"
    
    /**
     * SPL Token Program ID
     */
    const val TOKEN_PROGRAM_ID = "TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA"
    
    /**
     * Associated Token Account Program ID
     */
    const val ASSOCIATED_TOKEN_PROGRAM_ID = "ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL"
    
    /**
     * Инструкции программы (Borsh encoded)
     * 
     * 0 - Initialize: Инициализация program state с authority
     *     Accounts: [authority (signer, writable), program_state_pda (writable), system_program]
     * 
     * 1 - CreateBox: Создание нового escrow бокса (SOL)
     *     Data: id (Pubkey, 32 bytes), deadline_days (u16), amount (u64)
     *     Accounts: [sender (signer, writable), box_pda (writable), system_program]
     *     PDA seeds: ["box", sender.key, id]
     * 
     * 2 - OpenBox: Открытие бокса и получение SOL (до deadline)
     *     Accounts: [box_pda (writable), recipient (writable)]
     * 
     * 3 - SweepBox: Sweep просроченного бокса (после deadline, средства идут authority)
     *     Accounts: [program_state_pda, box_pda (writable), authority (writable)]
     * 
     * 4 - CreateBoxToken: Создание нового token escrow бокса (SPL Token)
     *     Data: id (Pubkey, 32 bytes), deadline_days (u16), amount (u64)
     *     Accounts: [sender (signer, writable), sender_token_account (writable), 
     *                token_box_pda (writable), vault_ata (writable), mint,
     *                vault_authority, token_program, associated_token_program, system_program]
     *     PDA seeds: ["token_box", sender.key, id]
     *     Vault authority PDA seeds: ["vault", token_box_pda]
     * 
     * 5 - OpenBoxToken: Открытие token бокса и получение токенов (до deadline)
     *     Accounts: [token_box_pda (writable), vault_ata (writable), 
     *                recipient_token_account (writable), sender (writable),
     *                vault_authority, token_program]
     * 
     * 6 - SweepBoxToken: Sweep просроченного token бокса (после deadline)
     *     Accounts: [program_state_pda, token_box_pda (writable), vault_ata (writable),
     *                authority_token_account (writable), authority (signer),
     *                vault_authority, token_program]
     */
    object Instructions {
        const val INITIALIZE = 0
        const val CREATE_BOX = 1
        const val OPEN_BOX = 2
        const val SWEEP_BOX = 3
        const val CREATE_BOX_TOKEN = 4
        const val OPEN_BOX_TOKEN = 5
        const val SWEEP_BOX_TOKEN = 6
    }
    
    /**
     * Структура данных Box (Borsh serialized)
     * 
     * sender: Pubkey (32 bytes)
     * id: Pubkey (32 bytes)  
     * deadline: i64 (8 bytes) - Unix timestamp
     * amount: u64 (8 bytes) - в lamports
     * 
     * Total: 80 bytes
     */
    const val BOX_DATA_SIZE = 80
    
    /**
     * Структура данных TokenBox (Borsh serialized)
     * 
     * sender: Pubkey (32 bytes)
     * id: Pubkey (32 bytes)  
     * deadline: i64 (8 bytes) - Unix timestamp
     * amount: u64 (8 bytes) - в token units
     * mint: Pubkey (32 bytes) - mint address
     * 
     * Total: 112 bytes
     */
    const val TOKEN_BOX_DATA_SIZE = 112
    
    /**
     * Структура данных ProgramState (Borsh serialized)
     * 
     * authority: Pubkey (32 bytes)
     * 
     * Total: 32 bytes
     */
    const val PROGRAM_STATE_SIZE = 32
    
    /**
     * Коды ошибок программы
     */
    object Errors {
        const val BAD_DEADLINE = 0     // deadline должен быть 1-365 дней
        const val UNKNOWN_ID = 1       // бокс не найден
        const val TOO_LATE = 2         // deadline уже прошёл
        const val NOT_EXPIRED = 3      // бокс ещё не просрочен (для sweep)
        const val UNAUTHORIZED = 4     // не authority
        const val NO_SOL = 5           // amount должен быть > 0
        const val INVALID_TOKEN_ACCOUNT = 6 // неверный token account
    }
    
    /**
     * PDA Seeds
     */
    object Seeds {
        val BOX = "box".toByteArray()
        val TOKEN_BOX = "token_box".toByteArray()
        val VAULT = "vault".toByteArray()
        val PROGRAM_STATE = "program_state".toByteArray()
    }
}







