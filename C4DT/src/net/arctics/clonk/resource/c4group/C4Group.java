package net.arctics.clonk.resource.c4group;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import net.arctics.clonk.ClonkCore;
import net.arctics.clonk.util.INode;
import net.arctics.clonk.util.ITreeNode;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileInfo;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.filesystem.provider.FileInfo;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.SubProgressMonitor;

/**
 * C4Group support.
 * @author ZokRadonh
 *
 */
public class C4Group extends C4GroupItem implements Serializable, ITreeNode {
	
	public enum GroupType {
		OtherGroup,
		DefinitionGroup,
		ResourceGroup,
		ScenarioGroup,
		FolderGroup
	}
	
	public interface StreamReadCallback {
		public void readStream(InputStream stream);
	}
	
	private static final long serialVersionUID = ClonkCore.SERIAL_VERSION_UID;

	public synchronized void readFromStream(C4GroupItem whoWantsThat, long pos, StreamReadCallback callback) throws IOException {
		getParentGroup().readFromStream(whoWantsThat, pos, callback);
	}
	
	/**
	 * Causes the group to create its stream. If it already has been created it gets recreated
	 * @return the stream
	 * @throws FileNotFoundException
	 * @throws IOException
	 */
	public InputStream requireStream() throws FileNotFoundException, IOException {
		return getParentGroup().requireStream();
	}
	
	/**
	 * Causes the group to drop its stream.
	 * @throws IOException
	 */
	public void releaseStream() throws IOException {
		getParentGroup().releaseStream();
	}
	
	private final String entryName;
	private List<C4GroupItem> childEntries;
	private boolean completed;
	private boolean hasChildren;
	private C4GroupHeader header;
	private C4GroupEntryHeader entryHeader;
	private C4Group parentGroup;
	private int offset;
	
	private long originModifiedAtLoadTime;
	private File origin;
	
	public int baseOffset() {
		int result = 0;
		if (getParentGroup() != null)
			result += getParentGroup().baseOffset();
		result += offset + C4GroupHeader.STORED_SIZE + C4GroupEntryHeader.STORED_SIZE * header.getEntries();
		return result;
	}
	
	public File getOrigin() {
		return origin;
	}
	
	/**
	 * Constructor for nested groups
	 * @param parent
	 * @param file 
	 */
	protected C4Group(C4Group parent, String name, File file) {
		parentGroup = parent;
		entryName = name;
		origin = file;
		if (file != null)
			originModifiedAtLoadTime = file.lastModified();
	}
	
	protected C4Group(String name, C4GroupHeader header) {
		entryName = name;
		childEntries = new LinkedList<C4GroupItem>();
		this.header = header;
	}
	
	/*
	protected static C4Group makeGroup(IFolder folder) {
		try {
			String groupName = folder.getName();
			IResource[] children = folder.members();
			int entryCount = children.length;
			C4Group group = new C4Group(groupName, C4GroupHeader.createHeader(entryCount, "C4DT C4ScriptEditor"));// TODO implement new project option for "maker" string
			int groupSize = 0;
			for (int i = 0; i < entryCount;i++) {
				String entryName = children[i].getName();
				if (children[i] instanceof IFolder) {
					C4Group subGroup = makeGroup((IFolder)children[i]);
					subGroup.entryHeader.setOffset(groupSize);
					group.getChildEntries().add(subGroup);
				}
				else if (children[i] instanceof IFile) {
					File file = new File(((IFile)children[i]).getRawLocationURI());
					int size = (int)file.length();
					C4GroupEntry entry = C4GroupEntry.makeEntry(group, C4EntryHeader.createHeader(entryName, false, false, size, 0, 0, (int) GregorianCalendar.getInstance().getTimeInMillis()),file);
					entry.getEntryHeader().setOffset(groupSize);
					group.getChildEntries().add(entry);
				}
				else {
					System.out.println("Don't know how to treat resource " + children[i].getName() + " of class " + children[i].getClass().getName());
					continue;
				}
				groupSize += group.getChildEntries().get(group.getChildEntries().size()).computeSize();
			}
			groupSize += C4GroupHeader.STORED_SIZE; // group header
			groupSize += C4GroupEntry.STORED_SIZE * entryCount; // entry headers
			group.entryHeader = C4EntryHeader.createHeader(groupName, false, true, groupSize, 0, 0, (int) GregorianCalendar.getInstance().getTimeInMillis());
			return group;
		} catch (CoreException e) {
			e.printStackTrace();
			return null;
		}
	}*/

	public static C4Group openDirectory(File file) {
		return new C4GroupTopLevelCompressed(file);
	}
	
