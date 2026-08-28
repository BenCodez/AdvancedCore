from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[2]


def write(relative, content):
    path = ROOT / relative
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content.rstrip() + "\n", encoding="utf-8")


def replace_once(text, old, new, label):
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one {label} match, found {count}")
    return text.replace(old, new, 1)


write("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/javascript/JavascriptEngine.java", r'''
package com.bencodez.advancedcore.api.javascript;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import javax.script.Bindings;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptException;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderBinder.PreparedJavascript;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;
import com.bencodez.simpleapi.messages.MessageAPI;

public class JavascriptEngine {
    private final HashMap<String, Object> engineAPI;
    private final HashMap<String, String> placeholders;
    private OfflinePlayer placeholderPlayer;

    public JavascriptEngine() {
        engineAPI = new HashMap<>();
        placeholders = new HashMap<>();
    }

    public JavascriptEngine addPlayer(AdvancedCoreUser user) {
        placeholderPlayer = user.getOfflinePlayer();
        addToEngine("PlayerName", user.getPlayerName());
        addToEngine("PlayerUUID", user.getUUID());
        addToEngine("AdvancedCoreUser", user);
        for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance().getJavascriptEngineRequests()) {
            addToEngine(request.getStr(), request.getObject(user.getOfflinePlayer()));
        }
        if (user.isOnline()) {
            return addPlayer(user.getPlayer());
        }
        return this;
    }

    public JavascriptEngine addPlayer(CommandSender player) {
        addToEngine("CommandSender", player);
        if (player instanceof Player) {
            Player onlinePlayer = (Player) player;
            placeholderPlayer = onlinePlayer;
            addToEngine("Player", onlinePlayer);
            addToEngine("PlayerName", onlinePlayer.getName());
            addToEngine("PlayerUUID", onlinePlayer.getUniqueId().toString());
            addToEngine("AdvancedCoreUser", AdvancedCorePlugin.getInstance().getUserManager().getUser(onlinePlayer));
            for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance()
                    .getJavascriptEngineRequests()) {
                addToEngine(request.getStr(), request.getObject(onlinePlayer));
            }
        } else {
            addToEngine("Player", player);
        }
        return this;
    }

    public JavascriptEngine addPlayer(OfflinePlayer player) {
        placeholderPlayer = player;
        addToEngine("Player", player);
        addToEngine("PlayerName", player.getName());
        addToEngine("PlayerUUID", player.getUniqueId().toString());
        addToEngine("AdvancedCoreUser", AdvancedCorePlugin.getInstance().getUserManager().getUser(player));
        addToEngine("CommandSender", player);
        for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance().getJavascriptEngineRequests()) {
            addToEngine(request.getStr(), request.getObject(player));
        }
        if (player.isOnline()) {
            return addPlayer(player.getPlayer());
        }
        return this;
    }

    public JavascriptEngine addPlayer(Player player) {
        if (player != null) {
            placeholderPlayer = player;
            addToEngine("Player", player);
            addToEngine("PlayerName", player.getName());
            addToEngine("PlayerUUID", player.getUniqueId().toString());
            addToEngine("AdvancedCoreUser", AdvancedCorePlugin.getInstance().getUserManager().getUser(player));
            addToEngine("CommandSender", player);
            for (JavascriptPlaceholderRequest request : AdvancedCorePlugin.getInstance()
                    .getJavascriptEngineRequests()) {
                addToEngine(request.getStr(), request.getObject(player));
            }
        }
        return this;
    }

    public JavascriptEngine addPlaceholders(Map<String, String> values) {
        if (values != null && !values.isEmpty()) {
            placeholders.putAll(values);
        }
        return this;
    }

    public JavascriptEngine addToEngine(HashMap<String, Object> values) {
        if (values != null && !values.isEmpty()) {
            engineAPI.putAll(values);
        }
        return this;
    }

    public JavascriptEngine addToEngine(String text, Object object) {
        engineAPI.put(text, object);
        return this;
    }

    public void execute(String expression) {
        getResult(expression);
    }

    public boolean getBooleanValue(String expression) {
        Object result = getResult(expression);
        if (result instanceof Boolean) {
            return ((Boolean) result).booleanValue();
        }
        return result != null && Boolean.parseBoolean(result.toString());
    }

    public Object getResult(String expression) {
        if (expression == null || expression.isEmpty()) {
            return null;
        }
        if (!AdvancedCorePlugin.getInstance().getOptions().isJavascriptEngineEnabled()) {
            return null;
        }

        PreparedJavascript prepared;
        try {
            prepared = JavascriptPlaceholderBinder.prepare(expression, placeholderPlayer, placeholders);
        } catch (IllegalArgumentException exception) {
            AdvancedCorePlugin.getInstance().getLogger()
                    .warning("Refusing to evaluate unsafe or invalid javascript: " + exception.getMessage());
            AdvancedCorePlugin.getInstance().debug(exception);
            return null;
        }

        ScriptEngine engine = JavascriptEngineHandler.getInstance().getJSScriptEngine();
        if (engine == null) {
            AdvancedCorePlugin.getInstance().debug("Failed to process javascript, engine == null");
            return null;
        }

        engine.put("Bukkit", Bukkit.getServer());
        engine.put("AdvancedCore", AdvancedCorePlugin.getInstance());
        engine.put("Console", Bukkit.getConsoleSender());
        engine.put("UserManager", AdvancedCorePlugin.getInstance().getUserManager());
        engine.put("RewardHandler", AdvancedCorePlugin.getInstance().getRewardHandler());
        engine.put("MessageAPI", MessageAPI.class);
        engineAPI.putAll(AdvancedCorePlugin.getInstance().getJavascriptEngine());
        for (Entry<String, Object> entry : engineAPI.entrySet()) {
            engine.put(entry.getKey(), entry.getValue());
        }

        Bindings engineBindings = engine.getBindings(ScriptContext.ENGINE_SCOPE);
        prepared.getBindings().forEach(engineBindings::put);
        try {
            return engine.eval(prepared.getSource());
        } catch (ScriptException exception) {
            AdvancedCorePlugin.getInstance().getLogger().warning(
                    "Error occurred while evaluating javascript, turn debug on to see stacktrace: " + exception);
            AdvancedCorePlugin.getInstance().debug(exception);
        } finally {
            prepared.getBindings().keySet().forEach(engineBindings::remove);
        }
        return null;
    }

    public String getStringValue(String expression) {
        try {
            Object result = getResult(expression);
            if (result != null) {
                return result.toString();
            }
        } catch (Exception exception) {
            AdvancedCorePlugin.getInstance().debug(exception);
        }
        return "";
    }
}
''')

