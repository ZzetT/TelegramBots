package org.telegram.telegrambots.meta.generics;

import java.util.List;

import org.telegram.telegrambots.meta.api.objects.Update;

/**
 * @author Ruben Bermudez
 * @version 1.0
 * @brief Callback to handle updates.
 * @date 20 of June of 2015
 */
public interface LongPollingBot {
	/**
	 * This method is called when receiving updates via GetUpdates method
	 * 
	 * @param update Update received
	 */
	void onUpdateReceived(Update update);

	/**
	 * This method is called when receiving updates via GetUpdates method. If not
	 * reimplemented - it just sends updates by one into
	 * {@link #onUpdateReceived(Update)}
	 * 
	 * @param updates list of Update received
	 */
	default void onUpdatesReceived(List<Update> updates) {
		updates.forEach(this::onUpdateReceived);
	}

	/**
	 * Return bot username of this bot
	 */
	String getBotUsername();

	/**
	 * Return bot token to access Telegram API
	 */
	String getBotToken();

	/**
	 * Gets options for current bot
	 * 
	 * @return BotOptions object with options information
	 */
	BotOptions getOptions();

	/**
	 * Called when the BotSession is being closed
	 */
	default void onClosing() {
	}
}
