package net.arctics.clonk.parser;

import java.io.InputStream;
import java.io.Reader;
import java.util.regex.Pattern;

import net.arctics.clonk.util.StreamUtil;

import org.eclipse.core.resources.IFile;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;

/**
 * Scanner operating on a string stored in memory. Can be created from a file, an input stream or a raw string
 */
public class BufferedScanner {

	public static final Pattern IDENTIFIER_PATTERN = Pattern.compile("[A-Za-z_][A-Za-z_0-9]*");
	public static final Pattern NUMERAL_PATTERN = Pattern.compile("[0-9]+");
	
	/**
	 * characters that represent whitespace
	 */
	public static final char[] WHITESPACE_CHARS = new char[] { ' ', '\n', '\r', '\t' };
	
	/**
	 * characters that represent a new-line
	 */
	public static final char[] NEWLINE_CHARS = new char[] { '\n', '\r' };
	
	/**
	 * whitespace chars without new line chars
	 */
	public static final char[] WHITESPACE_WITHOUT_NEWLINE_CHARS = new char[] { ' ', '\t' };

	/**
	 * The buffer
	 */
	protected String buffer;
	
	/**
	 * Size of the buffer
	 */
	protected int size;
	
	/**
	 * Current offset
	 */
	protected int offset;

	/**
	 * Create a new scanner that scans a string
	 * @param withString
	 */
	public BufferedScanner(String withString) {
		initScanner(withString);
	}
	
	protected void initScanner(String withString) {
		offset = 0;
		buffer = withString;
		size = buffer.length();
	}

	public BufferedScanner(Object source) {
		this(stringFromSource(source));
	}
	
	private static String stringFromSource(Object source) {
		if (source instanceof IFile) {
			return StreamUtil.stringFromFileDocument((IFile) source);
		} else if (source instanceof Reader) {
			return StreamUtil.stringFromReader((Reader)source);
		} else if (source instanceof InputStream) {
			return StreamUtil.stringFromInputStream((InputStream)source);
		} else if (source instanceof String) {
			return (String)source;
		} else {
			return "";
		}
	}

	/**
	 * Read the next character in the buffer
	 * @return the character (can be cast to char) or -1 if the current offset exceeds the size of the buffer
	 */
	public int read() {
		if (offset >= size) {
			offset++; // increment anyway so unread works as expected
			return -1;
		}
		return buffer.charAt(offset++);
	}

	/**
	 * Reverts the last read() call
	 * @return
	 */
	public boolean unread() {
		offset--;
		return true;
	}

	/**
	 * Reads a string of the supplied length from the buffer
	 * @param length the length
	 * @return the read string
	 */
	public String readString(int length) {
		if (offset+length > size)
			return null;
		String result = buffer.substring(offset, offset+length);
		offset += length;
		return result;
	}

	/**
	 * Returns whether character is part of a regular identifier (e.g. is on of the characters contained in 'A'..'Z', 'a'..'z', '_', '0'..'9')
	 * @param character the character
	 * @return
	 */
	public static boolean isWordPart(int character) {
		return ('A' <= character && character <= 'Z') ||
		('a'<= character && character <= 'z') ||
		(character == '_') ||
		(/*length > 0 &&*/ '0' <= character && character <= '9');
	}
	
	public static boolean isUmlaut(char character) {
		character = Character.toLowerCase(character);
	    return character == 'ä' || character == 'ö' || character == 'ü' || character == 'ß';
    }
	
	public static boolean isWordStart(int character) {
		return (character >= 'a' && character <= 'z') || (character >= 'A' && character <= 'Z') || character == '_';
	}
	
	/**
	 * Reads a code-word. (like regexp class [0-9a-zA-Z_])
	 * @return the code-word
	 */
	public String readIdent() {
		int start = offset;
		int length = 0;
		do {
			int readByte = read();
			boolean win = offset == start+1
				? isWordStart(readByte)
				: isWordPart(readByte);
			if (win) {
				length++;
			}
			else {
				seek(start);
				return readString(length);
			}
		} while(!reachedEOF());
		return readStringAt(start, start+length);
	}