placeholder_path = ROOT / "AdvancedCore/src/main/java/com/bencodez/advancedcore/api/messages/PlaceholderUtils.java"
placeholder = placeholder_path.read_text(encoding="utf-8")
placeholder = replace_once(placeholder,
        "import com.bencodez.advancedcore.api.javascript.JavascriptEngine;\n",
        "import com.bencodez.advancedcore.api.javascript.JavascriptEngine;\n"
        "import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderValue;\n",
        "PlaceholderUtils JavaScript import")

start = placeholder.index("\tpublic static String replaceJavascript(String text, JavascriptEngine engine) {")
end = placeholder.index("\n\tpublic static ArrayList<String> replacePlaceHolder(", start)
placeholder = placeholder[:start] + r'''	public static String replaceJavascript(String text, JavascriptEngine engine) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		if (engine == null) {
			engine = new JavascriptEngine();
		}
		return AuthoredJavascriptText.evaluate(text, engine);
	}

	private static String replaceJavascript(String text, JavascriptEngine engine, OfflinePlayer player) {
		return replaceJavascript(text, engine);
	}
''' + placeholder[end:]

start = placeholder.index("\tpublic static ArrayList<String> replacePlaceHolder(")
end = placeholder.index("\n\tpublic static ArrayList<String> replacePlaceHolders(ArrayList<String> list, Player p)", start)
placeholder = placeholder[:start] + r'''	public static ArrayList<String> replacePlaceHolder(ArrayList<String> list, HashMap<String, String> placeholders) {
		ArrayList<String> newList = new ArrayList<>();
		for (String value : list) {
			newList.add(replacePlaceHolder(value, placeholders));
		}
		return newList;
	}

	public static String replacePlaceHolder(String str, HashMap<String, String> placeholders) {
		return replacePlaceHolder(str, placeholders, true);
	}

	public static String replacePlaceHolder(String str, HashMap<String, String> placeholders, boolean ignoreCase) {
		if (placeholders == null) {
			return str;
		}
		return AuthoredJavascriptText.transform(str,
				value -> replacePlaceHolderMapRaw(value, placeholders, ignoreCase),
				value -> replacePlaceHolderMapEncoded(value, placeholders, ignoreCase));
	}

	/**
	 * Replace place holder.
	 *
	 * @param str         the str
	 * @param toReplace   the to replace
	 * @param replaceWith the replace with
	 * @return the string
	 */
	public static String replacePlaceHolder(String str, String toReplace, String replaceWith) {
		return replacePlaceHolder(str, toReplace, replaceWith, true);
	}

	public static String replacePlaceHolder(String str, String toReplace, String replaceWith, boolean ignoreCase) {
		return AuthoredJavascriptText.transform(str,
				value -> replacePlaceHolderRaw(value, toReplace, replaceWith, ignoreCase),
				value -> replacePlaceHolderRaw(value, toReplace, JavascriptPlaceholderValue.encode(replaceWith),
						ignoreCase));
	}

	private static String replacePlaceHolderMapRaw(String str, HashMap<String, String> placeholders,
			boolean ignoreCase) {
		String result = str;
		for (Entry<String, String> entry : placeholders.entrySet()) {
			result = replacePlaceHolderRaw(result, entry.getKey(), entry.getValue(), ignoreCase);
		}
		return result;
	}

	private static String replacePlaceHolderMapEncoded(String str, HashMap<String, String> placeholders,
			boolean ignoreCase) {
		String result = str;
		for (Entry<String, String> entry : placeholders.entrySet()) {
			result = replacePlaceHolderRaw(result, entry.getKey(), JavascriptPlaceholderValue.encode(entry.getValue()),
					ignoreCase);
		}
		return result;
	}

	private static String replacePlaceHolderRaw(String str, String toReplace, String replaceWith, boolean ignoreCase) {
		if (ignoreCase) {
			return MessageAPI.replaceIgnoreCase(MessageAPI.replaceIgnoreCase(str, "%" + toReplace + "%", replaceWith),
					"\\{" + toReplace + "\\}", replaceWith);
		}
		str = str.replaceAll("\\{", "%");
		str = str.replaceAll("\\}", "%");
		return str.replace("%" + toReplace + "%", replaceWith);
	}
''' + placeholder[end:]

