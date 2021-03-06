package org.telegram.telegrambots;

import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

/**
 * @author Ruben Bermudez
 * @version 1.0
 * Initialization of ApiContext
 */
public final class ApiContextInitializer {
    private ApiContextInitializer() {
    }

    public static void init() {
        ApiContext.register(BotSession.class, DefaultBotSession.class);
    }
}
