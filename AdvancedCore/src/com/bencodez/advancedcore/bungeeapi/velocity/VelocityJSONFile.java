package com.bencodez.advancedcore.bungeeapi.velocity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.google.common.reflect.TypeToken;

import lombok.Getter;
import lombok.Setter;
import ninja.leaping.configurate.ConfigurationNode;
import ninja.leaping.configurate.gson.GsonConfigurationLoader;
import ninja.leaping.configurate.objectmapping.ObjectMappingException;

public class VelocityJSONFile {
	@Getter
	@Setter
	private ConfigurationNode conf;
	@Getter
	private File file;
	private GsonConfigurationLoader loader;

	public VelocityJSONFile(File file) {

		this.file = file;
		if (!file.exists()) {
			try {
				file.getParentFile().mkdirs();
				file.createNewFile();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		loader = GsonConfigurationLoader.builder().setPath(file.toPath()).build();

		try {
			conf = loader.load();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public boolean getBoolean(ConfigurationNode node, boolean def) {
		return node.getBoolean(def);
	}

	public ConfigurationNode getData() {
		return conf;
	}

	public int getInt(ConfigurationNode node, int def) {
		return node.getInt(def);
	}

	public ArrayList<String> getKeys(ConfigurationNode node) {
		ArrayList<String> keys = new ArrayList<String>();
		for (ConfigurationNode key : node.getChildrenMap().values()) {
			keys.add(key.getKey().toString());
		}
		return keys;
	}

	public long getLong(ConfigurationNode node, long def) {
		return node.getLong(def);
	}

	public ConfigurationNode getNode(Object... path) {
		return getData().getNode(path);
	}

	public String getString(ConfigurationNode node, String def) {
		return node.getString(def);
	}

	public List<String> getStringList(ConfigurationNode node, ArrayList<String> def) {
		try {
			return node.getList(TypeToken.of(String.class), def);
		} catch (ObjectMappingException e) {
			e.printStackTrace();
			return def;
		}
	}

	public void reload() {
		loader = GsonConfigurationLoader.builder().setPath(file.toPath()).build();

		try {
			conf = loader.load();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void save() {
		try {
			loader.save(conf);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
