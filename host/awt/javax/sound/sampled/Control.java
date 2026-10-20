package javax.sound.sampled;

/** Android shim of javax.sound.sampled.Control. */
public abstract class Control {
    private final Type type;

    protected Control(Type type) { this.type = type; }

    public Type getType() { return type; }

    @Override public String toString() { return type + " control"; }

    public static class Type {
        private final String name;

        protected Type(String name) { this.name = name; }

        @Override public final String toString() { return name; }
    }
}
