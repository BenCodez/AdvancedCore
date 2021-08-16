package com.bencodez.advancedcore.api.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;

final class HoverEventSupport15 implements HoverEventSupport {

    @SuppressWarnings("deprecation")
    @Override
    public HoverEvent createHoverEvent(BaseComponent[] value) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, value);
    }

}
