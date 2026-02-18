use solana_program::program_error::ProgramError;

#[repr(u32)]
pub enum EscrowError {
    BadDeadline = 0,
    UnknownId = 1,
    TooLate = 2,
    NotExpired = 3,
    Unauthorized = 4,
    NoSol = 5,
    InvalidMint = 6,
    TokenTransferFailed = 7,
    InvalidTokenAccount = 8,
}

impl From<EscrowError> for ProgramError {
    fn from(e: EscrowError) -> Self {
        ProgramError::Custom(e as u32)
    }
}