	/**
	 * Open a C4Group file but do not parse it yet
	 * @param file the file to open
	 * @return the group created from the file
	 * @throws C4GroupInvalidDataException
	 * @throws IOException 
	 */
	public static C4Group openFile(File file) throws C4GroupInvalidDataException, IOException {
		C4Group result = new C4GroupTopLevelCompressed(file.getName(), file);
		result.requireStream();
		return result;
	}
	
	public static C4Group openFile(IFile file) throws IOException, CoreException {
		C4Group result = new C4GroupTopLevelCompressed(file.getName(), new File(file.getFullPath().toOSString()));
		result.requireStream();
		return result;
	}
	
    public static void MemScramble(byte[] buffer, int size)
    {
        int cnt; byte temp;
        // XOR deface
        for (cnt = 0; cnt < size; cnt++)
            buffer[cnt] ^= 237;
        // BYTE swap
        for (cnt = 0; cnt + 2 < size; cnt += 3)
        {
            temp = buffer[cnt];
            buffer[cnt] = buffer[cnt + 2];
            buffer[cnt + 2] = temp;
        }
    }

	@Override
	public boolean hasChildren() {
		return hasChildren;
	}
	
    /**
     * Reads the c4group (or parts of it based on the supplied filter) into memory
     * @param recursively whether recursively or only this group
     * @param filter the filter used for deciding which files are to be dropped
     * @throws C4GroupInvalidDataException
     * @throws IOException 
     * @throws CoreException
     */
	@Override
	public void readIntoMemory(boolean recursively, C4GroupHeaderFilterBase filter, InputStream stream) throws C4GroupInvalidDataException, IOException, CoreException {

		if (stream == null)
			stream = getStream();
		
		String[] files;
		
		// compressed
		if (stream != null) {

			// read all items before this one in the parent group so the stream offset is right
			/*if (parentGroup != null && parentGroup.getChildren().get(0) != this) {
				C4GroupItem predecessor = parentGroup.getChildren().get(parentGroup.getChildren().indexOf(this) - 1);
				predecessor.readIntoMemory(true, filter);
			}*/

			if (!completed) {

				completed = true;
				header = C4GroupHeader.createFromStream(stream);

				childEntries = new ArrayList<C4GroupItem>(header.getEntries());
				List<Object> readObjects = new ArrayList<Object>(header.getEntries());

				// populate readObjects with either C4GroupHeader (meaning the file this header describes is to be skipped) or C4GroupItem (meaning this item is to be added to child list of the calling group)
				for(int i = 0; i < header.getEntries(); i++) {
					hasChildren = true;
					C4GroupEntryHeader entryHeader = C4GroupEntryHeader.createFromStream(stream);
					if (!(filter.accepts(entryHeader, this) || entryHeader.isGroup())) {
						// FIXME: skipping groups will not work at this time
						readObjects.add(entryHeader);
					}
					else {
						C4GroupItem entry;
						if (entryHeader.isGroup()) {
							C4Group group = new C4Group(this, entryHeader.getEntryName(), null); // a group does not need all header information
							group.offset = entryHeader.getOffset();
							entry = group;
						}
						else {
							entry = new C4GroupEntry(this, entryHeader);
						}
						filter.created(entryHeader, entry);
						readObjects.add(entry);
					}
				}

				// process group before processing child items
				filter.processData(this);

				if (recursively) {
					// open (read into memory or process in a way defined by the filter) or skip 
					for (Object o : readObjects) {
						if (o instanceof C4GroupEntryHeader) {
							((C4GroupEntryHeader)o).skipData(stream);
						}
						else if (o instanceof C4GroupItem) {
							C4GroupItem item = (C4GroupItem) o;
							childEntries.add(item);
							item.readIntoMemory(true, filter, stream);
						}
					}
				}
				else {
					for (Object o : readObjects)
						if (o instanceof C4GroupItem) {
							childEntries.add((C4GroupItem) o);
							// not recursive: only read files
							if (o instanceof C4GroupEntry)
								((C4GroupEntry)o).readIntoMemory(false, filter, stream);
						}
				}

			}

		}
		
		// not compressed
		else if (origin != null && (files = origin.list()) != null) {
			childEntries = new ArrayList<C4GroupItem>(files.length);
			for (String childFileName : files) {
				File child = new File(origin, childFileName);
				C4GroupEntryHeader header = new C4GroupEntryHeader(child);
				if (filter.accepts(header, this)) {
					boolean isFile = !header.isGroup();
					C4GroupItem childItem =
						isFile
							? new C4GroupEntry(this, header)
							: child.isDirectory()
								? new C4GroupUncompressed(this, child.getName(), child)
								: new C4GroupTopLevelCompressed(this, child.getName(), child);
					if (isFile)
						stream = new FileInputStream(child);
					try {
						childEntries.add(childItem);
						if (recursively || !(childItem instanceof C4Group))
							childItem.readIntoMemory(true, filter, stream);
					} finally {
						if (isFile) {
							stream.close();
							stream = null;
						}
					}
				}
			}
		}
		
	}
	