start = placeholder.index("\tpublic static String replacePlaceHolders(OfflinePlayer player, String text) {")
end = placeholder.rfind("\n}")
placeholder = placeholder[:start] + r'''	public static String replacePlaceHolders(OfflinePlayer player, String text) {
		if (player == null) {
			return text;
		}
		if (AdvancedCorePlugin.getInstance().isPlaceHolderAPIEnabled()) {
			return AuthoredJavascriptText.transform(text,
					value -> PlaceholderAPI.setPlaceholders(player, value), value -> value);
		}
		return text;
	}

	/**
	 * Replace place holders.
	 *
	 * @param player the player
	 * @param text   the text
	 * @return the string
	 */
	public static String replacePlaceHolders(Player player, String text) {
		return replacePlaceHolders((OfflinePlayer) player, text);
	}
''' + placeholder[end:]
placeholder_path.write_text(placeholder, encoding="utf-8")

write("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/rewards/builtin/requirements/RequirementJavascript.java", r'''
package com.bencodez.advancedcore.api.rewards.builtin.requirements;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueString;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.RewardOptions;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInject;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectString;
import com.bencodez.advancedcore.api.rewards.injectedrequirement.RequirementInjectValidator;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RequirementJavascript {

    private RequirementJavascript() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRequirements().add(new RequirementInjectString("JavascriptExpression", "") {
            @Override
            public boolean onRequirementsRequest(Reward reward, AdvancedCoreUser user, String expression,
                    RewardOptions rewardOptions) {
                return expression.equals("") || new JavascriptEngine().addPlayer(user.getOfflinePlayer())
                        .addPlaceholders(rewardOptions.getPlaceholders()).getBooleanValue(expression);
            }
        }.priority(90).addEditButton(new EditGUIButton(new ItemBuilder("DETECTOR_RAIL"),
                new EditGUIValueString("JavascriptExpression", null) {
                    @Override
                    public void setValue(Player player, String value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(getKey(), value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.addLore("Javascript expression required to run reward"))).validator(new RequirementInjectValidator() {
                    @Override
                    public void onValidate(Reward reward, RequirementInject inject, ConfigurationSection data) {
                        String str = data.getString("JavascriptExpression", null);
                        if (str != null && str.isEmpty()) {
                            warning(reward, inject, "No javascript expression set");
                        }
                    }
                }));
    }
}
''')

