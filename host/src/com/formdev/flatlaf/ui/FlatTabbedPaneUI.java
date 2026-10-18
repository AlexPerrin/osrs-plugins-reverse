package com.formdev.flatlaf.ui;

import java.awt.Component;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.awt.event.MouseListener;
import javax.swing.JComponent;
import javax.swing.JTabbedPane;
import javax.swing.plaf.ComponentUI;

/**
 * Host stand-in for FlatLaf's tabbed pane UI (FlatLaf isn't in the host). RuneLite's
 * RuneLiteTabbedPaneUI extends it, and plugins subclass that for their JTabbedPanes (Slayer
 * Assistant's TaskTabs). The sidebar lays tabs out itself, so this only carries the protected
 * members RuneLiteTabbedPaneUI and its subclasses use (BasicTabbedPaneUI's), with inert bodies.
 */
public class FlatTabbedPaneUI extends javax.swing.plaf.TabbedPaneUI {
    protected JTabbedPane tabPane;
    protected Rectangle[] rects = new Rectangle[0];
    protected int[] tabRuns = new int[10];
    protected int runCount, selectedRun, maxTabWidth, maxTabHeight;

    public FlatTabbedPaneUI() {}

    public static ComponentUI createUI(JComponent c) { return new FlatTabbedPaneUI(); }

    public void installUI(JComponent c) { if (c instanceof JTabbedPane) tabPane = (JTabbedPane) c; }
    public void uninstallUI(JComponent c) { tabPane = null; }

    public int tabForCoordinate(JTabbedPane pane, int x, int y) { return -1; }
    public Rectangle getTabBounds(JTabbedPane pane, int index) { return new Rectangle(); }
    public int getTabRunCount(JTabbedPane pane) { return 1; }

    protected LayoutManager createLayoutManager() { return new FlatTabbedPaneLayout(); }
    protected MouseListener createMouseListener() { return new java.awt.event.MouseAdapter() {}; }
    protected boolean shouldPadTabRun(int tabPlacement, int run) { return false; }
    protected int calculateTabWidth(int tabPlacement, int tabIndex, FontMetrics metrics) { return 0; }
    protected int calculateTabHeight(int tabPlacement, int tabIndex, int fontHeight) { return 0; }
    protected int calculateMaxTabWidth(int tabPlacement) { return 0; }
    protected int calculateMaxTabHeight(int tabPlacement) { return 0; }
    protected Insets getTabInsets(int tabPlacement, int tabIndex) { return new Insets(0, 0, 0, 0); }
    protected Insets getTabAreaInsets(int tabPlacement) { return new Insets(0, 0, 0, 0); }
    protected Insets getContentBorderInsets(int tabPlacement) { return new Insets(0, 0, 0, 0); }
    protected int preferredTabAreaHeight(int tabPlacement, int width) { return 0; }
    protected int preferredTabAreaWidth(int tabPlacement, int height) { return 0; }

    public class FlatTabbedPaneLayout implements LayoutManager {
        public FlatTabbedPaneLayout() {}
        @Override public void addLayoutComponent(String name, Component comp) {}
        @Override public void removeLayoutComponent(Component comp) {}
        @Override public Dimension preferredLayoutSize(Container parent) { return calculateSize(false); }
        @Override public Dimension minimumLayoutSize(Container parent) { return calculateSize(true); }
        @Override public void layoutContainer(Container parent) {}
        protected Dimension calculateSize(boolean minimum) { return new Dimension(0, 0); }
        protected void normalizeTabRuns(int tabPlacement, int tabCount, int start, int max) {}
    }
}
