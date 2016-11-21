package com.Ben12345rocks.AdvancedCore.Util.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;

import com.Ben12345rocks.AdvancedCore.Main;

/**
 * Easily create itemstacks, without messing your hands.
 * 
 * Credit to NonameSL for creating this
 * 
 * Modified by Ben12345rocks
 * 
 */
public class ItemBuilder {
	private ItemStack is;

	/**
	 * Create ItemBuilder from a ConfigurationSection
	 * 
	 * @param data
	 *            ConfigurationSection
	 */
	public ItemBuilder(ConfigurationSection data) {
		if (data == null) {
			throw new IllegalArgumentException("Data can not be null!");
		} else {
			Material material = Material.STONE;
			try {
				material = Material.valueOf(data.getString("Material"));
			} catch (Exception e) {
				Main.plugin.debug(e);
			}

			int amount = data.getInt("Amount");
			int minAmount = data.getInt("MinAmount");
			int maxAmount = data.getInt("MaxAmount");

			int currentAmount = 0;
			if (amount > 0) {
				currentAmount = amount;
			} else {
				currentAmount = (int) ((Math.random() * maxAmount) + 1);
				if (currentAmount < minAmount) {
					currentAmount = minAmount;
				}
			}

			int dat = data.getInt("Data");
			is = new ItemStack(material, currentAmount, (short) dat);
			String name = data.getString("Name");
			List<String> lore = data.getStringList("Lore");
			if (name != null && !name.equals("")) {
				is.getItemMeta().setDisplayName(name);
			}
			if (lore != null && lore.size() > 0) {
				is.getItemMeta().setLore(lore);
			}
			int durability = data.getInt("Durability");
			if (durability > 0) {
				is.setDurability((short) durability);
			}

			if (data.isConfigurationSection("Enchants")) {
				HashMap<String, Integer> enchants = new HashMap<String, Integer>();
				for (String enchant : data.getConfigurationSection("Enchants").getKeys(false)) {
					enchants.put(enchant, data.getInt("Enchants." + enchant));
				}
				addEnchantments(enchants);
			}

		}
	}

	/**
	 * Create a new ItemBuilder from scratch.
	 * 
	 * @param m
	 *            The material to create the ItemBuilder with.
	 */
	public ItemBuilder(Material m) {
		this(m, 1);
	}

	/**
	 * Create a new ItemBuilder over an existing itemstack.
	 * 
	 * @param is
	 *            The itemstack to create the ItemBuilder over.
	 */
	public ItemBuilder(ItemStack is) {
		this.is = is;
	}

	/**
	 * Create a new ItemBuilder from scratch.
	 * 
	 * @param m
	 *            The material of the item.
	 * @param amount
	 *            The amount of the item.
	 */
	public ItemBuilder(Material m, int amount) {
		is = new ItemStack(m, amount);
	}

	/**
	 * Create a new ItemBuilder from scratch.
	 * 
	 * @param m
	 *            The material of the item.
	 * @param amount
	 *            The amount of the item.
	 * @param s
	 *            The durability of the item.
	 */
	public ItemBuilder(Material m, int amount, short s) {
		is = new ItemStack(m, amount, s);
	}

	/**
	 * Clone the ItemBuilder into a new one.
	 * 
	 * @return The cloned instance.
	 */
	public ItemBuilder clone() {
		return new ItemBuilder(is);
	}

	/**
	 * Change the durability of the item.
	 * 
	 * @param dur
	 *            The durability to set it to.
	 * 
	 * @return ItemBuilder
	 */
	public ItemBuilder setDurability(short dur) {
		is.setDurability(dur);
		return this;
	}

