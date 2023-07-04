package com.bencodez.advancedcore.api.yml.editor;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventoryButton;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUI;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueBoolean;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueNumber;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.yml.YMLConfig;
import com.bencodez.advancedcore.api.yml.YMLFile;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataBoolean;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataConfigurationSection;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataDouble;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataInt;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataKeys;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataListString;
import com.bencodez.advancedcore.api.yml.annotation.ConfigDataString;

public class ConfigEditor {
	private YMLFile ymlFile;
	private YMLConfig ymlConfig;
	private Class<?> ymlConfigClass;

	private ArrayList<EditGUIButton> buttons = new ArrayList<EditGUIButton>();

	public ConfigEditor(YMLFile ymlFile) {
		this.ymlFile = ymlFile;

		load();
	}

	public ConfigEditor(YMLFile ymlFile, YMLConfig ymlConfig, Class<?> ymlConfigClass) {
		this.ymlFile = ymlFile;
		this.ymlConfig = ymlConfig;
		this.ymlConfigClass = ymlConfigClass;

		load();
	}

	public void load() {
		Class<?> clazz = ymlFile.getClass();
		ConfigurationSection config = ymlFile.getData();
		addButtons(ymlFile, clazz, config);

		if (ymlConfig != null) {
			if (ymlConfigClass != null) {
				addButtons(ymlConfig, ymlConfigClass, ymlConfig.getData());
			} else {
				addButtons(ymlConfig, ymlConfig.getClass(), ymlConfig.getData());
			}
		}
	}

