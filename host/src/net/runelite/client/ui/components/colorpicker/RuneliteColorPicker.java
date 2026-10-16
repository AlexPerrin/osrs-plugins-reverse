package net.runelite.client.ui.components.colorpicker;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Window;
import java.util.function.Consumer;
import javax.swing.JDialog;
import net.runelite.client.config.ConfigManager;

/**
 * Same-name replacement: RuneLite's Swing colour picker has no window on Android, so
 * setVisible(true) opens an Android colour dialog (net.runelite.host.ColorPrompt) instead.
 * Picking a colour calls onColorChange then onClose on the game thread, as closing the
 * desktop picker does; Cancel calls nothing.
 */
public class RuneliteColorPicker extends JDialog {
    static final String CONFIG_GROUP = "colorpicker";

    private final String title;
    private final boolean alphaHidden;
    private Color selectedColor;
    private Consumer<Color> onColorChange;
    private Consumer<Color> onClose;

    RuneliteColorPicker(Window parent, Color previousColor, String title, boolean alphaHidden,
                        ConfigManager configManager, ColorPickerManager colorPickerManager) {
        this.title = title;
        this.alphaHidden = alphaHidden;
        this.selectedColor = previousColor != null ? previousColor : Color.WHITE;
    }

    public Color getSelectedColor() {
        return selectedColor;
    }

    public void setOnColorChange(Consumer<Color> onColorChange) {
        this.onColorChange = onColorChange;
    }

    public void setOnClose(Consumer<Color> onClose) {
        this.onClose = onClose;
    }

    @Override
    public void setLocationRelativeTo(Component c) {
    }

    @Override
    public void setVisible(boolean visible) {
        if (!visible) return;
        net.runelite.host.ColorPrompt.show(title, selectedColor, !alphaHidden, c -> {
            selectedColor = c;
            if (onColorChange != null) onColorChange.accept(c);
            if (onClose != null) onClose.accept(c);
        });
    }

    static String getReplacedText(javax.swing.text.DocumentFilter.FilterBypass fb, int offset, int length, String str)
            throws javax.swing.text.BadLocationException {
        javax.swing.text.Document doc = fb.getDocument();
        StringBuilder sb = new StringBuilder(doc.getText(0, doc.getLength()));
        sb.replace(offset, offset + length, str);
        return sb.toString();
    }
}
