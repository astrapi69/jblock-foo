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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.astrapi69.mystic.crypt.secret.SecretShare;

/**
 * The acceptance of milestone 4 (lethenon#2): a wallet restored from its shares alone signs a
 * transaction that the replay accepts.
 * <p>
 * "Alone" is taken literally. The original wallet is dropped after the split, the shares travel as
 * the text lines a person would write down or hand over, and the restored wallet is built from
 * three of five of those lines and nothing else. The chain then has to accept what it signed -
 * which it only does when the restored key is the very key the genesis allocation went to,
 * because a key that merely signs correctly is a key with no balance.
 */
class RecoveryAcceptanceTest
{

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void aWalletRestoredFromThreeOfFiveShares_signsATransferTheReplayAccepts(
		final SignatureSuite suite)
	{
		Wallet original = Wallet.create();
		Bytes holder = original.spendKey(suite);
		List<String> writtenDown = original.split(3, 5).stream().map(SecretShare::encode).toList();
		original = null;

		Wallet restored = Wallet.restore(
			List.of(writtenDown.get(4), writtenDown.get(0), writtenDown.get(2)).stream()
				.map(SecretShare::decode).toList());
		Bytes recipient = Bytes.of("whoever was paid".getBytes());
		SignedTransaction transfer = restored.sign(new TransactionBody(Chain.IDENTIFIER, 0L, holder,
			Destination.direct(recipient), Amount.ofLeth(42L), Amount.ZERO,
			"restored from three pieces of paper"), suite);
		List<BlockBody> chain = chainWith(transfer);

		Replay replay = Replay.verify(chain, minersFor(chain), holder);

		assertEquals(1L, replay.signatures(), replay.describe());
		assertEquals(Amount.ofLeth(42L), replay.finalState().balanceOf(recipient),
			replay.describe());
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void aWalletRestoredFromItsTwentyFourWordsAlone_signsATransferTheReplayAccepts(
		final SignatureSuite suite)
	{
		Wallet original = Wallet.create();
		Bytes holder = original.spendKey(suite);
		String writtenDown = original.phrase();
		original = null;

		Wallet restored = Wallet.fromPhrase(writtenDown);
		Bytes recipient = Bytes.of("whoever was paid".getBytes());
		SignedTransaction transfer = restored.sign(new TransactionBody(Chain.IDENTIFIER, 0L, holder,
			Destination.direct(recipient), Amount.ofLeth(42L), Amount.ZERO,
			"restored from twenty-four words"), suite);
		List<BlockBody> chain = chainWith(transfer);

		Replay replay = Replay.verify(chain, minersFor(chain), holder);

		assertEquals(1L, replay.signatures(), replay.describe());
		assertEquals(Amount.ofLeth(42L), replay.finalState().balanceOf(recipient),
			replay.describe());
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void aWalletReadFromItsPasswordProtectedFileAlone_signsATransferTheReplayAccepts(
		final SignatureSuite suite, @TempDir final Path directory) throws Exception
	{
		Wallet original = Wallet.create();
		Bytes holder = original.spendKey(suite);
		Path file = directory.resolve("wallet.lethenon");
		char[] password = Wallet.create().phrase().toCharArray();
		WalletFile.write(file, original, password);
		original = null;

		Wallet restored = WalletFile.read(file, password);
		Bytes recipient = Bytes.of("whoever was paid".getBytes());
		SignedTransaction transfer = restored.sign(new TransactionBody(Chain.IDENTIFIER, 0L, holder,
			Destination.direct(recipient), Amount.ofLeth(42L), Amount.ZERO,
			"read back from its own file"), suite);
		List<BlockBody> chain = chainWith(transfer);

		Replay replay = Replay.verify(chain, minersFor(chain), holder);

		assertEquals(1L, replay.signatures(), replay.describe());
		assertEquals(Amount.ofLeth(42L), replay.finalState().balanceOf(recipient),
			replay.describe());
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void aWalletFromDifferentShares_isADifferentWallet_andTheReplayRefusesItsTransfer(
		final SignatureSuite suite)
	{
		Bytes holder = Wallet.create().spendKey(suite);
		Wallet stranger = Wallet.restore(Wallet.create().split(2, 3).subList(0, 2));
		SignedTransaction transfer = stranger.sign(new TransactionBody(Chain.IDENTIFIER, 0L,
			stranger.spendKey(suite), Destination.direct(holder), Amount.ofLeth(1L), Amount.ZERO,
			"not the holder"), suite);
		List<BlockBody> chain = chainWith(transfer);

		ChainRejected refused = assertThrows(ChainRejected.class,
			() -> Replay.verify(chain, minersFor(chain), holder));

		assertTrue(refused.getMessage().contains("from an account holding"), refused.getMessage());
	}
}
