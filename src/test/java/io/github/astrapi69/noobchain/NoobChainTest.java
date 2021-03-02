package io.github.astrapi69.noobchain;

import java.security.Security;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.google.gson.GsonBuilder;

class NoobChainTest
{

	static NoobChain noobChain;

	@BeforeAll
	static void beforeAll() {
		noobChain = new NoobChain();
		//Create the new wallets
		noobChain.walletA = new Wallet();
		noobChain.walletB = new Wallet();
	}

	@Test
	void test2()
	{
		//add our blocks to the blockchain ArrayList:
		Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider()); //Setup Bouncey castle as a Security Provider

		Wallet coinbase = new Wallet();

		//create genesis transaction, which sends 100 NoobCoin to walletA:
		noobChain.genesisTransaction = new Transaction(coinbase.publicKey, noobChain.walletA.publicKey, 100f, null);
		noobChain.genesisTransaction.generateSignature(coinbase.privateKey);	 //manually sign the genesis transaction
		noobChain.genesisTransaction.transactionId = "0"; //manually set the transaction id
		noobChain.genesisTransaction.outputs.add(new TransactionOutput(noobChain.genesisTransaction.reciepient, noobChain.genesisTransaction.value, noobChain.genesisTransaction.transactionId)); //manually add the Transactions Output
		noobChain.UTXOs.put(noobChain.genesisTransaction.outputs.get(0).id, noobChain.genesisTransaction.outputs.get(0)); //its important to store our first transaction in the UTXOs list.

		System.out.println("Creating and Mining Genesis block... ");
		Block genesis = new Block("0");
		genesis.addTransaction(noobChain.genesisTransaction);
		noobChain.addBlock(genesis);

		//testing
		Block block1 = new Block(genesis.hash);
		System.out.println("\nWalletA's balance is: " + noobChain.walletA.getBalance());
		System.out.println("\nWalletA is Attempting to send funds (40) to WalletB...");
		block1.addTransaction(noobChain.walletA.sendFunds(noobChain.walletB.publicKey, 40f));
		noobChain.addBlock(block1);
		System.out.println("\nWalletA's balance is: " + noobChain.walletA.getBalance());
		System.out.println("WalletB's balance is: " + noobChain.walletB.getBalance());

		Block block2 = new Block(block1.hash);
		System.out.println("\nWalletA Attempting to send more funds (1000) than it has...");
		block2.addTransaction(noobChain.walletA.sendFunds(noobChain.walletB.publicKey, 1000f));
		noobChain.addBlock(block2);
		System.out.println("\nWalletA's balance is: " + noobChain.walletA.getBalance());
		System.out.println("WalletB's balance is: " + noobChain.walletB.getBalance());

		Block block3 = new Block(block2.hash);
		System.out.println("\nWalletB is Attempting to send funds (20) to WalletA...");
		block3.addTransaction(noobChain.walletB.sendFunds( noobChain.walletA.publicKey, 20));
		System.out.println("\nWalletA's balance is: " + noobChain.walletA.getBalance());
		System.out.println("WalletB's balance is: " + noobChain.walletB.getBalance());

		noobChain.isChainValid();
	}

}
