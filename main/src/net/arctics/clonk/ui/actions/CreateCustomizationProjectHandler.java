package net.arctics.clonk.ui.actions;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.ui.PlatformUI;

import net.arctics.clonk.builder.CustomizationNature;
import net.arctics.clonk.util.UI;

public class CreateCustomizationProjectHandler extends AbstractHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final CustomizationNature custom = CustomizationNature.get();
		if (custom != null) {
			UI.projectExplorer().selectReveal(new StructuredSelection(custom.getProject()));
		} else {
			final String val = UI.input(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), Messages.CreateCustomizationProjectHandler_ProvideName, Messages.CreateCustomizationProjectHandler_NamePromptDesc, Messages.CreateCustomizationProjectHandler_DefaultName);
			if (val != null) {
				CustomizationNature.create(val);
			}
		}
		return null;
	}

}