	/**
	 * Reads a string until a char from <code>delimiters</code> occurs
	 * @param delimiters
	 * @return string sequence, without delimiter char
	 */
	public String readStringUntil(char ...delimiters) {
		int start = offset;
		int subtract = 0;
		Outer: do {
			int readByte = read();
			for (int i = 0; i < delimiters.length; i++) {
				if (readByte == delimiters[i]) {
					subtract = 1;
					break Outer;
				}
			}
		} while(!reachedEOF());
		int stringLength = offset - start - subtract;
		seek(start);
		return readString(stringLength);
	}
	
	public int skipUntil(char... delimiters) {
		int subtract = 0;
		int len;
		Outer: for (len = 0; !reachedEOF(); len++) {
			int readByte = read();
			for(int i = 0; i < delimiters.length;i++) {
				if (readByte == delimiters[i]) {
					subtract = 1;
					break Outer;
				}
			}
		}
		seek(this.offset-subtract);
		return len;
	}
	
	public boolean skipSingleLineEnding() {
		if (read() == '\r') {
			if (read() != '\n')
				unread();
			return true;
		} else {
			unread();
			return false;
		}
	}
	
	/**
	 * Reads a string until a newline character occurs
	 * Cursor is after newline char(s)
	 * @return the line without newline char(s)
	 */
	public String readLine() {
		int start = offset;
		String line = readStringUntil(NEWLINE_CHARS);
		if (line == null) {
			return readStringAt(start, offset);
		}
		if (read() == '\r') {
			if (read() != '\n')
				unread();
			return line;
		}
		else {
			unread();
			if (read() == '\n') {
				if (read() != '\r')
					unread();
				return line;
			}
		}
		return line;
	}

	/**
	 * Moves offset until a char from <code>delimiters</code> occurs
	 * @param delimiters
	 */
	public void moveUntil(char[] delimiters) {
		do {
			int readByte = read();
			for(int i = 0; i < delimiters.length;i++) {
				if (readByte == delimiters[i]) {
					return;
				}
			}
		} while(!reachedEOF());
	}

	/**
	 * Moves offset until any other char than <code>charsToEat</code> occurs
	 * @param charsToEat
	 */
	public int eat(char[] charsToEat) {
		if (reachedEOF())
			return 0; // no unreading() when already reached EOF
		int result = 0;
		do {
			int readByte = read();
			boolean doEat = false;
			for (int i = 0; i < charsToEat.length;i++) {
				if (readByte == charsToEat[i]) {
					doEat = true;
					result++;
					break;
				}
			}
			if (!doEat) {
				unread();
				return result;
			}
		} while(!reachedEOF());
		return result;
	}
	
	public int eatUntil(char ...delimiters) {
		if (reachedEOF())
			return 0; // no unreading() when already reached EOF
		int result = 0;
		do {
			int readByte = read();
			boolean isDelimiter = false;
			for (int i = 0; i < delimiters.length;i++) {
				if (readByte == delimiters[i]) {
					isDelimiter = true;
					result++;
					break;
				}
			}
			if (isDelimiter) {
				unread();
				return result;
			}
		} while(!reachedEOF());
		return result;
	}

	/**
	 * Eats all characters that are considered whitespace
	 * @return the amount of whitespace eaten
	 */
	public int eatWhitespace() {
		return eat(WHITESPACE_CHARS);
	}
	
	public static int indentationOfStringAtPos(String s, int pos) {
		if (pos >= s.length())
			pos = s.length();
		int tabs = 0;
		for (--pos; pos >= 0 && !isLineDelimiterChar(s.charAt(pos)); pos--) {
			if (pos < s.length() && s.charAt(pos) == '\t') {
				tabs++;
			} else {
				tabs = 0; // don't count tabs not at the start of the line
			}
		}
		return tabs;
	}
	
	public int indentationAt(int offset) {
		return indentationOfStringAtPos(buffer, offset);
	}
	
	public int currentIndentation() {
		return indentationOfStringAtPos(buffer, tell());
	}

	/**
	 * Absolute offset manipulation
	 * @param newPos
	 * @return new offset
	 */
	public int seek(int newPos) {
		offset = newPos;
		//if (offset >= size) offset = size - 1;
		return offset;
	}
	
	/**
	 * Advance the current position by the given delta.
	 * @param delta The delta to advance the current position by
	 * @return The new position
	 */
	public final int advance(int delta) {
		return offset += delta;
	}