	public final void readIntoMemory(boolean recursively, C4GroupHeaderFilterBase filter) throws C4GroupInvalidDataException, IOException, CoreException {
		readIntoMemory(recursively, filter, getStream());
	}
	
	/**
	 * Reads all the group's contents into memory
	 * @param recursively whether recursively or only for this group 
	 * @throws C4GroupInvalidDataException
	 * @throws IOException
	 * @throws CoreException
	 */
	public void readIntoMemory(boolean recursively) throws C4GroupInvalidDataException, IOException, CoreException {
		readIntoMemory(recursively, ACCEPT_EVERYTHING, getStream());
	}

	/**
	 * Extracts the group to the specified location in the project 
	 */	
	@Override
	public void extractToFileSystem(IContainer parent) throws CoreException {
		extractToFileSystem(parent, null);
	}
	
	/**
	 * Extracts the group to the specified location in the project using a progress monitor
	 */	
	@Override
	public void extractToFileSystem(IContainer parent, IProgressMonitor monitor) throws CoreException {
		IFolder me = null;
		if (parent instanceof IFolder) {
			me = ((IFolder)parent).getFolder(getName());
		}
		else if (parent instanceof IProject) {
			me = ((IProject)parent).getFolder(getName());
		}
		else {
			throw new InvalidParameterException(Messages.NoImportingToWorkspaceRoot);
		}
		me.create(IResource.NONE, true, monitor);
		for(C4GroupItem item : childEntries) {
			if (monitor.isCanceled()) break;
			item.extractToFileSystem(me, monitor);
			monitor.worked(item.computeSize());
		}
	}
	
	@Override
	public String toString() {
		StringBuilder builder = new StringBuilder();
    	for (C4GroupItem item = this; item != null; item = item.getParentGroup()) {
    		builder.insert(0, item.getName());
    		builder.insert(0, '/');
    	}
        return builder.toString();
	}
	
	/**
	 * Returns the file name of the item
	 */
	@Override
	public String getName() {
		return entryName;
	}

	public boolean isCompleted() {
		return completed;
	}
	
	/**
	 * @return the stream the group is read from
	 */
	public InputStream getStream() {
		return getParentGroup().getStream();
	}
	
