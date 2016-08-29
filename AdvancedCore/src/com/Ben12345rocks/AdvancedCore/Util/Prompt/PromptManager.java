package com.Ben12345rocks.AdvancedCore.Util.Prompt;

import org.bukkit.conversations.Conversable;
import org.bukkit.conversations.ConversationFactory;

public class PromptManager {

	ConversationFactory conversationFactory;

	public abstract class PromptListener {

		public abstract void onInput();

	}

	String promptText;

	public PromptManager(String promptText, ConversationFactory convoFactory) {
		this.promptText = promptText;
		this.conversationFactory = convoFactory;
	}

	public void stringPrompt(Conversable conversable, PromptReturnString prompt) {
		prompt.promptText = this.promptText;
		conversationFactory.withFirstPrompt(prompt)
				.buildConversation(conversable).begin();
	}

	public void numberPrompt(Conversable conversable, PromptReturnNumber prompt) {
		prompt.promptText = this.promptText;
		conversationFactory.withFirstPrompt(prompt)
				.buildConversation(conversable).begin();
	}

}
