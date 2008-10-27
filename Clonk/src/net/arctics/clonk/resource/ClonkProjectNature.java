package net.arctics.clonk.resource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import net.arctics.clonk.parser.C4ID;
import net.arctics.clonk.parser.ClonkIndex;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectNature;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;

public class ClonkProjectNature implements IProjectNature {

	private static final String indexDataFile = "indexdata";
	
	private IProject project;
	private boolean isIndexed = false;
	
	private ClonkIndex indexedData = null;
	
	public ClonkProjectNature() {
	}
	
	public void configure() throws CoreException {
	}

	public void deconfigure() throws CoreException {
	}

	public IProject getProject() {
		return project;
	}

	public void setProject(IProject project) {
		this.project = project;
	}

	/**
	 * @return the isIndexed
	 */
	public boolean isIndexed() {
		return isIndexed;
	}

	/**
	 * @return the indexedData
	 */
	public ClonkIndex getIndexedData() {
		if (indexedData == null)
			loadIndexData();
		return indexedData;
	}
	
	public void saveIndexData() {
		final IFile index = project.getFile(indexDataFile);
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ObjectOutputStream objStream = new ObjectOutputStream(out);
			objStream.writeObject(getIndexedData());
			objStream.close();
			ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
			if (index.exists()) {
				index.setContents(in, true, false, null);
			} else {
				index.create(in, IResource.DERIVED | IResource.HIDDEN | IResource.FORCE, null);
			}
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (CoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private class InputStreamRespectingUniqueIDs extends ObjectInputStream {

		public InputStreamRespectingUniqueIDs(InputStream input)
				throws IOException {
			super(input);
			enableResolveObject(true);
		}

		@Override
		protected Object resolveObject(Object obj) throws IOException {
			if (obj instanceof C4ID) {
				System.out.println(obj.toString());
				return C4ID.getID(((C4ID)obj).getName());
			}
			// TODO Auto-generated method stub
			return super.resolveObject(obj);
		}
		
	}
	
	private void loadIndexData() {
		final IFile index = project.getFile(indexDataFile);
		if (!index.exists()) {
			indexedData = new ClonkIndex(project);
			return;
		}
		try {
			InputStream in = index.getContents();
			ObjectInputStream objStream = new InputStreamRespectingUniqueIDs(in);
			indexedData = (ClonkIndex)objStream.readObject();
			indexedData.setProject(getProject());
			indexedData.fixReferencesAfterSerialization();
		} catch (Exception e) {
			e.printStackTrace();
			// somehow failed - ignore
			indexedData = new ClonkIndex(project);
		}
	}

}