write("AdvancedCore/src/main/java/com/bencodez/advancedcore/api/rewards/builtin/RewardJavascript.java", r'''
package com.bencodez.advancedcore.api.rewards.builtin;

import java.util.ArrayList;
import java.util.HashMap;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.inventory.BInventory.ClickEvent;
import com.bencodez.advancedcore.api.inventory.editgui.EditGUIButton;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueInventory;
import com.bencodez.advancedcore.api.inventory.editgui.valuetypes.EditGUIValueList;
import com.bencodez.advancedcore.api.item.ItemBuilder;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.api.rewards.DefinedReward;
import com.bencodez.advancedcore.api.rewards.Reward;
import com.bencodez.advancedcore.api.rewards.RewardBuilder;
import com.bencodez.advancedcore.api.rewards.RewardEditData;
import com.bencodez.advancedcore.api.rewards.RewardHandler;
import com.bencodez.advancedcore.api.rewards.SubDirectlyDefinedReward;
import com.bencodez.advancedcore.api.rewards.editbuttons.RewardEditJavascript;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectConfigurationSection;
import com.bencodez.advancedcore.api.rewards.injected.RewardInjectStringList;
import com.bencodez.advancedcore.api.user.AdvancedCoreUser;

public final class RewardJavascript {

    private RewardJavascript() {
    }

    public static void register(RewardHandler handler, AdvancedCorePlugin plugin) {
        handler.getInjectedRewards().add(new RewardInjectStringList("Javascripts") {
            @Override
            public String onRewardRequest(Reward reward, AdvancedCoreUser user, ArrayList<String> list,
                    HashMap<String, String> placeholders) {
                if (!list.isEmpty()) {
                    JavascriptEngine engine = new JavascriptEngine().addPlayer(user.getOfflinePlayer())
                            .addPlaceholders(placeholders);
                    for (String script : list) {
                        engine.execute(script);
                    }
                }
                return null;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueList("Javascripts", null) {
            @Override
            public void setValue(Player player, ArrayList<String> value) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                reward.setValue(getKey(), value);
                plugin.reloadAdvancedCore(false);
                reward.reOpenEditGUI(player);
            }
        }.addLore("Javascript expressions to run"))));

        handler.getInjectedRewards().add(new RewardInjectConfigurationSection("Javascript") {
            @Override
            public String onRewardRequested(Reward reward, AdvancedCoreUser user, ConfigurationSection section,
                    HashMap<String, String> placeholders) {
                if (section.getBoolean("Enabled")) {
                    String expression = section.getString("Expression");
                    if (new JavascriptEngine().addPlayer(user.getOfflinePlayer()).addPlaceholders(placeholders)
                            .getBooleanValue(expression)) {
                        new RewardBuilder(section, "TrueRewards").withPrefix(reward.getName() + ".Javascript").send(user);
                    } else {
                        new RewardBuilder(section, "FalseRewards").withPrefix(reward.getName() + ".Javascript").send(user);
                    }
                }
                return null;
            }

            @Override
            public ArrayList<SubDirectlyDefinedReward> subRewards(DefinedReward direct) {
                ArrayList<SubDirectlyDefinedReward> subs = new ArrayList<>();
                if (direct.getFileData().isConfigurationSection(
                        direct.getPath() + direct.needsDot() + "Javascript.TrueRewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Javascript.TrueRewards"));
                }
                if (direct.getFileData().isConfigurationSection(
                        direct.getPath() + direct.needsDot() + "Javascript.FalseRewards")) {
                    subs.add(new SubDirectlyDefinedReward(direct, "Javascript.FalseRewards"));
                }
                return subs;
            }
        }.addEditButton(new EditGUIButton(new ItemBuilder(Material.PAPER), new EditGUIValueInventory("Javascript") {
            @Override
            public void openInventory(ClickEvent clickEvent) {
                RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                new RewardEditJavascript() {
                    @Override
                    public void setVal(String key, Object value) {
                        RewardEditData reward = (RewardEditData) getInv().getData("Reward");
                        reward.setValue(key, value);
                        plugin.reloadAdvancedCore(false);
                    }
                }.open(clickEvent.getPlayer(), reward);
            }
        }.addLore("Run javascript to run rewards based on expression return value of true/false"))));
    }
}
''')

