package se.fortnox.reactivewizard.util.rx;

import rx.Observable;
import rx.functions.Func0;

import static rx.Observable.just;

/**
 * Helper class used to chain sequential work in Rx, or omit foo variables. Turns code like this:
 * <p>
 * <pre>
 * {@code
 * doStuff().flatMap(foo->empty());
 * }
 * </pre>
 * Into this:
 * <p>
 * <pre>
 * {@code
 * first(doStuff()).thenReturnEmpty();
 * }
 * </pre>
 */
public class FirstThen {

    private Observable<?> doFirst;

    private FirstThen(Observable<?> doFirst) {
        this.doFirst = doFirst;
    }

    public static FirstThen first(Observable<?> doFirst) {
        return new FirstThen(doFirst);
    }

    @SuppressWarnings("unchecked")
    private static <S> Observable<S> ignoreElements(Observable<?> toConsume) {
        return (Observable<S>)toConsume.ignoreElements();
    }

    public <T> FirstThen then(Observable<T> thenReturn) {
        return new FirstThen(ignoreElements(doFirst).<T>concatWith(thenReturn));
    }

    public <T> FirstThen then(Func0<Observable<T>> thenFn) {
        return new FirstThen(ignoreElements(doFirst).concatWith(Observable.defer(thenFn)));
    }

    public <T> Observable<T> thenReturn(Func0<Observable<T>> thenFn) {
        return thenReturn(Observable.defer(thenFn));
    }

    public <T> Observable<T> thenReturn(T thenReturn) {
        return thenReturn(just(thenReturn));
    }

    public <T> Observable<T> thenReturn(Observable<T> thenReturn) {
        return FirstThen.<T>ignoreElements(doFirst).concatWith(thenReturn);
    }

    /**
     * An empty observable, signalling success or error.
     * @return empty Observable
     */
    public <T> Observable<T> thenReturnEmpty() {
        return thenReturn(Observable.empty());
    }

}
