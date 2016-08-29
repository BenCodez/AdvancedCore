package com.Ben12345rocks.AdvancedCore.Util.Prompt;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;

public abstract class PromptReturnString extends StringPrompt {
	
	public String promptText;

	@Override
	public String getPromptText(ConversationContext context) {
		return promptText;
	}

	@Override
	public Prompt acceptInput(ConversationContext context, String input) {

		context.setSessionData("Input", input);
		onInput(context, context.getForWhom(), input);
		return Prompt.END_OF_CONVERSATION;
	}

	public abstract void onInput(ConversationContext context, Conversable conversable,
			String input);

}
