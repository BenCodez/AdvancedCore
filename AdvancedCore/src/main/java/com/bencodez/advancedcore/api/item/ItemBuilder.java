package com.bencodez.advancedcore.api.item;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;
import com.bencodez.advancedcore.api.misc.MiscUtils;
import com.bencodez.simpleapi.array.ArrayUtils;
import com.bencodez.simpleapi.messages.MessageAPI;
import com.bencodez.simpleapi.nms.NMSManager;
import com.bencodez.simpleapi.skull.SkullCache;
import com.google.common.collect.Multimap;

import lombok.Getter;
import lombok.Setter;

/**
 * Easily create itemstacks, without messing your hands.
 *
 * Credit to NonameSL for creating this
 *
 * Modified by Ben12345rocks
 *
 */
public class ItemBuilder {
	@Getter
	@Setter
	private boolean chancePass = true;
	@Getter
	@Setter
	private boolean checkLoreLength = true;
	@Getter
	private boolean conditional = false;
	@Getter
	private ConfigurationSection conditionalValues;
	@Getter
	private List<Integer> fillSlots = new ArrayList<>();
	private ItemStack is;
	@Getter
	private String javascriptConditional = "";

	@Getter
	private boolean legacy = false;

	private int loreLength = -1;

	@Getter
	private String path;

	@Getter
	private String identifier;

	private HashMap<String, String> placeholders = new HashMap<>();

	private String skull = "";

	@Getter
	private int slot = -1;

	@Getter
	private boolean fillEmptySlots = false;

	@Getter
	private boolean validMaterial = true;

	@Getter
	private boolean closeGUISet = false;

	@Getter
	private boolean closeGUI = true;

