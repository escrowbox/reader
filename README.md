# Escrow reader

Escrow Reader is an aplication that makes reading an interesting activity.

## How does this work?

You make a deposit that you risk losing if you fail to complete the reading within the set deadline. You determine the deposit amount and deadline yourself. A smart contract on the Solana blockchain will automatically return your deposit once it verifies that the read has completed successfully.

The application offers two reading modes to choose from: checkpoint detection and timer reset.

## Checkpoint detection

A checkpoint is any word or sentence, for example, \"This is a checkpoint!!!\", that does not visually stand out from the surrounding text in color or font. You may define the checkpoint text at your own discretion. The application places three checkpoints at the beginning, in the middle, and at the end of the book, randomly. For the book to be considered read, you must find all of them before the deadline. If you fail to do it in time, then you will lose your deposit.

## Timer reset

You choose a book and set the number of hours you plan to spend on it. Once you start flipping through the pages, the timer begins. When you stop flipping, the timer pauses. Reading is not required, but you must reset the timer before the deadline. If you fail to do so, you will lose your deposit.

## Warning

The app stores a unique key for each deposit in your phone's memory. Without the key, the contract will not be able to return the deposit, so do not delete the app, otherwise the keys will be irretrievably lost. Reinstalling the app will not restore it.

The application code is open, its behavior is completely predictable, and there are no other ways to appropriate your money.

---

[![скачать escrow_reader.apk](https://img.shields.io/badge/скачать-escrow_reader.apk-2ea043?style=for-the-badge)](./escrow_reader.apk)
