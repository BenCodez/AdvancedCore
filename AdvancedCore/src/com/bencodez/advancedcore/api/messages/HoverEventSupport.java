package com.bencodez.advancedcore.api.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;

public interface HoverEventSupport {

    HoverEvent createHoverEvent(BaseComponent[] value);

    static HoverEventSupport findInstance() {
        try {
            Class.forName("net.md_5.bungee.api.chat.hover.content.Content");
            return new HoverEventSupport16();
        } catch (ClassNotFoundException ignored) {
            return new HoverEventSupport15();
        }
    }
}
