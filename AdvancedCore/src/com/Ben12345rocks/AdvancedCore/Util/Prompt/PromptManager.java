package com.Ben12345rocks.AdvancedCore.Util.Prompt;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationFactory;

/**
 * The Class PromptManager.
 */
public class PromptManager {

	/** The conversation factory. */
	ConversationFactory conversationFactory;

	/**
	 * Prompt Listener
	 */
	public abstract class PromptListener {

		/**
		 * On input.
		 */
		public abstract void onInput();

	}

	/** The prompt text. */
	String promptText;

	/**
	 * Instantiates a new prompt manager.
	 *
	 * @param promptText
	 *            the prompt text
	 * @param convoFactory
	 *            the convo factory
	 */
	public PromptManager(String promptText, ConversationFactory convoFactory) {
		this.promptText = promptText;
		this.conversationFactory = convoFactory;
	}

	/**
	 * String prompt.
	 *
	 * @param conversable
	 *            the conversable
	 * @param prompt
	 *            the prompt
	 */
	public void stringPrompt(Conversable conversable, PromptReturnString prompt) {
		prompt.promptText = this.promptText;
		conversationFactory.withFirstPrompt(prompt)
				.buildConversation(conversable).begin();
	}

	/**
	 * Number prompt.
	 *
	 * @param conversable
	 *            the conversable
	 * @param prompt
	 *            the prompt
	 */
	public void numberPrompt(Conversable conversable, PromptReturnNumber prompt) {
		prompt.promptText = this.promptText;
		conversationFactory.withFirstPrompt(prompt)
				.buildConversation(conversable).begin();
	}

}
