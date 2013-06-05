package com.frostwire.gui.theme;

/**
 * This interface outlines the methods of classes that wish to be notified of
 * theme changes.  This is required, for example, when classes have include
 * images that need to be updated when a theme is changed.
 */
public interface ThemeObserver {

	/**
	 * Update any required theme settings, such as colors or images.
	 */
	public void updateTheme();
}