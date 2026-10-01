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

import java.security.KeyPair;
import java.security.SecureRandom;
import java.util.List;

import io.github.astrapi69.crypt.api.algorithm.key.KeyPairGeneratorAlgorithm;
import io.github.astrapi69.lethenon.HierarchicalDerivation.Curve;
import io.github.astrapi69.mystic.crypt.secret.SecretShare;
import io.github.astrapi69.mystic.crypt.secret.SecretSharing;

/**
 * A wallet that can be recovered: one seed, every key derived from it, and the seed split into
 * shares as the backup (lethenon#2, milestone 4).
 * <p>
 * The keys are never stored on their own. Each one is a path below the seed, after SLIP-0010:
 *
 * <pre>
 * ed25519 tree     m/1984'/0'/0'   the Ed25519 spend key
 * curve25519 tree  m/1984'/0'/1'   the X25519 view key of the published address
 * ed25519 tree     m/1984'/0'/2'   the seed xi of the ML-DSA-65 spend key
 * </pre>
 *
 * 1984 is this chain's purpose number, the 0 after it the account. SLIP-0010 knows nothing of
 * ML-DSA; its 32 byte seed is taken from a branch of the ed25519 tree, which gives 32 bytes that
 * nothing else uses, and FIPS 204 makes the key pair from them. These paths are a recovery format:
 * once a wallet exists, changing one makes its keys unreachable from its own backup.
 * <p>
 * The backup is {@link SecretSharing} from mystic-crypt - Shamir over the seed, with every share
 * knowing its split and its threshold, so shares of two different wallets or too few of them are
 * refused instead of combining into a wrong seed and a wallet that is silently somebody else's.
 */
public final class Wallet
{

	/** How many bytes of seed a new wallet is made from */
	public static final int SEED_LENGTH = 32;

	/** This chain's purpose number, the first step of every path */
	static final int PURPOSE = 1984;

	/** The only account so far */
	static final int ACCOUNT = 0;

	private final byte[] seed;

	private Wallet(final byte[] seed)
	{
		if (seed.length != SEED_LENGTH)
		{
			throw new IllegalArgumentException("a wallet seed is " + SEED_LENGTH
				+ " bytes, and this one is " + seed.length + " bytes");
		}
		this.seed = seed.clone();
	}

	/**
	 * A new wallet from fresh randomness
	 *
	 * @return the wallet
	 */
	public static Wallet create()
	{
		byte[] seed = new byte[SEED_LENGTH];
		new SecureRandom().nextBytes(seed);
		return new Wallet(seed);
	}

	/**
	 * The wallet that the given shares are the backup of
	 *
	 * @param shares
	 *            at least as many shares of one split as that split needs
	 * @return the wallet, with every key it had
	 * @throws IllegalArgumentException
	 *             for too few shares, shares of different splits, a share given twice, or a secret
	 *             that is not a wallet seed
	 */
	public static Wallet restore(final List<SecretShare> shares)
	{
		return new Wallet(SecretSharing.combine(shares));
	}

	/**
	 * Splits the seed into shares, any {@code threshold} of which bring the wallet back
	 *
	 * @param threshold
	 *            how many shares a restore needs, at least 2
	 * @param shares
	 *            how many shares to make
	 * @return the shares, each one encodable as a line of text with {@link SecretShare#encode()}
	 */
	public List<SecretShare> split(final int threshold, final int shares)
	{
		return SecretSharing.split(seed, threshold, shares);
	}

	/**
	 * The account key of a suite, the bytes a transfer names as its sender
	 *
	 * @param suite
	 *            the signature scheme
	 * @return the encoded public key
	 */
	public Bytes spendKey(final SignatureSuite suite)
	{
		return TransactionSigner.asBytes(spendKeyPair(suite).getPublic());
	}

	/**
	 * What this wallet publishes to be paid at one-time destinations: the X25519 view key and the
	 * Ed25519 spend key
	 *
	 * @return the published address
	 */
	public PublishedAddress address()
	{
		return PublishedAddress.of(viewKeyPair(), spendKeyPair(SignatureSuite.ED25519));
	}

	/**
	 * The view key pair, whose private half recognises payments to {@link #address()} and moves
	 * nothing
	 *
	 * @return the X25519 key pair
	 */
	public KeyPair viewKeyPair()
	{
		return DeterministicKeys.from(KeyPairGeneratorAlgorithm.X25519.getAlgorithm(),
			HierarchicalDerivation.derive(Curve.CURVE25519, seed, PURPOSE, ACCOUNT, 1)
				.privateKey());
	}

	/**
	 * Signs a transfer from this wallet's account of the given suite
	 *
	 * @param body
	 *            the transfer; its sender must be {@link #spendKey(SignatureSuite)} of the suite
	 * @param suite
	 *            the signature scheme
	 * @return the signed transaction
	 * @throws IllegalArgumentException
	 *             when the transfer names another sender, which no signature of this wallet could
	 *             make valid
	 */
	public SignedTransaction sign(final TransactionBody body, final SignatureSuite suite)
	{
		KeyPair keyPair = spendKeyPair(suite);
		Bytes own = TransactionSigner.asBytes(keyPair.getPublic());
		if (!own.equals(body.sender()))
		{
			throw new IllegalArgumentException("this wallet's " + suite.identifier()
				+ " account is " + own + ", and the transfer is from " + body.sender());
		}
		return TransactionSigner.sign(body, suite, keyPair.getPrivate());
	}

	private KeyPair spendKeyPair(final SignatureSuite suite)
	{
		int role = switch (suite)
		{
			case ED25519 -> 0;
			case ML_DSA_65 -> 2;
		};
		Bytes keySeed = HierarchicalDerivation.derive(Curve.ED25519, seed, PURPOSE, ACCOUNT, role)
			.privateKey();
		return DeterministicKeys.from(suite.algorithm(), keySeed);
	}
}
