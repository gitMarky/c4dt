package net.arctics.clonk.util;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Scenario;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.parser.c4script.Script;
import net.arctics.clonk.ui.editors.c4script.C4ScriptEditor;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.console.ConsolePlugin;
import org.eclipse.ui.console.IConsole;
import org.eclipse.ui.console.IConsoleConstants;
import org.eclipse.ui.console.IConsoleManager;
import org.eclipse.ui.console.IConsoleView;
import org.eclipse.ui.console.MessageConsole;
import org.eclipse.ui.console.MessageConsoleStream;
import org.eclipse.ui.part.FileEditorInput;

/**
 * Contains various utility functions
 *
 */
public abstract class Utilities {

	private static MessageConsole clonkConsole = null;
	private static MessageConsoleStream debugConsoleStream = null;

	public static MessageConsole clonkConsole() {
		if (clonkConsole == null)
			clonkConsole = consoleWithName(Messages.Utilities_ClonkConsole);
		return clonkConsole;
	}

	public static MessageConsole consoleWithName(String name) {
		ConsolePlugin plugin = ConsolePlugin.getDefault();
		IConsoleManager conMan = plugin.getConsoleManager();
		IConsole[] existing = conMan.getConsoles();
		for (int i = 0; i < existing.length; i++)
			if (name.equals(existing[i].getName()))
				return (MessageConsole) existing[i];
		//no console found, so create a new one
		MessageConsole console = new MessageConsole(name, null);
		conMan.addConsoles(new IConsole[]{console});
		return console;
	}

	public static MessageConsoleStream debugStream() {
		if (debugConsoleStream == null)
			debugConsoleStream = consoleWithName(Messages.Utilities_DebugConsole).newMessageStream();
		return debugConsoleStream;
	}

