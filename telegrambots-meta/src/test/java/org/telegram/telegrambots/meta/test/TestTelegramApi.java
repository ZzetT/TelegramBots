package org.telegram.telegrambots.meta.test;

import org.junit.Test;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.test.base.TestBase;

/**
 * @author Ruben Bermudez
 * @version 1.0
 */
public class TestTelegramApi extends TestBase {

	@Test
	public void TestTelegramApiMustBeInitializableForLongPolling() {
		new TelegramBotsApi();
	}

}