	/**
	 * Relative offset manipulation
	 * @param distance
	 * @return new offset
	 */
	public int move(int distance) {
		offset += distance;
		if (offset >= size) offset = size - 1;
		return offset;
	}

	/**
	 * True if {@link #tell()} >= {@link #bufferSize()}
	 * @return whether eof reached
	 */
	public final boolean reachedEOF() {
		return offset >= size;
	}

	/**
	 * Current offset
	 * @return offset
	 */
	public final int tell() {
		return offset;
	}

	/**
	 * Reads a string at specified offset. The current offset of the scanner is not modified by this method
	 * @param start start offset of string
	 * @param end end offset of string
	 * @return the read string
	 */
	public String readStringAt(int start, int end) {
		if (start == end)
			return ""; //$NON-NLS-1$
		int p = tell();
		seek(start);
		String result = readString(end-start);
		seek(p);
		return result;
	}
	
	/**
	 * Returns whether c is a line delimiter char
	 * @param c the char
	 * @return true if it is one, false if not
	 */
	public static boolean isLineDelimiterChar(char c) {
		return c == '\n' || c == '\r';
	}
	
	/**
	 * Returns whether is whitespace but not a line delimiter (' ', '\t')
	 * @param c the character
	 * @return see above
	 */
	public static boolean isWhiteSpaceButNotLineDelimiterChar(char c) {
		return c == ' '	|| c == '\t';
	}
	
	public static boolean isWhiteSpace(char c) {
		return isLineDelimiterChar(c) || isWhiteSpaceButNotLineDelimiterChar(c);
	}

	/**
	 * Returns the line region is contained in as a string
	 * @param region the region
	 * @return the line string
	 */
	public String lineAtRegion(IRegion region) {
		IRegion lineRegion = regionOfLineContainingRegion(region);
		return buffer.substring(lineRegion.getOffset(), lineRegion.getOffset()+lineRegion.getLength());
	}
	
	/**
	 * Returns a substring of the script denoted by a region
	 * @param region the region
	 * @return the substring
	 */
	public String bufferSubstringAtRegion(IRegion region) {
		return this.readStringAt(region.getOffset(), region.getOffset()+region.getLength()+1);
	}
	
	/**
	 * Returns the line region is contained in as a region
	 * @param text the string to look for the line in
	 * @param regionInLine the region
	 * @return the line region
	 */
	public static IRegion regionOfLineContainingRegion(String text, IRegion regionInLine) {
		int start, end;
		for (start = regionInLine.getOffset(); start > 0 && start < text.length() && !isLineDelimiterChar(text.charAt(start-1)); start--);
		for (end = regionInLine.getOffset()+regionInLine.getLength(); end < text.length()-1 && !isLineDelimiterChar(text.charAt(end+1)); end++);
		return new Region(start, end-start);
	}
	
	public IRegion regionOfLineContainingRegion(IRegion regionInLine) {
		return regionOfLineContainingRegion(this.buffer, regionInLine);
	}

	/**
	 * returns the size of the buffer 
	 * @return the buffer size
	 */
	public int bufferSize() {
		return size;
	}
	
	/**
	 * Return the buffer the scanner operates on
	 * @return the buffer
	 */
	public String buffer() {
		return buffer;
	}
	
	@Override
	public String toString() {
		return "offset: " + tell() + "; next: " + peekString(10); //$NON-NLS-1$ //$NON-NLS-2$
	}

	/**
	 * Return the next-to-be-read char without modifying the scanner position
	 * @return the next char
	 */
	public int peek() {
		int p = read();
		unread();
		return p;
	}
	
	/**
	 * Returns the first character from the current offset that is not whitespace. This method does not alter the current offset
	 * @return
	 */
	public int peekAfterWhitespace() {
		int pos = offset;
		eatWhitespace();
		int result = read();
		seek(pos);
		return result;
	}
	
	public String peekString(int length) {
		int pos = offset;
		String result = readString(Math.min(length, size-offset));
		seek(pos);
		return result;
	}
	
	public String stringAtRegion(IRegion region) {
		return buffer.substring(region.getOffset(), region.getOffset()+region.getLength());
	}
	
	public final void reset(String text) {
		if (text == null)
			text = "";
		buffer = text;
		offset = 0;
		size = buffer.length();
	}
	
	public void reset() {
		offset = 0;
	}
	
}