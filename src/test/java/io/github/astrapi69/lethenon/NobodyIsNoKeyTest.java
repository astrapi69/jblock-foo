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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * {@link Genesis#NOBODY} is no key of any registered signature suite, and no signature counts for
 * it (#148, ADR 0005). The consensus rule refuses every transfer from it already; this is the
 * second lock, which holds even for a build that lost the rule. It runs over
 * {@link SignatureSuite#values()}, so a suite added later is tested without anybody remembering
 * to: ADR 0005 makes passing it a condition of adding one
 */
class NobodyIsNoKeyTest
{

	private static final Destination SOMEONE = Destination.direct(Bytes.of(new byte[] { 7 }));

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void nobody_doesNotDecodeAsAPublicKey(final SignatureSuite suite) throws Exception
	{
		KeyFactory keys = KeyFactory.getInstance(suite.algorithm());

		assertThrows(InvalidKeySpecException.class,
			() -> keys.generatePublic(new X509EncodedKeySpec(Genesis.NOBODY.toByteArray())));
	}

	@ParameterizedTest(name = "{0}")
	@EnumSource(SignatureSuite.class)
	void noSignature_countsForNobody(final SignatureSuite suite)
	{
		KeyPair somebody = TransactionSigner.newKeyPair(suite);
		TransactionBody fromNobody = new TransactionBody(Chain.TEST_IDENTIFIER, 0L, Genesis.NOBODY,
			SOMEONE, Amount.ofLeth(1L), Amount.ZERO, "the genesis reward");

		SignedTransaction signedBySomebody = TransactionSigner.sign(fromNobody, suite,
			somebody.getPrivate());
		SignedTransaction unsigned = new SignedTransaction(fromNobody, suite, Bytes.of(new byte[0]));

		assertFalse(TransactionSigner.verify(signedBySomebody),
			"a signature by somebody's key is no signature by nobody");
		assertFalse(TransactionSigner.verify(unsigned));
	}
}
