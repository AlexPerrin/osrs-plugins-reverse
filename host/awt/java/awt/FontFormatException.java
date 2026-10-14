package java.awt;

/** Android shim of java.awt.FontFormatException. */
public class FontFormatException extends Exception {
    public FontFormatException(String reason) { super(reason); }
}
