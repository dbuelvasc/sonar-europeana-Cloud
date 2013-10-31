package eu.europeana.cloud.service.uis.encoder;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Base50 encoder. The characters consists of consonants (capital/lowercase) and
 * numbers only. The implementation ensures that the same input will generate
 * the same result and that the output will always be 40 characters by filling
 * with 0
 * 
 * @href http://blog.maxant.co.uk/pebble/2010/02/02/1265138340000.html
 * @author Yorgos.Mamakis@ kb.nl
 * @since Oct 31, 2013
 */
public class Base50 {

	/** Base50 DICTIONARY */
	public static final char[] DICTIONARY = new char[] { '0', '1', '2', '3',
			'4', '5', '6', '7', '8', '9', 'b', 'c', 'd', 'f', 'g', 'h', 'j',
			'k', 'l', 'm', 'n', 'p', 'q', 'r', 's', 't', 'v', 'w', 'x', 'z',
			'B', 'C', 'D', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P', 'Q',
			'R', 'S', 'T', 'V', 'W', 'X', 'Z' };

	/**
	 * Encode a given string according to a custom Base50 implementation
	 * 
	 * @param str
	 *            The string to encode
	 * @return A 40 character encoded version of the String representation
	 */
	public static String encode(String str) {
		List<Character> result = new ArrayList<>();
		BigInteger base = new BigInteger("" + DICTIONARY.length);
		int exponent = 1;
		BigInteger remaining = new BigInteger(convertToNum(str));
		while (true) {
			BigInteger power = base.pow(exponent);
			BigInteger modulo = remaining.mod(power);
			BigInteger powerMinusOne = base.pow(exponent - 1);
			BigInteger times = modulo.divide(powerMinusOne);
			result.add(DICTIONARY[times.intValue()]);
			remaining = remaining.subtract(modulo);
			if (remaining.equals(BigInteger.ZERO)) {
				break;
			}
			exponent++;
		}
		StringBuffer sb = new StringBuffer();
		for (int i = result.size() - 1; i > -1; i--) {
			sb.append(result.get(i));
		}
		if (sb.length() < 40) {
			char[] cArr = new char[40 - sb.length()];
			Arrays.fill(cArr, (char) 48);
			sb.append(cArr);
			return sb.reverse().toString();
		}
		return sb.substring(0, 40);
	}

	private static String convertToNum(String str) {
		StringBuffer sb = new StringBuffer();
		for (char c : str.toCharArray()) {
			sb.append((c - 32 + "000"));
		}
		return sb.toString();
	}
}
