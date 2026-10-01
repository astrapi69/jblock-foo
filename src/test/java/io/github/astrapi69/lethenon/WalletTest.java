/*
 * The MIT License
 *
 * Copyright (C) 2015 Asterios Raptis
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
 * LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
 * OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
 * WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.astrapi69.lethenon;

import static io.github.astrapi69.lethenon.TestChains.chainWith;
import static io.github.astrapi69.lethenon.TestChains.minersFor;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.astrapi69.mystic.crypt.secret.SecretShare;
import io.github.astrapi69.mystic.crypt.secret.SecretSharing;

/**
 * What a restored wallet has to be: the same wallet, whichever shares it came from, with every key
 * - the two spend keys and the view key - and nothing that is not its own.
 */
class WalletTest
{

	private final Wallet original = Wallet.create();

	private final List<SecretShare> shares = original.split(3, 5);

	static Stream<List<Integer>> threeOfFive()
	{
		return Stream.of(List.of(0, 1, 2), List.of(2, 3, 4), List.of(4, 0, 2), List.of(1, 3, 4));
	}

	@ParameterizedTest(name = "shares {0}")
	@MethodSource("threeOfFive")
	void anyThreeOfFiveShares_restoreEveryKeyOfTheWallet(final List<Integer> chosen)
	{
		Wallet restored = Wallet.restore(chosen.stream().map(shares::get).toList());

		for (SignatureSuite suite : SignatureSuite.values())
		{
			assertEquals(original.spendKey(suite), restored.spendKey(suite), suite.identifier());
		}
		assertEquals(original.address(), restored.address());
	}

	/**
	 * The paths are a recovery format: a backup made today has to bring back the same keys after
	 * any later change to this class. Seed 00 01 .. 1f. The Ed25519 and X25519 keys were computed a
	 * second time outside this code, with Python's hmac and pyca/cryptography, along SLIP-0010
	 * m/1984'/0'/0' and m/1984'/0'/1'. The ML-DSA-65 key has no second implementation to compare
	 * with; its SHA-256 is pinned as first measured, so that it can at least not move unnoticed.
	 */
	@Test
	void theKeysOfAKnownSeed_staySoForever()
	{
		byte[] seed = new byte[Wallet.SEED_LENGTH];
		for (int index = 0; index < seed.length; index++)
		{
			seed[index] = (byte)index;
		}
		Wallet known = Wallet.restore(SecretSharing.split(seed, 2, 2));

		assertEquals(
			Bytes.ofHex("d01538323336d200de992273a7904049a56a59ab10605f1a41ed417504feb0b3"),
			last32(known.spendKey(SignatureSuite.ED25519)));
		assertEquals(
			Bytes.ofHex("ba46d9d5ea1cac475b50f4647249e0502693817a937217d718d0f6fbeb645c2a"),
			last32(known.address().viewKey()));
		assertEquals(
			Bytes.ofHex("e519a8b5bc04559ef5c7e2f14de82d549c35b560cddd023657f5ee8fa6aff534"),
			Bytes.of(SigningPayload
				.digestOf(known.spendKey(SignatureSuite.ML_DSA_65).toByteArray())));
	}

	@Test
	void theKeysOfOneWallet_areDifferentFromEachOther_andFromAnotherWallet()
	{
		Wallet another = Wallet.create();

		assertNotEquals(original.spendKey(SignatureSuite.ED25519),
			another.spendKey(SignatureSuite.ED25519));
		assertNotEquals(original.address().viewKey(), another.address().viewKey());
		assertNotEquals(original.spendKey(SignatureSuite.ML_DSA_65),
			another.spendKey(SignatureSuite.ML_DSA_65));
	}

	@Test
	void theRestoredViewKey_findsThePaymentsMadeToTheOriginalAddress()
	{
		Wallet sender = Wallet.create();
		Bytes senderKey = sender.spendKey(SignatureSuite.ED25519);
		SignedTransaction payment = sender.sign(new TransactionBody(Chain.IDENTIFIER, 0L, senderKey,
			OneTimeAddresses.destinationFor(original.address(),
				OneTimeAddresses.newEphemeralKeyPair()),
			Amount.ofLeth(9L), Amount.ZERO, "paid before the wallet was lost"),
			SignatureSuite.ED25519);
		List<BlockBody> chain = chainWith(payment);
		Wallet restored = Wallet.restore(List.of(shares.get(1), shares.get(2), shares.get(4)));

		WalletScan scan = WalletScan.over(chain, minersFor(chain), senderKey, restored.address(),
			restored.viewKeyPair().getPrivate());

		assertEquals(Amount.ofLeth(9L), scan.balance(), scan.describe());
	}

	@Test
	void fewerSharesThanTheThreshold_areRefused()
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Wallet.restore(shares.subList(0, 2)));

		assertTrue(refused.getMessage().contains("needs 3 shares, but 2 were given"),
			refused.getMessage());
	}

	@Test
	void sharesOfTwoWallets_areRefused_ratherThanCombinedIntoAThird()
	{
		List<SecretShare> mixed = new ArrayList<>(shares.subList(0, 2));
		mixed.add(Wallet.create().split(3, 5).get(2));

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Wallet.restore(mixed));

		assertTrue(refused.getMessage().contains("different splits"), refused.getMessage());
	}

	@Test
	void sharesOfASecretThatIsNoWalletSeed_areRefused()
	{
		List<SecretShare> notAWallet = SecretSharing.split(new byte[16], 2, 2);

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> Wallet.restore(notAWallet));

		assertTrue(refused.getMessage().contains("16 bytes"), refused.getMessage());
	}

	@Test
	void aTransferFromAnotherAccount_isNotSigned()
	{
		Bytes somebodyElse = Wallet.create().spendKey(SignatureSuite.ED25519);
		TransactionBody body = new TransactionBody(Chain.IDENTIFIER, 0L, somebodyElse,
			Destination.direct(somebodyElse), Amount.ofLeth(1L), Amount.ZERO, "not mine to sign");

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> original.sign(body, SignatureSuite.ED25519));

		assertTrue(refused.getMessage().contains("the transfer is from " + somebodyElse),
			refused.getMessage());
	}

	@Test
	void anAccountOfOneSuite_isNotSignedWithTheOther()
	{
		TransactionBody body = new TransactionBody(Chain.IDENTIFIER, 0L,
			original.spendKey(SignatureSuite.ED25519),
			Destination.direct(Bytes.of(new byte[] { 1 })),
			Amount.ofLeth(1L), Amount.ZERO, "the right wallet, the wrong suite");

		assertThrows(IllegalArgumentException.class,
			() -> original.sign(body, SignatureSuite.ML_DSA_65));
	}

	private static Bytes last32(final Bytes encoded)
	{
		byte[] bytes = encoded.toByteArray();
		return Bytes.of(Arrays.copyOfRange(bytes, bytes.length - 32, bytes.length));
	}
}
