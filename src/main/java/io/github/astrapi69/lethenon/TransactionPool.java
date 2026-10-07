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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The transfers waiting for a block, admitted only if the next block could carry them (ADR 0003)
 * <p>
 * A transfer is admitted when its chain identifier and schemes are admitted at the next height,
 * its signature verifies, its nonce is exactly the sender's next one counting the sender's
 * transfers already waiting, and the sender's balance covers it after them. A second transfer from
 * the same sender with the same nonce is a double spend: it is refused and the first one seen
 * stays, there is no replacement by fee.
 * <p>
 * Transfers to a sender that are still waiting are not counted as part of its balance, the same
 * as {@link Transfers#prepare}: a pool of a sender's transfers is then valid in any block that
 * carries them in the order they wait, whatever else the block carries.
 */
public final class TransactionPool
{

	/** What became of a transfer offered to the pool */
	public enum Outcome
	{
		/** it waits for a block and is to be relayed */
		ADMITTED,
		/** it was waiting already; nothing changes and nothing is relayed */
		KNOWN,
		/** the next block could not carry it */
		REFUSED
	}

	/**
	 * The outcome of an offer and, for a refusal, the reason
	 *
	 * @param outcome
	 *            what became of the transfer
	 * @param reason
	 *            why it was refused, empty otherwise
	 */
	public record Admission(Outcome outcome, String reason)
	{
	}

	/** The most transfers a pool holds (ADR 0003, limits) */
	public static final int LIMIT = 5_000;

	private final List<SignedTransaction> waiting = new ArrayList<>();

	private final Set<SignedTransaction> known = new HashSet<>();

	private final Map<Bytes, Sender> senders = new HashMap<>();

	private final int limit;

	/**
	 * What a sender's waiting transfers already take: how many there are and what they spend.
	 * Admission checks a new transfer against this instead of applying the sender's waiting ones
	 * again, so each signature is checked once (#92).
	 */
	private record Sender(long count, Amount spent)
	{
		Sender with(final TransactionBody body)
		{
			return new Sender(count + 1, spent.plus(body.amount().plus(body.fee())));
		}
	}

	private ChainState tip;

	private long nextHeight;

	/**
	 * An empty pool on top of a verified chain
	 *
	 * @param replay
	 *            the replay of the chain the next block extends
	 */
	public TransactionPool(final Replay replay)
	{
		this(replay, LIMIT);
	}

	/**
	 * An empty pool with another limit, so that the limit can be shown to hold without thousands
	 * of transfers
	 */
	TransactionPool(final Replay replay, final int limit)
	{
		this.tip = replay.finalState();
		this.nextHeight = replay.blocks();
		this.limit = limit;
	}

	/**
	 * Offers one transfer
	 *
	 * @param transfer
	 *            the signed transfer
	 * @return whether it was admitted, was known, or why it was refused
	 */
	public synchronized Admission offer(final SignedTransaction transfer)
	{
		if (known.contains(transfer))
		{
			return new Admission(Outcome.KNOWN, "");
		}
		TransactionBody body = transfer.body();
		Sender sender = senders.getOrDefault(body.sender(), new Sender(0L, Amount.ZERO));
		long first = tip.nextNonceOf(body.sender());
		if (body.nonce() >= first && body.nonce() < first + sender.count())
		{
			return new Admission(Outcome.REFUSED, "a double spend: account " + body.sender()
				+ " already has a transfer with nonce " + body.nonce()
				+ " waiting, and the first one seen stays");
		}
		if (waiting.size() >= limit)
		{
			return new Admission(Outcome.REFUSED,
				"the pool holds " + waiting.size() + " transfers, its limit");
		}
		ChainState trial = tip.copy();
		try
		{
			trial.reserve(body.sender(), sender.spent(), sender.count());
			trial.applyTransfer(transfer, nextHeight);
		}
		catch (ChainRejected refused)
		{
			return new Admission(Outcome.REFUSED, refused.getMessage());
		}
		waiting.add(transfer);
		known.add(transfer);
		senders.put(body.sender(), sender.with(body));
		return new Admission(Outcome.ADMITTED, "");
	}

	/**
	 * The transfers waiting, in the order they were admitted, which is an order a block can carry
	 * them in
	 *
	 * @return a snapshot
	 */
	public synchronized List<SignedTransaction> waiting()
	{
		return List.copyOf(waiting);
	}

	/**
	 * Moves the pool onto a new tip: every waiting transfer is offered again in its order, and
	 * what the new chain carried already or no longer fits is dropped
	 *
	 * @param replay
	 *            the replay of the new chain
	 * @return the dropped transfers, in their former order
	 */
	public List<SignedTransaction> advanceTo(final Replay replay)
	{
		return advanceTo(replay, List.of());
	}

	/**
	 * Moves the pool onto a new tip after a switch to another chain: the transfers of the blocks
	 * that were rolled back are offered first, in their order, then the waiting ones; what the new
	 * chain carried already or no longer fits is dropped
	 *
	 * @param replay
	 *            the replay of the new chain
	 * @param rolledBack
	 *            the transfers of the dropped blocks, oldest first
	 * @return the dropped transfers, rolled back ones first
	 */
	public synchronized List<SignedTransaction> advanceTo(final Replay replay,
		final List<SignedTransaction> rolledBack)
	{
		List<SignedTransaction> before = new ArrayList<>(rolledBack);
		before.addAll(waiting);
		waiting.clear();
		known.clear();
		senders.clear();
		tip = replay.finalState();
		nextHeight = replay.blocks();
		List<SignedTransaction> dropped = new ArrayList<>();
		for (SignedTransaction transfer : before)
		{
			if (offer(transfer).outcome() != Outcome.ADMITTED)
			{
				dropped.add(transfer);
			}
		}
		return List.copyOf(dropped);
	}
}
