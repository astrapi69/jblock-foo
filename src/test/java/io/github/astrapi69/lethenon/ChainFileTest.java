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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A chain and the transfers waiting for its next block live in two files next to each other
 * (lethenon#32). Both are written through a file beside them and a move, so a crash leaves the old
 * content or the new one, never half of either.
 */
class ChainFileTest
{

	@TempDir
	File directory;

	private final KeyPair holder = TransactionSigner.newKeyPair(SignatureSuite.ED25519);

	private final Bytes holderKey = TransactionSigner.asBytes(holder.getPublic());

	@Test
	void aChainThatWasNotWrittenYetIsEmpty_andRequiringItSaysSo() throws Exception
	{
		ChainFile file = new ChainFile(new File(directory, "chain.lethenon").toPath());

		assertEquals(List.of(), file.read());
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			file::require);
		assertTrue(refused.getMessage().contains("chain.lethenon"), refused.getMessage());
	}

	@Test
	void aWrittenChainReadsBackAsTheSameBlocks_andLeavesNoTemporaryFile() throws Exception
	{
		Path path = new File(directory, "chain.lethenon").toPath();
		ChainFile file = new ChainFile(path);
		List<BlockBody> chain = TestChains.chainWith(holderKey, transfer(0L, 3L));

		file.write(chain);

		assertEquals(chain, file.read());
		assertEquals(chain, CanonicalEncoding.readChain(Files.readAllBytes(path)),
			"the file holds the canonical encoding and nothing else");
		assertFalse(Files.exists(path.resolveSibling("chain.lethenon.writing")));
	}

	@Test
	void waitingTransfersReadBackInTheOrderTheyWereSigned() throws Exception
	{
		ChainFile file = new ChainFile(new File(directory, "chain.lethenon").toPath());
		List<SignedTransaction> waiting = List.of(transfer(0L, 3L), transfer(1L, 4L));

		assertEquals(List.of(), file.readPending(), "no pending file is no waiting transfer");
		file.writePending(waiting);

		assertEquals(waiting, file.readPending());
	}

	@Test
	void writingNoWaitingTransfersRemovesThePendingFile() throws Exception
	{
		Path path = new File(directory, "chain.lethenon").toPath();
		ChainFile file = new ChainFile(path);
		file.writePending(List.of(transfer(0L, 3L)));

		file.writePending(List.of());

		assertFalse(Files.exists(path.resolveSibling("chain.lethenon.pending")));
	}

	@Test
	void aPendingFileOfAnotherVersionIsRefusedWithBothVersions() throws Exception
	{
		Path path = new File(directory, "chain.lethenon").toPath();
		Files.write(path.resolveSibling("chain.lethenon.pending"), new byte[] { 9, 0, 0, 0, 0 });

		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> new ChainFile(path).readPending());

		assertTrue(refused.getMessage().contains("version 9"), refused.getMessage());
		assertTrue(refused.getMessage().contains("reads version " + ChainFile.PENDING_VERSION),
			refused.getMessage());
	}

	private SignedTransaction transfer(final long nonce, final long leth)
	{
		return TransactionSigner.sign(new TransactionBody(Chain.IDENTIFIER, nonce, holderKey,
			Destination.direct(Bytes.of(new byte[] { 7 })), Amount.ofLeth(leth), Amount.ZERO,
			"waiting " + nonce), SignatureSuite.ED25519, holder.getPrivate());
	}
}
