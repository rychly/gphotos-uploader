package io.gitlab.rychly.gphotos_uploader.tuples;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

/**
 * Tuple as a triplet.
 *
 * @param <I1> a class of the first item in the tuple
 * @param <I2> a class of the second item in the tuple
 * @param <I2> a class of the third item in the tuple
 */
public abstract class Tuple3<I1, I2, I3> {
    /**
     * Create a new tuple.
     *
     * @param i1   an object of the first item in the tuple
     * @param i2   an object of the second item in the tuple
     * @param i3   an object of the third item in the tuple
     * @param <I1> a class of the first item in the tuple
     * @param <I2> a class of the second item in the tuple
     * @param <I3> a class of the third item in the tuple
     * @return the tuple
     */
    @NotNull
    @Contract(value = "msg, msg -> new", pure = true)
    public static <I1, I2, I3> Tuple3<I1, I2, I3> makeTuple(final I1 i1, final I2 i2, final I3 i3) {
        return new Tuple3<I1, I2, I3>() {
            public I1 i1() {
                return i1;
            }

            public I2 i2() {
                return i2;
            }

            public I3 i3() {
                return i3;
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

    /**
     * Get an object of the third item in the tuple.
     *
     * @return the object of the third item in the tuple
     */
    public abstract I3 i3();
}