item_path = ROOT / "AdvancedCore/src/main/java/com/bencodez/advancedcore/api/item/ItemBuilder.java"
item = item_path.read_text(encoding="utf-8")
item = item.replace("new JavascriptEngine().addPlayer(player)",
                    "new JavascriptEngine().addPlayer(player).addPlaceholders(placeholders)")
item = item.replace("setConditional(new JavascriptEngine()).toItemStack()",
                    "setConditional(new JavascriptEngine().addPlaceholders(placeholders)).toItemStack()")
item_path.write_text(item, encoding="utf-8")

command_path = ROOT / "AdvancedCore/src/main/java/com/bencodez/advancedcore/command/CommandLoader.java"
command = command_path.read_text(encoding="utf-8")
pattern = re.compile(r"\n\s*if \(sender instanceof Player\) \{\s*str = PlaceholderUtils\.replacePlaceHolders\(\(Player\) sender, str\);\s*\}")
command, count = pattern.subn("", command, count=1)
if count != 1:
    raise RuntimeError(f"Expected one CommandLoader JavaScript PAPI expansion block, found {count}")
command_path.write_text(command, encoding="utf-8")

pom_path = ROOT / "AdvancedCore/pom.xml"
pom = pom_path.read_text(encoding="utf-8")
if "<artifactId>rhino</artifactId>" not in pom:
    junit_group = "            <groupId>org.junit.jupiter</groupId>"
    group_index = pom.index(junit_group)
    dependency_index = pom.rfind("        <dependency>", 0, group_index)
    dependency = ("        <dependency>\n"
                  "            <groupId>org.mozilla</groupId>\n"
                  "            <artifactId>rhino</artifactId>\n"
                  "            <version>1.9.1</version>\n"
                  "        </dependency>\n")
    pom = pom[:dependency_index] + dependency + pom[dependency_index:]
if "<pattern>org.mozilla.javascript</pattern>" not in pom:
    close = "                            </relocations>"
    relocation = ("                                <relocation>\n"
                  "                                    <pattern>org.mozilla.javascript</pattern>\n"
                  "                                    <shadedPattern>${project.groupId}.advancedcore.rhino</shadedPattern>\n"
                  "                                </relocation>\n")
    pom = replace_once(pom, close, relocation + close, "relocations closing tag")
pom_path.write_text(pom, encoding="utf-8")

write("AdvancedCore/src/test/java/com/bencodez/advancedcore/api/javascript/JavascriptPlaceholderBinderTest.java", r'''
package com.bencodez.advancedcore.api.javascript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.javascript.JavascriptPlaceholderBinder.PreparedJavascript;

class JavascriptPlaceholderBinderTest {

    @Test
    void executablePlaceholderBecomesEngineData() {
        String injection = "Bukkit.dispatchCommand(Console, 'op attacker')";
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("%value% == true", ignored -> injection);

        assertFalse(prepared.getSource().contains(injection));
        assertEquals(1, prepared.getBindings().size());
        assertTrue(prepared.getBindings().containsValue(injection));
    }

    @Test
    void quotedPlaceholderKeepsStringSemanticsAndEscapesBreakout() {
        String injection = "'; Bukkit.dispatchCommand(Console, 'op attacker'); '";
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("'%value%' == 'safe'", ignored -> injection);

        assertTrue(prepared.getBindings().isEmpty());
        assertFalse(prepared.getSource().contains("''; Bukkit"));
        assertTrue(prepared.getSource().contains("\\'; Bukkit"));
    }

    @Test
    void numericLookingQuotedValueRemainsAString() {
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("'%code%' === '001'", ignored -> "001");

        assertEquals("'001' === '001'", prepared.getSource());
        assertTrue(prepared.getBindings().isEmpty());
    }

    @Test
    void templatePlaceholderCannotCreateInterpolation() {
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("`Hello %name%`",
                ignored -> "${Bukkit.shutdown()}");

        assertEquals("`Hello \\${Bukkit.shutdown()}`", prepared.getSource());
        assertTrue(prepared.getBindings().isEmpty());
    }

    @Test
    void regexPlaceholderIsQuotedAsLiteralPatternData() {
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("/^%name%$/i.test(value)",
                ignored -> "Ben.*");

        assertEquals("/^Ben\\.\\*$/i.test(value)", prepared.getSource());
        assertTrue(prepared.getBindings().isEmpty());
    }

    @Test
    void commentsDoNotChangeExecutablePlaceholderContext() {
        String injection = "Bukkit.dispatchCommand(Console, 'op attacker')";
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("/* ' */ %name%; /* ' */",
                ignored -> injection);

        assertFalse(prepared.getSource().contains(injection));
        assertTrue(prepared.getBindings().containsValue(injection));
    }

    @Test
    void modernSyntaxUsesRealParserWithoutFallbackGuessing() {
        PreparedJavascript prepared = JavascriptPlaceholderBinder.prepare("object?.name && %enabled%",
                ignored -> "true");

        assertTrue(prepared.getSource().startsWith("object?.name && __advancedCorePlaceholder_"));
        assertTrue(prepared.getBindings().containsValue(Boolean.TRUE));
    }

    @Test
    void invalidJavascriptFailsClosed() {
        assertThrows(IllegalArgumentException.class,
                () -> JavascriptPlaceholderBinder.prepare("Player.hasPermission( && %enabled%", ignored -> "true"));
    }
}
''')

