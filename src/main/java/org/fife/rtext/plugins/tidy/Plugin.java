/*
 * 03/22/2010
 *
 * Plugin.java - Plugin that "tidies" source code.
 * Copyright (C) 2010 Robert Futrell
 * http://fifesoft.com/rtext
 * Licensed under a modified BSD license.
 * See the included license file for details.
 */
package org.fife.rtext.plugins.tidy;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.util.Properties;
import java.util.ResourceBundle;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JMenu;
import javax.swing.JMenuItem;

import org.fife.rtext.CurrentTextAreaEvent;
import org.fife.rtext.CurrentTextAreaListener;
import org.fife.rtext.RText;
import org.fife.rtext.RTextEditorPane;
import org.fife.rtext.RTextMenuBar;
import org.fife.rtext.RTextUtilities;
import org.fife.ui.OS;
import org.fife.ui.app.AbstractPluggableGUIApplication;
import org.fife.ui.app.AbstractPlugin;
import org.fife.ui.app.PluginOptionsDialogPanel;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.fife.ui.rtextarea.RTextAreaOptionPanel;


/**
 * A plugin that "tidies" source code. This code calls into other libraries
 * for its functionality.
 *
 * @author Robert Futrell
 * @version 1.0
 */
public class Plugin extends AbstractPlugin
		implements CurrentTextAreaListener {

	private final RText rtext;
	private TidyAction action;
	private Icon icon;

	private HtmlOptions htmlOptions;
	private XmlOptions xmlOptions;
	private JsonOptions jsonOptions;

	private static final String PLUGIN_VERSION			= "4.0.0";

	private static final String TIDY_ACTION = "PrettyPrintAction";

	private static final String MSG_BUNDLE = "org.fife.rtext.plugins.tidy.Plugin";

	/**
	 * The resource bundle used across this plugin.
	 */
	static final ResourceBundle MSG = ResourceBundle.getBundle(MSG_BUNDLE);


	/**
	 * Constructor.
	 *
	 * @param app The parent application.
	 */
	public Plugin(AbstractPluggableGUIApplication<?> app) {
		this.rtext = (RText)app; // Needed in loadPreferences if error
	}


	/**
	 * Listens for text area events.  If the active text area changes, or the
	 * active text area's syntax style changes, we re-evaluate whether this
	 * action should be active.
	 *
	 * @param e The event.
	 */
	@Override
	public void currentTextAreaPropertyChanged(CurrentTextAreaEvent e) {
		if (e.getType()==CurrentTextAreaEvent.TEXT_AREA_CHANGED ||
				e.getType()==CurrentTextAreaEvent.SYNTAX_STYLE_CNANGED) {
			possiblyEnableAction();
		}
	}


	/**
	 * Attempts to delete older versions' preferences (&lt;= RText 2.06),
	 * since this plugin has consolidated all preferences into a single file.
	 */
	private static void deleteOldVersionTidyPreferenceFiles() {

		File oldPrefsDir = new File(RTextUtilities.getPreferencesDirectory(),
				"tidy");
		if (oldPrefsDir.isDirectory()) {

			boolean success = true;
			File[] propFiles = oldPrefsDir.listFiles();
			for (File propFile : propFiles) {
				success &= propFile.delete();
			}

			if (success) {
				oldPrefsDir.delete();
			}

		}

	}


	/**
	 * Returns the options related to tidying HTML.
	 *
	 * @return The HTML options.
	 */
	public HtmlOptions getHtmlOptions() {
		return htmlOptions;
	}


	/**
	 * Returns the options related to tidying JSON.
	 *
	 * @return The JSON options.
	 */
	public JsonOptions getJsonOptions() {
		return jsonOptions;
	}


	@Override
	public PluginOptionsDialogPanel<Plugin> getOptionsDialogPanel() {
		return new OptionsPanel(this);
	}


	/**
	 * This panel should be parented under the main text editor panel.
	 *
	 * @return The parent panel ID.
	 */
	@Override
	public String getOptionsDialogPanelParentPanelID() {
		return RTextAreaOptionPanel.OPTION_PANEL_ID;
	}


	@Override
	public String getPluginAuthor() {
		return "Robert Futrell";
	}


	@Override
	public Icon getPluginIcon(boolean darkLookAndFeel) {
		if (icon==null) {
			URL url = getClass().getResource("lightning.png");
			if (url!=null) { // Should always be true
				try {
					icon = new ImageIcon(ImageIO.read(url));
				} catch (IOException ioe) {
					rtext.displayException(ioe);
				}
			}
		}
		return icon;
	}


	@Override
	public String getPluginName() {
		return MSG.getString("Name");
	}


	@Override
	public String getPluginVersion() {
		return PLUGIN_VERSION;
	}


	/**
	 * Returns the file preferences for this plugin are saved in.
	 *
	 * @return The file.
	 */
	private static File getPrefsFile() {
		return new File(RTextUtilities.getPreferencesDirectory(),
						"tidy.properties");
	}


	/**
	 * Returns the parent application.
	 *
	 * @return The parent application.
	 */
	RText getRText() {
		return rtext;
	}


	/**
	 * Returns the options related to tidying XML.
	 *
	 * @return The XML tidying-options.
	 */
	public XmlOptions getXmlOptions() {
		return xmlOptions;
	}


	@Override
	public void install(AbstractPluggableGUIApplication<?> app) {

		PluginPrefs prefs = loadPreferences();

		RTextMenuBar mb = (RTextMenuBar)rtext.getJMenuBar();
		JMenu menu = mb.getMenuByName(RTextMenuBar.MENU_EDIT);

		action = new TidyAction((RText)app, this);
		action.setAccelerator(prefs.tidyActionAccelerator);
		rtext.addAction(TIDY_ACTION, action);
		action.setEnabled(false); // Gets enabled for appropriate files below
		possiblyEnableAction();
		JMenuItem item = new JMenuItem(action);
		item.setToolTipText(null);

		// On OS X, the "Options" menu item is in the application menu, not
		// in the Edit menu.
		if (rtext.getOS()==OS.MAC_OS_X) {
			menu.addSeparator();
			menu.add(item);
		}
		else {
			int index = menu.getMenuComponentCount() - 2;
			menu.insert(item, index);
			menu.insertSeparator(index);
		}

		rtext.getMainView().addCurrentTextAreaListener(this);

	}


	/**
	 * Returns whether the specified language can be pretty printed by this
	 * plugin.
	 *
	 * @param style The language.
	 * @return Whether the language can be pretty printed.
	 */
	private static boolean isSupportedLanguage(String style) {
		return SyntaxConstants.SYNTAX_STYLE_HTML.equals(style) ||
				SyntaxConstants.SYNTAX_STYLE_XML.equals(style) ||
				SyntaxConstants.SYNTAX_STYLE_JSON.equals(style);
	}


	/**
	 * Loads our tidying preferences.
	 */
	private PluginPrefs loadPreferences() {

		PluginPrefs prefs = new PluginPrefs();
		htmlOptions = new HtmlOptions();
		xmlOptions = new XmlOptions();
		jsonOptions = new JsonOptions();

		File prefsFile = getPrefsFile();
		if (prefsFile.isFile()) {

			try {

				Properties props = new Properties();
				try (BufferedInputStream bin = new BufferedInputStream(
					new FileInputStream(prefsFile))) {
					props.load(bin);
				}

				htmlOptions.load(props);
				xmlOptions.load(props);
				jsonOptions.load(props);
				prefs.load(props);

			} catch (IOException ioe) {
				rtext.displayException(ioe);
			}

		}

		return prefs;

	}


	private void possiblyEnableAction() {
		RTextEditorPane textArea = rtext.getMainView().getCurrentTextArea();
		if (textArea != null) { // Possibly null on startup
			String style = textArea.getSyntaxEditingStyle();
			action.setEnabled(isSupportedLanguage(style));
		}
	}


	@Override
	public void savePreferences() {

		deleteOldVersionTidyPreferenceFiles();

		File prefsFile = getPrefsFile();
		if (!prefsFile.getParentFile().isDirectory()) {
			prefsFile.getParentFile().mkdirs();
		}

		try {

			Properties props = new Properties();
			getHtmlOptions().save(props);
			getXmlOptions().save(props);
			getJsonOptions().save(props);

			PluginPrefs prefs = new PluginPrefs();
			prefs.tidyActionAccelerator = action.getAccelerator();
			prefs.save(props);

			try (BufferedOutputStream out = new BufferedOutputStream(
				new FileOutputStream(prefsFile))) {
				props.store(out,
					"Preferences for the tidy/pretty-print plugin");
			}

		} catch (IOException ioe) {
			rtext.displayException(ioe);
		}

	}


	@Override
	public boolean uninstall() {
		return true;
	}


}
