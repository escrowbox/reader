use borsh::{BorshDeserialize, BorshSerialize};
use solana_program::{
    account_info::{next_account_info, AccountInfo},
    entrypoint::ProgramResult,
    program_error::ProgramError,
    pubkey::Pubkey,
    rent::Rent,
    sysvar::Sysvar,
    clock::Clock,
    program::invoke_signed,
    program::invoke,
    system_instruction,
};
use spl_token::instruction as token_instruction;
use spl_associated_token_account::instruction as ata_instruction;
use spl_associated_token_account::get_associated_token_address;

use crate::{error::EscrowError, instruction::EscrowInstruction, state::{Box, ProgramState, TokenBox}};

pub struct Processor;

impl Processor {
    pub fn process(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
        instruction_data: &[u8],
    ) -> ProgramResult {
        let instruction = EscrowInstruction::unpack(instruction_data)?;

        match instruction {
            EscrowInstruction::Initialize => {
                Self::process_initialize(program_id, accounts)
            }
            EscrowInstruction::CreateBox { id, deadline_days, amount } => {
                Self::process_create_box(program_id, accounts, id, deadline_days, amount)
            }
            EscrowInstruction::OpenBox => {
                Self::process_open_box(program_id, accounts)
            }
            EscrowInstruction::SweepBox => {
                Self::process_sweep_box(program_id, accounts)
            }
            EscrowInstruction::CreateBoxToken { id, deadline_days, amount } => {
                Self::process_create_box_token(program_id, accounts, id, deadline_days, amount)
            }
            EscrowInstruction::OpenBoxToken => {
                Self::process_open_box_token(program_id, accounts)
            }
            EscrowInstruction::SweepBoxToken => {
                Self::process_sweep_box_token(program_id, accounts)
            }
        }
    }

    fn process_initialize(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let authority = next_account_info(account_info_iter)?;
        let program_state_account = next_account_info(account_info_iter)?;
        let system_program = next_account_info(account_info_iter)?;

        if !authority.is_signer {
            return Err(ProgramError::MissingRequiredSignature);
        }

        let (program_state_pda, bump) = Pubkey::find_program_address(
            &[b"program_state"],
            program_id,
        );

        if program_state_pda != *program_state_account.key {
            return Err(ProgramError::InvalidSeeds);
        }

        let rent = Rent::get()?;
        let space = ProgramState::LEN;
        let lamports = rent.minimum_balance(space);

        invoke_signed(
            &system_instruction::create_account(
                authority.key,
                program_state_account.key,
                lamports,
                space as u64,
                program_id,
            ),
            &[authority.clone(), program_state_account.clone(), system_program.clone()],
            &[&[b"program_state", &[bump]]],
        )?;

        let program_state = ProgramState {
            authority: *authority.key,
        };
        program_state.serialize(&mut &mut program_state_account.data.borrow_mut()[..])?;

        Ok(())
    }

    fn process_create_box(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
        id: Pubkey,
        deadline_days: u16,
        amount: u64,
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let sender = next_account_info(account_info_iter)?;
        let box_account = next_account_info(account_info_iter)?;
        let system_program = next_account_info(account_info_iter)?;

        if !sender.is_signer {
            return Err(ProgramError::MissingRequiredSignature);
        }

        if deadline_days == 0 || deadline_days > 365 {
            return Err(EscrowError::BadDeadline.into());
        }

        if amount == 0 {
            return Err(EscrowError::NoSol.into());
        }

        let (box_pda, bump) = Pubkey::find_program_address(
            &[b"box", sender.key.as_ref(), id.as_ref()],
            program_id,
        );

        if box_pda != *box_account.key {
            return Err(ProgramError::InvalidSeeds);
        }

        let clock = Clock::get()?;
        let final_deadline = clock
            .unix_timestamp
            .checked_add((deadline_days as i64).checked_mul(86400).unwrap())
            .unwrap();

        let rent = Rent::get()?;
        let space = Box::LEN;
        let lamports = rent.minimum_balance(space);

        invoke_signed(
            &system_instruction::create_account(
                sender.key,
                box_account.key,
                lamports,
                space as u64,
                program_id,
            ),
            &[sender.clone(), box_account.clone(), system_program.clone()],
            &[&[b"box", sender.key.as_ref(), id.as_ref(), &[bump]]],
        )?;

        let escrow_box = Box {
            sender: *sender.key,
            id,
            deadline: final_deadline,
            amount,
        };
        escrow_box.serialize(&mut &mut box_account.data.borrow_mut()[..])?;

        // Transfer SOL to box
        invoke_signed(
            &system_instruction::transfer(sender.key, box_account.key, amount),
            &[sender.clone(), box_account.clone(), system_program.clone()],
            &[],
        )?;

        Ok(())
    }

