package net.arctics.clonk.util;

import static java.util.Arrays.stream;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import net.arctics.clonk.util.Sink.Decision;

public class ArrayUtil {

	@SafeVarargs
	public static <T> boolean allMatch(Predicate<T> pred, T... items) {
		return stream(items).allMatch(pred);
	}

	/** Concat a list of arrays into a large array */
	public static <T> T[] concat(Class<T> type, final Collection<T[]> chain) {
		final int requiredLength = chain.stream().mapToInt(a -> a.length).reduce(0, (result, length) -> result + length);
		@SuppressWarnings("unchecked")
		final T[] result = (T[]) Array.newInstance(type, requiredLength);
		int at = 0;
		for (final T[] array : chain) {
			System.arraycopy(array, 0, result, at, array.length);
			at += array.length;
		}
		return result;
	}

	@SafeVarargs
	@SuppressWarnings("unchecked")
	public static <T> T[] concat(final T[] a, final T... b) {
		final int alen = a != null ? a.length : 0;
		final int blen = b != null ? b.length : 0;
		if (alen == 0) {
			return b != null ? b : (T[])new Object[0];
		}
		if (blen == 0) {
			return a != null ? a : (T[])new Object[0];
		}
		final T[] result = (T[]) Array.newInstance(Utilities.baseClass(
			a.getClass().getComponentType(),
			b.getClass().getComponentType()
		), alen+blen);
		System.arraycopy(a, 0, result, 0, alen);
		System.arraycopy(b, 0, result, alen, blen);
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] concat(final T first, final T... rest) {
		final T[] result = (T[]) Array.newInstance(rest.getClass().getComponentType(), 1+rest.length);
		result[0] = first;
		for (int i = 0; i < rest.length; i++) {
			result[1+i] = rest[i];
		}
		return result;
	}

	public static <T, B extends T> T[] arrayRange(final B[] source, final int start, final int length, final Class<T> elementClass) {
		@SuppressWarnings("unchecked")
		final
		T[] result = (T[]) Array.newInstance(elementClass, length);
		for (int i = 0; i < length; i++) {
			result[i] = source[start+i];
		}
		return result;
	}

	public static <T> T firstOrNull(final T[] arr) {
		return arr != null && arr.length > 0 ? arr[0] : null;
	}

	public static <T> T[] removeNullElements(T[] array, final Class<T> cls) {
		int actualCount = 0;
		for (final T t : array) {
			if (t != null) {
				actualCount++;
			}
		}
		if (actualCount != array.length) {
			@SuppressWarnings("unchecked")
			final
			T[] nonNullIngredients = (T[])Array.newInstance(cls, actualCount);
			actualCount = 0;
			for (final T t : array) {
				if (t != null) {
					nonNullIngredients[actualCount++] = t;
				}
			}
			array = nonNullIngredients;
		}
		return array;
	}

