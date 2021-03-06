package net.arctics.clonk.util;

import static java.lang.String.format;
import static java.util.Arrays.stream;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceDescription;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Status;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.text.IRegion;
import org.eclipse.jface.text.Region;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;

import net.arctics.clonk.Core;
import net.arctics.clonk.c4script.Script;
import net.arctics.clonk.index.Definition;
import net.arctics.clonk.index.Scenario;
import net.arctics.clonk.parser.BufferedScanner;
import net.arctics.clonk.ui.editors.c4script.C4ScriptEditor;

/**
 * Contains various utility functions
 *
 */
public abstract class Utilities {

	public static IFile fileEditedBy(final IEditorPart editor) {
		final IEditorInput editorInput = editor.getEditorInput();
		return fileFromEditorInput(editorInput);
	}

	public static IFile fileFromEditorInput(final IEditorInput editorInput) {
		return editorInput instanceof FileEditorInput ? ((FileEditorInput)editorInput).getFile() : null;
	}

	public static Script scriptForResource(final IResource resource) {
		return (
			resource instanceof IContainer ? Definition.at((IContainer) resource) :
			resource instanceof IFile ?  Script.get(resource, true) :
			null
		);
	}

	public static Script scriptForEditor(final IEditorPart editor) {
		return editor instanceof C4ScriptEditor ? ((C4ScriptEditor) editor).script() : null;
	}

	/**
	 * Shorthand for comparing resources
	 * @param a the first resource
	 * @param b the second resource
	 * @return true if resources are both null or denote the same resource, false if not
	 */
	public static boolean eq(final Object a, final Object b) {
		return (a == null && b == null) || (a != null && b != null && (a.equals(b)||b.equals(a)));
	}

	/**
	 * Returns whether resource somewhere below container in the file hierarchy
	 * @param resource the resource
	 * @param container the container
	 * @return true if resource is below container, false if not
	 */
	public static boolean resourceInside(final IResource resource, final IContainer container) {
		return walk(resource instanceof IContainer ? (IContainer)resource : resource.getParent(), IContainer::getParent)
			.anyMatch(c -> c.equals(container));
	}

