use borsh::{BorshDeserialize, BorshSerialize};
use solana_program::{
    program_error::ProgramError,
    pubkey::Pubkey,
};

#[derive(BorshSerialize, BorshDeserialize, Debug)]
pub enum EscrowInstruction {
    /// Initialize program state with authority
    /// Accounts:
    /// 0. `[writable, signer]` Authority account
    /// 1. `[writable]` Program state PDA
    /// 2. `[]` System program
    Initialize,

    /// Create a new escrow box
    /// Accounts:
    /// 0. `[writable, signer]` Sender account
    /// 1. `[writable]` Box PDA account
    /// 2. `[]` System program
    CreateBox {
        id: Pubkey,
        deadline_days: u16,
        amount: u64,
    },

    /// Open an escrow box (before deadline)
    /// Accounts:
    /// 0. `[writable]` Box PDA account
    /// 1. `[writable]` Recipient account
    OpenBox,

    /// Sweep expired box (after deadline, funds go to authority)
    /// Accounts:
    /// 0. `[]` Program state PDA
    /// 1. `[writable]` Box PDA account
    /// 2. `[writable]` Authority account
    SweepBox,

    /// Create a new token escrow box
    /// Accounts:
    /// 0. `[writable, signer]` Sender account
    /// 1. `[writable]` Sender token account (ATA)
    /// 2. `[writable]` TokenBox PDA account
    /// 3. `[writable]` Vault ATA (PDA-owned)
    /// 4. `[]` Mint account
    /// 5. `[]` Vault authority PDA (seeds: ["vault", token_box_pda])
    /// 6. `[]` Token program
    /// 7. `[]` Associated token program
    /// 8. `[]` System program
    CreateBoxToken {
        id: Pubkey,
        deadline_days: u16,
        amount: u64,
    },

    /// Open a token escrow box (before deadline)
    /// Accounts:
    /// 0. `[writable]` TokenBox PDA account
    /// 1. `[writable]` Vault ATA
    /// 2. `[writable]` Recipient token account
    /// 3. `[writable]` Sender account (for rent return)
    /// 4. `[]` Vault authority PDA (seeds: ["vault", token_box_pda])
    /// 5. `[]` Token program
    OpenBoxToken,

    /// Sweep expired token box (after deadline, tokens go to authority)
    /// Accounts:
    /// 0. `[]` Program state PDA
    /// 1. `[writable]` TokenBox PDA account
    /// 2. `[writable]` Vault ATA
    /// 3. `[writable]` Authority token account
    /// 4. `[signer]` Authority
    /// 5. `[]` Vault authority PDA (seeds: ["vault", token_box_pda])
    /// 6. `[]` Token program
    SweepBoxToken,
}

impl EscrowInstruction {
    pub fn unpack(input: &[u8]) -> Result<Self, ProgramError> {
        let (&variant, rest) = input.split_first().ok_or(ProgramError::InvalidInstructionData)?;
        
        Ok(match variant {
            0 => Self::Initialize,
            1 => {
                let payload = CreateBoxPayload::try_from_slice(rest)
                    .map_err(|_| ProgramError::InvalidInstructionData)?;
                Self::CreateBox {
                    id: payload.id,
                    deadline_days: payload.deadline_days,
                    amount: payload.amount,
                }
            }
            2 => Self::OpenBox,
            3 => Self::SweepBox,
            4 => {
                let payload = CreateBoxPayload::try_from_slice(rest)
                    .map_err(|_| ProgramError::InvalidInstructionData)?;
                Self::CreateBoxToken {
                    id: payload.id,
                    deadline_days: payload.deadline_days,
                    amount: payload.amount,
                }
            }
            5 => Self::OpenBoxToken,
            6 => Self::SweepBoxToken,
            _ => return Err(ProgramError::InvalidInstructionData),
        })
    }
}

#[derive(BorshSerialize, BorshDeserialize)]
struct CreateBoxPayload {
    id: Pubkey,
    deadline_days: u16,
    amount: u64,
}

