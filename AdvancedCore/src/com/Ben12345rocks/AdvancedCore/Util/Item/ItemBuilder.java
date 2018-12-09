package com.Ben12345rocks.AdvancedCore.Util.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Color;
import org.bukkit.DyeColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.Ben12345rocks.AdvancedCore.AdvancedCoreHook;
import com.Ben12345rocks.AdvancedCore.NMSManager.NMSManager;
import com.Ben12345rocks.AdvancedCore.Util.Misc.ArrayUtils;
import com.Ben12345rocks.AdvancedCore.Util.Misc.StringUtils;
import com.google.common.collect.Multimap;

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
	private HashMap<String, String> placeholders = new HashMap<String, String>();
	private int slot = -1;
	private boolean validMaterial = true;

	/**
	 * Create ItemBuilder from a ConfigurationSection
	 *
	 * @param data
	 *            ConfigurationSection
	 */
	public ItemBuilder(ConfigurationSection data) {
		if (data == null) {
			try {
				throw new IllegalArgumentException(
						"ConfigurationSection can not be null! You are probably missing a section in your config.");
			} catch (IllegalArgumentException e) {
				AdvancedCoreHook.getInstance().getPlugin().getLogger().warning(
						"Error occoured while obtaining item, turn debug on to see full stacktrace: " + e.toString());
				AdvancedCoreHook.getInstance().debug(e);
			}
			setBlank();
		} else {
			double chance = data.getDouble("Chance", 100);
			if (checkChance(chance)) {
				Material material = null;
				List<String> lore = data.getStringList("Lore");
				String materialStr = data.getString("Material", data.getName());

				if (!NMSManager.getInstance().isVersion("1.12")) {
					try {
						material = Material.matchMaterial(materialStr.toUpperCase());

						// temp
						if (material == null) {
							material = Material.matchMaterial(materialStr, true);
							if (material != null) {
								AdvancedCoreHook.getInstance().getPlugin().getLogger()
										.warning("Found legacy material name: " + materialStr
												+ ", please update this to prevent this message");
							}
						}
					} catch (NoSuchMethodError e) {
						material = Material.valueOf(materialStr.toUpperCase());
					}
				} else {
					if (materialStr.equalsIgnoreCase("PLAYER_HEAD")) {
						materialStr = "SKULL";
					} else if (materialStr.equalsIgnoreCase("CLOCK")) {
						materialStr = "WATCH";
					}
					material = Material.valueOf(materialStr);
				}

				if (material == null) {
					material = Material.STONE;
					AdvancedCoreHook.getInstance().getPlugin().getLogger()
							.warning("Invalid material: " + data.getString("Material"));
					validMaterial = false;
					lore.add("&cInvalid material: " + material);
				}

				int amount = data.getInt("Amount");
				int minAmount = data.getInt("MinAmount");
				int maxAmount = data.getInt("MaxAmount");

				int currentAmount = 0;
				if (amount > 0) {
					currentAmount = amount;
				} else {
					currentAmount = ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
				}

				is = new ItemStack(material, currentAmount);
				String name = data.getString("Name");

				if (name != null && !name.equals("")) {
					setName(name);
				}
				if (lore != null && lore.size() > 0) {
					setLore(lore);
				} else {
					String line = data.getString("Lore", "");
					if (!line.equals("")) {
						addLoreLine(line);
					}
				}
				int durability = data.getInt("Durability");
				if (durability > 0) {
					setDurability((short) durability);
				}

				if (data.isConfigurationSection("Enchants")) {
					HashMap<String, Integer> enchants = new HashMap<String, Integer>();
					for (String enchant : data.getConfigurationSection("Enchants").getKeys(false)) {
						enchants.put(enchant, data.getInt("Enchants." + enchant));
					}
					addEnchantments(enchants);
				}

				@SuppressWarnings("unchecked")
				ArrayList<String> itemFlags = (ArrayList<String>) data.getList("ItemFlags", new ArrayList<String>());
				for (String flag : itemFlags) {
					addItemFlag(flag);
				}

				String skull = data.getString("Skull", "");
				if (!skull.equals("")) {
					setSkullOwner(skull);
				}

				if (data.getBoolean("Glow")) {
					addGlow();
				}

				if (data.isConfigurationSection("Potions")) {
					for (String pot : data.getConfigurationSection("Potions").getKeys(false)) {
						PotionEffectType type = PotionEffectType.getByName(pot);
						if (type != null) {
							addPotionEffect(type, data.getInt("Potions." + pot + ".Duration"),
									data.getInt("Potions." + pot + ".Amplifier", 1));
						} else {
							AdvancedCoreHook.getInstance().getPlugin().getLogger()
									.warning("Invalid potion effect type: " + pot);
						}
					}
				}

				setUnbreakable(data.getBoolean("Unbreakable", false));

				slot = data.getInt("Slot", -1);
			} else {
				setBlank();
			}
		}
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
	 *            The material to create the ItemBuilder with.
	 */
	public ItemBuilder(Material m) {
		this(m, 1);
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

	public ItemBuilder addAttributeModifier(Attribute att, AttributeModifier modifier) {
		ItemMeta im = is.getItemMeta();
		im.addAttributeModifier(att, modifier);
		is.setItemMeta(im);
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
		if (ench != null) {
			ItemMeta im = is.getItemMeta();
			im.addEnchant(ench, level, true);
			is.setItemMeta(im);
		}
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
		HashMap<Enchantment, Integer> enchantments = new HashMap<Enchantment, Integer>();
		for (String enchant : enchants.keySet()) {
			enchantments.put(Enchantment.getByKey(NamespacedKey.minecraft(enchant)), enchants.get(enchant));
		}
		return addEnchantments(enchantments);
	}

	/**
	 * Add multiple enchants at once.
	 *
	 * @param enchantments
	 *            The enchants to add.
	 * @return ItemBuilder
	 */
	public ItemBuilder addEnchantments(Map<Enchantment, Integer> enchantments) {
		for (Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
			addEnchant(entry.getKey(), entry.getValue());
		}
		return this;
	}

	public ItemBuilder addGlow() {
		ItemMeta meta = is.getItemMeta();
		meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		is.setItemMeta(meta);
		is.addUnsafeEnchantment(Enchantment.LUCK, 1);
		return this;
	}

	public ItemBuilder addItemFlag(String flag) {
		try {
			ItemMeta meta = is.getItemMeta();
			meta.addItemFlags(ItemFlag.valueOf(flag));
			is.setItemMeta(meta);
		} catch (Exception ex) {
			AdvancedCoreHook.getInstance().debug("Invalid flag: " + flag);
		}
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
		if (line != null) {
			ItemMeta im = is.getItemMeta();
			List<String> lore = new ArrayList<>();
			if (im.hasLore()) {
				lore = new ArrayList<>(im.getLore());
			}
			for (String str : line.split("%NewLine%")) {
				lore.add(str);
			}
			setLore(lore);
		}
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
		return setLore(lore);
	}

	public ItemBuilder addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	public ItemBuilder addPotionEffect(PotionEffectType type, int duration, int amplifier) {
		PotionMeta meta = (PotionMeta) is.getItemMeta();
		meta.addCustomEffect(new PotionEffect(type, duration, amplifier), false);
		is.setItemMeta(meta);
		return this;
	}

	private boolean checkChance(double chance) {
		if ((chance == 0) || (chance == 100)) {
			return true;
		}

		double randomNum = ThreadLocalRandom.current().nextDouble(100);

		if (randomNum <= chance) {
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Clone the ItemBuilder into a new one.
	 *
	 * @return The cloned instance.
	 */
	@Override
	public ItemBuilder clone() {
		return new ItemBuilder(is);
	}

	@SuppressWarnings("deprecation")
	public LinkedHashMap<String, Object> createConfigurationData() {
		LinkedHashMap<String, Object> data = new LinkedHashMap<String, Object>();
		data.put("Material", is.getType().toString());
		data.put("Amount", getAmount());
		if (hasCustomDisplayName()) {
			data.put("Name", getName());
		}
		if (hasCustomLore()) {
			data.put("Lore", getLore());
		}
		data.put("Durability", is.getDurability());
		data.put("Data", is.getData().getData());

		for (Entry<Enchantment, Integer> en : is.getItemMeta().getEnchants().entrySet()) {
			data.put("Enchants." + en.getKey().getName(), en.getValue());
		}

		ArrayList<String> flags = new ArrayList<String>();
		for (ItemFlag fl : is.getItemMeta().getItemFlags()) {
			flags.add(fl.toString());
		}

		data.put("ItemFlags", flags);

		data.put("Unbreakable", is.getItemMeta().isUnbreakable());

		data.put("Skull", getSkull());

		return data;

	}

	@Override
	public boolean equals(Object ob) {
		if (ob instanceof ItemBuilder) {
			ItemBuilder b = (ItemBuilder) ob;
			return b.toItemStack().equals(toItemStack());
		}

		return false;
	}

	private int getAmount() {
		return is.getAmount();
	}

	public Multimap<Attribute, AttributeModifier> getAttributeModifiers() {
		return is.getItemMeta().getAttributeModifiers();
	}

	public Collection<AttributeModifier> getAttributeModifiers(Attribute att) {
		return is.getItemMeta().getAttributeModifiers(att);
	}

	public ArrayList<String> getLore() {
		if (hasCustomLore()) {
			List<String> lore = is.getItemMeta().getLore();
			ArrayList<String> list = new ArrayList<String>();
			if (lore != null) {
				list.addAll(lore);
			}
			return list;
		}
		return new ArrayList<String>();

	}

	public String getName() {
		if (hasCustomDisplayName()) {
			return is.getItemMeta().getDisplayName();
		}
		return "";
	}

	/**
	 * @return the skull
	 */
	@Deprecated
	public String getSkull() {
		try {
			SkullMeta im = (SkullMeta) is.getItemMeta();
			if (im.hasOwner()) {
				return im.getOwner();
			}
		} catch (ClassCastException expected) {
		}
		return "";
	}

	public OfflinePlayer getSkullOwner() {
		try {
			SkullMeta im = (SkullMeta) is.getItemMeta();
			if (im.hasOwner()) {
				return im.getOwningPlayer();
			}
		} catch (ClassCastException expected) {
		}
		return null;
	}

	/**
	 * @return the slot
	 */
	public int getSlot() {
		return slot;
	}

	public boolean hasAttributes() {
		return is.getItemMeta().hasAttributeModifiers();
	}

	public boolean hasCustomDisplayName() {
		if (hasItemMeta()) {
			return is.getItemMeta().hasDisplayName();
		}
		return false;
	}

	public boolean hasCustomLore() {
		if (hasItemMeta()) {
			return is.getItemMeta().hasLore();
		}
		return false;
	}

	public boolean hasItemMeta() {
		return is.hasItemMeta();
	}

	public boolean isValidMaterial() {
		return validMaterial;
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
	 * Remove a lore line.
	 *
	 * @param index
	 *            The index of the lore line to remove.
	 * @return ItemBuilder
	 */
	public ItemBuilder removeLoreLine(int index) {
		ItemMeta im = is.getItemMeta();
		List<String> lore = new ArrayList<>(im.getLore());
		if (index < 0 || index > lore.size()) {
			return this;
		}
		lore.remove(index);
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
		if (!lore.contains(line)) {
			return this;
		}
		lore.remove(line);
		im.setLore(lore);
		is.setItemMeta(im);
		return this;
	}

	public ItemBuilder setAmount(int amount) {
		is.setAmount(amount);
		return this;
	}

	public ItemBuilder setAmountNone(int i) {
		if (getAmount() == 0) {
			setAmount(i);
		}
		return this;
	}

	private void setBlank() {
		is = new ItemStack(Material.STONE);
		setAmount(0);
	}

	/**
	 * Change the durability of the item.
	 *
	 * @param dur
	 *            The durability to set it to.
	 *
	 * @return ItemBuilder
	 */
	@Deprecated
	public ItemBuilder setDurability(short dur) {
		is.setDurability(dur);
		return this;
	}

	/**
	 * Sets the dye color on an item. <b>* Notice that this doesn't check for item
	 * type, sets the literal data of the dyecolor as durability.</b>
	 *
	 * @param color
	 *            The color to put.
	 * @return ItemBuilder
	 */
	@Deprecated
	public ItemBuilder setDyeColor(DyeColor color) {
		is.setDurability(color.getDyeData());
		return this;
	}

	/**
	 * Sets infinity durability on the item by setting the durability to
	 * Short.MAX_VALUE.
	 *
	 * @return ItemBuilder
	 */
	public ItemBuilder setInfinityDurability() {
		ItemMeta meta = is.getItemMeta();
		meta.setUnbreakable(true);
		is.setItemMeta(meta);
		return this;
	}

	/**
	 * Sets the armor color of a leather armor piece. Works only on leather armor
	 * pieces.
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
	 * Re-sets the lore.
	 *
	 * @param lore
	 *            The lore to set it to.
	 *
	 * @return ItemBuilder
	 */
	public ItemBuilder setLore(List<String> lore) {
		List<String> list = new ArrayList<String>();
		for (String str : lore) {
			for (String s : str.split("%NewLine%")) {
				list.add(s);
			}
		}
		ItemMeta im = is.getItemMeta();
		im.setLore(ArrayUtils.getInstance().colorize(list));
		is.setItemMeta(im);
		return this;
	}

	public ItemBuilder removeLore() {
		setLore(new String[0]);
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
		return setLore(Arrays.asList(lore));
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
		im.setDisplayName(StringUtils.getInstance().colorize(name));
		is.setItemMeta(im);
		return this;
	}

	public ItemBuilder setNameIfNotExist(String name) {
		if (!hasCustomDisplayName()) {
			setName(name);
		}
		return this;
	}

	public ItemBuilder setPlaceholders(HashMap<String, String> placeholders) {
		this.placeholders = placeholders;
		return this;
	}

	public ItemBuilder setSkullOwner(OfflinePlayer offlinePlayer) {
		if (offlinePlayer != null) {
			try {
				SkullMeta im = (SkullMeta) is.getItemMeta();
				im.setOwningPlayer(offlinePlayer);
				is.setItemMeta(im);
			} catch (Exception expected) {
				setSkullOwner(offlinePlayer.getName());
			}
		}
		return this;
	}

	/**
	 * Set the skull owner for the item. Works on skulls only.
	 *
	 * @param owner
	 *            The name of the skull's owner.
	 * @return ItemBuilder
	 */
	@Deprecated
	public ItemBuilder setSkullOwner(String owner) {
		if (owner != null && !owner.isEmpty()) {
			try {
				SkullMeta im = (SkullMeta) is.getItemMeta();
				im.setOwner(owner);
				is.setItemMeta(im);
			} catch (ClassCastException expected) {
			}
		}
		return this;
	}

	public ItemBuilder setSlot(int slot) {
		this.slot = slot;
		return this;
	}

	public ItemBuilder setUnbreakable(boolean unbreakable) {
		try {
			ItemMeta meta = is.getItemMeta();
			meta.setUnbreakable(unbreakable);
			is.setItemMeta(meta);
		} catch (NoSuchMethodError e) {

		}
		return this;
	}

	/**
	 * Retrieves the itemstack from the ItemBuilder.
	 *
	 * @return The itemstack created/modified by the ItemBuilder instance.
	 *
	 * @deprecated Use toItemStack(Player player)
	 */
	@Deprecated
	public ItemStack toItemStack() {
		setName(StringUtils.getInstance()
				.replaceJavascript(StringUtils.getInstance().replacePlaceHolder(getName(), placeholders)));
		setLore(ArrayUtils.getInstance()
				.replaceJavascript(ArrayUtils.getInstance().replacePlaceHolder(getLore(), placeholders)));
		return is;
	}

	public ItemStack toItemStack(OfflinePlayer player) {
		return parsePlaceholders(player);
	}

	public ItemStack toItemStack(Player player) {
		return parsePlaceholders(player);
	}

	public ItemStack parsePlaceholders(OfflinePlayer player) {
		if (player != null) {
			setName(StringUtils.getInstance().replaceJavascript(player,
					StringUtils.getInstance().replacePlaceHolder(getName(), placeholders)));
			setLore(ArrayUtils.getInstance().replaceJavascript(player,
					ArrayUtils.getInstance().replacePlaceHolder(getLore(), placeholders)));
			if (!getSkull().equals("")) {
				setSkullOwner(StringUtils.getInstance().replacePlaceHolder(getSkull(), "player", player.getName()));
			}
		} else {
			return toItemStack();
		}
		return is;
	}
}