	public void open(Player player) {
		EditGUI inv = new EditGUI("EDIT: " + ymlFile.getdFile().getName());
		Set<String> configSections = new HashSet<String>();

		for (EditGUIButton button : buttons) {
			String[] split = button.getEditor().getKey().split("\\.");
			if (split.length > 1) {
				configSections.add(split[0]);
			} else {
				inv.addButton(button);
			}
		}
		inv.sort();

		for (String configSec : configSections) {
			inv.addButton(new BInventoryButton(new ItemBuilder(Material.CHEST).setName(configSec)) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					open(clickEvent.getPlayer(), (String) clickEvent.getButton().getData("sec"));
				}
			}.addData("sec", configSec));
		}

		inv.openInventory(player);
	}

	public void open(Player player, String sec) {
		EditGUI inv = new EditGUI("EDIT: " + ymlFile.getdFile().getName());
		Set<String> configSections = new HashSet<String>();

		for (EditGUIButton button : buttons) {
			if (button.getEditor().getKey().startsWith(sec)) {
				String[] split = button.getEditor().getKey().split("\\.");

				int arg = sec.split("\\.").length;
				AdvancedCorePlugin.getInstance().debug("" + button.getEditor().getKey() + "/" + arg);
				if (split.length > arg + 1) {
					configSections.add(sec + "." + split[arg]);
				} else {
					inv.addButton(button);
				}
			}
		}
		inv.sort();
		for (String configSec : configSections) {
			inv.addButton(new BInventoryButton(new ItemBuilder(Material.CHEST).setName(configSec)) {

				@Override
				public void onClick(ClickEvent clickEvent) {
					open(clickEvent.getPlayer(), (String) clickEvent.getButton().getData("sec"));
				}
			}.addData("sec", configSec));
		}

		inv.addButton(new BInventoryButton(new ItemBuilder(Material.BARRIER).setName("Go Back")) {

			@Override
			public void onClick(ClickEvent clickEvent) {
				open(clickEvent.getPlayer());
			}
		});

		inv.openInventory(player);
	}

	@SuppressWarnings("unchecked")
	public void addButtons(YMLFile ymlFile, Class<?> clazz, ConfigurationSection config) {

		for (Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);

				final ConfigDataString stringAnnotation = field.getAnnotation(ConfigDataString.class);
				if (stringAnnotation != null) {

					String defaultValue = stringAnnotation.defaultValue();
					if (defaultValue.isEmpty()) {
						try {
							String v = (String) field.get(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					String value = "";
					if (!stringAnnotation.secondPath().isEmpty()) {
						value = config.getString(stringAnnotation.path(),
								config.getString(stringAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getString(stringAnnotation.path(), defaultValue);
					}
					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(stringAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueString(stringAnnotation.path(), value) {

						@Override
						public void setValue(Player player, String value) {
							ymlFile.setValue(stringAnnotation.path(), value);
						}
					}.addLore(comments).addOptions(stringAnnotation.possibleValues())));

				}

				final ConfigDataBoolean booleanAnnotation = field.getAnnotation(ConfigDataBoolean.class);
				if (booleanAnnotation != null) {
					boolean defaultValue = booleanAnnotation.defaultValue();
					if (!defaultValue) {
						try {
							boolean v = field.getBoolean(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}

					}
					boolean value = false;
					if (!booleanAnnotation.secondPath().isEmpty()) {
						value = config.getBoolean(booleanAnnotation.path(),
								config.getBoolean(booleanAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getBoolean(booleanAnnotation.path(), defaultValue);
					}

					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(booleanAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueBoolean(booleanAnnotation.path(), value) {

						@Override
						public void setValue(Player player, boolean value) {
							ymlFile.setValue(booleanAnnotation.path(), value);
						}
					}.addLore(comments)));
				}

				final ConfigDataInt intAnnotation = field.getAnnotation(ConfigDataInt.class);
				if (intAnnotation != null) {
					int defaultValue = intAnnotation.defaultValue();
					if (defaultValue == 0) {
						try {
							int v = field.getInt(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					int value = 0;
					if (!intAnnotation.secondPath().isEmpty()) {
						value = config.getInt(intAnnotation.path(),
								config.getInt(intAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getInt(intAnnotation.path(), defaultValue);
					}
					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(intAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueNumber(intAnnotation.path(), value) {

						@Override
						public void setValue(Player player, Number value) {
							ymlFile.setValue(intAnnotation.path(), value.intValue());
						}
					}.addLore(comments).addOptions(intAnnotation.possibleValues())));
				}

				final ConfigDataDouble doubleAnnotation = field.getAnnotation(ConfigDataDouble.class);
				if (doubleAnnotation != null) {
					double defaultValue = doubleAnnotation.defaultValue();
					if (defaultValue == 0) {
						try {
							double v = field.getDouble(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					double value = 0;
					if (!doubleAnnotation.secondPath().isEmpty()) {
						value = config.getDouble(doubleAnnotation.path(),
								config.getDouble(doubleAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getDouble(doubleAnnotation.path(), defaultValue);
					}

					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(doubleAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueNumber(doubleAnnotation.path(), value) {

						@Override
						public void setValue(Player player, Number value) {
							ymlFile.setValue(doubleAnnotation.path(), value.doubleValue());
						}
					}.addLore(comments).addOptions(doubleAnnotation.possibleValues())));
				}

				final ConfigDataListString listAnnotation = field.getAnnotation(ConfigDataListString.class);
				if (listAnnotation != null) {
					ArrayList<String> defaultValue = new ArrayList<String>();
					try {
						ArrayList<String> v = (ArrayList<String>) field.get(clazz);
						defaultValue = v;
					} catch (Exception e) {

					}
					ArrayList<String> value = null;
					if (!listAnnotation.secondPath().isEmpty()) {
						value = (ArrayList<String>) config.getList(listAnnotation.path(),
								config.getList(listAnnotation.secondPath(), defaultValue));
					} else {
						value = (ArrayList<String>) config.getList(listAnnotation.path(), defaultValue);
					}

					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(listAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}

					buttons.add(new EditGUIButton(new EditGUIValueList(listAnnotation.path(), value) {

						@Override
						public void setValue(Player player, ArrayList<String> value) {
							ymlFile.setValue(listAnnotation.path(), value);
						}

					}.addLore(comments).addOptions(listAnnotation.possibleValues())));
				}

				final ConfigDataKeys setAnnotation = field.getAnnotation(ConfigDataKeys.class);
				if (setAnnotation != null) {
					Set<String> value = null;
					if (config.isConfigurationSection(setAnnotation.path())) {
						value = config.getConfigurationSection(setAnnotation.path()).getKeys(false);
					} else if (config.isConfigurationSection(setAnnotation.secondPath())) {
						value = config.getConfigurationSection(setAnnotation.secondPath()).getKeys(false);
					}

					ArrayList<String> keys = new ArrayList<String>();
					if (value != null) {
						keys.addAll(value);
					}
					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(setAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueList(setAnnotation.path(), keys) {

						@Override
						public void setValue(Player player, ArrayList<String> value) {
							ymlFile.setValue(setAnnotation.path(), value);
						}

					}.addLore(comments).addOptions(setAnnotation.possibleValues())));
				}

				ConfigDataConfigurationSection confAnnotation = field
						.getAnnotation(ConfigDataConfigurationSection.class);
				if (confAnnotation != null) {
					@SuppressWarnings("unused")
					ConfigurationSection value = null;
					if (config.isConfigurationSection(confAnnotation.path())) {
						value = config.getConfigurationSection(confAnnotation.path());
					} else if (config.isConfigurationSection(confAnnotation.secondPath())) {
						value = config.getConfigurationSection(confAnnotation.secondPath());
					}

					// not implemented yet
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	@SuppressWarnings("unchecked")
	public void addButtons(YMLConfig ymlConfig, Class<?> clazz, ConfigurationSection config) {

		for (Field field : clazz.getDeclaredFields()) {
			try {
				field.setAccessible(true);

				final ConfigDataString stringAnnotation = field.getAnnotation(ConfigDataString.class);
				if (stringAnnotation != null) {

					String defaultValue = stringAnnotation.defaultValue();
					if (defaultValue.isEmpty()) {
						try {
							String v = (String) field.get(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					String value = "";
					if (!stringAnnotation.secondPath().isEmpty()) {
						value = config.getString(stringAnnotation.path(),
								config.getString(stringAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getString(stringAnnotation.path(), defaultValue);
					}
					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(stringAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueString(stringAnnotation.path(), value) {

						@Override
						public void setValue(Player player, String value) {
							ymlConfig.setValue(stringAnnotation.path(), value);
						}
					}.addLore(comments).addOptions(stringAnnotation.possibleValues())));

				}

				final ConfigDataBoolean booleanAnnotation = field.getAnnotation(ConfigDataBoolean.class);
				if (booleanAnnotation != null) {
					boolean defaultValue = booleanAnnotation.defaultValue();
					if (!defaultValue) {
						try {
							boolean v = field.getBoolean(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}

					}
					boolean value = false;
					if (!booleanAnnotation.secondPath().isEmpty()) {
						value = config.getBoolean(booleanAnnotation.path(),
								config.getBoolean(booleanAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getBoolean(booleanAnnotation.path(), defaultValue);
					}

					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(booleanAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueBoolean(booleanAnnotation.path(), value) {

						@Override
						public void setValue(Player player, boolean value) {
							ymlConfig.setValue(booleanAnnotation.path(), value);
						}
					}.addLore(comments)));
				}

				final ConfigDataInt intAnnotation = field.getAnnotation(ConfigDataInt.class);
				if (intAnnotation != null) {
					int defaultValue = intAnnotation.defaultValue();
					if (defaultValue == 0) {
						try {
							int v = field.getInt(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					int value = 0;
					if (!intAnnotation.secondPath().isEmpty()) {
						value = config.getInt(intAnnotation.path(),
								config.getInt(intAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getInt(intAnnotation.path(), defaultValue);
					}
					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(intAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueNumber(intAnnotation.path(), value) {

						@Override
						public void setValue(Player player, Number value) {
							ymlConfig.setValue(intAnnotation.path(), value.intValue());
						}
					}.addLore(comments).addOptions(intAnnotation.possibleValues())));
				}

				final ConfigDataDouble doubleAnnotation = field.getAnnotation(ConfigDataDouble.class);
				if (doubleAnnotation != null) {
					double defaultValue = doubleAnnotation.defaultValue();
					if (defaultValue == 0) {
						try {
							double v = field.getDouble(clazz);
							defaultValue = v;
						} catch (Exception e) {

						}
					}
					double value = 0;
					if (!doubleAnnotation.secondPath().isEmpty()) {
						value = config.getDouble(doubleAnnotation.path(),
								config.getDouble(doubleAnnotation.secondPath(), defaultValue));
					} else {
						value = config.getDouble(doubleAnnotation.path(), defaultValue);
					}

					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(doubleAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueNumber(doubleAnnotation.path(), value) {

						@Override
						public void setValue(Player player, Number value) {
							ymlConfig.setValue(doubleAnnotation.path(), value.doubleValue());
						}
					}.addLore(comments).addOptions(doubleAnnotation.possibleValues())));
				}

				final ConfigDataListString listAnnotation = field.getAnnotation(ConfigDataListString.class);
				if (listAnnotation != null) {
					ArrayList<String> defaultValue = new ArrayList<String>();
					try {
						ArrayList<String> v = (ArrayList<String>) field.get(clazz);
						defaultValue = v;
					} catch (Exception e) {

					}
					ArrayList<String> value = null;
					if (!listAnnotation.secondPath().isEmpty()) {
						value = (ArrayList<String>) config.getList(listAnnotation.path(),
								config.getList(listAnnotation.secondPath(), defaultValue));
					} else {
						value = (ArrayList<String>) config.getList(listAnnotation.path(), defaultValue);
					}

					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(listAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}

					buttons.add(new EditGUIButton(new EditGUIValueList(listAnnotation.path(), value) {

						@Override
						public void setValue(Player player, ArrayList<String> value) {
							ymlConfig.setValue(listAnnotation.path(), value);
						}

					}.addLore(comments).addOptions(listAnnotation.possibleValues())));
				}

				final ConfigDataKeys setAnnotation = field.getAnnotation(ConfigDataKeys.class);
				if (setAnnotation != null) {
					Set<String> value = null;
					if (config.isConfigurationSection(setAnnotation.path())) {
						value = config.getConfigurationSection(setAnnotation.path()).getKeys(false);
					} else if (config.isConfigurationSection(setAnnotation.secondPath())) {
						value = config.getConfigurationSection(setAnnotation.secondPath()).getKeys(false);
					}

					ArrayList<String> keys = new ArrayList<String>();
					if (value != null) {
						keys.addAll(value);
					}
					ArrayList<String> comments = new ArrayList<String>();
					try {
						for (String str : config.getComments(setAnnotation.path())) {
							if (str != null) {
								comments.add(str);
							}
						}
					} catch (Exception e) {
						// failsafe for older versions?
					}
					buttons.add(new EditGUIButton(new EditGUIValueList(setAnnotation.path(), keys) {

						@Override
						public void setValue(Player player, ArrayList<String> value) {
							ymlConfig.setValue(setAnnotation.path(), value);
						}

					}.addLore(comments).addOptions(setAnnotation.possibleValues())));
				}

				ConfigDataConfigurationSection confAnnotation = field
						.getAnnotation(ConfigDataConfigurationSection.class);
				if (confAnnotation != null) {
					@SuppressWarnings("unused")
					ConfigurationSection value = null;
					if (config.isConfigurationSection(confAnnotation.path())) {
						value = config.getConfigurationSection(confAnnotation.path());
					} else if (config.isConfigurationSection(confAnnotation.secondPath())) {
						value = config.getConfigurationSection(confAnnotation.secondPath());
					}

					// not implemented yet
				}

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
