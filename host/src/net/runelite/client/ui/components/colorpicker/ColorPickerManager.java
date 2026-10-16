package net.runelite.client.ui.components.colorpicker;

import java.awt.Color;
import java.awt.Component;
import java.awt.Window;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;

/**
 * Same-name replacement: RuneLite's create(Client, ...) casts the client to a Component (the
 * desktop applet), which AndroidClient is not, and the picker itself is the Android dialog in
 * RuneliteColorPicker.
 */
@Singleton
public class ColorPickerManager {
    private final ConfigManager configManager;
    private RuneliteColorPicker currentPicker;

    @Inject
    public ColorPickerManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    void setCurrentPicker(RuneliteColorPicker currentPicker) {
        this.currentPicker = currentPicker;
    }

    RuneliteColorPicker getCurrentPicker() {
        return currentPicker;
    }

    public RuneliteColorPicker create(Client client, Color previousColor, String title, boolean alphaHidden) {
        return create((Window) null, previousColor, title, alphaHidden);
    }

    public RuneliteColorPicker create(Component owner, Color previousColor, String title, boolean alphaHidden) {
        return create((Window) null, previousColor, title, alphaHidden);
    }

    public RuneliteColorPicker create(Window owner, Color previousColor, String title, boolean alphaHidden) {
        currentPicker = new RuneliteColorPicker(owner, previousColor, title, alphaHidden, configManager, this);
        return currentPicker;
    }
}
