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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * A key pair made from a given 32 bytes instead of from the platform's randomness, which is what
 * makes a wallet's keys come back from its seed.
 */
class DeterministicKeysTest
{

	private static final Bytes RFC_8032_TEST_1_SECRET = Bytes
		.ofHex("9d61b19deffd5a60ba844af492ec2cc44449c5697b326919703bac031cae7f60");

	@Test
	void ed25519_fromTheSecretOfRfc8032Test1_isThePublicKeyOfRfc8032Test1()
	{
		byte[] encoded = DeterministicKeys.from("Ed25519", RFC_8032_TEST_1_SECRET).getPublic()
			.getEncoded();

		assertEquals(
			Bytes.ofHex("d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a"),
			Bytes.of(Arrays.copyOfRange(encoded, encoded.length - 32, encoded.length)));
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void theSameSeedMakesTheSameKeyPair_andAnotherSeedAnother(final SignatureSuite suite)
	{
		Bytes other = Bytes.of(new byte[32]);

		Bytes first = TransactionSigner
			.asBytes(DeterministicKeys.from(suite.algorithm(), RFC_8032_TEST_1_SECRET).getPublic());
		Bytes again = TransactionSigner
			.asBytes(DeterministicKeys.from(suite.algorithm(), RFC_8032_TEST_1_SECRET).getPublic());
		Bytes different = TransactionSigner
			.asBytes(DeterministicKeys.from(suite.algorithm(), other).getPublic());

		assertEquals(first, again);
		assertNotEquals(first, different);
	}

	@ParameterizedTest(name = "a seed of {0} bytes is refused")
	@ValueSource(ints = { 0, 31, 33 })
	void refusesASeedThatIsNotThirtyTwoBytes(final int length)
	{
		IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
			() -> DeterministicKeys.from("Ed25519", Bytes.of(new byte[length])));

		assertTrue(refused.getMessage().contains(length + " bytes"), refused.getMessage());
	}

	@Test
	void theRandomnessRefusesToBeReadTwice_ratherThanRepeatOrInventBytes()
	{
		DeterministicKeys.OneSeed random = new DeterministicKeys.OneSeed(RFC_8032_TEST_1_SECRET);
		byte[] first = new byte[32];
		random.nextBytes(first);

		IllegalStateException refused = assertThrows(IllegalStateException.class,
			() -> random.nextBytes(new byte[32]));

		assertEquals(RFC_8032_TEST_1_SECRET, Bytes.of(first));
		assertTrue(refused.getMessage().contains("once"), refused.getMessage());
	}

	@ParameterizedTest(name = "a read of {0} bytes is refused")
	@ValueSource(ints = { 16, 64 })
	void theRandomnessRefusesARead_ofAnyOtherLength(final int length)
	{
		DeterministicKeys.OneSeed random = new DeterministicKeys.OneSeed(RFC_8032_TEST_1_SECRET);

		IllegalStateException refused = assertThrows(IllegalStateException.class,
			() -> random.nextBytes(new byte[length]));

		assertTrue(refused.getMessage().contains(length + " bytes"), refused.getMessage());
	}
}