	/**
	 * Set the displayname of the item.
	 * 
	 * @param name
	 *            The name to change it to.
	 * @return ItemBuilder
	 */
	public ItemBuilder setName(String name) {
		ItemMeta im = is.getItemMeta();
		im.setDisplayName(name);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Add an unsafe enchantment.
	 * 
	 * @param ench
	 *            The enchantment to add.
	 * @param level
	 *            The level to put the enchant on.
	 * @return ItemBuilder
	 */
	public ItemBuilder addUnsafeEnchantment(Enchantment ench, int level) {
		is.addUnsafeEnchantment(ench, level);
		return this;
	}

	/**
	 * Remove a certain enchant from the item.
	 * 
	 * @param ench
	 *            The enchantment to remove
	 * @return ItemBuilder
	 */
	public ItemBuilder removeEnchantment(Enchantment ench) {
		is.removeEnchantment(ench);
		return this;
	}

	/**
	 * Set the skull owner for the item. Works on skulls only.
	 * 
	 * @param owner
	 *            The name of the skull's owner.
	 * @return ItemBuilder
	 */
	public ItemBuilder setSkullOwner(String owner) {
		try {
			SkullMeta im = (SkullMeta) is.getItemMeta();
			im.setOwner(owner);
			is.setItemMeta(im);
		} catch (ClassCastException expected) {
		}
		return this;
	}

	/**
	 * Add an enchant to the item.
	 * 
	 * @param ench
	 *            The enchant to add
	 * @param level
	 *            The level
	 * @return ItemBuilder
	 */
	public ItemBuilder addEnchant(Enchantment ench, int level) {
		ItemMeta im = is.getItemMeta();
		im.addEnchant(ench, level, true);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Add multiple enchants at once.
	 * 
	 * @param enchantments
	 *            The enchants to add.
	 * @return ItemBuilder
	 */
	public ItemBuilder addEnchantments(Map<Enchantment, Integer> enchantments) {
		is.addEnchantments(enchantments);
		return this;
	}

	/**
	 * Add multiple enchants at once.
	 * 
	 * @param enchants
	 *            The enchants to add.
	 * @return ItemBuilder
	 */
	public ItemBuilder addEnchantments(HashMap<String, Integer> enchants) {
		if ((enchants == null) || (enchants.size() == 0)) {
			return this;
		}
		ItemMeta meta = is.getItemMeta();
		for (String enchant : enchants.keySet()) {
			meta.addEnchant(Enchantment.getByName(enchant), enchants.get(enchant), false);
		}
		is.setItemMeta(meta);
		return this;
	}

	/**
	 * Sets infinity durability on the item by setting the durability to
	 * Short.MAX_VALUE.
	 * 
	 * @return ItemBuilder
	 */
	public ItemBuilder setInfinityDurability() {
		is.setDurability(Short.MAX_VALUE);
		return this;
	}

	/**
	 * Re-sets the lore.
	 * 
	 * @param lore
	 *            The lore to set it to.
	 * @return ItemBuilder
	 */
	public ItemBuilder setLore(String... lore) {
		ItemMeta im = is.getItemMeta();
		im.setLore(Arrays.asList(lore));
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Re-sets the lore.
	 * 
	 * @param lore
	 *            The lore to set it to.
	 * 
	 * @return ItemBuilder
	 */
	public ItemBuilder setLore(List<String> lore) {
		ItemMeta im = is.getItemMeta();
		im.setLore(lore);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Remove a lore line.
	 * 
	 * @param line
	 *            The lore to remove.
	 * @return ItemBuilder
	 */
	public ItemBuilder removeLoreLine(String line) {
		ItemMeta im = is.getItemMeta();
		List<String> lore = new ArrayList<>(im.getLore());
		if (!lore.contains(line))
			return this;
		lore.remove(line);
		im.setLore(lore);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Remove a lore line.
	 * 
	 * @param index
	 *            The index of the lore line to remove.
	 * @return ItemBuilder
	 */
	public ItemBuilder removeLoreLine(int index) {
		ItemMeta im = is.getItemMeta();
		List<String> lore = new ArrayList<>(im.getLore());
		if (index < 0 || index > lore.size())
			return this;
		lore.remove(index);
		im.setLore(lore);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Add a lore line.
	 * 
	 * @param line
	 *            The lore line to add.
	 * @return ItemBuilder
	 */
	public ItemBuilder addLoreLine(String line) {
		ItemMeta im = is.getItemMeta();
		List<String> lore = new ArrayList<>();
		if (im.hasLore())
			lore = new ArrayList<>(im.getLore());
		lore.add(line);
		im.setLore(lore);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Add a lore line.
	 * 
	 * @param line
	 *            The lore line to add.
	 * @param pos
	 *            The index of where to put it.
	 * @return ItemBuilder
	 */
	public ItemBuilder addLoreLine(String line, int pos) {
		ItemMeta im = is.getItemMeta();
		List<String> lore = new ArrayList<>(im.getLore());
		lore.set(pos, line);
		im.setLore(lore);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Sets the dye color on an item. <b>* Notice that this doesn't check for
	 * item type, sets the literal data of the dyecolor as durability.</b>
	 * 
	 * @param color
	 *            The color to put.
	 * @return ItemBuilder
	 */
	@SuppressWarnings("deprecation")
	public ItemBuilder setDyeColor(DyeColor color) {
		this.is.setDurability(color.getDyeData());
		return this;
	}

	/**
	 * Sets the armor color of a leather armor piece. Works only on leather
	 * armor pieces.
	 * 
	 * @param color
	 *            The color to set it to.
	 * @return ItemBuilder
	 */
	public ItemBuilder setLeatherArmorColor(Color color) {
		try {
			LeatherArmorMeta im = (LeatherArmorMeta) is.getItemMeta();
			im.setColor(color);
			is.setItemMeta(im);
		} catch (ClassCastException expected) {
		}
		return this;
	}

	/**
	 * Retrieves the itemstack from the ItemBuilder.
	 * 
	 * @return The itemstack created/modified by the ItemBuilder instance.
	 */
	public ItemStack toItemStack() {
		return is;
	}
}
