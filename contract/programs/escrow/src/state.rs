use borsh::{BorshDeserialize, BorshSerialize};
use solana_program::pubkey::Pubkey;

#[derive(BorshSerialize, BorshDeserialize, Debug)]
pub struct ProgramState {
    pub authority: Pubkey,
}

impl ProgramState {
    pub const LEN: usize = 32; // authority pubkey
}

#[derive(BorshSerialize, BorshDeserialize, Debug)]
pub struct Box {
    pub sender: Pubkey,
    pub id: Pubkey,
    pub deadline: i64,
    pub amount: u64,
}

impl Box {
    pub const LEN: usize = 32 + 32 + 8 + 8; // sender + id + deadline + amount
}

#[derive(BorshSerialize, BorshDeserialize, Debug)]
pub struct TokenBox {
    pub sender: Pubkey,
    pub id: Pubkey,
    pub deadline: i64,
    pub amount: u64,
    pub mint: Pubkey,
}impl TokenBox {
    pub const LEN: usize = 32 + 32 + 8 + 8 + 32; // sender + id + deadline + amount + mint
}