    fn process_open_box(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let box_account = next_account_info(account_info_iter)?;
        let recipient = next_account_info(account_info_iter)?;

        if box_account.owner != program_id {
            return Err(ProgramError::InvalidAccountOwner);
        }

        let mut escrow_box = Box::try_from_slice(&box_account.data.borrow())?;

        if escrow_box.deadline == 0 {
            return Err(EscrowError::UnknownId.into());
        }

        let clock = Clock::get()?;
        if clock.unix_timestamp >= escrow_box.deadline {
            return Err(EscrowError::TooLate.into());
        }

        let amount = escrow_box.amount;

        // Transfer SOL to recipient
        **box_account.try_borrow_mut_lamports()? -= amount;
        **recipient.try_borrow_mut_lamports()? += amount;

        // Mark box as closed
        escrow_box.deadline = 0;
        escrow_box.amount = 0;
        escrow_box.serialize(&mut &mut box_account.data.borrow_mut()[..])?;

        Ok(())
    }

    fn process_sweep_box(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let program_state_account = next_account_info(account_info_iter)?;
        let box_account = next_account_info(account_info_iter)?;
        let authority = next_account_info(account_info_iter)?;

        if program_state_account.owner != program_id {
            return Err(ProgramError::InvalidAccountOwner);
        }

        if box_account.owner != program_id {
            return Err(ProgramError::InvalidAccountOwner);
        }

        let program_state = ProgramState::try_from_slice(&program_state_account.data.borrow())?;

        if *authority.key != program_state.authority {
            return Err(EscrowError::Unauthorized.into());
        }

        let mut escrow_box = Box::try_from_slice(&box_account.data.borrow())?;

        if escrow_box.deadline == 0 {
            return Err(EscrowError::UnknownId.into());
        }

        let clock = Clock::get()?;
        if clock.unix_timestamp < escrow_box.deadline {
            return Err(EscrowError::NotExpired.into());
        }

        let amount = escrow_box.amount;

        // Transfer SOL to authority
        **box_account.try_borrow_mut_lamports()? -= amount;
        **authority.try_borrow_mut_lamports()? += amount;

        // Mark box as closed
        escrow_box.deadline = 0;
        escrow_box.amount = 0;
        escrow_box.serialize(&mut &mut box_account.data.borrow_mut()[..])?;

        Ok(())
    }

