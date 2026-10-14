package java.lang.invoke;

/**
 * Compile-time only: android.jar lacks this class, but javac needs it to emit lambdas,
 * which d8 then desugars. Never dexed.
 */
public final class LambdaMetafactory {
    public static final int FLAG_SERIALIZABLE = 1, FLAG_MARKERS = 2, FLAG_BRIDGES = 4;

    public static CallSite metafactory(MethodHandles.Lookup caller, String name, MethodType type, MethodType samType,
                                       MethodHandle impl, MethodType instantiatedType) {
        throw new UnsupportedOperationException();
    }

    public static CallSite altMetafactory(MethodHandles.Lookup caller, String name, MethodType type, Object... args) {
        throw new UnsupportedOperationException();
    }
}
