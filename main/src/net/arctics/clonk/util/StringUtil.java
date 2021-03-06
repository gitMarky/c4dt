package net.arctics.clonk.util;

import static net.arctics.clonk.util.ArrayUtil.iterable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Iterator;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * String utilty functions.
 * @author madeen
 *
 */
public class StringUtil {
	/**
	 * Return a copy of the first string with the first character capitalized
	 * @param s The string to return a capitalized version of
	 * @return The capitalized version
	 */
	public static String capitalize(final String s) {
		if (s == null || s.length() == 0)
			return "";
		return Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
	/**
	 * Write a block of items, consisting of a start string, an end string, a delimiter string and the elements which are obtained from some {@link Iterable}
	 * @param output The {@link Appendable} the resulting string will be appended to
	 * @param startBlock The start block string
	 * @param endBlock The end block string
	 * @param delimiter The delimiter string
	 * @param enumeration The elements {@link Iterable}
	 * @return If output is set to null, the resulting string will be returned. Null is returned if there is an output to append the result to.
	 */
	public static Appendable writeBlock(
		final Appendable output0,
		final CharSequence startBlock, final CharSequence endBlock, final CharSequence delimiter,
		final Stream<?> enumeration
	) {
		final Appendable output = output0 != null ? output0 : new StringBuilder();
		final String joined = enumeration
			.filter(o -> o != null)
			.map(Object::toString)
			.collect(Collectors.joining(delimiter, startBlock, endBlock));
		try {
			output.append(joined);
		} catch (final IOException e) {
			e.printStackTrace();
		}
		return output;
	}

	public static String blockString(final CharSequence startBlock, final CharSequence endBlock, final CharSequence delimiter, final Iterable<?> enumeration) {
		return writeBlock(null, startBlock, endBlock, delimiter, StreamSupport.stream(enumeration.spliterator(), false)).toString();
	}

	public static String blockString(final CharSequence startBlock, final CharSequence endBlock, final CharSequence delimiter, final Stream<?> enumeration) {
		return writeBlock(null, startBlock, endBlock, delimiter, enumeration).toString();
	}

	/**
	 * Evaluate escapes such as \" and \\
	 * @param str The string containing escapes
	 * @return The str with escapes evaluated
	 */
	public static String evaluateEscapes(final String str) {
		final StringBuilder sBuilder = new StringBuilder(str.length());
		final int len = str.length();
		for (int i = 0; i < len; i++) {
			if (i < len-1) switch (str.charAt(i)) {
			case '\\':
				switch (str.charAt(i+1)) {
				case '\\': case '"':
					sBuilder.append(str.charAt(++i));
					continue;
				}
				break;
			}
			sBuilder.append(str.charAt(i));
		}
		return sBuilder.toString();
	}

	public static String wildcardToRegex(final String wildcard){
		final StringBuffer s = new StringBuffer(wildcard.length());
		//s.append('^');
		for (int i = 0, is = wildcard.length(); i < is; i++) {
			final char c = wildcard.charAt(i);
			switch(c) {
			case '*':
				s.append(".*");
				break;
			case '?':
				s.append(".");
				break;
				// escape special regexp-characters
			case '(': case ')': case '[': case ']': case '$':
			case '^': case '.': case '{': case '}': case '|':
			case '\\':
				s.append("\\");
				s.append(c);
				break;
			default:
				s.append(c);
				break;
			}
		}
		// s.append('$');
		return s.toString();
	}

	public static Pattern patternFromRegExOrWildcard(final String pattern) {
		try {
			return Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
		} catch (final Exception e) {
			try {
				return Pattern.compile(StringUtil.wildcardToRegex(pattern), Pattern.CASE_INSENSITIVE);
			} catch (final Exception e2) {
				return Pattern.compile("ponies");
			}
		}
	}

	public static String rawFileName(final String s) {

	    final String separator = System.getProperty("file.separator");
	    String filename;

	    // Remove the path upto the filename.
	    final int lastSeparatorIndex = s.lastIndexOf(separator);
	    if (lastSeparatorIndex == -1)
			filename = s;
		else
			filename = s.substring(lastSeparatorIndex + 1);

	    // Remove the extension.
	    final int extensionIndex = filename.lastIndexOf(".");
	    if (extensionIndex == -1)
	        return filename;

	    return filename.substring(0, extensionIndex);
	}

	public static String unquote(final String s) {
		if (s.length() >= 2 && s.charAt(0) == '"' && s.charAt(s.length()-1) == '"')
			return s.substring(1, s.length()-1);
		else
			return s;
	}

	public static Iterable<String> lines(final Reader reader) {
		return () -> new Iterator<String>() {

			private final BufferedReader bufferedReader = new BufferedReader(reader);
			private String line;

			@Override
			public boolean hasNext() {
				try {
					return (line = bufferedReader.readLine()) != null;
				} catch (final IOException e) {
					e.printStackTrace();
					return false;
				}
			}

			@Override
			public String next() {
				return line;
			}

			@Override
			public void remove() {
				// ignore
			}

		};
	}

	// Copy-Pasta org.eclipse.jdt.internal.ui.text.correction.NameMatcher

	/**
	 * Returns a similarity value of the two names.
	 * The range of is from 0 to 256. no similarity is negative
	 * @param name1 the first name
	 * @param name2 the second name
	 * @return the similarity valuer
	 */
	public static int similarityOf(String name1, String name2) {
		if (name1.length() > name2.length()) {
			final String tmp= name1;
			name1= name2;
			name2= tmp;
		}
		final int name1len= name1.length();
		final int name2len= name2.length();

		int nMatched= 0;

		int i= 0;
		while (i < name1len && StringUtil.isSimilarChar(name1.charAt(i), name2.charAt(i))) {
			i++;
			nMatched++;
		}

		int k= name1len;
		final int diff= name2len - name1len;
		while (k > i && StringUtil.isSimilarChar(name1.charAt(k - 1), name2.charAt(k + diff - 1))) {
			k--;
			nMatched++;
		}

		if (nMatched == name2len)
			return 200;

		if (name2len - nMatched > nMatched)
			return -1;

		final int tolerance= name2len / 4 + 1;
		return (tolerance - (k - i)) * 256 / tolerance;
	}
	static boolean isSimilarChar(final char ch1, final char ch2) {
		return Character.toLowerCase(ch1) == Character.toLowerCase(ch2);
	}
	/**
	 * Return a string consisting of times repetitions of s
	 * @param s The string to repeat
	 * @param times The number of repetitions
	 * @return The string containing the repetitions
	 */
	public static String multiply(final String s, final int times) {
		if (times <= 0)
			return "";
		final StringBuilder builder = new StringBuilder(s.length()*times);
		for (int i = 0; i < times; i++)
			builder.append(s);
		return builder.toString();
	}
	public static String htmlerize(final String text) {
		return text.
			replace("&", "&amp;"). //$NON-NLS-1$ //$NON-NLS-2$
			replace("<", "&lt;"). //$NON-NLS-1$ //$NON-NLS-2$
			replace(">", "&gt;"). //$NON-NLS-1$ //$NON-NLS-2$
			replace("\n", " "). //$NON-NLS-1$ //$NON-NLS-2$
			replace("\t", " "); //$NON-NLS-1$ //$NON-NLS-2$
	}
	public static boolean nullOrEmpty(String str) {
		return str == null || str.equals("");
	}

	public static String join(String joinStr, String... parts) {
		return blockString("", "", joinStr, iterable(parts));
	}
}
