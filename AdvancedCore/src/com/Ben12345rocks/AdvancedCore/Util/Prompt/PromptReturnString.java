package com.Ben12345rocks.AdvancedCore.Util.Prompt;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.Prompt;
import org.bukkit.conversations.StringPrompt;

/**
 * The Class PromptReturnString.
 */
public abstract class PromptReturnString extends StringPrompt {

	/** The prompt text. */
	public String promptText;

	/*
	 * (non-Javadoc)
	 * @see org.bukkit.conversations.Prompt#acceptInput(org.bukkit.conversations.
	 * ConversationContext, java.lang.String)
	 */
	@Override
	public Prompt acceptInput(ConversationContext context, String input) {

		context.setSessionData("Input", input);
		onInput(context, context.getForWhom(), input);
		return Prompt.END_OF_CONVERSATION;
	}

	/*
	 * (non-Javadoc)
	 * @see org.bukkit.conversations.Prompt#getPromptText(org.bukkit.conversations
	 * .ConversationContext)
	 */
	@Override
	public String getPromptText(ConversationContext context) {
		return promptText;
	}

	/**
	 * On input.
	 *
	 * @param context
	 *            the context
	 * @param conversable
	 *            the conversable
	 * @param input
	 *            the input
	 */
	public abstract void onInput(ConversationContext context, Conversable conversable, String input);

}
