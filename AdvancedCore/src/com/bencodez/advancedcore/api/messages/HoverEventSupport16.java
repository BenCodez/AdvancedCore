package com.bencodez.advancedcore.api.messages;

import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.hover.content.Text;

final class HoverEventSupport16 implements HoverEventSupport {

    @Override
    public HoverEvent createHoverEvent(BaseComponent[] value) {
        return new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(value));
    }

}
