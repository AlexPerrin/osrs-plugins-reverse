package javax.sound.sampled;

/** Android shim of javax.sound.sampled.FloatControl: a value clamped to [min, max]. */
public abstract class FloatControl extends Control {
    private final float minimum, maximum, precision;
    private final String units;
    private float value;

    protected FloatControl(Type type, float minimum, float maximum, float precision, int updatePeriod,
                           float initialValue, String units) {
        super(type);
        this.minimum = minimum;
        this.maximum = maximum;
        this.precision = precision;
        this.units = units;
        this.value = initialValue;
    }

    public void setValue(float newValue) {
        if (newValue > maximum || newValue < minimum)
            throw new IllegalArgumentException("Requested value " + newValue + " outside [" + minimum + ", " + maximum + "]");
        value = newValue;
    }
    public float getValue() { return value; }
    public float getMaximum() { return maximum; }
    public float getMinimum() { return minimum; }
    public float getPrecision() { return precision; }
    public String getUnits() { return units; }

    public static class Type extends Control.Type {
        public static final Type MASTER_GAIN = new Type("Master Gain");
        public static final Type AUX_SEND = new Type("AUX Send");
        public static final Type AUX_RETURN = new Type("AUX Return");
        public static final Type REVERB_SEND = new Type("Reverb Send");
        public static final Type REVERB_RETURN = new Type("Reverb Return");
        public static final Type VOLUME = new Type("Volume");
        public static final Type PAN = new Type("Pan");
        public static final Type BALANCE = new Type("Balance");
        public static final Type SAMPLE_RATE = new Type("Sample Rate");

        protected Type(String name) { super(name); }
    }
}
