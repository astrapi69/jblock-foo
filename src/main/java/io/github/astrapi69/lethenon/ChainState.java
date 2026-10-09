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

import java.util.HashMap;
import java.util.Map;

/**
 * Who holds what, and what a block does to it.
 * <p>
 * Every rule a transfer has to satisfy is here, and every one of them is checked on replay by
 * somebody who did not build the block: the chain it belongs to, the sender's nonce, the signature,
 * and whether the money is there. The supply invariant is asserted after every block - the sum of
 * every balance equals {@link Emission#GENESIS_SUPPLY} plus what the tail emission has minted
 * (#133), because a block reward is a transfer out of a pool that exists in the genesis block, and
 * only the part of a reward the pool's share does not cover is minted, and counted.
 */
public final class ChainState
{

	/** The account the genesis block puts the mining pool into */
	public static final Bytes POOL = Bytes.of("lethenon-mining-pool".getBytes(
		java.nio.charset.StandardCharsets.UTF_8));

	private final Map<Bytes, Amount> balances = new HashMap<>();

	private final Map<Bytes, Long> nonces = new HashMap<>();

	private Amount minted = Amount.ZERO;

	private String chainIdentifier = Chain.IDENTIFIER;

	private final ConsensusRules rules;

	/**
	 * An empty state under the rule both chains run, {@link ConsensusRules#LETHENON}
	 */
	public ChainState()
	{
		this(ConsensusRules.LETHENON);
	}

	/**
	 * An empty state under the given rule
	 *
	 * @param rules
	 *            which schemes the chain admits at which height
	 */
	ChainState(final ConsensusRules rules)
	{
		this.rules = rules;
	}

	/**
	 * The chain this state belongs to, as its genesis block named it
	 *
	 * @return the chain identifier, or {@link Chain#IDENTIFIER} before a genesis block was applied
	 */
	public String chainIdentifier()
	{
		return chainIdentifier;
	}

	/**
	 * The balance of an account, zero when it has never been paid
	 *
	 * @param account
	 *            the account key
	 * @return its balance
	 */
	public Amount balanceOf(final Bytes account)
	{
		return balances.getOrDefault(account, Amount.ZERO);
	}

	/**
	 * The next nonce the account has to use
	 *
	 * @param account
	 *            the account key
	 * @return the nonce
	 */
	public long nextNonceOf(final Bytes account)
	{
		return nonces.getOrDefault(account, 0L);
	}

	/**
	 * The sum of every balance, which has to be the supply at every height
	 *
	 * @return the total
	 */
	public Amount total()
	{
		Amount total = Amount.ZERO;
		for (Amount balance : balances.values())
		{
			total = total.plus(balance);
		}
		return total;
	}

	/**
	 * What the tail emission has minted so far (#133)
	 *
	 * @return the sum of the minted parts of every reward, zero before the tail
	 */
	public Amount minted()
	{
		return minted;
	}

	/**
	 * The supply at this height: the genesis supply plus what has been minted, which the sum of
	 * every balance has to equal
	 *
	 * @return the supply
	 */
	public Amount supply()
	{
		return Emission.GENESIS_SUPPLY.plus(minted);
	}

	/**
	 * A copy that can be changed without changing this state, for trying transfers on top of it
	 *
	 * @return the copy, under the same rule
	 */
	ChainState copy()
	{
		ChainState copy = new ChainState(rules);
		copy.balances.putAll(balances);
		copy.nonces.putAll(nonces);
		copy.minted = minted;
		copy.chainIdentifier = chainIdentifier;
		return copy;
	}

	/**
	 * Takes what an account's waiting transfers will spend off its balance and moves its nonce past
	 * them, so that a further transfer can be tried on top without applying them again
	 *
	 * @param account
	 *            the sender
	 * @param spent
	 *            what its waiting transfers spend, amounts and fees
	 * @param transfers
	 *            how many are waiting; they were admitted against this state, so the balance
	 *            covers what they spend, and {@link Amount#minus} refuses a negative balance if it
	 *            ever did not
	 */
	void reserve(final Bytes account, final Amount spent, final long transfers)
	{
		balances.put(account, balanceOf(account).minus(spent));
		nonces.put(account, Math.addExact(nextNonceOf(account), transfers));
	}

