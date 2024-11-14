package com.yohann.ocihelper.bean;

/**
 * <p>
 * Tuple3
 * </p >
 *
 * @author yohann
 * @since 2024/4/12 11:08
 */
public class Tuple3<T1, T2, T3> {
    private final T1 first;
    private final T2 second;
    private final T3 third;

    public Tuple3(T1 first, T2 second, T3 third) {
        this.first = first;
        this.second = second;
        this.third = third;
    }

    public T1 getFirst() {
        return this.first;
    }

    public T2 getSecond() {
        return this.second;
    }

    public T3 getThird() {
        return this.third;
    }

    public static <T1, T2, T3> Tuple3<T1, T2, T3> of(T1 t1, T2 t2, T3 t3) {
        return new Tuple3(t1, t2, t3);
    }
}