	public static <T> T[] removeElement(T[] array, int ndx) {
		if (ndx < 0 || ndx >= array.length) {
			throw new IllegalArgumentException();
		}
		@SuppressWarnings("unchecked")
		final T[] result = (T[]) Array.newInstance(array.getClass().getComponentType(), array.length-1);
		System.arraycopy(array, 0, result, 0, ndx);
		System.arraycopy(array, ndx+1, result, ndx, array.length-ndx-1);
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <E> ArrayList<E> list(final E... elements) {
		final ArrayList<E> result = new ArrayList<E>(elements.length);
		for (final E e : elements) {
			result.add(e);
		}
		return result;
	}

	@SuppressWarnings("unchecked")
	public static <T> T[] convertArray(final Object[] baseArray, final Class<T> newElementClass) {
		final T[] result = (T[]) Array.newInstance(newElementClass, baseArray.length);
		System.arraycopy(baseArray, 0, result, 0, baseArray.length);
		return result;
	}

	public static <T> Collector<T, LinkedList<T>, T[]> toArray(Class<T> cls) {
		return new Collector<T, LinkedList<T>, T[]>() {
			final Set<Characteristics> characteristics = EnumSet.noneOf(Characteristics.class);
			@Override
			public Supplier<LinkedList<T>> supplier() { return () -> new LinkedList<T>(); }
			@Override
			public BiConsumer<LinkedList<T>, T> accumulator() { return LinkedList<T>::add; }
			@Override
			public BinaryOperator<LinkedList<T>> combiner() { return (a, b) -> { a.addAll(b); return a; }; }
			@SuppressWarnings("unchecked")
			@Override
			public Function<LinkedList<T>, T[]> finisher() { return l -> l.toArray((T[]) Array.newInstance(cls, l.size())); }
			@Override
			public Set<java.util.stream.Collector.Characteristics> characteristics() { return characteristics; }
		};
	}

	@SuppressWarnings("unchecked")
	public static <T, C> C[] filter(final Iterable<T> iterable, final Class<C> cls) {
		final LinkedList<C> result = new LinkedList<C>();
		for (final T item : iterable) {
			if (cls.isAssignableFrom(item.getClass())) {
				result.add((C) item);
			}
		}
		return result.toArray((C[]) Array.newInstance(cls, result.size()));
	}

	@SuppressWarnings("unchecked")
	public static <A, B> B[] filter(final A[] array, final Class<B> cls) {
		final List<B> items = new ArrayList<B>(array.length);
		for (final A item : array) {
			if (cls.isAssignableFrom(item.getClass())) {
				items.add((B)item);
			}
		}
		return items.toArray((B[]) Array.newInstance(cls, items.size()));
	}

	@SuppressWarnings("unchecked")
	public static <T> Iterable<T> iterable(final T... items) {
		return () -> new Iterator<T>() {
			private int index = -1;
			@Override
			public boolean hasNext() {
				for (int i = index+1; i < items.length; i++) {
					if (items[i] != null) {
						return true;
					}
				}
				return false;
			}
			@Override
			public T next() {
				for (index++; index < items.length; index++) {
					if (items[index] != null) {
						return items[index];
					}
				}
				return null;
			}
			@Override
			public void remove() {}
		};
	}

	public static <T> T boundChecked(final T[] items, final int index) {
		return index >= 0 && index < items.length ? items[index] : null;
	}

	public static <T> int indexOf(final T item, final T[] items) {
		for (int index = 0; index < items.length; index++) {
			if (items[index] == item) {
				return index;
			}
		}
		return -1;
	}

	public static <T> int indexOfItemSatisfying(final Iterable<T> items, Predicate<T> pred) {
		int i = 0;
		for (final T item : items) {
			if (pred.test(item)) {
				return i;
			} else {
				i++;
			}
		}
		return -1;
	}

	@SuppressWarnings("unchecked")
	public static <From, To> To[] map(final From[] elms, final Class<To> toClass, final Function<From, To> converter) {
		final To[] result = (To[]) Array.newInstance(toClass, elms.length);
		for (int i = 0; i < result.length; i++) {
			result[i] = converter.apply(elms[i]);
		}
		return result;
	}

	public static <T> Map<T, Integer> mapValueToIndex(final T[] items) {
		final Map<T, Integer> result = new HashMap<T, Integer>();
		for (int i = 0; i < items.length; i++) {
			result.put(items[i], i);
		}
		return result;
	}

	public static <From, To> Stream<To> map(final Iterable<? extends From> source, final Function<From, To> converter) {
		return StreamSupport.stream(source.spliterator(), false).map(converter);
	}

	/**
	 * Helper for creating a map with one assignment
	 * @param <KeyType> key type for resulting map
	 * @param <ValueType> value type for resulting map
	 * @param mapClass class the method is to instantiate
	 * @param keysAndValues array containing keys and values. keys are at even indices while values are at uneven ones
	 * @return the map
	 */
	@SuppressWarnings("unchecked")
	public static <KeyType, ValueType> Map<KeyType, ValueType> mapOfType(final boolean modifiable, final Map<KeyType, ValueType> resultMap, final Object... keysAndValues) {
		try {
			for (int i = 0; i < keysAndValues.length-1; i += 2) {
				resultMap.put((KeyType)keysAndValues[i], (ValueType)keysAndValues[i+1]);
			}
			return modifiable ? resultMap : Collections.unmodifiableMap(resultMap);
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	public static <KeyType, ValueType> Map<ValueType, KeyType> reverseMap(final Map<KeyType, ValueType> originalMap, final Map<ValueType, KeyType> resultMap) {
		try {
			for (final Map.Entry<KeyType, ValueType> entry : originalMap.entrySet()) {
				resultMap.put(entry.getValue(), entry.getKey());
			}
			return resultMap;
		} catch (final Exception e) {
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * like mapOfType, but called with HashMap.class
	 * @param <KeyType>
	 * @param <ValueType>
	 * @param keysAndValues
	 * @return
	 */
	public static <KeyType, ValueType> Map<KeyType, ValueType> map(final boolean modifiable, final Object... keysAndValues) {
		return mapOfType(modifiable, new HashMap<KeyType, ValueType>(), keysAndValues);
	}

	public static <A, B> Iterable<B> filteredIterable(final Iterable<A> base, final Class<B> cls) {
		return new FilteredIterable<B, A>(cls, base, false);
	}

	// nowhere to be found oO
	/**
	 * Return the index of an item in an array
	 */
	public static <T> int indexOf(final T[] items, final T item) {
		for (int i = 0; i < items.length; i++) {
			if (Utilities.eq(items[i], item)) {
				return i;
			}
		}
		return -1;
	}

	public static void purgeNullEntries(final Collection<?>... collections) {
		for (final Collection<?> c : collections) {
			if (c != null) {
				c.removeAll(Collections.singletonList(null));
			}
		}
	}

	public static <T> Set<T> set(@SuppressWarnings("unchecked") final T... items) {
		final HashSet<T> t = new HashSet<T>();
		for (final T i : items) {
			t.add(i);
		}
		return t;
	}

	public static <T> Set<? extends T> setFromIterable(final Iterable<T> iterable) {
		final Set<T> set = new HashSet<T>();
		for (final T d : iterable) {
			set.add(d);
		}
		return set;
	}

	public static <T extends Comparable<? super T>> List<T> asSortedList(final Collection<T> c) {
		final List<T> list = new ArrayList<T>(c);
		Collections.sort(list);
		return list;
	}

	public static <T> List<T> listFromIterable(final Iterable<T> iterable) {
		final LinkedList<T> result = new LinkedList<T>();
		for (final T t : iterable) {
			result.add(t);
		}
		return result;
	}

	public static <T> List<? extends T> copyListOrReturnDefaultList(final Collection<? extends T> list, final List<? extends T> defaultResult) {
		if (list == null) {
			return defaultResult;
		} else {
			synchronized (list) {
				return new ArrayList<T>(list);
			}
		}
	}

	public static <T> void addAllSynchronized(final Collection<? extends T> list, final List<T> into, Object lock) {
		if (lock == null) {
			lock = list;
		}
		if (list != null && lock != null) {
			synchronized (lock) {
				into.addAll(list);
			}
		}
	}

	public static <T> List<T> list(final Iterable<? extends T> iterable) {
		final List<T> result = new ArrayList<T>();
		for (final T i : iterable) {
			result.add(i);
		}
		return result;
	}

	public static <T> Decision sink(final Iterable<? extends T> iterable, final Sink<T> sink) {
		final Iterator<? extends T> it = iterable.iterator();
		while (it.hasNext()) {
			switch (sink.elutriate(it.next())) {
			case PurgeItem:
				it.remove();
				break;
			case AbortIteration:
				return Decision.AbortIteration;
			default:
				break;
			}
		}
		return Decision.Continue;
	}

	public static <T> Sink<? super T> collectionSink(final Collection<? super T> collection) {
		return collection::add;
	}

	public static <T> boolean same(T[] a, T[] b, BiFunction<T, T, Boolean> comp) {
		return (
			a.length == b.length &&
			IntStream.range(0, a.length).allMatch(x -> comp.apply(a[x], b[x]))
		);
	}

	@SuppressWarnings("unchecked")
	@SafeVarargs
	public static <T> T[] nonNulls(T... items) {
		return stream(items)
			.filter(x -> x != null)
			.toArray(l -> (T[])Array.newInstance(items.getClass().getComponentType(), l));
	}

}