	/**
	 * @return the childEntries
	 */
	public List<C4GroupItem> getChildren() {
		if (childEntries == null) {
			try {
				if (origin != null)
					originModifiedAtLoadTime = origin.lastModified();
				if (origin != null && origin.isDirectory())
					readIntoMemory(false, ACCEPT_EVERYTHING_DONTSTORECONTENTS, null);
				else {
					readFromStream(this, (getParentGroup() != null ? getParentGroup().baseOffset() : 0) + offset, new StreamReadCallback() {
						@Override
						public void readStream(InputStream stream) {
							try {
								readIntoMemory(false, ACCEPT_EVERYTHING_DONTSTORECONTENTS, stream);
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					});
				}
			} catch (IOException e) {
				e.printStackTrace();
			} catch (C4GroupInvalidDataException e) {
				e.printStackTrace();
			} catch (CoreException e) {
				e.printStackTrace();
			}
		}
		return childEntries;
	}

	/**
	 * @return the header
	 */
	public C4GroupHeader getHeader() {
		return header;
	}

	@Override
	public C4Group getParentGroup() {
		return parentGroup;
	}
	
	public C4GroupItem findChild(String itemName) {
		for (C4GroupItem item : getChildren()) {
			if (item.getName().equalsIgnoreCase(itemName))
				return item;
		}
		return null;
	}
	
	public C4GroupItem findChild(IPath path) {
		C4GroupItem item = findChild(path.segment(0));
		if (item != null) {
			if (path.segmentCount() == 1)
				return item;
			else {
				if (item instanceof C4Group) {
					return ((C4Group)item).findChild(path.removeFirstSegments(1));
				}
			}
		}
		return null;
	}
	
	/**
	 * Close the stream the group is being read from
	 * @throws IOException 
	 */
	public void close() throws IOException {
		if (childEntries != null) {
			childEntries.clear();
			childEntries = null;
		}
		releaseStream();
	}

	@Override
	public int computeSize() {
		if (entryHeader == null) {
			int size = 0;
			for(C4GroupItem item : childEntries) {
				size += item.computeSize();
			}
			return size;
		}
		else return entryHeader.getSize();
	}

	@Override
	public C4GroupEntryHeader getEntryHeader() {
		return entryHeader;
	}

	@Override
	public void writeTo(OutputStream stream) throws FileNotFoundException {
		try {
			header.writeTo(stream); // group header
			for(C4GroupItem item : childEntries) { // write index
				item.getEntryHeader().writeTo(stream); 
			}
			for(C4GroupItem item : childEntries) { // write data
				item.writeTo(stream);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}		
	}
	
	@Override
	public void releaseData() {
	}

	@Override
	public void addChild(ITreeNode node) {
		// nope
	}

	@Override
	public Collection<? extends INode> childCollection() {
		return this.getChildren();
	}

	@Override
	public ITreeNode parentNode() {
		return getParentGroup();
	}

	@Override
	public IPath path() {
		return ITreeNode.Default.getPath(this);
	}

	@Override
	public boolean subNodeOf(ITreeNode node) {
		return ITreeNode.Default.subNodeOf(this, node);
	}

	@Override
	public String nodeName() {
		return getName();
	}

	public C4Group getMasterGroup() {
		C4Group result;
		for (result = this; result.getParentGroup() != null; result = result.getParentGroup());
		return result;
	}

	@Override
	public IFileStore[] childStores(int options, IProgressMonitor monitor) throws CoreException {
		if (childEntries == null)
			return new IFileStore[0];
		else
			return childEntries.toArray(new IFileStore[childEntries.size()]);
	}
	
	@Override
	public String[] childNames(int options, IProgressMonitor monitor) throws CoreException {
		if (outdated()) {
			childEntries = null; // force refresh
			completed = false;
		}
		List<C4GroupItem> childEntries = this.getChildren();
		String[] result = new String[childEntries.size()];
		for (int i = 0; i < result.length; i++) {
			result[i] = childEntries.get(i).getName();
		}
		return result;
	}

	@Override
	public IFileInfo fetchInfo(int options, IProgressMonitor monitor) throws CoreException {
		FileInfo fileInfo = new FileInfo(getName());
		fileInfo.setExists(true);
		fileInfo.setAttribute(EFS.ATTRIBUTE_HIDDEN, false);
		fileInfo.setAttribute(EFS.ATTRIBUTE_READ_ONLY, true);
		fileInfo.setAttribute(EFS.ATTRIBUTE_ARCHIVE, false);
		fileInfo.setDirectory(true);
		return fileInfo;
	}

	@Override
	public IFileStore getChild(String name) {
		return findChild(name);
	}

	@Override
	public IFileStore getParent() {
		return getParentGroup();
	}

	@Override
	public InputStream openInputStream(int options, IProgressMonitor monitor) throws CoreException {
		throw new CoreException(new Status(1, ClonkCore.PLUGIN_ID, "FileStore stream cannot be opened on C4Group")); //$NON-NLS-1$
	}
	
	public long lastModified() {
		if (origin != null)
			return origin.lastModified();
		else if (getParentGroup() != null)
			return getParentGroup().lastModified();
		else
			return EFS.NONE;
	}
	
	@Override
	protected void copyDirectory(IFileInfo sourceInfo, IFileStore destination, int options, IProgressMonitor monitor) throws CoreException {
		
		boolean needsToReleaseStream = getStream() == null;
		try {
			if (needsToReleaseStream)
				requireStream();
		} catch (IOException e) {
			e.printStackTrace();
			return;
		}

		try {

			try {
				IFileStore[] children = null;
				int opWork = 1;
				if ((options & EFS.SHALLOW) == 0) {
					children = childStores(EFS.NONE, null);
					opWork += children.length;
				}
				monitor.beginTask("", opWork); //$NON-NLS-1$
				monitor.subTask(Messages.C4Group_Copying);
				// create directory 
				destination.mkdir(EFS.NONE, new SubProgressMonitor(monitor, 1));
				// don't copy attributes since that is stupid
				//transferAttributes(sourceInfo, destination);

				if (children == null)
					return;
				// copy children
				for (int i = 0; i < children.length; i++)
					children[i].copy(destination.getChild(children[i].getName()), options, new SubProgressMonitor(monitor, 1));
			} finally {
				monitor.done();
			}

		} finally {
			try {
				if (needsToReleaseStream)
					releaseStream();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}

	}
	
	/**
	 * Return whether the file this group was loaded from has been changed since reading its structure
	 * @return what he says
	 */
	public boolean outdated() {
		if (origin != null) {
			return origin.lastModified() != originModifiedAtLoadTime;
		}
		else {
			return parentGroup != null ? parentGroup.outdated() : false;
		}
	}
	
	@Override
	public File toLocalFile(int options, IProgressMonitor monitor) throws CoreException {
		return origin;
	}
	
	/**
	 * Return whether the file this group originates from still exists in the filesystem
	 * @return true if it exists, otherwise false
	 */
	public boolean existsOnDisk() {
		// delegate to parent
		return parentGroup == null || parentGroup.existsOnDisk();
	}
	
}
