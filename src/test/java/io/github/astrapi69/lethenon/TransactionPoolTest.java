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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.github.astrapi69.lethenon.TransactionPool.Admission;
import io.github.astrapi69.lethenon.TransactionPool.Outcome;

/**
 * The pool of waiting transfers admits what the next block could carry and nothing else (ADR 0003)
 */
class TransactionPoolTest
{

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	private final Wallet sender = Wallet.create();

	private final Bytes account = sender.spendKey(SignatureSuite.ED25519);

	private final List<BlockBody> chain = TestChains.chainWith(account);

	private final TransactionPool pool = new TransactionPool(Replay.verify(chain));

	private SignedTransaction transfer(final long nonce, final Amount amount, final String memo)
	{
		return sender.sign(new TransactionBody(Chain.IDENTIFIER, nonce, account, SOMEONE, amount,
			Amount.ZERO, memo), SignatureSuite.ED25519);
	}

	@Test
	void aTransferTheNextBlockCouldCarry_isAdmittedAndWaits()
	{
		SignedTransaction first = transfer(0L, Amount.ofLeth(3L), "first");

		assertEquals(Outcome.ADMITTED, pool.offer(first).outcome());
		assertEquals(List.of(first), pool.waiting());
	}

	@Test
	void theSameTransferTwice_isKnown_andWaitsOnce()
	{
		SignedTransaction first = transfer(0L, Amount.ofLeth(3L), "first");
		pool.offer(first);

		Admission again = pool.offer(first);

		assertEquals(Outcome.KNOWN, again.outcome());
		assertEquals(List.of(first), pool.waiting());
	}

	@Test
	void aSecondTransferWithTheSameNonce_isADoubleSpend_andTheFirstStays()
	{
		SignedTransaction first = transfer(0L, Amount.ofLeth(3L), "first");
		SignedTransaction second = transfer(0L, Amount.ofLeth(4L), "the same money again");
		pool.offer(first);

		Admission refused = pool.offer(second);

		assertEquals(Outcome.REFUSED, refused.outcome());
		assertTrue(refused.reason().contains("double spend"), refused.reason());
		assertTrue(refused.reason().contains("nonce 0"), refused.reason());
		assertEquals(List.of(first), pool.waiting());
	}

	@Test
	void theNonceCountsTheSendersWaitingTransfers()
	{
		SignedTransaction first = transfer(0L, Amount.ofLeth(3L), "first");
		SignedTransaction second = transfer(1L, Amount.ofLeth(4L), "second");

		pool.offer(first);

		assertEquals(Outcome.ADMITTED, pool.offer(second).outcome());
		assertEquals(List.of(first, second), pool.waiting());
	}

	record Refusal(String name, Function<TransactionPoolTest, SignedTransaction> transfer,
		String reasonNames)
	{
		@Override
		public String toString()
		{
			return name;
		}
	}

	static Stream<Refusal> refusals()
	{
		return Stream.of(
			new Refusal("a nonce that skips one",
				test -> test.transfer(1L, Amount.ofLeth(1L), "too early"), "nonce 1 where 0"),
			new Refusal("more than the account holds",
				test -> test.transfer(0L, Amount.ofLeth(2_000_000_000L), "too much"),
				"from an account holding"),
			new Refusal("a signature over other bytes",
				test -> test.tampered(test.transfer(0L, Amount.ofLeth(1L), "signed")),
				"signature"),
			new Refusal("another chain",
				test -> test.sender.sign(new TransactionBody(Chain.TEST_IDENTIFIER, 0L,
					test.account, SOMEONE, Amount.ofLeth(1L), Amount.ZERO, "elsewhere"),
					SignatureSuite.ED25519),
				"lethenon-test-1"));
	}

	private SignedTransaction tampered(final SignedTransaction signed)
	{
		TransactionBody body = signed.body();
		return new SignedTransaction(new TransactionBody(body.chainIdentifier(), body.nonce(),
			body.sender(), body.recipient(), Amount.ofLeth(2L), body.fee(), body.memo()),
			signed.suite(), signed.signature());
	}

	@ParameterizedTest(name = "{0} is refused")
	@MethodSource("refusals")
	void aTransferTheNextBlockCouldNotCarry_isRefused_withTheReason(final Refusal refusal)
	{
		Admission admission = pool.offer(refusal.transfer().apply(this));

		assertEquals(Outcome.REFUSED, admission.outcome());
		assertTrue(admission.reason().contains(refusal.reasonNames()), admission.reason());
		assertEquals(List.of(), pool.waiting());
	}

	@Test
	void theBalanceCountsTheSendersWaitingTransfers()
	{
		Amount holding = Replay.verify(chain).finalState().balanceOf(account);
		pool.offer(transfer(0L, holding.minus(Amount.ofLeth(1L)), "almost all"));

		Admission refused = pool.offer(transfer(1L, Amount.ofLeth(2L), "one too many"));

		assertEquals(Outcome.REFUSED, refused.outcome());
		assertTrue(refused.reason().contains("from an account holding"), refused.reason());
	}

	@Test
	void aNewTip_dropsWhatTheBlockCarried_andKeepsWhatStillFits()
	{
		SignedTransaction first = transfer(0L, Amount.ofLeth(3L), "first");
		SignedTransaction second = transfer(1L, Amount.ofLeth(4L), "second");
		pool.offer(first);
		pool.offer(second);

		List<SignedTransaction> dropped = pool.advanceTo(Replay.verify(
			TestChains.chainWith(account, first)));

		assertEquals(List.of(first), dropped);
		assertEquals(List.of(second), pool.waiting());
	}

	@Test
	void aNewTip_carryingAnotherTransferWithTheSameNonce_dropsTheWaitingOne()
	{
		SignedTransaction waiting = transfer(0L, Amount.ofLeth(3L), "seen here");
		SignedTransaction minedElsewhere = transfer(0L, Amount.ofLeth(5L), "mined elsewhere");
		SignedTransaction after = transfer(1L, Amount.ofLeth(1L), "builds on the one seen here");
		pool.offer(waiting);
		pool.offer(after);

		List<SignedTransaction> dropped = pool.advanceTo(Replay.verify(
			TestChains.chainWith(account, minedElsewhere)));

		assertEquals(List.of(waiting), dropped,
			"the waiting transfer lost the race; the next one now takes its nonce");
		assertEquals(List.of(after), pool.waiting());
	}
}
