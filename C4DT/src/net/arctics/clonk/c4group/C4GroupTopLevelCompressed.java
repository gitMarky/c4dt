package net.arctics.clonk.c4group;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.GZIPInputStream;

import net.arctics.clonk.Core;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;

/**
 * Represents a top-level compressed C4Group.
 */
public class C4GroupTopLevelCompressed extends C4Group {
	
	private static final long serialVersionUID = Core.SERIAL_VERSION_UID;

	private transient InputStream stream;
	private long streamPos;
	
	public C4GroupTopLevelCompressed(final String name, final File file) {
		super(null, name, file);
	}
	
	protected C4GroupTopLevelCompressed(final C4Group parent, final String name, final File file) {
		super(parent, name, file);
	}
	
	/**
	 * Creates a group from an uncompressed folder
	 * @param parent
	 * @param folder
	 */
	protected C4GroupTopLevelCompressed(final File folder) {
		this (folder.getName(), folder);
		assert (folder.isDirectory());
	}
	
	@Override
	public InputStream requireStream() throws FileNotFoundException, IOException {
		streamPos = 0;
		if (stream != null)
			releaseStream();
		stream = createGroupFileStream(new FileInputStream(origin()));
		if (stream == null) {
			System.out.println("Failed to create stream for " + origin());
			return null;
		} else
			return stream;
	}
	
	@Override
	public synchronized void readFromStream(final C4GroupItem whoWantsThat, final long pos, final StreamReadCallback callback) throws IOException {
		try {
			final boolean createdStream = stream == null;
			if (createdStream)
				requireStream();
			try {
				if (stream != null) {
					if (pos > streamPos) {
						stream.skip(pos-streamPos);
						streamPos = pos;
					}
					else if (pos < streamPos) {
						releaseStream();
						requireStream();
						stream.skip(streamPos = pos);
					}
					callback.readStream(new InputStream() {

						@Override
						public int read() throws IOException {
							final int result = stream.read();
							if (result != -1)
								streamPos++;
							return result;
						}

						@Override
						public int read(final byte[] b) throws IOException {
							final int read = stream.read(b);
							streamPos += read;
							return read;
						}

						@Override
						public int read(final byte[] b, final int off, final int len) throws IOException {
							final int read = stream.read(b, off, len);
							streamPos += read;
							return read;
						}

					});
				}
				else
					throw new IOException("C4Group.readFromStream: No stream"); //$NON-NLS-1$
			} finally {
				if (createdStream)
					releaseStream();
			}
		} catch (final Exception e) {
			System.out.println("Look what you did, " + whoWantsThat.toString() + ")"); //$NON-NLS-1$ //$NON-NLS-2$
			e.printStackTrace();
		}
	}

	@Override
	public void releaseStream() throws IOException {
		if (stream == null)
			return;
		streamPos = 0;
		stream.close();
		stream = null;
//		for (C4GroupItem item : getChildren())
//			if (item instanceof C4Group)
//				((C4Group)item).releaseStream();
	}

	private static InputStream createGroupFileStream(final InputStream stream) throws IOException {
		try {
			return new GZIPInputStream(new InputStream() {
				private int timesRead = 0;

				@Override
				public int read() throws IOException {
					if (timesRead < 2) { // deface magic header
						timesRead++;
						final int readByte = stream.read();
						if (readByte == 0x1E) return 0x1F;
						if (readByte == 0x8C) return 0x8B;
						return readByte;
					}
					return stream.read();
				}

				@Override
				public int read(final byte[] b) throws IOException {
					return stream.read(b);
				}

				@Override
				public int read(final byte[] b, final int off, final int len) throws IOException {
					return stream.read(b, off, len);
				}

				@Override
				public boolean markSupported() {
					return stream.markSupported();
				}

				@Override
				public synchronized void mark(final int readlimit) {
					stream.mark(readlimit);
				}

				@Override
				public synchronized void reset() throws IOException {
					stream.reset();
				}

				@Override
				public long skip(final long n) throws IOException {
					return stream.skip(n);
				}

				@Override
				public void close() throws IOException {
					stream.close();
				}

				@Override
				public int available() throws IOException {
					return stream.available();
				}

			});
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}
	
	@Override
	public InputStream stream() {
		return stream;
	}
	
	@Override
	public void delete(final int options, final IProgressMonitor monitor) throws CoreException {
		C4GroupFileSystem.instance().removeGroupFromRegistry(this);
	}
	
	@Override
	protected void finalize() throws Throwable {
		releaseStream();
		super.finalize();
	}
	
	@Override
	public boolean existsOnDisk() {
		final File origin = origin();
		return origin != null && origin.exists();
	}

}