	public static void displayClonkConsole() {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				IWorkbenchPage page = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getActivePage();
				String id = IConsoleConstants.ID_CONSOLE_VIEW;

				// show console
				try {
					IConsoleView view = (IConsoleView) page.showView(id);
					view.display(clonkConsole());
				} catch (PartInitException e) {
					e.printStackTrace();
				}
			}
		});
	}

	public static IFile fileEditedBy(IEditorPart editor) {
		if (editor.getEditorInput() instanceof FileEditorInput)
			return ((FileEditorInput)editor.getEditorInput()).getFile();
		else
			return null;
	}

	public static Script scriptForResource(IResource resource) throws CoreException {
		if (resource instanceof IContainer)
			return Definition.definitionCorrespondingToFolder((IContainer) resource);
		else if (resource instanceof IFile)
			return Script.get(resource, true);
		else
			return null;
	}

	public static Script scriptForEditor(IEditorPart editor) {
		if (editor instanceof C4ScriptEditor)
			return ((C4ScriptEditor) editor).script();
		else
			return null;
	}

	/**
	 * Shorthand for comparing resources
	 * @param a the first resource
	 * @param b the second resource
	 * @return true if resources are both null or denote the same resource, false if not
	 */
	public static boolean objectsEqual(Object a, Object b) {
		return (a == null && b == null) || (a != null && b != null && a.equals(b));
	}

	public static boolean objectsNonNullEqual(Object a, Object b) {
		return a != null && b != null && a.equals(b);
	}

	/**
	 * Returns whether resource somewhere below container in the file hierarchy
	 * @param resource the resource
	 * @param container the container
	 * @return true if resource is below container, false if not
	 */
	public static boolean resourceInside(IResource resource, IContainer container) {
		for (IContainer c = resource instanceof IContainer ? (IContainer)resource : resource.getParent(); c != null; c = c.getParent())
			if (c.equals(container))
				return true;
		return false;
	}

	private static int distanceToCommonContainer(IResource a, IResource b, Scenario aScenario, Scenario bScenario) {
		IContainer c;
		int dist = 0;
		for (c = a instanceof IContainer ? (IContainer)a : a.getParent(); c != null; c = c.getParent()) {
			if (resourceInside(b, c))
				break;
			dist++;
		}
		if (aScenario == null)
			aScenario = Scenario.containingScenario(a);
		if (bScenario == null)
			bScenario = Scenario.containingScenario(b);
		if (aScenario != bScenario) {
			dist += 500; // penalty for scenario boundary
			if (aScenario != null && bScenario != null)
				dist += 500; // double penalty for different scenarios
		}
		return dist;
	}

	/**
	 * From some list containing {@link IHasRelatedResource} thingies, pick the one with the least amount of hops between its related {@link IResource} ({@link IHasRelatedResource#resource()}) and the specified {@link IResource}
	 * @param <T> The type of elements in the passed list, constrained to extend {@link IHasRelatedResource}
	 * @param fromList The list to pick the result from
	 * @param resource The pivot dictating the perspective of the call.
	 * @param filter A filter to exclude some of the items contained in the list
	 * @return The item 'nearest' to resource
	 */
	public static <T extends IHasRelatedResource> T pickNearest(List<T> fromList, IResource resource, IPredicate<T> filter) {
		int bestDist = Integer.MAX_VALUE;
		T best = null;
		if (fromList != null) {
			if (fromList.size() == 1) {
				T soleItem = fromList.get(0);
				return filter == null || filter.test(soleItem) ? soleItem : null;
			}
			Scenario scen = Scenario.containingScenario(resource);
			for (T o : fromList) {
				if (filter != null && !filter.test(o))
					continue;
				IResource res = o.resource();
				int newDist = res != null
					? distanceToCommonContainer(resource, res, scen, null)
					: Integer.MAX_VALUE;
				if (best == null || newDist < bestDist) {
					best = o;
					bestDist = newDist;
				}
			}
		}
		return best;
	}

	public static boolean allInstanceOf(Object[] objects, Class<?> cls) {
		for (Object item : objects)
			if (!(cls.isAssignableFrom(item.getClass())))
				return false;
		return true;
	}

	public static Class<?> baseClass(Class<?> a, Class<?> b) {
		Class<?> result = a;
		while (!result.isAssignableFrom(b))
			result = result.getSuperclass();
		return result;
	}

	public static IRegion wordRegionAt(CharSequence line, int relativeOffset) {
		int start, end;
		relativeOffset = clamp(relativeOffset, 0, line.length()-1);
		start = end = relativeOffset;
		for (int s = relativeOffset; s >= 0 && BufferedScanner.isWordPart(line.charAt(s)); s--)
			start = s;
		for (int e = relativeOffset+1; e < line.length() && BufferedScanner.isWordPart(line.charAt(e)); e++)
			end = e;
		return new Region(start, end-start+1);
	}

	public static boolean regionContainsOffset(IRegion region, int offset) {
		return offset >= region.getOffset() && offset < region.getOffset() + region.getLength();
	}

	public static boolean regionContainsOtherRegion(IRegion region, IRegion otherRegion) {
		return otherRegion.getOffset() >= region.getOffset() && otherRegion.getOffset()+otherRegion.getLength() < region.getOffset()+region.getLength();
	}

	public static int clamp(int value, int min, int max) {
		if (value < min)
			return min;
		else if (value > max)
			return max;
		else
			return value;
	}

	public static <T> T itemMatching(IPredicate<T> predicate, List<T> sectionsList) {
		for (T item : sectionsList)
			if (predicate.test(item))
				return item;
		return null;
	}

	public static Enum<?>[] enumValues(Class<?> enumClass) {
		try {
			return (Enum<?>[]) enumClass.getMethod("values").invoke(null); //$NON-NLS-1$
		} catch (IllegalAccessException | IllegalArgumentException
			| InvocationTargetException | NoSuchMethodException
			| SecurityException e) {
			e.printStackTrace();
			return null;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T enumValueFromString(Class<T> enumClass, String value) {
		return (T) Enum.valueOf((Class<Enum>)enumClass, value);
	}

	public static <E, T extends Collection<E>> T collectionFromArray(Class<T> cls, E[] array) {
		try {
			T result = cls.newInstance();
			for (E e : array)
				result.add(e);
			return result;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static <S, T extends S> boolean isAnyOf(S something, @SuppressWarnings("unchecked") T... things) {
		if (something != null)
			for (Object o : things)
				if (something.equals(o))
					return true;
		return false;
	}

	public static <T> boolean collectionContains(Collection<T> list, T elm) {
		for (T e : list)
			if (e.equals(elm))
				return true;
		return false;
	}

	public static <T> List<T> filter(Iterable<? extends T> iterable, IPredicate<T> filter) {
		List<T> result = new LinkedList<T>();
		for (T elm : iterable)
			if (filter.test(elm))
				result.add(elm);
		return result;
	}

	public static void errorMessage(Throwable error, final String title) {
		String message = error.getClass().getSimpleName();
		if (error.getLocalizedMessage() != null)
			message += ": " + error.getLocalizedMessage(); //$NON-NLS-1$
		errorMessage(message, title);
	}

	public static void errorMessage(final String message, final String title) {
		Display.getDefault().asyncExec(new Runnable() {
			@Override
			public void run() {
				MessageDialog messageDialog = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
					title == null ? Messages.Utilities_InternalError : title, null,
					message, MessageDialog.ERROR,
					new String[] { Messages.Utilities_InternalErrorButton }, 1
				);
				messageDialog.open();
			}
		});
	}

	public static IResource findMemberCaseInsensitively(IContainer container, String name) {
		try {
	        for (IResource child : container.members())
				if (child.getName().equalsIgnoreCase(name))
	        		return child;
        } catch (CoreException e) {
	        //e.printStackTrace(); just return null, will just be some case of having a referenced container that does not exist anymore
        }
		return null;
	}

	/**
	 * Returns true if there is anything in items that matches the predicate
	 * @param <T>
	 * @param items items
	 * @param predicate predicate
	 * @return see above
	 */
	public static <T> boolean any(Iterable<? extends T> items, IPredicate<T> predicate) {
		for (T item : items)
			if (predicate.test(item))
				return true;
		return false;
	}

	@SuppressWarnings("rawtypes")
	public static final IConverter<Object, Class> INSTANCE_TO_CLASS_CONVERTER = new IConverter<Object, Class>() {
		@Override
		public Class convert(Object from) {
			return from.getClass();
		}
	};

	public static <A, B> B as(A obj, Class<B> type) {
		return type.isInstance(obj) ? type.cast(obj) : null;
	}

	public static <A> A defaulting(A firstChoice, A defaultChoice) {
		return firstChoice != null ? firstChoice : defaultChoice;
	}

	public static <A> A or(A a, A b) {
		if (a != null)
			return a;
		else
			return b;
	}

	public static String multiply(String s, int times) {
		StringBuilder builder = new StringBuilder(s.length()*times);
		for (int i = 0; i < times; i++)
			builder.append(s);
		return s.toString();
	}

	public static void threadPool(Sink<ExecutorService> action, int timeoutMinutes) {
		final ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
		try {
			action.receivedObject(pool);
		} finally {
			pool.shutdown();
			try {
				pool.awaitTermination(timeoutMinutes, TimeUnit.MINUTES);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	public static Object token(final String token) {
		return new Object() {
			@Override
			public String toString() {
				return token;
			}
		};
	}

	private static Object autoBuildDisablingLock = new Object();

	public static void runWithoutAutoBuild(Runnable runnable) {
		synchronized (autoBuildDisablingLock) {
			IWorkspace workspace = ResourcesPlugin.getWorkspace();
			IWorkspaceDescription workspaceDescription = workspace.getDescription();
			boolean autoBuilding = workspaceDescription.isAutoBuilding();
			workspaceDescription.setAutoBuilding(false);
			try {
				workspace.setDescription(workspaceDescription);
			} catch (CoreException e2) {
				e2.printStackTrace();
			}
			try {
				runnable.run();
			} finally {
				workspaceDescription.setAutoBuilding(autoBuilding);
				try {
					workspace.setDescription(workspaceDescription);
				} catch (CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public interface Folder<T, Y extends T> {
		Y fold(T interim, T next, int index);
	}

	public static <T, Y extends T> Y foldl(Iterable<? extends T> iterable, Folder<T, Y> folder) {
		Y interim = null;
		T first = null;
		int i = 0;
		for (T item : iterable) {
			if (interim == null) {
				if (first != null)
					interim = folder.fold(first, item, i);
				else
					first = item;
			} else
				interim = folder.fold(interim, item, i);
			i++;
		}
		return interim;
	}

	private static class ApiTyped<T, S extends T> {
		public ApiTyped(S object, Class<T> apiType) {
			super();
			this.object = object;
			this.apiType = apiType;
		}
		public final S object;
		public final Class<T> apiType;
	}

	public static Object apiCall(Object target, String method, Object... arguments) throws ReflectiveOperationException, IllegalArgumentException, SecurityException {
		Class<?>[] classes = new Class<?>[arguments.length];
		for (int i = 0; i < arguments.length; i++)
			if (arguments[i] instanceof ApiTyped<?,	?>) {
				ApiTyped<?, ?> apiTyped = (ApiTyped<?, ?>)arguments[i];
				arguments[i] = apiTyped.object;
				classes[i] = apiTyped.apiType;
			} else
				classes[i] = arguments[i].getClass();
		return target.getClass().getMethod(method, classes).invoke(target, arguments);
	}

	public static <T, S extends T> Object apiTyped(S item, Class<T> cls) {
		return new ApiTyped<>(item, cls);
	}

}