package com.yohann.ocihelper.bean;

/**
 * <p>
 * Tuple2
 * </p >
 *
 * @author yohann
 * @since 2024/4/12 11:04
 */
public class Tuple2<T1, T2> {
    private final T1 first;
    private final T2 second;

    public Tuple2(T1 first, T2 second) {
        this.first = first;
        this.second = second;
    }

    public T1 getFirst() {
        return this.first;
    }

    public T2 getSecond() {
        return this.second;
    }

    public static <T1, T2> Tuple2<T1, T2> of(T1 t1, T2 t2) {
        return new Tuple2(t1, t2);
    }
}