	/**
	 * Create ItemBuilder from a ConfigurationSection
	 *
	 * @param data ConfigurationSection
	 */
	@SuppressWarnings("deprecation")
	public ItemBuilder(ConfigurationSection data) {
		if (data == null) {
			try {
				throw new IllegalArgumentException(
						"ConfigurationSection can not be null! You are probably missing a section in your yml file");
			} catch (IllegalArgumentException e) {
				AdvancedCorePlugin.getInstance().getLogger().warning(
						"Error occurred while obtaining item, turn debug on to see full stacktrace: " + e.toString());
				AdvancedCorePlugin.getInstance().debug(e);
			}
			setBlank();
		} else {
			path = data.getCurrentPath();
			identifier = data.getName();
			double chance = data.getDouble("Chance", 100);
			if (checkChance(chance)) {
				chancePass = true;
				javascriptConditional = data.getString("ConditionalJavascript", "");
				if (!javascriptConditional.isEmpty()) {
					// process conditional item
					conditional = true;
					conditionalValues = data.getConfigurationSection("Conditional");
					is = new ItemStack(Material.STONE);

					slot = data.getInt("Slot", -1);

					fillSlots = data.getIntegerList("FillSlots");

					fillEmptySlots = data.getBoolean("FillEmptySlots");

					if (data.isBoolean("CloseGUI")) {
						closeGUISet = true;
						closeGUI = data.getBoolean("CloseGUI", true);
					} else {
						closeGUISet = false;
					}

				} else {
					if (data.isConfigurationSection("ItemStack")) {
						HashMap<String, Object> map = new HashMap<>();
						for (String key : data.getConfigurationSection("ItemStack").getKeys(false)) {
							map.put(key, data.get("ItemStack." + key));
						}
						is = ItemStack.deserialize(map);
					} else if (!data.getString("ItemsAdder", "").isEmpty() || !data.getString("Nexo", "").isEmpty()) {

						if (!data.getString("ItemsAdder", "").isEmpty()
								&& Bukkit.getPluginManager().getPlugin("ItemsAdder") != null) {
							ItemStack item = new ItemsAdderHandle().getItem(data.getString("ItemsAdder"));
							if (item != null) {
								is = item;
							}
						} else if (!data.getString("Nexo", "").isEmpty()
								&& Bukkit.getPluginManager().getPlugin("Nexo") != null) {
							ItemStack item = new NexoItemHandle().getItem(data.getString("Nexo"));
							if (item != null) {
								is = item;
							}
						} else {
							is = new ItemStack(Material.STONE);
						}

						List<String> lore = data.getStringList("Lore");
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
						int amount = data.getInt("Amount");
						int minAmount = data.getInt("MinAmount");
						int maxAmount = data.getInt("MaxAmount");

						int currentAmount = 0;
						if (amount > 0) {
							currentAmount = amount;
						} else {
							currentAmount = ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
						}
						is.setAmount(currentAmount);
					} else {

						Material material = null;
						List<String> lore = data.getStringList("Lore");
						String materialStr = data.getString("Material", data.getName());
						if (NMSManager.getInstance().isVersion("1.12")) {
							if (materialStr.equalsIgnoreCase("player_head")) {
								materialStr = "SKULL";
							} else if (materialStr.equalsIgnoreCase("CLOCK")) {
								materialStr = "WATCH";
							} else if (materialStr.equalsIgnoreCase("OAK_SIGN")) {
								materialStr = "SIGN";
							} else if (materialStr.equalsIgnoreCase("BLACK_STAINED_GLASS_PANE")) {
								materialStr = "STAINED_GLASS_PANE";
							}
						}

						try {
							material = Material.matchMaterial(materialStr.toUpperCase());

							// temp
							if (material == null) {
								material = Material.matchMaterial(materialStr, true);
								if (material != null) {
									AdvancedCorePlugin.getInstance().getLogger().warning("Found legacy material name: "
											+ materialStr
											+ ", please update this to prevent this message and prevent issues, path: "
											+ data.getCurrentPath());
									legacy = true;
								}
							}
						} catch (NoSuchMethodError e) {
							material = Material.valueOf(materialStr.toUpperCase());
						}

						if (material == null) {
							material = Material.STONE;
							AdvancedCorePlugin.getInstance().getLogger()
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
							if (minAmount >= 0 && maxAmount > 0) {
								currentAmount = ThreadLocalRandom.current().nextInt(minAmount, maxAmount + 1);
							} else {
								currentAmount = 1;
							}
						}

						is = new ItemStack(material, currentAmount);
						int power = data.getInt("Power", -1);
						if (power > 0) {
							setFireworkPower(power);
						}

						skull = data.getString("Skull", "");
						if (!skull.equals("") && !skull.contains("%")) {
							setSkullOwner(skull);

						}
						String texture = data.getString("SkullTexture", "");
						if (!texture.equals("")) {
							setHeadFromBase64(texture);
							is.setAmount(currentAmount);
						}

						String textureURL = data.getString("SkullURL", "");
						if (!textureURL.equals("")) {
							is = SkullCache.getSkullURL(textureURL);
							is.setAmount(currentAmount);
						}

						String textureUUID = data.getString("SkullUUID", "");
						if (!textureUUID.equals("")) {
							try {
								is = SkullCache.getSkull(UUID.fromString(textureUUID), "");
							} catch (IOException e) {
								e.printStackTrace();
							}
							is.setAmount(currentAmount);
						}

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
							HashMap<String, Integer> enchants = new HashMap<>();
							for (String enchant : data.getConfigurationSection("Enchants").getKeys(false)) {
								enchants.put(enchant, data.getInt("Enchants." + enchant));
							}
							addEnchantments(enchants);
						}

						@SuppressWarnings("unchecked")
						ArrayList<String> itemFlags = (ArrayList<String>) data.getList("ItemFlags", new ArrayList<>());
						for (String flag : itemFlags) {
							addItemFlag(flag);
						}

						if (data.getBoolean("Glow")) {
							addGlow();
						}

						checkLoreLength = data.getBoolean("CheckLoreLength", true);
						loreLength = data.getInt("LoreLength", -1);

						Color color = null;
						if (data.isConfigurationSection("PotionColor")) {
							ConfigurationSection potionColor = data.getConfigurationSection("PotionColor");
							color = Color.fromRGB(potionColor.getInt("Red", 0), potionColor.getInt("Green", 0),
									potionColor.getInt("Blue", 0));
						}

						if (data.isConfigurationSection("Potions")) {
							for (String pot : data.getConfigurationSection("Potions").getKeys(false)) {
								PotionEffectType type = PotionEffectType.getByName(pot);
								if (type != null) {
									addPotionEffect(type, data.getInt("Potions." + pot + ".Duration"),
											data.getInt("Potions." + pot + ".Amplifier", 1), color);
								} else {
									AdvancedCorePlugin.getInstance().getLogger()
											.warning("Invalid potion effect type: " + pot);
								}
							}
						}

						if (data.contains("CustomModelData")) {
							setCustomModelData(data.getInt("CustomModelData", -1));
						}

						if (data.contains("ItemModel")) {
							is = ItemModelHandler.getItemWithModel(is, data.getString("ItemModel"));
						}

						setUnbreakable(data.getBoolean("Unbreakable", false));
					}
					slot = data.getInt("Slot", -1);

					fillSlots = data.getIntegerList("FillSlots");

					fillEmptySlots = data.getBoolean("FillEmptySlots");

					if (data.isBoolean("CloseGUI")) {
						closeGUISet = true;
						closeGUI = data.getBoolean("CloseGUI", true);
					} else {
						closeGUISet = false;
					}

					if (data.getBoolean("HideToolTip", false)) {

						setHideTooltipCompat(is, true);
					}
				}

			} else {
				setBlank();
				chancePass = false;
			}
		}
	}

	/**
	 * Hides (or shows) the item tooltip. On 1.20.5+ this uses the native
	 * ItemMeta#setHideTooltip method; on older versions it simply adds all
	 * available ItemFlags (so at least most tooltip lines are hidden).
	 *
	 * @param item the ItemStack to modify
	 * @param hide true to hide, false to show
	 */
	public void setHideTooltipCompat(ItemStack item, boolean hide) {
		if (item == null)
			return;

		ItemMeta meta = item.getItemMeta();
		if (meta == null)
			return;

		try {
			// Try to call ItemMeta#setHideTooltip(boolean) reflectively
			Method m = ItemMeta.class.getMethod("setHideTooltip", boolean.class);
			m.invoke(meta, hide);
		} catch (NoSuchMethodException e) {
			// Older versions: no native hideTooltip
			if (hide) {
				// add all flags to hide as much as possible
				meta.addItemFlags(ItemFlag.values());
			} else {
				// remove all flags so tooltip shows again
				meta.removeItemFlags(ItemFlag.values());
			}
		} catch (ReflectiveOperationException ex) {
			// something went wrong with reflection; just ignore
		}

		item.setItemMeta(meta);
	}

	/**
	 * Create a new ItemBuilder over an existing itemstack.
	 *
	 * @param is The itemstack to create the ItemBuilder over.
	 */
	public ItemBuilder(ItemStack is) {
		this.is = is;
	}

	/**
	 * Create a new ItemBuilder from scratch.
	 *
	 * @param m The material to create the ItemBuilder with.
	 */
	public ItemBuilder(Material m) {
		this(m, 1);
	}

	/**
	 * Create a new ItemBuilder from scratch.
	 *
	 * @param material The material of the item.
	 * @param amount   The amount of the item.
	 */
	public ItemBuilder(Material material, int amount) {
		is = new ItemStack(material, amount);
	}

	/**
	 * Constructs an ItemBuilder from a material string.
	 * 
	 * @param material the material name
	 */
	public ItemBuilder(String material) {
		String materialStr = material;
		if (NMSManager.getInstance().isVersion("1.12")) {
			if (material.equalsIgnoreCase("player_head")) {
				materialStr = "SKULL";
			} else {
				materialStr = "PAPER";
			}
		}
		try {
			this.is = new ItemStack(Material.valueOf(materialStr));
		} catch (Exception e) {
			this.is = new ItemStack(Material.PAPER);
			AdvancedCorePlugin.getInstance().debug("Invalid material: " + material);
		}
	}

	/**
	 * Adds an attribute modifier to the item.
	 * 
	 * @param att the attribute
	 * @param modifier the attribute modifier
	 * @return this ItemBuilder
	 */
	public ItemBuilder addAttributeModifier(Attribute att, AttributeModifier modifier) {
		ItemMeta im = is.getItemMeta();
		im.addAttributeModifier(att, modifier);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Add an enchant to the item.
	 *
	 * @param ench  The enchant to add
	 * @param level The level
	 * @return ItemBuilder
	 */
	public ItemBuilder addEnchant(Enchantment ench, int level) {
		if (ench != null) {
			if (is.getType().equals(Material.ENCHANTED_BOOK)) {
				EnchantmentStorageMeta im = (EnchantmentStorageMeta) is.getItemMeta();
				im.addStoredEnchant(ench, level, true);
				is.setItemMeta(im);
			} else {
				is.addUnsafeEnchantment(ench, level);
			}
		}
		return this;
	}

	/**
	 * Add multiple enchants at once.
	 *
	 * @param enchants The enchants to add.
	 * @return ItemBuilder
	 */
	@SuppressWarnings("deprecation")
	public ItemBuilder addEnchantments(HashMap<String, Integer> enchants) {
		if ((enchants == null) || (enchants.size() == 0)) {
			return this;
		}
		HashMap<Enchantment, Integer> enchantments = new HashMap<>();
		for (String enchant : enchants.keySet()) {
			try {
				if (!NMSManager.getInstance().isVersion("1.12")) {
					Enchantment ench = Enchantment.getByKey(NamespacedKey.minecraft(enchant));
					if (ench == null) {
						for (Enchantment en : Enchantment.values()) {
							if (en.toString().equalsIgnoreCase(enchant)) {
								ench = en;
							}
						}
					}
					if (ench != null) {
						enchantments.put(ench, enchants.get(enchant));
					} else {
						AdvancedCorePlugin.getInstance().getLogger()
								.warning("Invalid enchantment: " + enchant + ", Path: " + path);
					}
				} else {
					for (Enchantment en : Enchantment.values()) {
						if (en.toString().equalsIgnoreCase(enchant)) {
							enchantments.put(en, enchants.get(enchant));
						}
					}

				}
			} catch (Exception e) {
				AdvancedCorePlugin.getInstance().getLogger().warning("Failed to add enchantment: " + enchant);
				e.printStackTrace();
			}
		}
		return addEnchantments(enchantments);
	}

	/**
	 * Add multiple enchants at once.
	 *
	 * @param enchantments The enchants to add.
	 * @return ItemBuilder
	 */
	public ItemBuilder addEnchantments(Map<Enchantment, Integer> enchantments) {
		for (Entry<Enchantment, Integer> entry : enchantments.entrySet()) {
			addEnchant(entry.getKey(), entry.getValue());
		}
		return this;
	}

	/**
	 * Adds a glow effect to the item.
	 * 
	 * @return this ItemBuilder
	 */
	public ItemBuilder addGlow() {
		ItemMeta meta = is.getItemMeta();

		try {
			// for newer minecraft versions
			meta.setEnchantmentGlintOverride(true);
			if (meta.getClass().getMethod("setEnchantmentGlintOverride", boolean.class) != null) {
				meta.getClass().getMethod("setEnchantmentGlintOverride", boolean.class).invoke(meta, true);
				is.setItemMeta(meta);
				return this;
			}
		} catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException
				| SecurityException e) {
			//e.printStackTrace();
		}

		meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
		is.setItemMeta(meta);

		is.addUnsafeEnchantment(MiscUtils.getInstance().getEnchant("LOOTING", "LUCK"), 1);
		return this;
	}

	/**
	 * Adds an item flag to the item.
	 * 
	 * @param flag the flag name
	 * @return this ItemBuilder
	 */
	public ItemBuilder addItemFlag(String flag) {
		try {
			ItemMeta meta = is.getItemMeta();
			meta.addItemFlags(ItemFlag.valueOf(flag));
			is.setItemMeta(meta);
		} catch (Exception ex) {
			AdvancedCorePlugin.getInstance().debug("Invalid flag: " + flag);
		}
		return this;
	}

	/**
	 * Add a lore line.
	 *
	 * @param line The lore line to add.
	 * @return ItemBuilder
	 */
	public ItemBuilder addLoreLine(String line) {
		if (line != null) {
			ItemMeta im = is.getItemMeta();
			if (im != null) {
				List<String> lore = new ArrayList<>();
				if (im.hasLore()) {
					lore = new ArrayList<>(im.getLore());
				}
				for (String str : line.split("%NewLine%")) {
					lore.add(str);
				}
				setLore(lore);
			}
		}
		return this;
	}

	/**
	 * Add a lore line.
	 *
	 * @param line The lore line to add.
	 * @param pos  The index of where to put it.
	 * @return ItemBuilder
	 */
	public ItemBuilder addLoreLine(String line, int pos) {
		ItemMeta im = is.getItemMeta();
		if (im != null) {
			List<String> lore = new ArrayList<>(im.getLore());
			lore.set(pos, line);
			return setLore(lore);
		}
		return this;
	}

	/**
	 * Adds a placeholder mapping.
	 * 
	 * @param placeholders the map of placeholders
	 * @return this ItemBuilder
	 */
	public ItemBuilder addPlaceholder(HashMap<String, String> placeholders) {
		if (placeholders != null) {
			this.placeholders.putAll(placeholders);
		}
		return this;
	}

	/**
	 * Adds a single placeholder.
	 * 
	 * @param toReplace the text to replace
	 * @param replaceWith the replacement text
	 * @return this ItemBuilder
	 */
	public ItemBuilder addPlaceholder(String toReplace, String replaceWith) {
		placeholders.put(toReplace, replaceWith);
		return this;
	}

	/**
	 * Adds a potion effect to the item.
	 * 
	 * @param type the potion effect type
	 * @param duration the duration
	 * @param amplifier the amplifier
	 * @param color the potion color
	 * @return this ItemBuilder
	 */
	public ItemBuilder addPotionEffect(PotionEffectType type, int duration, int amplifier, Color color) {
		PotionMeta meta = (PotionMeta) is.getItemMeta();
		meta.addCustomEffect(new PotionEffect(type, duration, amplifier), false);
		if (color != null) {
			meta.setColor(color);
		}
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
		}
		return false;
	}

	private ItemBuilder checkLoreLength() {
		int loreLength = getLoreLength();
		ArrayList<String> currentLore = getLore();
		ArrayList<String> newLore = new ArrayList<>();
		for (String lore : currentLore) {
			StringBuilder builder = new StringBuilder();
			int count = 0;
			for (char character : lore.toCharArray()) {
				count++;
				builder.append(character);
				if (count > loreLength && character == ' ') {
					String str = builder.toString();
					builder = new StringBuilder();
					builder.append(ChatColor.getLastColors(str));
					if (!ChatColor.stripColor(str).isEmpty()) {
						newLore.add(str);
					}
					count = 0;
				}
			}
			String s = builder.toString();
			if (!ChatColor.stripColor(s).isEmpty()) {
				newLore.add(builder.toString());
			}
		}
		setLore(newLore);
		return this;
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

	/**
	 * Creates configuration data from this ItemBuilder.
	 * 
	 * @return the configuration data map
	 */
	@SuppressWarnings("deprecation")
	public LinkedHashMap<String, Object> createConfigurationData() {
		LinkedHashMap<String, Object> data = new LinkedHashMap<>();
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

		ArrayList<String> flags = new ArrayList<>();
		for (ItemFlag fl : is.getItemMeta().getItemFlags()) {
			flags.add(fl.toString());
		}

		data.put("ItemFlags", flags);

		data.put("Unbreakable", is.getItemMeta().isUnbreakable());

		data.put("Skull", getSkull());

		return data;

	}

	/**
	 * Disables lore length checking.
	 * 
	 * @return this ItemBuilder
	 */
	public ItemBuilder dontCheckLoreLength() {
		checkLoreLength = false;
		return this;
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

	/**
	 * Gets the attribute modifiers multimap.
	 * 
	 * @return the attribute modifiers
	 */
	public Multimap<Attribute, AttributeModifier> getAttributeModifiers() {
		return is.getItemMeta().getAttributeModifiers();
	}

	/**
	 * Gets the attribute modifiers for a specific attribute.
	 * 
	 * @param att the attribute
	 * @return the collection of attribute modifiers
	 */
	public Collection<AttributeModifier> getAttributeModifiers(Attribute att) {
		return is.getItemMeta().getAttributeModifiers(att);
	}

	/**
	 * Gets a conditional ItemBuilder based on player conditions.
	 * 
	 * @param player the player
	 * @return the conditional ItemBuilder or this if no conditions match
	 */
	public ItemBuilder getConditionItemBuilder(OfflinePlayer player) {
		return setConditional(new JavascriptEngine().addPlayer(player));
	}

	/**
	 * Gets the configuration map.
	 * 
	 * @param deseralize whether to deserialize the item
	 * @return the configuration map
	 */
	@SuppressWarnings("deprecation")
	public Map<String, Object> getConfiguration(boolean deseralize) {
		if (deseralize) {
			return is.serialize();
		}
		HashMap<String, Object> map = new HashMap<>();
		map.put("Material", is.getType().toString());
		map.put("Amount", is.getAmount());
		if (hasCustomDisplayName()) {
			map.put("Name", getName());
		}
		if (hasCustomLore()) {
			map.put("Lore", getLore());
		}
		ItemMeta im = is.getItemMeta();
		for (Entry<Enchantment, Integer> entry : im.getEnchants().entrySet()) {
			map.put("Enchants." + entry.getKey().getKeyOrThrow().getKey(), entry.getValue().intValue());
		}

		ArrayList<String> flagList = new ArrayList<>();
		for (ItemFlag flag : im.getItemFlags()) {
			flagList.add(flag.toString());
		}
		map.put("ItemFlags", flagList);

		if (im.hasCustomModelData()) {
			map.put("CustomModelData", im.getCustomModelData());
		}

		if (hasGetItemModel(im)) {
			map.put("ItemModel", ItemModelHandler.getModel(is));
		}

		return map;

	}

	/**
	 * Checks if the ItemMeta has getItemModel method.
	 * 
	 * @param meta the item meta
	 * @return true if the method exists, false otherwise
	 */
	public boolean hasGetItemModel(ItemMeta meta) {
		try {
			Method method = meta.getClass().getMethod("getItemModel");
			return method != null;
		} catch (NoSuchMethodException e) {
			return false;
		}
	}

	/**
	 * Gets custom data from persistent data container.
	 * 
	 * @param key the data key
	 * @return the custom data value
	 */
	public String getCustomData(String key) {
		NamespacedKey namespace = new NamespacedKey(AdvancedCorePlugin.getInstance(), key);
		ItemMeta itemMeta = is.getItemMeta();
		PersistentDataContainer tagContainer = itemMeta.getPersistentDataContainer();
		if (tagContainer.has(namespace, PersistentDataType.STRING)) {
			return tagContainer.get(namespace, PersistentDataType.STRING);
		}
		return null;
	}

	/**
	 * Gets the lore of the item.
	 * 
	 * @return the lore list
	 */
	public ArrayList<String> getLore() {
		if (hasCustomLore()) {
			List<String> lore = is.getItemMeta().getLore();
			ArrayList<String> list = new ArrayList<>();
			if (lore != null) {
				list.addAll(lore);
			}
			return list;
		}
		return new ArrayList<>();

	}

	/**
	 * Gets the lore length setting.
	 * 
	 * @return the lore length
	 */
	public int getLoreLength() {
		if (loreLength < 0) {
			return AdvancedCorePlugin.getInstance().getOptions().getNewLoreLength();
		}
		return loreLength;
	}

	/**
	 * Gets the item name.
	 * 
	 * @return the item name
	 */
	public String getName() {
		if (hasCustomDisplayName()) {
			return is.getItemMeta().getDisplayName();
		}
		return "";
	}

	/**
	 * Gets the rewards path for the player.
	 * 
	 * @param player the player
	 * @return the rewards path
	 */
	public String getRewardsPath(Player player) {
		if (conditional) {
			JavascriptEngine engine = new JavascriptEngine().addPlayer(player);
			String value = engine.getStringValue(javascriptConditional);
			return "Conditional." + value + ".Rewards";
		}
		return "Rewards";
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

	/**
	 * Gets the skull owner.
	 * 
	 * @return the skull owner
	 */
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
	 * Gets the item material type.
	 * 
	 * @return the material
	 */
	public Material getType() {
		return is.getType();
	}

	/**
	 * Checks if the item has attributes.
	 * 
	 * @return true if the item has attributes, false otherwise
	 */
	public boolean hasAttributes() {
		return is.getItemMeta().hasAttributeModifiers();
	}

	/**
	 * Checks if the item has a custom display name.
	 * 
	 * @return true if the item has a custom display name, false otherwise
	 */
	public boolean hasCustomDisplayName() {
		if (hasItemMeta()) {
			return is.getItemMeta().hasDisplayName();
		}
		return false;
	}

	/**
	 * Checks if the item has custom lore.
	 * 
	 * @return true if the item has lore, false otherwise
	 */
	public boolean hasCustomLore() {
		if (hasItemMeta()) {
			return is.getItemMeta().hasLore();
		}
		return false;
	}

	/**
	 * Checks if the item has item meta.
	 * 
	 * @return true if the item has meta, false otherwise
	 */
	public boolean hasItemMeta() {
		return is.hasItemMeta();
	}

	/**
	 * Parses placeholders in the item for the player.
	 * 
	 * @param player the player
	 * @return the itemstack with parsed placeholders
	 */
	public ItemStack parsePlaceholders(OfflinePlayer player) {
		if (player == null) {
			return toItemStack();
		}
		setName(MessageAPI.colorize(PlaceholderUtils.replaceJavascript(player,
				PlaceholderUtils.replacePlaceHolder(getName(), placeholders))));
		setLore(ArrayUtils.colorize(PlaceholderUtils.replaceJavascript(player,
				PlaceholderUtils.replacePlaceHolder(getLore(), placeholders))));
		if (skull.contains("%")) {
			setSkullOwner(PlaceholderUtils.replaceJavascript(player,
					PlaceholderUtils.replacePlaceHolder(skull, placeholders)));
		}
		return is;
	}

	/**
	 * Remove a certain enchant from the item.
	 *
	 * @param ench The enchantment to remove
	 * @return ItemBuilder
	 */
	public ItemBuilder removeEnchantment(Enchantment ench) {
		is.removeEnchantment(ench);
		return this;
	}

	/**
	 * Removes all lore from the item.
	 * 
	 * @return this ItemBuilder
	 */
	public ItemBuilder removeLore() {
		setLore(new String[0]);
		return this;
	}

	/**
	 * Remove a lore line.
	 *
	 * @param index The index of the lore line to remove.
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
	 * @param line The lore to remove.
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

	/**
	 * Sets the amount of items.
	 * 
	 * @param amount the amount to set
	 * @return this ItemBuilder
	 */
	public ItemBuilder setAmount(int amount) {
		is.setAmount(amount);
		return this;
	}

	/**
	 * Sets the amount only if current amount is zero.
	 * 
	 * @param i the amount to set
	 * @return this ItemBuilder
	 */
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

	private ItemBuilder setConditional(JavascriptEngine engine) {
		if (conditional) {
			String value = engine.getStringValue(javascriptConditional);
			ConfigurationSection data = conditionalValues.getConfigurationSection(value);
			if (data != null) {
				return new ItemBuilder(data);
			}
		}
		return null;
	}

	/**
	 * Sets custom data in the persistent data container.
	 * 
	 * @param key the data key
	 * @param value the data value
	 * @return this ItemBuilder
	 */
	public ItemBuilder setCustomData(String key, String value) {
		NamespacedKey namespace = new NamespacedKey(AdvancedCorePlugin.getInstance(), key);
		ItemMeta itemMeta = is.getItemMeta();
		itemMeta.getPersistentDataContainer().set(namespace, PersistentDataType.STRING, value);
		is.setItemMeta(itemMeta);
		return this;
	}

	/**
	 * Sets the custom model data.
	 * 
	 * @param data the custom model data
	 * @return this ItemBuilder
	 */
	@SuppressWarnings("deprecation")
	public ItemBuilder setCustomModelData(int data) {
		ItemMeta im = is.getItemMeta();
		im.setCustomModelData(data);
		is.setItemMeta(im);
		return this;
	}

	/**
	 * Change the durability of the item.
	 *
	 * @param dur The durability to set it to.
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
	 * @param color The color to put.
	 * @return ItemBuilder
	 */
	@Deprecated
	public ItemBuilder setDyeColor(DyeColor color) {
		is.setDurability(color.getDyeData());
		return this;
	}

	/**
	 * Sets the firework power.
	 * 
	 * @param power the power level
	 * @return this ItemBuilder
	 */
	public ItemBuilder setFireworkPower(int power) {
		try {
			FireworkMeta meta = (FireworkMeta) is.getItemMeta();
			meta.setPower(power);
			is.setItemMeta(meta);
		} catch (Exception e) {
			e.printStackTrace();
		}

		return this;
	}

	/**
	 * Sets the skull head from base64 texture.
	 * 
	 * @param value the base64 texture value
	 * @return this ItemBuilder
	 */
	public ItemBuilder setHeadFromBase64(String value) {
		is = SkullCache.getSkullBase64(value);
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
	 * @param color The color to set it to.
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
	 * @param lore The lore to set it to.
	 *
	 * @return ItemBuilder
	 */
	public ItemBuilder setLore(List<String> lore) {
		List<String> list = new ArrayList<>();
		for (String str : lore) {
			for (String s : str.split("%NewLine%")) {
				list.add(s);
			}
		}
		ItemMeta im = is.getItemMeta();
		if (im != null) {
			im.setLore(list);
			is.setItemMeta(im);
		}
		return this;
	}

	/**
	 * Re-sets the lore.
	 *
	 * @param lore The lore to set it to.
	 * @return ItemBuilder
	 */
	public ItemBuilder setLore(String... lore) {
		return setLore(ArrayUtils.convert(lore));
	}

	/**
	 * Sets the lore length for wrapping.
	 * 
	 * @param length the lore length
	 * @return this ItemBuilder
	 */
	public ItemBuilder setLoreLength(int length) {
		loreLength = length;
		return this;
	}

	/**
	 * Set the displayname of the item.
	 *
	 * @param name The name to change it to.
	 * @return ItemBuilder
	 */
	public ItemBuilder setName(String name) {
		ItemMeta im = is.getItemMeta();
		if (im != null) {
			im.setDisplayName(name);
			is.setItemMeta(im);
		}
		return this;
	}

	/**
	 * Sets the item name only if it doesn't already have a custom display name.
	 * 
	 * @param name the name to set
	 * @return this ItemBuilder
	 */
	public ItemBuilder setNameIfNotExist(String name) {
		if (!hasCustomDisplayName()) {
			setName(name);
		}
		return this;
	}

	/**
	 * Sets the placeholder mappings.
	 * 
	 * @param placeholders the placeholders map
	 * @return this ItemBuilder
	 */
	public ItemBuilder setPlaceholders(HashMap<String, String> placeholders) {
		this.placeholders = placeholders;
		return this;
	}

	/**
	 * Sets the skull owner by offline player.
	 * 
	 * @param offlinePlayer the offline player
	 * @return this ItemBuilder
	 */
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
	 * @param owner The name of the skull's owner.
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

	/**
	 * Sets the slot for the item in an inventory.
	 * 
	 * @param slot the slot number
	 * @return this ItemBuilder
	 */
	public ItemBuilder setSlot(int slot) {
		this.slot = slot;
		return this;
	}

	/**
	 * Sets whether the item is unbreakable.
	 * 
	 * @param unbreakable whether the item should be unbreakable
	 * @return this ItemBuilder
	 */
	public ItemBuilder setUnbreakable(boolean unbreakable) {
		try {
			ItemMeta meta = is.getItemMeta();
			if (meta != null) {
				meta.setUnbreakable(unbreakable);
				is.setItemMeta(meta);
			}
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
		if (conditional) {
			return setConditional(new JavascriptEngine()).toItemStack();
		}

		setName(MessageAPI.colorize(
				PlaceholderUtils.replaceJavascript(PlaceholderUtils.replacePlaceHolder(getName(), placeholders))));
		setLore(ArrayUtils.colorize(
				PlaceholderUtils.replaceJavascript(PlaceholderUtils.replacePlaceHolder(getLore(), placeholders))));
		if (checkLoreLength) {
			checkLoreLength();
		}
		return is;
	}

	/**
	 * Converts the ItemBuilder to an ItemStack with player-specific placeholders.
	 * 
	 * @param player the offline player for placeholder replacement
	 * @return the final itemstack
	 */
	public ItemStack toItemStack(OfflinePlayer player) {
		if (!placeholders.containsKey("player")) {
			placeholders.put("player", player.getName());
		}
		if (conditional) {
			return getConditionItemBuilder(player).setPlaceholders(placeholders).toItemStack(player);
		}
		parsePlaceholders(player);
		if (checkLoreLength) {
			checkLoreLength();
		}
		return is;
	}

	/**
	 * Converts the ItemBuilder to an ItemStack with player-specific placeholders.
	 * 
	 * @param player the player for placeholder replacement
	 * @return the final itemstack
	 */
	public ItemStack toItemStack(Player player) {
		if (!placeholders.containsKey("player")) {
			placeholders.put("player", player.getName());
		}
		if (conditional) {
			return setConditional(new JavascriptEngine().addPlayer(player)).setPlaceholders(placeholders)
					.toItemStack(player);
		}
		parsePlaceholders(player);
		if (checkLoreLength) {
			checkLoreLength();
		}
		return is;
	}

	@Override
	public String toString() {
		return is.toString();
	}
}
