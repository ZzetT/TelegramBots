package org.telegram.telegrambots.updatesreceivers;

import static org.telegram.telegrambots.Constants.SOCKET_TIMEOUT;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.json.JSONException;
import org.telegram.telegrambots.meta.ApiConstants;
import org.telegram.telegrambots.meta.api.methods.updates.GetUpdates;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.generics.BotSession;
import org.telegram.telegrambots.meta.generics.LongPollingBot;
import org.telegram.telegrambots.meta.generics.UpdatesHandler;
import org.telegram.telegrambots.meta.generics.UpdatesReader;
import org.telegram.telegrambots.meta.logging.BotLogger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

/**
 * @author Ruben Bermudez
 * @version 1.0 Thread to request updates with active wait
 */
public class DefaultBotSession implements BotSession {
	private static final String LOGTAG = "BOTSESSION";

	private volatile boolean running = false;

	private final ConcurrentLinkedDeque<Update> receivedUpdates = new ConcurrentLinkedDeque<>();
	private final ObjectMapper objectMapper = new ObjectMapper();

	private ReaderThread readerThread;
	private HandlerThread handlerThread;
	private LongPollingBot callback;
	private String token;
	private int lastReceivedUpdate = 0;
	private UpdatesSupplier updatesSupplier;

	@Inject
	public DefaultBotSession() {
	}

	@Override
	public synchronized void start() {
		if (running) {
			throw new IllegalStateException("Session already running");
		}

		running = true;

		lastReceivedUpdate = 0;

		readerThread = new ReaderThread(updatesSupplier, this);
		readerThread.setName(callback.getBotUsername() + " Telegram Connection");
		readerThread.start();

		handlerThread = new HandlerThread();
		handlerThread.setName(callback.getBotUsername() + " Telegram Executor");
		handlerThread.start();
	}

	@Override
	public void stop() {
		if (!running) {
			throw new IllegalStateException("Session already stopped");
		}

		running = false;

		if (readerThread != null) {
			readerThread.interrupt();
		}

		if (handlerThread != null) {
			handlerThread.interrupt();
		}

		if (callback != null) {
			callback.onClosing();
		}
	}

	public void setUpdatesSupplier(UpdatesSupplier updatesSupplier) {
		this.updatesSupplier = updatesSupplier;
	}

	@Override
	public void setToken(String token) {
		if (this.token != null) {
			throw new InvalidParameterException("Token has already been set");
		}
		this.token = token;
	}

	@Override
	public void setCallback(LongPollingBot callback) {
		if (this.callback != null) {
			throw new InvalidParameterException("Callback has already been set");
		}
		this.callback = callback;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	private class ReaderThread extends Thread implements UpdatesReader {

		private final UpdatesSupplier updatesSupplier;
		private final Object lock;
		private HttpClient httpclient;
		private ExponentialBackOff exponentialBackOff;

		public ReaderThread(UpdatesSupplier updatesSupplier, Object lock) {
			this.updatesSupplier = Optional.ofNullable(updatesSupplier).orElse(this::getUpdatesFromServer);
			this.lock = lock;
		}

		@Override
		public synchronized void start() {
			httpclient = new HttpClient(new SslContextFactory());

			exponentialBackOff = new ExponentialBackOff();

			httpclient.setConnectTimeout(SOCKET_TIMEOUT);

			try {
				httpclient.start();
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			super.start();
		}

		@Override
		public void run() {
			setPriority(Thread.MIN_PRIORITY);
			while (running) {
				synchronized (lock) {
					if (running) {
						try {
							List<Update> updates = updatesSupplier.getUpdates();
							if (updates.isEmpty()) {
								lock.wait(500);
							} else {
								updates.removeIf(x -> x.getUpdateId() < lastReceivedUpdate);
								lastReceivedUpdate = updates.parallelStream().map(Update::getUpdateId)
										.max(Integer::compareTo).orElse(0);
								receivedUpdates.addAll(updates);

								synchronized (receivedUpdates) {
									receivedUpdates.notifyAll();
								}
							}
						} catch (InterruptedException e) {
							if (!running) {
								receivedUpdates.clear();
							}
							BotLogger.debug(LOGTAG, e);
							interrupt();
						} catch (Exception global) {
							BotLogger.severe(LOGTAG, global);
							try {
								synchronized (lock) {
									lock.wait(exponentialBackOff.nextBackOffMillis());
								}
							} catch (InterruptedException e) {
								if (!running) {
									receivedUpdates.clear();
								}
								BotLogger.debug(LOGTAG, e);
								interrupt();
							}
						}
					}
				}
			}
			BotLogger.debug(LOGTAG, "Reader thread has being closed");
		}

		private List<Update> getUpdatesFromServer() throws IOException {
			GetUpdates request = new GetUpdates().setLimit(100).setTimeout(ApiConstants.GETUPDATES_TIMEOUT)
					.setOffset(lastReceivedUpdate + 1);

			String url = ApiConstants.BASE_URL + token + "/" + GetUpdates.PATH;
			Request httpPost = httpclient.POST(url);
			httpPost.header("charset", StandardCharsets.UTF_8.name());
			httpPost.content(new StringContentProvider(objectMapper.writeValueAsString(request)), "application/json");

			try {
				ContentResponse response = httpPost.send();
				String responseContent = response.getContentAsString();

				if (response.getStatus() >= 500) {
					BotLogger.warn(LOGTAG, responseContent);
					synchronized (lock) {
						lock.wait(500);
					}
				} else {
					try {
						List<Update> updates = request.deserializeResponse(responseContent);
						exponentialBackOff.reset();
						return updates;
					} catch (JSONException e) {
						BotLogger.severe(responseContent, LOGTAG, e);
					}
				}
			} catch (ExecutionException | TelegramApiRequestException e) {
				BotLogger.severe(LOGTAG, e);
			} catch (TimeoutException e) {
				BotLogger.fine(LOGTAG, e);
			} catch (InterruptedException e) {
				BotLogger.fine(LOGTAG, e);
				interrupt();
			}
			return Collections.emptyList();
		}
	}

	public interface UpdatesSupplier {

		List<Update> getUpdates() throws Exception;
	}

	private List<Update> getUpdateList() {
		List<Update> updates = new ArrayList<>();
		for (Iterator<Update> it = receivedUpdates.iterator(); it.hasNext();) {
			updates.add(it.next());
			it.remove();
		}
		return updates;
	}

	private class HandlerThread extends Thread implements UpdatesHandler {
		@Override
		public void run() {
			setPriority(Thread.MIN_PRIORITY);
			while (running) {
				try {
					List<Update> updates = getUpdateList();
					if (updates.isEmpty()) {
						synchronized (receivedUpdates) {
							receivedUpdates.wait();
							updates = getUpdateList();
							if (updates.isEmpty()) {
								continue;
							}
						}
					}
					callback.onUpdatesReceived(updates);
				} catch (InterruptedException e) {
					BotLogger.debug(LOGTAG, e);
					interrupt();
				} catch (Exception e) {
					BotLogger.severe(LOGTAG, e);
				}
			}
			BotLogger.debug(LOGTAG, "Handler thread has being closed");
		}
	}
}
