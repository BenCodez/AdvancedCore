package com.Ben12345rocks.AdvancedCore.Util.Prompt;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.NumericPrompt;
import org.bukkit.conversations.Prompt;

public abstract class PromptReturnNumber extends NumericPrompt {

	public String promptText;

	@Override
	public String getPromptText(ConversationContext context) {
		return promptText;
	}

	@Override
	protected Prompt acceptValidatedInput(ConversationContext context,
			Number input) {
		context.setSessionData("Input", input);
		onInput(context, context.getForWhom(), input);
		return Prompt.END_OF_CONVERSATION;
	}

	public abstract void onInput(ConversationContext context, Conversable conversable,
			Number input);
	

}
