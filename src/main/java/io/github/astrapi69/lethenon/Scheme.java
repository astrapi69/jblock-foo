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
import java.util.List;

/**
 * A scheme that fills one {@link BuildingBlock} of a transfer, under an identifier that never
 * changes.
 * <p>
 * The identifier is part of the wire or of the signed bytes wherever the scheme is named there,
 * so it is this project's own and not an upstream enum's name. A scheme that changes in any way a
 * verifier could notice gets a new identifier rather than a new meaning for the old one -
 * {@code stealth-v1} and {@code stealth-v2} are the precedent (#21). Which scheme a chain admits,
 * and from which height, is the consensus rule's decision ({@link ConsensusRules}), not the
 * scheme's.
 */
public interface Scheme
{

	/**
	 * The identifier, permanent once a chain carries it
	 *
	 * @return the identifier
	 */
	String identifier();

	/**
	 * The building block this scheme fills
	 *
	 * @return the block
	 */
	BuildingBlock block();

	/**
	 * Every scheme this build knows, of every building block
	 *
	 * @return the schemes
	 */
	static List<Scheme> all()
	{
		List<Scheme> schemes = new ArrayList<>();
		schemes.addAll(List.of(SignatureSuite.values()));
		schemes.addAll(List.of(AddressScheme.values()));
		schemes.addAll(List.of(AmountScheme.values()));
		return List.copyOf(schemes);
	}
}