	/**
	 * Puts the genesis allocation in place: the whole supply into the mining pool, and out of it the
	 * ordinary block reward to the burn account {@link Genesis#NOBODY}, which the genesis block has
	 * to name as its beneficiary (#148). Its balance counts in every sum like any other, so the
	 * burned reward is part of the supply and can be seen in it, but it never moves again. The same on both chains, so the test chain tests the main chain's rule
	 * (#111)
	 *
	 * @param genesis
	 *            the genesis block
	 */
	void allocateGenesis(final BlockBody genesis)
	{
		chainIdentifier = genesis.chainIdentifier();
		balances.put(POOL, Emission.MINING_POOL);
		payTheReward(genesis.beneficiary());
	}

	/**
	 * Applies a block: every transfer in order, then the reward out of the pool to the miner the
	 * block names as its beneficiary
	 *
	 * @param block
	 *            the block
	 * @throws ChainRejected
	 *             if any rule is broken; the state is then not to be used further
	 */
	void apply(final BlockBody block)
	{
		for (SignedTransaction transaction : block.transactions())
		{
			applyTransfer(transaction, block.height());
		}
		payTheReward(block.beneficiary());
		if (!supply().equals(total()))
		{
			throw new ChainRejected("the supply moved: " + total() + " instead of " + supply()
				+ " (" + Emission.GENESIS_SUPPLY + " at genesis plus " + minted + " minted) at height "
				+ block.height());
		}
	}

	/**
	 * Applies one transfer as the block at the given height would, without the block's reward
	 *
	 * @param transaction
	 *            the transfer
	 * @param height
	 *            the height of the block that would carry it, which decides the admitted schemes
	 * @throws ChainRejected
	 *             if any rule is broken; the state is then not to be used further
	 */
	void applyTransfer(final SignedTransaction transaction, final long height)
	{
		TransactionBody body = transaction.body();
		if (!chainIdentifier.equals(body.chainIdentifier()))
		{
			throw new ChainRejected(
				"a transfer for chain '" + body.chainIdentifier() + "' in a " + chainIdentifier
					+ " block");
		}
		if (AddressScheme.STEALTH_V1.equals(body.recipient().scheme()))
		{
			// the first stealth destination's key was a hash, so nothing could ever sign for it:
			// money paid there was lost by construction. Refusing it here means a chain cannot
			// carry such a payment at all, rather than carrying one nobody notices until the
			// recipient tries to spend (#21)
			throw new ChainRejected("a transfer to a " + AddressScheme.STEALTH_V1.identifier()
				+ " destination, whose key is a hash and therefore the private half of nothing; "
				+ "those funds could never be moved again");
		}
		if (Genesis.NOBODY.equals(body.sender()))
		{
			throw new ChainRejected("a transfer from the burn account, which block 0 paid its reward "
				+ "to and which no transfer may spend (#148)");
		}
		rules.requireAdmitted(chainIdentifier, transaction.suite(), height);
		rules.requireAdmitted(chainIdentifier, body.recipient().scheme(), height);
		rules.requireAdmitted(chainIdentifier, body.amountScheme(), height);
		if (!TransactionSigner.verify(transaction))
		{
			throw new ChainRejected("a transfer whose signature does not match its sender");
		}
		Bytes sender = body.sender();
		long expected = nextNonceOf(sender);
		if (body.nonce() != expected)
		{
			throw new ChainRejected("nonce " + body.nonce() + " where " + expected
				+ " was due: a transfer cannot be replayed, and none may be skipped");
		}
		Amount due = body.amount().plus(body.fee());
		if (balanceOf(sender).compareTo(due) < 0)
		{
			throw new ChainRejected(
				"a transfer of " + due + " from an account holding " + balanceOf(sender));
		}
		balances.put(sender, balanceOf(sender).minus(due));
		Bytes recipient = body.recipient().key();
		balances.put(recipient, balanceOf(recipient).plus(body.amount()));
		// the fee goes to the pool rather than to the miner directly: the pool is what the reward
		// is paid from, so a fee shortens nothing and lengthens the reward's life
		balances.put(POOL, balanceOf(POOL).plus(body.fee()));
		nonces.put(sender, expected + 1);
	}

	private void payTheReward(final Bytes miner)
	{
		BlockReward reward = rules.emission().rewardFor(balanceOf(POOL));
		balances.put(POOL, balanceOf(POOL).minus(reward.fromPool()));
		minted = minted.plus(reward.minted());
		balances.put(miner, balanceOf(miner).plus(reward.total()));
	}
}
