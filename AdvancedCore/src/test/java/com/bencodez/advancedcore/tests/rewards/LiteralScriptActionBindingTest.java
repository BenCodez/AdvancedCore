package com.bencodez.advancedcore.tests.rewards;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bencodez.advancedcore.api.messages.LiteralMessageTemplate;
import com.bencodez.advancedcore.api.messages.PlaceholderUtils;

import net.md_5.bungee.api.chat.BaseComponent;

/** Regression coverage for protected script fragments used as component actions. */
class LiteralScriptActionBindingTest {
    @Test
    void protectedScriptInCommandKeepsRawActionSeparateFromVisibleLiteral() {
        BaseComponent[] rendered = render("[Text=\"%SiteName%\",Command=\"/inspect [Javascript=%SiteName%]\"]",
                Map.of("SiteName", "Friendly Site"), Map.of("SiteName", "raw-site"));

        assertEquals("/inspect [Javascript=raw-site]", action(rendered[0]));
        assertFalse(action(rendered[0]).contains("Friendly Site"));
        assertEquals("Friendly Site", BaseComponent.toPlainText(rendered));
    }

    @Test
    void protectedScriptInUrlUsesEmptyLiteralWhenRawActionIsMissing() {
        BaseComponent[] rendered = render("[Text=\"Open\",URL=\"https://example.com/[Javascript=%SiteName%]\"]",
                Map.of("SiteName", "Friendly Site"), Map.of());

        assertEquals("https://example.com/[Javascript=]", action(rendered[0]));
    }

    @Test
    void protectedScriptsKeepCaseVariantsAndMultipleLiteralFragmentsIndependent() {
        BaseComponent[] rendered = render(
                "[Text=\"Open\",Command=\"/inspect [Javascript=%sitename%/%OTHER%/%provider%]\"]",
                Map.of("SiteName", "First", "Other", "Second"),
                Map.of("sItEnAmE", "raw-first", "oThEr", "raw-second"));

        assertEquals("/inspect [Javascript=raw-first/raw-second/%provider%]", action(rendered[0]));
    }

    private String action(BaseComponent component) {
        if (component.getClickEvent() != null) return component.getClickEvent().getValue();
        if (component.getExtra() != null) {
            for (BaseComponent child : component.getExtra()) {
                String found = action(child);
                if (found != null) return found;
            }
        }
        return null;
    }

    private BaseComponent[] render(String source, Map<String, String> literals, Map<String, String> actions) {
        LiteralMessageTemplate.Prepared prepared = LiteralMessageTemplate.prepare(source, literals, actions);
        return new BaseComponent[] { prepared.finish(PlaceholderUtils.parseJson(prepared.template())) };
    }
}