    fn process_create_box_token(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
        id: Pubkey,
        deadline_days: u16,
        amount: u64,
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let sender = next_account_info(account_info_iter)?;
        let sender_token_account = next_account_info(account_info_iter)?;
        let token_box_account = next_account_info(account_info_iter)?;
        let vault_ata = next_account_info(account_info_iter)?;
        let mint = next_account_info(account_info_iter)?;
        let vault_authority_info = next_account_info(account_info_iter)?;
        let token_program = next_account_info(account_info_iter)?;
        let associated_token_program = next_account_info(account_info_iter)?;
        let system_program = next_account_info(account_info_iter)?;

        if !sender.is_signer {
            return Err(ProgramError::MissingRequiredSignature);
        }

        if deadline_days == 0 || deadline_days > 365 {
            return Err(EscrowError::BadDeadline.into());
        }

        if amount == 0 {
            return Err(EscrowError::NoSol.into());
        }

        // Derive TokenBox PDA
        let (token_box_pda, box_bump) = Pubkey::find_program_address(
            &[b"token_box", sender.key.as_ref(), id.as_ref()],
            program_id,
        );

        if token_box_pda != *token_box_account.key {
            return Err(ProgramError::InvalidSeeds);
        }

        // Derive vault PDA (will own the ATA)
        let (vault_authority, vault_bump) = Pubkey::find_program_address(
            &[b"vault", token_box_pda.as_ref()],
            program_id,
        );

        // Verify vault authority account matches derived PDA
        if vault_authority != *vault_authority_info.key {
            return Err(ProgramError::InvalidSeeds);
        }

        // Expected vault ATA
        let expected_vault_ata = get_associated_token_address(&vault_authority, mint.key);
        if expected_vault_ata != *vault_ata.key {
            return Err(EscrowError::InvalidTokenAccount.into());
        }

        let clock = Clock::get()?;
        let final_deadline = clock
            .unix_timestamp
            .checked_add((deadline_days as i64).checked_mul(86400).unwrap())
            .unwrap();

        // Create TokenBox account
        let rent = Rent::get()?;
        let space = TokenBox::LEN;
        let lamports = rent.minimum_balance(space);

        invoke_signed(
            &system_instruction::create_account(
                sender.key,
                token_box_account.key,
                lamports,
                space as u64,
                program_id,
            ),
            &[sender.clone(), token_box_account.clone(), system_program.clone()],
            &[&[b"token_box", sender.key.as_ref(), id.as_ref(), &[box_bump]]],
        )?;

        // Save TokenBox state
        let token_box = TokenBox {
            sender: *sender.key,
            id,
            deadline: final_deadline,
            amount,
            mint: *mint.key,
        };
        token_box.serialize(&mut &mut token_box_account.data.borrow_mut()[..])?;

        // Create vault ATA if it doesn't exist
        if vault_ata.data_is_empty() {
            invoke(
                &ata_instruction::create_associated_token_account(
                    sender.key,
                    &vault_authority,
                    mint.key,
                    token_program.key,
                ),
                &[
                    sender.clone(),
                    vault_ata.clone(),
                    vault_authority_info.clone(),
                    mint.clone(),
                    system_program.clone(),
                    token_program.clone(),
                    associated_token_program.clone(),
                ],
            )?;
        }

        // Transfer tokens from sender to vault
        invoke(
            &token_instruction::transfer(
                token_program.key,
                sender_token_account.key,
                vault_ata.key,
                sender.key,
                &[],
                amount,
            )?,
            &[
                sender_token_account.clone(),
                vault_ata.clone(),
                sender.clone(),
                token_program.clone(),
            ],
        )?;

        Ok(())
    }

    fn process_open_box_token(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let token_box_account = next_account_info(account_info_iter)?;
        let vault_ata = next_account_info(account_info_iter)?;
        let recipient_token_account = next_account_info(account_info_iter)?;
        let sender = next_account_info(account_info_iter)?;
        let vault_authority_info = next_account_info(account_info_iter)?;
        let token_program = next_account_info(account_info_iter)?;

        if token_box_account.owner != program_id {
            return Err(ProgramError::InvalidAccountOwner);
        }

        let mut token_box = TokenBox::try_from_slice(&token_box_account.data.borrow())?;

        if token_box.deadline == 0 {
            return Err(EscrowError::UnknownId.into());
        }

        let clock = Clock::get()?;
        if clock.unix_timestamp >= token_box.deadline {
            return Err(EscrowError::TooLate.into());
        }

        // Derive vault authority PDA
        let (vault_authority, vault_bump) = Pubkey::find_program_address(
            &[b"vault", token_box_account.key.as_ref()],
            program_id,
        );

        // Verify vault authority account matches derived PDA
        if vault_authority != *vault_authority_info.key {
            return Err(ProgramError::InvalidSeeds);
        }

        let amount = token_box.amount;

        // Transfer tokens from vault to recipient
        invoke_signed(
            &token_instruction::transfer(
                token_program.key,
                vault_ata.key,
                recipient_token_account.key,
                &vault_authority,
                &[],
                amount,
            )?,
            &[
                vault_ata.clone(),
                recipient_token_account.clone(),
                vault_authority_info.clone(),
                token_program.clone(),
            ],
            &[&[b"vault", token_box_account.key.as_ref(), &[vault_bump]]],
        )?;

        // Close vault ATA and return rent to sender
        invoke_signed(
            &token_instruction::close_account(
                token_program.key,
                vault_ata.key,
                sender.key,
                &vault_authority,
                &[],
            )?,
            &[
                vault_ata.clone(),
                sender.clone(),
                vault_authority_info.clone(),
                token_program.clone(),
            ],
            &[&[b"vault", token_box_account.key.as_ref(), &[vault_bump]]],
        )?;

        // Mark box as closed
        token_box.deadline = 0;
        token_box.amount = 0;
        token_box.serialize(&mut &mut token_box_account.data.borrow_mut()[..])?;

        Ok(())
    }

