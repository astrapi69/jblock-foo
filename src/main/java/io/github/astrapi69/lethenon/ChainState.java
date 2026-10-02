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
 * every balance equals {@link Emission#TOTAL_SUPPLY}, because the block reward is a transfer out of
 * a pool that exists in the genesis block and nothing is ever minted.
 */
public final class ChainState
{

	/** The account the genesis block puts the mining pool into */
	public static final Bytes POOL = Bytes.of("lethenon-mining-pool".getBytes(
		java.nio.charset.StandardCharsets.UTF_8));

	private final Map<Bytes, Amount> balances = new HashMap<>();

	private final Map<Bytes, Long> nonces = new HashMap<>();

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
	 * Puts the genesis allocation in place: half the supply into the mining pool, half to the
	 * account the genesis block names as its beneficiary
	 *
	 * @param genesis
	 *            the genesis block
	 */
	void allocateGenesis(final BlockBody genesis)
	{
		balances.put(POOL, Emission.MINING_POOL);
		balances.put(genesis.beneficiary(), Emission.TOTAL_SUPPLY.minus(Emission.MINING_POOL));
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
			applyTransfer(transaction);
		}
		payTheReward(block.beneficiary());
		if (!Emission.TOTAL_SUPPLY.equals(total()))
		{
			throw new ChainRejected("the supply moved: " + total() + " instead of "
				+ Emission.TOTAL_SUPPLY + " at height " + block.height());
		}
	}

	private void applyTransfer(final SignedTransaction transaction)
	{
		TransactionBody body = transaction.body();
		if (!Chain.IDENTIFIER.equals(body.chainIdentifier()))
		{
			throw new ChainRejected(
				"a transfer for chain '" + body.chainIdentifier() + "' in a " + Chain.IDENTIFIER
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
		Amount reward = Emission.BLOCK_REWARD;
		if (balanceOf(POOL).compareTo(reward) < 0)
		{
			// the pool is empty: mining is paid by fees alone from here on, which is what the
			// fixed supply means
			return;
		}
		balances.put(POOL, balanceOf(POOL).minus(reward));
		balances.put(miner, balanceOf(miner).plus(reward));
	}
}
