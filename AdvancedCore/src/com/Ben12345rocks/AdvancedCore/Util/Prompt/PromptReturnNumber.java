package com.Ben12345rocks.AdvancedCore.Util.Prompt;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationContext;
import org.bukkit.conversations.NumericPrompt;
import org.bukkit.conversations.Prompt;

/**
 * The Class PromptReturnNumber.
 */
public abstract class PromptReturnNumber extends NumericPrompt {

	/** The prompt text. */
	public String promptText;

	/* (non-Javadoc)
	 * @see org.bukkit.conversations.Prompt#getPromptText(org.bukkit.conversations.ConversationContext)
	 */
	@Override
	public String getPromptText(ConversationContext context) {
		return promptText;
	}

	/* (non-Javadoc)
	 * @see org.bukkit.conversations.NumericPrompt#acceptValidatedInput(org.bukkit.conversations.ConversationContext, java.lang.Number)
	 */
	@Override
	protected Prompt acceptValidatedInput(ConversationContext context,
			Number input) {
		context.setSessionData("Input", input);
		onInput(context, context.getForWhom(), input);
		return Prompt.END_OF_CONVERSATION;
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
	public abstract void onInput(ConversationContext context, Conversable conversable,
			Number input);
	

}
