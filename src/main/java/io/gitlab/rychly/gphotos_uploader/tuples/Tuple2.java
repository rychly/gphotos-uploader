package io.gitlab.rychly.gphotos_uploader.tuples;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Tuple as a pair.
 *
 * @param <I1> a class of the first item in the tuple
 * @param <I2> a class of the second item in the tuple
 */
public abstract class Tuple2<I1, I2> {
    /**
     * Create a new tuple.
     *
     * @param i1   an object of the first item in the tuple
     * @param i2   an object of the second item in the tuple
     * @param <I1> a class of the first item in the tuple
     * @param <I2> a class of the second item in the tuple
     * @return the tuple
     */
    @NotNull
    @Contract(value = "msg, msg -> new", pure = true)
    public static <I1, I2> Tuple2<I1, I2> makeTuple(final I1 i1, final I2 i2) {
        return new Tuple2<I1, I2>() {
            public I1 i1() {
                return i1;
            }

            public I2 i2() {
                return i2;
            }
        };
    }

    /**
     * Get an object of the first item in the tuple.
     *
     * @return the object of the first item in the tuple
     */
    public abstract I1 i1();

    /**
     * Get an object of the second item in the tuple.
     *
     * @return the object of the second item in the tuple
     */
    public abstract I2 i2();
}
