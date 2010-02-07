package net.arctics.clonk.ui.navigator;

import org.eclipse.osgi.util.NLS;

public class Messages extends NLS {
	private static final String BUNDLE_NAME = "net.arctics.clonk.ui.navigator.messages"; //$NON-NLS-1$
	public static String ClonkActionProvider_QuickExport;
	public static String ClonkActionProvider_ConvertOldCode;
	public static String ClonkFolderView_Browse;
	public static String ClonkFolderView_Import;
	public static String ClonkFolderView_Link;
	public static String ClonkFolderView_OpenInCurrentProject;
	public static String ClonkFolderView_Project;
	public static String ClonkFolderView_Refresh0;
	public static String ClonkFolderView_RemoveLinkedFilesOnShutdown;
	public static String ClonkPreviewView_Updater;
	public static String ConvertOldCodeInBulkAction_ConvertingCode;
	public static String OpenSpecialItemAction_Open;
	public static String QuickImportAction_SelectFiles;
	static {
		// initialize resource bundle
		NLS.initializeMessages(BUNDLE_NAME, Messages.class);
	}

	private Messages() {
	}
}