	private static int distanceToCommonContainer(final IResource a, final IResource b, Scenario aScenario, Scenario bScenario) {
		IContainer c;
		int dist = 0;
		for (c = a instanceof IContainer ? (IContainer)a : a.getParent(); c != null; c = c.getParent()) {
			if (resourceInside(b, c)) {
				break;
			}
			dist++;
		}
		if (aScenario == null) {
			aScenario = Scenario.containingScenario(a);
		}
		if (bScenario == null) {
			bScenario = Scenario.containingScenario(b);
		}
		if (aScenario != bScenario) {
			dist += 500; // penalty for scenario boundary
			if (aScenario != null && bScenario != null)
			 {
				dist += 500; // double penalty for different scenarios
			}
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
	public static <T extends IHasRelatedResource> T pickNearest(final Stream<? extends T> fromList, final IResource resource, final Predicate<T> filter) {
		final Weighted<T> w = (fromList != null ? fromList : Stream.<T>empty())
			.filter(defaulting(filter, (T x) -> true))
			.map(o -> {
				final IResource res = o.resource();
				final Scenario scen = Scenario.containingScenario(resource);
				final int dist = res != null
					? distanceToCommonContainer(resource, res, scen, null)
					: Integer.MAX_VALUE;
				return new Weighted<T>(o, dist);
			})
			.reduce((a, b) -> b.weight < a.weight ? b : a)
			.orElse(null);
		return w != null ? w.object : null;
	}

	public static Class<?> baseClass(final Class<?> a, final Class<?> b) {
		Class<?> result = a;
		while (!result.isAssignableFrom(b)) {
			result = result.getSuperclass();
		}
		return result;
	}

	public static IRegion wordRegionAt(final CharSequence line, int relativeOffset) {
		int start, end;
		relativeOffset = clamp(relativeOffset, 0, line.length()-1);
		start = end = relativeOffset;
		for (int s = relativeOffset; s >= 0 && BufferedScanner.isWordPart(line.charAt(s)); s--) {
			start = s;
		}
		for (int e = relativeOffset+1; e < line.length() && BufferedScanner.isWordPart(line.charAt(e)); e++) {
			end = e;
		}
		return new Region(start, end-start+1);
	}

	public static boolean regionContainsOffset(final IRegion region, final int offset) {
		return offset >= region.getOffset() && offset < region.getOffset() + region.getLength();
	}

	public static boolean regionContainsOtherRegion(final IRegion region, final IRegion otherRegion) {
		return otherRegion.getOffset() >= region.getOffset() && otherRegion.getOffset()+otherRegion.getLength() < region.getOffset()+region.getLength();
	}

	public static int clamp(final int value, final int min, final int max) {
		return value < min ? min : value > max ? max : value;
	}

	public static Enum<?>[] enumValues(final Class<?> enumClass) {
		try {
			return (Enum<?>[]) enumClass.getMethod("values").invoke(null); //$NON-NLS-1$
		} catch (
			IllegalAccessException |
			IllegalArgumentException |
			InvocationTargetException |
			NoSuchMethodException |
			SecurityException e
		) {
			e.printStackTrace();
			return null;
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	public static <T> T enumValueFromString(final Class<T> enumClass, final String value) {
		try {
			return (T) Enum.valueOf((Class<Enum>)enumClass, value);
		} catch (final IllegalArgumentException e) {
			try {
				return (T) Enum.valueOf((Class<Enum>)enumClass, makeJavaConstantString(value));
			} catch (final IllegalArgumentException e2) {
				try {
					final Field f = enumClass.getField(value);
					return (T) f.get(null);
				} catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException e1) {
					return (T) enumValues(enumClass)[0];
				}
			}
		}
	}

	private static String makeJavaConstantString(final String value) {
		final StringBuilder builder = new StringBuilder(value.length()+5);
		for (int i = 0; i < value.length(); i++) {
			final char c = value.charAt(i);
			if (Character.isUpperCase(c)) {
				if (i > 0) {
					builder.append('_');
				}
				builder.append(c);
			} else {
				builder.append(Character.toUpperCase(c));
			}
		}
		return builder.toString();
	}

	public static <S, T extends S> boolean isAnyOf(final S something, @SuppressWarnings("unchecked") final T... things) {
		return stream(things).anyMatch(o -> eq(something, o));
	}

	public static void errorMessage(final Throwable error, final String title) {
		final String clsName = error.getClass().getSimpleName();
		final String message =  error.getLocalizedMessage() != null ? format("%s: %s", clsName, error.getLocalizedMessage()) : clsName;
		errorMessage(message, title);
	}

	public static void errorMessage(final String message, final String title) {
		Display.getDefault().asyncExec(() -> {
			final MessageDialog messageDialog = new MessageDialog(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(),
				title == null ? Messages.Utilities_InternalError : title, null,
				message, MessageDialog.ERROR,
				new String[] { Messages.Utilities_InternalErrorButton }, 1
			);
			messageDialog.open();
		});
	}

	public static IResource findMemberCaseInsensitively(final IContainer container, final String name) {
		if (container == null) {
			return null;
		}
		final IResource[] members = attempt(() -> container.members(), CoreException.class, e -> {});
		return members != null ? stream(members).filter(c -> c.getName().equalsIgnoreCase(name)).findFirst().orElse(null) : null;
	}

	public static <A, B> B as(final A obj, final Class<B> type) {
		return type.isInstance(obj) ? type.cast(obj) : null;
	}

	public static <A> A defaulting(final A firstChoice, final A defaultChoice) {
		return firstChoice != null ? firstChoice : defaultChoice;
	}

	@SafeVarargs
	public static <A> A defaulting(final A firstChoice, final Supplier<A>... secondaryChoices) {
		return firstChoice != null ? firstChoice :
			stream(secondaryChoices).map(Supplier::get).filter(x -> x != null).findFirst().orElse(null);
	}

	public static <A> A defaulting(final A firstChoice, final java.util.function.Supplier<? extends A> defaultChoice) {
		return firstChoice != null ? firstChoice : defaultChoice.get();
	}

	public static <A> A or(final A a, final A b) {
		return a != null ? a : b;
	}

	private static Object autoBuildDisablingLock = new Object();

	public static void runWithoutAutoBuild(final Runnable runnable) {
		synchronized (autoBuildDisablingLock) {
			final IWorkspace workspace = ResourcesPlugin.getWorkspace();
			final IWorkspaceDescription workspaceDescription = workspace.getDescription();
			final boolean autoBuilding = workspaceDescription.isAutoBuilding();
			workspaceDescription.setAutoBuilding(false);
			try {
				workspace.setDescription(workspaceDescription);
			} catch (final CoreException e2) {
				e2.printStackTrace();
			}
			try {
				runnable.run();
			} finally {
				workspaceDescription.setAutoBuilding(autoBuilding);
				try {
					workspace.setDescription(workspaceDescription);
				} catch (final CoreException e) {
					e.printStackTrace();
				}
			}
		}
	}

	public static void removeRecursively(final File f) {
		if (f.isDirectory()) {
			stream(f.listFiles()).forEach(Utilities::removeRecursively);
		}
		f.delete();
	}

	public static void abort(final int severity, final String message, final Throwable nested) throws CoreException {
		throw new CoreException(new Status(severity, Core.PLUGIN_ID, message, nested));
	}

	/** Helper for throwing CoreException objects */
	public static void abort(final int severity, final String message) throws CoreException {
		throw new CoreException(new Status(severity, Core.PLUGIN_ID, message));
	}

	@SuppressWarnings("unchecked")
	public static <T extends Herbert<? super T>> T clone(T item) {
		return (T)item.clone();
	}

	@FunctionalInterface
	public interface ThrowHappy<T, E extends Exception> {
		void accept(T item) throws E;
	}

	@FunctionalInterface
	public interface ThrowHappySupplier<T, E extends Exception> {
		T get() throws E;
	}

	/**
	 * Consumes an exception which occurs in a context described by the first argument.
	 * @author madeen
	 *
	 * @param <T> Type of context
	 * @param <E> Type of exception
	 */
	@FunctionalInterface
	public interface ContextualExceptionConsumer<T, E extends Exception> {
		void accept(T context, E exception);
	}

	public interface ThrowHappyRunnable<E extends Exception> {
		void run() throws E;
	}

	public static void unexpectedException(Exception e) {
		throw new RuntimeException(e);
	}

	@SuppressWarnings("unchecked")
	public static <T, E extends Exception> Consumer<? super T> splittingException(
		ThrowHappy<? super T, E> throwing,
		Class<E> expectedException,
		Consumer<E> exceptionHandler
	) {
		return item -> {
			try {
				throwing.accept(item);
			} catch (final Exception e) {
				if (expectedException.isInstance(e)) {
					exceptionHandler.accept((E)e);
				} else {
					unexpectedException(e);
				}
			}
		};
	}

	public static <T, E extends Exception> Consumer<? super T> printingException(
		ThrowHappy<? super T, E> throwing,
		Class<E> expectedException
	) {
		return splittingException(throwing, expectedException, Exception::printStackTrace);
	}

	public static <T> Stream<T> walk(T start, Predicate<T> condition, Function<T, T> next) {
		return StreamSupport.stream(new Spliterator<T>() {

			private T current = start;

			@Override
			public boolean tryAdvance(Consumer<? super T> action) {
				if (current != null) {
					action.accept(current);
					do {
						current = next.apply(current);
					} while (current != null && !condition.test(current));
					return true;
				} else {
					return false;
				}
			}

			@Override
			public Spliterator<T> trySplit() { return null; }
			@Override
			public long estimateSize() { return 0; }
			@Override
			public int characteristics() { return Spliterator.IMMUTABLE|Spliterator.NONNULL; }

		}, false);
	}

	public static <T> Stream<T> walk(T start, Function<T, T> next) {
		return walk(start, quak -> true, next);
	}

	public static <T> T synchronizing(Object lock, Supplier<T> supplier) {
		synchronized (lock) {
			return supplier.get();
		}
	}

	public static <E extends Exception> ThrowHappySupplier<Void, E> voidResult(ThrowHappyRunnable<E> runnable) {
		return () -> {
			runnable.run();
			return null;
		};
	}

	/**
	 * Given a consumer which potentially throws an exception, return a consumer which forwards to the original one and
	 * channels exceptions to an exception consumer when they occur.
	 * @param throwHappyConsumer The consumer which might throw an exception
	 * @param exceptionConsumer The exception consumer
	 * @return A consumer which does not throw exceptions, rather channels exceptions from underlying consumer to an exception consumer.
	 */
	@SuppressWarnings("unchecked")
	public static <T, E extends Exception> Consumer<T> consumingException(ThrowHappy<T, E> throwHappyConsumer, ContextualExceptionConsumer<T, E> exceptionConsumer) {
		return item -> {
			try {
				throwHappyConsumer.accept(item);
			} catch (final Exception exception) {
				exceptionConsumer.accept(item, (E)exception);
			}
		};
	}

	public static <T, E extends Exception> T attempt(ThrowHappySupplier<T, E> sup, Class<E> expectedException, Consumer<E> exceptionHandler) {
		try {
			return sup.get();
		} catch (final Exception e) {
			return tryHandleException(expectedException, exceptionHandler, e);
		}
	}

	public static <T> T attempt(ThrowHappySupplier<T, Exception> sup) {
		return attempt(sup, Exception.class, Exception::printStackTrace);
	}

	public interface ThrowHappyFunction<I, O, E extends Exception> {
		O apply(I input) throws E;
	}

	public static <I, O, E extends Exception> Function<I, O> attempt(
		ThrowHappyFunction<I, O, E> fun,
		Class<E> expectedException,
		Consumer<E> exceptionHandler
	) {
		return input -> {
			try {
				return fun.apply(input);
			} catch (final Exception e) {
				return tryHandleException(expectedException, exceptionHandler, e);
			}
		};
	}

	public static <I, O> Function<I, O> attempt(
		ThrowHappyFunction<I, O, Exception> fun
	) {
		return attempt(fun, Exception.class, Exception::printStackTrace);
	}

	@SuppressWarnings("unchecked")
	private static <O, E extends Exception> O tryHandleException(Class<E> expectedException, Consumer<E> exceptionHandler, Exception e) {
		if (expectedException.isInstance(e)) {
			exceptionHandler.accept((E)e);
		} else {
			unexpectedException(e);
		}
		return null;
	}

	public static <T extends AutoCloseable, E extends Exception, O> O attemptWithResource(
		ThrowHappySupplier<T, E> resourceSupplier,
		Function<T, O> consumer,
		Class<E> expectedException,
		Consumer<E> exceptionHandler
	) {
		try (T res = resourceSupplier.get()) {
			return consumer.apply(res);
		} catch (final Exception e) {
			return tryHandleException(expectedException, exceptionHandler, e);
		}
	}

	public static <T, E extends Throwable> T thro(E e) throws E { throw e; }

	@SafeVarargs
	public static <T> Stream<T> one(T... item) {
		return stream(item);
	}

	public static <T> T block(Supplier<T> sup) { return sup.get(); }

	@SuppressWarnings("unchecked")
	public static <T> Stream<T> flatten(Class<T> cls, Object... items) {
		return stream(items).flatMap(item -> {
			if (cls.isInstance(item)) {
				return one((T)item);
			}
			if (item instanceof Stream) {
				return (Stream<T>)item;
			}
			return Stream.empty();
		});
	}

	public static <K, V> V getOrAdd(Map<K, V> dictionary, K key, Supplier<V> creation) {
		return defaulting(dictionary.get(key), () -> {
			final V n = creation.get();
			dictionary.put(key, n);
			return n;
		});
	}

	public static <T, K> Map<K, List<T>> bucketize(Stream<T> list, Function<T, K> keySelector) {
		final HashMap<K, List<T>> result = new HashMap<>();
		list.forEach(e -> {
			final K k = keySelector.apply(e);
			if (k == null) {
				return;
			}
			final List<T> bucket = getOrAdd(result, k, LinkedList<T>::new);
			bucket.add(e);
		});
		return result;
	}

	public static <I, O> Function<I, O> memoize(Function<I, O> f) {
		final HashMap<I, O> m = new HashMap<>();
		return k -> synchronizing(m, () -> getOrAdd(m, k, () -> f.apply(k)));
	}

}