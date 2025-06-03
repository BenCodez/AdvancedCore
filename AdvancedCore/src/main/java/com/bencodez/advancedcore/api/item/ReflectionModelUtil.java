package com.bencodez.advancedcore.api.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;

public class ReflectionModelUtil {

    // Runtime flags
    private static final boolean HAS_STRING_COMPONENT;
    private static final boolean HAS_INT_CUSTOM_MODEL;

    // Cached reflection handles for string-model (1.20.3+)
    private static Class<?> customModelComponentClass;
    private static Method getCustomModelComponent;
    private static Method setCustomModelComponent;
    private static Method componentSetStrings;
    private static Constructor<?> componentConstructor;

    // Cached reflection handles for integer CustomModelData (1.14+)
    private static Method setCustomModelDataInt;
    private static Method hasCustomModelDataInt;
    private static Method getCustomModelDataInt;

    static {
        boolean hasString = false;
        boolean hasInt = false;

        // Detect string-model API (Spigot 1.20.3+)
        try {
            customModelComponentClass = Class.forName(
                "org.bukkit.inventory.meta.components.CustomModelDataComponent"
            );
            getCustomModelComponent = ItemMeta.class.getMethod("getCustomModelDataComponent");
            setCustomModelComponent = ItemMeta.class.getMethod(
                "setCustomModelDataComponent", customModelComponentClass
            );
            componentSetStrings = customModelComponentClass.getMethod("setStrings", List.class);
            try {
                componentConstructor = customModelComponentClass.getConstructor();
            } catch (NoSuchMethodException e) {
                componentConstructor = null;
            }
            hasString = true;
        } catch (ClassNotFoundException | NoSuchMethodException ignore) {
            hasString = false;
        }

        // Detect integer CustomModelData methods (Spigot 1.14+)
        try {
            setCustomModelDataInt = ItemMeta.class.getMethod("setCustomModelData", int.class);
            hasCustomModelDataInt = ItemMeta.class.getMethod("hasCustomModelData");
            getCustomModelDataInt = ItemMeta.class.getMethod("getCustomModelData");
            hasInt = true;
        } catch (NoSuchMethodException ignore) {
            hasInt = false;
        }

        HAS_STRING_COMPONENT = hasString;
        HAS_INT_CUSTOM_MODEL = hasInt;
    }

    /**
     * Apply custom-model data to the given ItemStack.
     *
     * If modelData is a String or List&lt;String&gt; and the string-model API exists,
     * it writes those string keys.
     * Otherwise, if modelData is an Integer and the integer CustomModelData API exists,
     * it writes that int.
     * If neither path is available, it returns the original ItemStack unchanged.
     *
     * @param item      The ItemStack to modify (cloned internally).
     * @param modelData Either an Integer, a String, or a List&lt;String&gt;:
     *                  - Integer -> sets integer CustomModelData (1.14+)
     *                  - String  -> treated as a single-element List&lt;String&gt; (1.20.3+)
     *                  - List&lt;String&gt; -> writes multiple string keys (1.20.3+)
     * @return A new ItemStack with custom model data applied if supported, or the original clone if not.
     */
    @SuppressWarnings("unchecked")
    public static ItemStack applyCustomModel(ItemStack item, Object modelData) {
        if (item == null || modelData == null) {
            return item;
        }
        ItemStack clone = item.clone();
        ItemMeta meta = clone.getItemMeta();
        if (meta == null) {
            return clone;
        }

        try {
            // 1) String-model branch (Spigot 1.20.3+)
            if (HAS_STRING_COMPONENT) {
                List<String> stringsToSet = null;

                if (modelData instanceof String) {
                    stringsToSet = Collections.singletonList((String) modelData);
                } else if (modelData instanceof List<?>) {
                    @SuppressWarnings("rawtypes")
                    List raw = (List) modelData;
                    boolean allStrings = raw.stream().allMatch(o -> o instanceof String);
                    if (allStrings) {
                        stringsToSet = (List<String>) raw;
                    }
                }

                if (stringsToSet != null) {
                    Object compInstance = getCustomModelComponent.invoke(meta);
                    if (compInstance == null && componentConstructor != null) {
                        compInstance = componentConstructor.newInstance();
                    }
                    if (compInstance != null) {
                        componentSetStrings.invoke(compInstance, stringsToSet);
                        setCustomModelComponent.invoke(meta, compInstance);
                        clone.setItemMeta(meta);
                        return clone;
                    }
                }
                // If modelData wasn't a String or List<String>, fall through to integer branch
            }

            // 2) Integer-only branch (Spigot 1.14+)
            if (HAS_INT_CUSTOM_MODEL && modelData instanceof Integer) {
                int intValue = (Integer) modelData;
                setCustomModelDataInt.invoke(meta, intValue);
                clone.setItemMeta(meta);
                return clone;
            }

            // 3) Unsupported combination: return original
            return clone;

        } catch (Exception e) {
            e.printStackTrace();
            return clone;
        }
    }

    /**
     * Read back the custom-model data from an ItemStack.
     *
     * If running on 1.20.3+, returns the List&lt;String&gt; of string-model keys.
     * Else if running on 1.14+, returns the integer CustomModelData.
     * Otherwise returns null.
     *
     * @param item The ItemStack to inspect.
     * @return Either:
     *         - List&lt;String&gt; of string-model keys,
     *         - Integer of the integer CustomModelData,
     *         - or null if none is found / unsupported.
     */
    @SuppressWarnings("unchecked")
    public static Object readCustomModel(ItemStack item) {
        if (item == null) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }

        try {
            // 1) String-model path (1.20.3+)
            if (HAS_STRING_COMPONENT) {
                Object compInstance = getCustomModelComponent.invoke(meta);
                if (compInstance != null) {
                    Method getStrings = customModelComponentClass.getMethod("getStrings");
                    return (List<String>) getStrings.invoke(compInstance);
                }
                return null;
            }

            // 2) Integer-only path (1.14+)
            if (HAS_INT_CUSTOM_MODEL) {
                boolean has = (boolean) hasCustomModelDataInt.invoke(meta);
                if (has) {
                    return (Integer) getCustomModelDataInt.invoke(meta);
                }
                return null;
            }

            // 3) Unsupported
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Check whether the ItemStack has any custom model data.
     *
     * Returns true if there are string-model keys (1.20.3+),
     * or if there is integer CustomModelData (1.14+).
     * Otherwise returns false.
     *
     * @param item The ItemStack to check.
     * @return True if custom model data exists, false otherwise.
     */
    public static boolean hasCustomModel(ItemStack item) {
        if (item == null) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        try {
            // 1) String-model check (1.20.3+)
            if (HAS_STRING_COMPONENT) {
                Object compInstance = getCustomModelComponent.invoke(meta);
                if (compInstance != null) {
                    Method getStrings = customModelComponentClass.getMethod("getStrings");
                    @SuppressWarnings("unchecked")
                    List<String> strings = (List<String>) getStrings.invoke(compInstance);
                    return strings != null && !strings.isEmpty();
                }
            }

            // 2) Integer-only check (1.14+)
            if (HAS_INT_CUSTOM_MODEL) {
                return (boolean) hasCustomModelDataInt.invoke(meta);
            }

            // 3) No supported API
            return false;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