write("AdvancedCore/src/test/java/com/bencodez/advancedcore/api/messages/AuthoredJavascriptBoundaryTest.java", r'''
package com.bencodez.advancedcore.api.messages;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.HashMap;

import org.bukkit.OfflinePlayer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.bencodez.advancedcore.AdvancedCorePlugin;
import com.bencodez.advancedcore.api.javascript.JavascriptEngine;
import com.bencodez.advancedcore.tests.BaseTest;

import me.clip.placeholderapi.PlaceholderAPI;

class AuthoredJavascriptBoundaryTest {

    @Test
    void customPlaceholderCannotCreateExecutableMarker() {
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("value", "[Javascript=Bukkit.shutdown()]");

        String result = PlaceholderUtils.replacePlaceHolder("prefix %value%", placeholders);

        assertEquals("prefix [Javascript =Bukkit.shutdown()]", result);
        assertFalse(result.contains("[Javascript="));
    }

    @Test
    void multiplePlaceholdersCannotAssembleExecutableMarker() {
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("first", "Java");
        placeholders.put("second", "script");

        assertEquals("[Javascript =danger]",
                PlaceholderUtils.replacePlaceHolder("[%first%%second%=danger]", placeholders));
    }

    @Test
    void authoredMarkerPreservesCustomValueAsOpaqueData() {
        HashMap<String, String> placeholders = new HashMap<>();
        String injection = "'; Bukkit.shutdown(); '";
        placeholders.put("value", injection);

        String result = PlaceholderUtils.replacePlaceHolder("[Javascript='%value%']", placeholders);

        assertTrue(result.startsWith("[Javascript='%__advancedcore_bound_"));
        assertFalse(result.contains(injection));
    }

    @Test
    void placeholderApiOutputCannotCreateExecutableMarker() {
        AdvancedCorePlugin plugin = BaseTest.getInstance().plugin;
        OfflinePlayer player = mock(OfflinePlayer.class);
        when(plugin.isPlaceHolderAPIEnabled()).thenReturn(true);

        try (MockedStatic<PlaceholderAPI> papi = mockStatic(PlaceholderAPI.class)) {
            papi.when(() -> PlaceholderAPI.setPlaceholders(player, "%untrusted%"))
                    .thenReturn("[Javascript=Bukkit.shutdown()]");

            assertEquals("[Javascript =Bukkit.shutdown()]",
                    PlaceholderUtils.replacePlaceHolders(player, "%untrusted%"));
        }
    }

    @Test
    void onlyOriginalMarkerIsExecuted() {
        RecordingJavascriptEngine engine = new RecordingJavascriptEngine();

        String result = PlaceholderUtils.replaceJavascript("before [Javascript=Player.getLevel()] after", engine);

        assertEquals("before evaluated after", result);
        assertEquals("Player.getLevel()", engine.expression);
    }

    private static final class RecordingJavascriptEngine extends JavascriptEngine {
        private String expression;

        @Override
        public Object getResult(String expression) {
            this.expression = expression;
            return "evaluated";
        }
    }
}
''')

print("Finalized authored JavaScript boundary implementation")
