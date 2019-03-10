package org.telegram.telegrambots.bots;

import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.generics.LongPollingBot;

/**
 * @author Ruben Bermudez
 * @version 1.0 Base abstract class for a bot that will get updates using
 *          <a href=
 *          "https://core.telegram.org/bots/api#getupdates">long-polling</a>
 *          method
 */
public abstract class TelegramLongPollingBot extends DefaultAbsSender implements LongPollingBot {
	public TelegramLongPollingBot() {
		this(ApiContext.getInstance(DefaultBotOptions.class));
	}

	public TelegramLongPollingBot(DefaultBotOptions options) {
		super(options);
	}

	@Override
	public void onClosing() {
		exe.shutdown();
	}
}