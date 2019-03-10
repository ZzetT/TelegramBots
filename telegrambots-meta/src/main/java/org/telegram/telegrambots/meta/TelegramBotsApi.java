package org.telegram.telegrambots.meta;

import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
import org.telegram.telegrambots.meta.generics.WebhookBot;

/**
 * @author Ruben Bermudez
 * @version 1.0
 * @brief Bots manager
 * @date 14 of January of 2016
 */
public class TelegramBotsApi {

	/**
	 *
	 */
	public TelegramBotsApi() {
	}

	/**
	 * Register a bot. The Bot Session is started immediately, and may be
	 * disconnected by calling close.
	 * 
	 * @param bot the bot to register
	 */
	public BotSession registerBot(LongPollingBot bot) throws TelegramApiRequestException {
		BotSession session = ApiContext.getInstance(BotSession.class);
		session.setToken(bot.getBotToken());
		session.setOptions(bot.getOptions());
		session.setCallback(bot);
		session.start();
		return session;
	}

	/**
	 * Register a bot in the api that will receive updates using webhook method
	 * 
	 * @param bot Bot to register
	 */
	public void registerBot(WebhookBot bot) throws TelegramApiRequestException {
	}
}