    fn process_sweep_box_token(
        program_id: &Pubkey,
        accounts: &[AccountInfo],
    ) -> ProgramResult {
        let account_info_iter = &mut accounts.iter();
        let program_state_account = next_account_info(account_info_iter)?;
        let token_box_account = next_account_info(account_info_iter)?;
        let vault_ata = next_account_info(account_info_iter)?;
        let authority_token_account = next_account_info(account_info_iter)?;
        let authority = next_account_info(account_info_iter)?;
        let vault_authority_info = next_account_info(account_info_iter)?;
        let token_program = next_account_info(account_info_iter)?;

        if program_state_account.owner != program_id {
            return Err(ProgramError::InvalidAccountOwner);
        }

        if token_box_account.owner != program_id {
            return Err(ProgramError::InvalidAccountOwner);
        }

        let program_state = ProgramState::try_from_slice(&program_state_account.data.borrow())?;

        if *authority.key != program_state.authority {
            return Err(EscrowError::Unauthorized.into());
        }

        if !authority.is_signer {
            return Err(ProgramError::MissingRequiredSignature);
        }

        let mut token_box = TokenBox::try_from_slice(&token_box_account.data.borrow())?;

        if token_box.deadline == 0 {
            return Err(EscrowError::UnknownId.into());
        }

        let clock = Clock::get()?;
        if clock.unix_timestamp < token_box.deadline {
            return Err(EscrowError::NotExpired.into());
        }

        // Derive vault authority PDA
        let (vault_authority, vault_bump) = Pubkey::find_program_address(
            &[b"vault", token_box_account.key.as_ref()],
            program_id,
        );

        // Verify vault authority account matches derived PDA
        if vault_authority != *vault_authority_info.key {
            return Err(ProgramError::InvalidSeeds);
        }

        let amount = token_box.amount;

        // Transfer tokens from vault to authority
        invoke_signed(
            &token_instruction::transfer(
                token_program.key,
                vault_ata.key,
                authority_token_account.key,
                &vault_authority,
                &[],
                amount,
            )?,
            &[
                vault_ata.clone(),
                authority_token_account.clone(),
                vault_authority_info.clone(),
                token_program.clone(),
            ],
            &[&[b"vault", token_box_account.key.as_ref(), &[vault_bump]]],
        )?;

        // Close vault ATA and return rent to authority
        invoke_signed(
            &token_instruction::close_account(
                token_program.key,
                vault_ata.key,
                authority.key,
                &vault_authority,
                &[],
            )?,
            &[
                vault_ata.clone(),
                authority.clone(),
                vault_authority_info.clone(),
                token_program.clone(),
            ],
            &[&[b"vault", token_box_account.key.as_ref(), &[vault_bump]]],
        )?;

        // Mark box as closed
        token_box.deadline = 0;
        token_box.amount = 0;
        token_box.serialize(&mut &mut token_box_account.data.borrow_mut()[..])?;

        Ok(())
    }
}

