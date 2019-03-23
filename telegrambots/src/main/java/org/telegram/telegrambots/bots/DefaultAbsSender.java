package org.telegram.telegrambots.bots;

import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;

import org.apache.commons.io.FileUtils;
import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.api.ContentResponse;
import org.eclipse.jetty.client.api.Request;
import org.eclipse.jetty.client.util.InputStreamContentProvider;
import org.eclipse.jetty.client.util.MultiPartContentProvider;
import org.eclipse.jetty.client.util.PathContentProvider;
import org.eclipse.jetty.client.util.StringContentProvider;
import org.telegram.telegrambots.meta.ApiConstants;
import org.telegram.telegrambots.meta.api.methods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.groupadministration.SetChatPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendAnimation;
import org.telegram.telegrambots.meta.api.methods.send.SendAudio;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.send.SendSticker;
import org.telegram.telegrambots.meta.api.methods.send.SendVideo;
import org.telegram.telegrambots.meta.api.methods.send.SendVideoNote;
import org.telegram.telegrambots.meta.api.methods.send.SendVoice;
import org.telegram.telegrambots.meta.api.methods.stickers.AddStickerToSet;
import org.telegram.telegrambots.meta.api.methods.stickers.CreateNewStickerSet;
import org.telegram.telegrambots.meta.api.methods.stickers.UploadStickerFile;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageMedia;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaAudio;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaDocument;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaVideo;
import org.telegram.telegrambots.meta.bots.AbsSender;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import org.telegram.telegrambots.meta.exceptions.TelegramApiValidationException;
import org.telegram.telegrambots.meta.updateshandlers.DownloadFileCallback;
import org.telegram.telegrambots.meta.updateshandlers.SentCallback;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Ruben Bermudez
 * @version 1.0 Implementation of all the methods needed to interact with
 *          Telegram Servers
 */
@SuppressWarnings({ "unused", "WeakerAccess" })
public abstract class DefaultAbsSender extends AbsSender {

	protected final ExecutorService exe;
	private final ObjectMapper objectMapper = new ObjectMapper();
	private volatile HttpClient httpclient;

	protected DefaultAbsSender(HttpClient httpclient) {
		super();

		this.exe = Executors.newFixedThreadPool(1);
		this.httpclient = httpclient;
	}

	/**
	 * Returns the token of the bot to be able to perform Telegram Api Requests
	 * 
	 * @return Token of the bot
	 */
	public abstract String getBotToken();

	// Send Requests

	public final java.io.File downloadFile(String filePath) throws TelegramApiException {
		if (filePath == null || filePath.isEmpty()) {
			throw new TelegramApiException("Parameter file can not be null");
		}
		String url = File.getFileUrl(getBotToken(), filePath);
		String tempFileName = Long.toString(System.currentTimeMillis());
		return downloadToTemporaryFileWrappingExceptions(url, tempFileName);
	}

	public final java.io.File downloadFile(File file) throws TelegramApiException {
		assertParamNotNull(file, "file");
		String url = file.getFileUrl(getBotToken());
		String tempFileName = file.getFileId();
		return downloadToTemporaryFileWrappingExceptions(url, tempFileName);
	}

	public final void downloadFileAsync(String filePath, DownloadFileCallback<String> callback)
			throws TelegramApiException {
		if (filePath == null || filePath.isEmpty()) {
			throw new TelegramApiException("Parameter filePath can not be null");
		}
		assertParamNotNull(callback, "callback");
		String url = File.getFileUrl(getBotToken(), filePath);
		String tempFileName = Long.toString(System.currentTimeMillis());
		exe.submit(getDownloadFileAsyncJob(filePath, callback, url, tempFileName));
	}

	public final void downloadFileAsync(File file, DownloadFileCallback<File> callback) throws TelegramApiException {
		assertParamNotNull(file, "file");
		assertParamNotNull(callback, "callback");
		String url = file.getFileUrl(getBotToken());
		String tempFileName = file.getFileId();
		exe.submit(getDownloadFileAsyncJob(file, callback, url, tempFileName));
	}

	// Specific Send Requests

	@Override
	public final Message execute(SendDocument sendDocument) throws TelegramApiException {
		assertParamNotNull(sendDocument, "sendDocument");

		sendDocument.validate();
		try {
			String url = getBaseUrl() + SendDocument.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendDocument.getChatId()), null);

			addInputFile(multiPart, sendDocument.getDocument(), SendDocument.DOCUMENT_FIELD, true);

			if (sendDocument.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendDocument.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendDocument.getReplyMarkup())),
						null);
			}
			if (sendDocument.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendDocument.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendDocument.getReplyToMessageId().toString()), null);
			}
			if (sendDocument.getCaption() != null) {
				multiPart.addFieldPart(SendDocument.CAPTION_FIELD, new StringContentProvider(sendDocument.getCaption()),
						null);
				if (sendDocument.getParseMode() != null) {
					multiPart.addFieldPart(SendDocument.PARSEMODE_FIELD,
							new StringContentProvider(sendDocument.getParseMode()), null);
				}
			}
			if (sendDocument.getDisableNotification() != null) {
				multiPart.addFieldPart(SendDocument.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendDocument.getDisableNotification().toString()), null);
			}

			if (sendDocument.getThumb() != null) {
				addInputFile(multiPart, sendDocument.getThumb(), SendDocument.THUMB_FIELD, false);
				multiPart.addFieldPart(SendDocument.THUMB_FIELD,
						new StringContentProvider(sendDocument.getThumb().getAttachName()), null);
			}

			httppost.content(multiPart);

			return sendDocument.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send document", e);
		}
	}

	@Override
	public final Message execute(SendPhoto sendPhoto) throws TelegramApiException {
		assertParamNotNull(sendPhoto, "sendPhoto");

		sendPhoto.validate();
		try {
			String url = getBaseUrl() + SendPhoto.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendPhoto.getChatId()), null);
			addInputFile(multiPart, sendPhoto.getPhoto(), SendPhoto.PHOTO_FIELD, true);

			if (sendPhoto.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendPhoto.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendPhoto.getReplyMarkup())), null);
			}
			if (sendPhoto.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendPhoto.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendPhoto.getReplyToMessageId().toString()), null);
			}
			if (sendPhoto.getCaption() != null) {
				multiPart.addFieldPart(SendPhoto.CAPTION_FIELD, new StringContentProvider(sendPhoto.getCaption()),
						null);
				if (sendPhoto.getParseMode() != null) {
					multiPart.addFieldPart(SendPhoto.PARSEMODE_FIELD,
							new StringContentProvider(sendPhoto.getParseMode()), null);
				}
			}
			if (sendPhoto.getDisableNotification() != null) {
				multiPart.addFieldPart(SendPhoto.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendPhoto.getDisableNotification().toString()), null);
			}
			httppost.content(multiPart);

			return sendPhoto.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send photo", e);
		}
	}

	@Override
	public final Message execute(SendVideo sendVideo) throws TelegramApiException {
		assertParamNotNull(sendVideo, "sendVideo");

		sendVideo.validate();
		try {
			String url = getBaseUrl() + SendVideo.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendVideo.getChatId()), null);

			addInputFile(multiPart, sendVideo.getVideo(), SendVideo.VIDEO_FIELD, true);

			if (sendVideo.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendVideo.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendVideo.getReplyMarkup())), null);
			}
			if (sendVideo.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendVideo.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendVideo.getReplyToMessageId().toString()), null);
			}
			if (sendVideo.getCaption() != null) {
				multiPart.addFieldPart(SendVideo.CAPTION_FIELD, new StringContentProvider(sendVideo.getCaption()),
						null);
				if (sendVideo.getParseMode() != null) {
					multiPart.addFieldPart(SendVideo.PARSEMODE_FIELD,
							new StringContentProvider(sendVideo.getParseMode()), null);
				}
			}
			if (sendVideo.getSupportsStreaming() != null) {
				multiPart.addFieldPart(SendVideo.SUPPORTSSTREAMING_FIELD,
						new StringContentProvider(sendVideo.getSupportsStreaming().toString()), null);
			}
			if (sendVideo.getDuration() != null) {
				multiPart.addFieldPart(SendVideo.DURATION_FIELD,
						new StringContentProvider(sendVideo.getDuration().toString()), null);
			}
			if (sendVideo.getWidth() != null) {
				multiPart.addFieldPart(SendVideo.WIDTH_FIELD,
						new StringContentProvider(sendVideo.getWidth().toString()), null);
			}
			if (sendVideo.getHeight() != null) {
				multiPart.addFieldPart(SendVideo.HEIGHT_FIELD,
						new StringContentProvider(sendVideo.getHeight().toString()), null);
			}
			if (sendVideo.getDisableNotification() != null) {
				multiPart.addFieldPart(SendVideo.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendVideo.getDisableNotification().toString()), null);
			}
			if (sendVideo.getThumb() != null) {
				addInputFile(multiPart, sendVideo.getThumb(), SendVideo.THUMB_FIELD, false);
				multiPart.addFieldPart(SendVideo.THUMB_FIELD,
						new StringContentProvider(sendVideo.getThumb().getAttachName()), null);
			}

			httppost.content(multiPart);

			return sendVideo.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send video", e);
		}
	}

	@Override
	public final Message execute(SendVideoNote sendVideoNote) throws TelegramApiException {
		assertParamNotNull(sendVideoNote, "sendVideoNote");

		sendVideoNote.validate();
		try {
			String url = getBaseUrl() + SendVideoNote.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendVideoNote.getChatId()), null);

			addInputFile(multiPart, sendVideoNote.getVideoNote(), SendVideoNote.VIDEONOTE_FIELD, true);

			if (sendVideoNote.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendVideoNote.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendVideoNote.getReplyMarkup())),
						null);
			}
			if (sendVideoNote.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendVideoNote.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendVideoNote.getReplyToMessageId().toString()), null);
			}
			if (sendVideoNote.getDuration() != null) {
				multiPart.addFieldPart(SendVideoNote.DURATION_FIELD,
						new StringContentProvider(sendVideoNote.getDuration().toString()), null);
			}
			if (sendVideoNote.getLength() != null) {
				multiPart.addFieldPart(SendVideoNote.LENGTH_FIELD,
						new StringContentProvider(sendVideoNote.getLength().toString()), null);
			}
			if (sendVideoNote.getDisableNotification() != null) {
				multiPart.addFieldPart(SendVideoNote.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendVideoNote.getDisableNotification().toString()), null);
			}
			if (sendVideoNote.getThumb() != null) {
				addInputFile(multiPart, sendVideoNote.getThumb(), SendVideoNote.THUMB_FIELD, false);
				multiPart.addFieldPart(SendVideoNote.THUMB_FIELD,
						new StringContentProvider(sendVideoNote.getThumb().getAttachName()), null);
			}
			httppost.content(multiPart);

			return sendVideoNote.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send video note", e);
		}
	}

	@Override
	public final Message execute(SendSticker sendSticker) throws TelegramApiException {
		assertParamNotNull(sendSticker, "sendSticker");

		sendSticker.validate();
		try {
			String url = getBaseUrl() + SendSticker.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendSticker.getChatId()), null);

			addInputFile(multiPart, sendSticker.getSticker(), SendSticker.STICKER_FIELD, true);

			if (sendSticker.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendSticker.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendSticker.getReplyMarkup())), null);
			}
			if (sendSticker.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendSticker.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendSticker.getReplyToMessageId().toString()), null);
			}
			if (sendSticker.getDisableNotification() != null) {
				multiPart.addFieldPart(SendSticker.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendSticker.getDisableNotification().toString()), null);
			}
			httppost.content(multiPart);

			return sendSticker.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send sticker", e);
		}
	}

	/**
	 * Sends a file using Send Audio method
	 * (https://core.telegram.org/bots/api#sendaudio)
	 * 
	 * @param sendAudio Information to send
	 * @return If success, the sent Message is returned
	 * @throws TelegramApiException If there is any error sending the audio
	 */
	@Override
	public final Message execute(SendAudio sendAudio) throws TelegramApiException {
		assertParamNotNull(sendAudio, "sendAudio");
		sendAudio.validate();
		try {
			String url = getBaseUrl() + SendAudio.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendAudio.getChatId()), null);

			addInputFile(multiPart, sendAudio.getAudio(), SendAudio.AUDIO_FIELD, true);

			if (sendAudio.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendAudio.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendAudio.getReplyMarkup())), null);
			}
			if (sendAudio.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendAudio.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendAudio.getReplyToMessageId().toString()), null);
			}
			if (sendAudio.getPerformer() != null) {
				multiPart.addFieldPart(SendAudio.PERFOMER_FIELD, new StringContentProvider(sendAudio.getPerformer()),
						null);
			}
			if (sendAudio.getTitle() != null) {
				multiPart.addFieldPart(SendAudio.TITLE_FIELD, new StringContentProvider(sendAudio.getTitle()), null);
			}
			if (sendAudio.getDuration() != null) {
				multiPart.addFieldPart(SendAudio.DURATION_FIELD,
						new StringContentProvider(sendAudio.getDuration().toString()), null);
			}
			if (sendAudio.getDisableNotification() != null) {
				multiPart.addFieldPart(SendAudio.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendAudio.getDisableNotification().toString()), null);
			}
			if (sendAudio.getCaption() != null) {
				multiPart.addFieldPart(SendAudio.CAPTION_FIELD, new StringContentProvider(sendAudio.getCaption()),
						null);
				if (sendAudio.getParseMode() != null) {
					multiPart.addFieldPart(SendAudio.PARSEMODE_FIELD,
							new StringContentProvider(sendAudio.getParseMode()), null);
				}
			}
			if (sendAudio.getThumb() != null) {
				addInputFile(multiPart, sendAudio.getThumb(), SendAudio.THUMB_FIELD, false);
				multiPart.addFieldPart(SendAudio.THUMB_FIELD,
						new StringContentProvider(sendAudio.getThumb().getAttachName()), null);
			}

			httppost.content(multiPart);

			return sendAudio.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send sticker", e);
		}
	}

	/**
	 * Sends a voice note using Send Voice method
	 * (https://core.telegram.org/bots/api#sendvoice) For this to work, your audio
	 * must be in an .ogg file encoded with OPUS
	 * 
	 * @param sendVoice Information to send
	 * @return If success, the sent Message is returned
	 * @throws TelegramApiException If there is any error sending the audio
	 */
	@Override
	public final Message execute(SendVoice sendVoice) throws TelegramApiException {
		assertParamNotNull(sendVoice, "sendVoice");
		sendVoice.validate();
		try {
			String url = getBaseUrl() + SendVoice.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendVoice.getChatId()), null);

			addInputFile(multiPart, sendVoice.getVoice(), SendVoice.VOICE_FIELD, true);

			if (sendVoice.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendVoice.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendVoice.getReplyMarkup())), null);
			}
			if (sendVoice.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendVoice.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendVoice.getReplyToMessageId().toString()), null);
			}
			if (sendVoice.getDisableNotification() != null) {
				multiPart.addFieldPart(SendVoice.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendVoice.getDisableNotification().toString()), null);
			}
			if (sendVoice.getDuration() != null) {
				multiPart.addFieldPart(SendVoice.DURATION_FIELD,
						new StringContentProvider(sendVoice.getDuration().toString()), null);
			}
			if (sendVoice.getCaption() != null) {
				multiPart.addFieldPart(SendVoice.CAPTION_FIELD, new StringContentProvider(sendVoice.getCaption()),
						null);
				if (sendVoice.getParseMode() != null) {
					multiPart.addFieldPart(SendVoice.PARSEMODE_FIELD,
							new StringContentProvider(sendVoice.getParseMode()), null);
				}
			}
			httppost.content(multiPart);

			return sendVoice.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to send voice", e);
		}
	}

	@Override
	public Boolean execute(SetChatPhoto setChatPhoto) throws TelegramApiException {
		assertParamNotNull(setChatPhoto, "setChatPhoto");
		setChatPhoto.validate();

		try {
			String url = getBaseUrl() + SetChatPhoto.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(setChatPhoto.getChatId()), null);

			if (setChatPhoto.getPhoto() != null) {
				multiPart.addFilePart(setChatPhoto.getPhoto().getName(), setChatPhoto.getPhoto().getName(),
						new PathContentProvider(Paths.get(setChatPhoto.getPhoto().getAbsolutePath())), null);
			} else if (setChatPhoto.getPhotoStream() != null) {
				multiPart.addFilePart(setChatPhoto.getPhoto().getName(), setChatPhoto.getPhoto().getName(),
						new InputStreamContentProvider(setChatPhoto.getPhotoStream()), null);
			}
			httppost.content(multiPart);

			return setChatPhoto.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to set chat photo", e);
		}
	}

	@Override
	public List<Message> execute(SendMediaGroup sendMediaGroup) throws TelegramApiException {
		assertParamNotNull(sendMediaGroup, "sendMediaGroup");
		sendMediaGroup.validate();

		try {
			String url = getBaseUrl() + SendMediaGroup.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			multiPart.addFieldPart(SendPhoto.CHATID_FIELD, new StringContentProvider(sendMediaGroup.getChatId()), null);

			addInputData(multiPart, sendMediaGroup.getMedia(), SendMediaGroup.MEDIA_FIELD);

			if (sendMediaGroup.getDisableNotification() != null) {
				multiPart.addFieldPart(SendMediaGroup.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendMediaGroup.getDisableNotification().toString()), null);
			}

			if (sendMediaGroup.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendMediaGroup.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendMediaGroup.getReplyToMessageId().toString()), null);
			}

			httppost.content(multiPart);

			return sendMediaGroup.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to set chat photo", e);
		}
	}

	@Override
	public Boolean execute(AddStickerToSet addStickerToSet) throws TelegramApiException {
		assertParamNotNull(addStickerToSet, "addStickerToSet");
		addStickerToSet.validate();
		try {
			String url = getBaseUrl() + AddStickerToSet.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();

			multiPart.addFieldPart(AddStickerToSet.USERID_FIELD,
					new StringContentProvider(addStickerToSet.getUserId().toString()), null);
			multiPart.addFieldPart(AddStickerToSet.NAME_FIELD, new StringContentProvider(addStickerToSet.getName()),
					null);
			multiPart.addFieldPart(AddStickerToSet.EMOJIS_FIELD, new StringContentProvider(addStickerToSet.getEmojis()),
					null);
			addInputFile(multiPart, addStickerToSet.getPngSticker(), AddStickerToSet.PNGSTICKER_FIELD, true);

			if (addStickerToSet.getMaskPosition() != null) {
				multiPart.addFieldPart(AddStickerToSet.MASKPOSITION_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(addStickerToSet.getMaskPosition())),
						null);
			}

			httppost.content(multiPart);

			return addStickerToSet.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to add sticker to set", e);
		}
	}

	@Override
	public Boolean execute(CreateNewStickerSet createNewStickerSet) throws TelegramApiException {
		assertParamNotNull(createNewStickerSet, "createNewStickerSet");
		createNewStickerSet.validate();
		try {
			String url = getBaseUrl() + CreateNewStickerSet.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();

			multiPart.addFieldPart(CreateNewStickerSet.USERID_FIELD,
					new StringContentProvider(createNewStickerSet.getUserId().toString()), null);
			multiPart.addFieldPart(CreateNewStickerSet.NAME_FIELD,
					new StringContentProvider(createNewStickerSet.getName()), null);
			multiPart.addFieldPart(CreateNewStickerSet.TITLE_FIELD,
					new StringContentProvider(createNewStickerSet.getTitle()), null);
			multiPart.addFieldPart(CreateNewStickerSet.EMOJIS_FIELD,
					new StringContentProvider(createNewStickerSet.getEmojis()), null);
			multiPart.addFieldPart(CreateNewStickerSet.CONTAINSMASKS_FIELD,
					new StringContentProvider(createNewStickerSet.getContainsMasks().toString()), null);
			addInputFile(multiPart, createNewStickerSet.getPngSticker(), CreateNewStickerSet.PNGSTICKER_FIELD, true);

			if (createNewStickerSet.getMaskPosition() != null) {
				multiPart.addFieldPart(CreateNewStickerSet.MASKPOSITION_FIELD, new StringContentProvider(
						objectMapper.writeValueAsString(createNewStickerSet.getMaskPosition())), null);
			}
			httppost.content(multiPart);

			return createNewStickerSet.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to create new sticker set", e);
		}
	}

	@Override
	public File execute(UploadStickerFile uploadStickerFile) throws TelegramApiException {
		assertParamNotNull(uploadStickerFile, "uploadStickerFile");
		uploadStickerFile.validate();
		try {
			String url = getBaseUrl() + UploadStickerFile.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();

			multiPart.addFieldPart(UploadStickerFile.USERID_FIELD,
					new StringContentProvider(uploadStickerFile.getUserId().toString()), null);
			addInputFile(multiPart, uploadStickerFile.getPngSticker(), UploadStickerFile.PNGSTICKER_FIELD, true);
			httppost.content(multiPart);

			return uploadStickerFile.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to upload new sticker file", e);
		}
	}

	@Override
	public Serializable execute(EditMessageMedia editMessageMedia) throws TelegramApiException {
		assertParamNotNull(editMessageMedia, "editMessageMedia");
		editMessageMedia.validate();
		try {
			String url = getBaseUrl() + EditMessageMedia.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();
			if (editMessageMedia.getInlineMessageId() == null) {
				multiPart.addFieldPart(EditMessageMedia.CHATID_FIELD,
						new StringContentProvider(editMessageMedia.getChatId()), null);
				multiPart.addFieldPart(EditMessageMedia.MESSAGEID_FIELD,
						new StringContentProvider(editMessageMedia.getMessageId().toString()), null);

			} else {
				multiPart.addFieldPart(EditMessageMedia.INLINE_MESSAGE_ID_FIELD,
						new StringContentProvider(editMessageMedia.getInlineMessageId()), null);
			}
			if (editMessageMedia.getReplyMarkup() != null) {
				multiPart.addFieldPart(EditMessageMedia.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(editMessageMedia.getReplyMarkup())),
						null);
			}

			addInputData(multiPart, editMessageMedia.getMedia(), EditMessageMedia.MEDIA_FIELD, true);

			httppost.content(multiPart);

			return editMessageMedia.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to edit message media", e);
		}
	}

	@Override
	public Message execute(SendAnimation sendAnimation) throws TelegramApiException {
		assertParamNotNull(sendAnimation, "sendAnimation");
		sendAnimation.validate();
		try {
			String url = getBaseUrl() + SendAnimation.PATH;
			Request httppost = configuredHttpPost(url);

			MultiPartContentProvider multiPart = new MultiPartContentProvider();

			multiPart.addFieldPart(SendAnimation.CHATID_FIELD, new StringContentProvider(sendAnimation.getChatId()),
					null);
			addInputFile(multiPart, sendAnimation.getAnimation(), SendAnimation.ANIMATION_FIELD, true);

			if (sendAnimation.getReplyMarkup() != null) {
				multiPart.addFieldPart(SendAnimation.REPLYMARKUP_FIELD,
						new StringContentProvider(objectMapper.writeValueAsString(sendAnimation.getReplyMarkup())),
						null);
			}
			if (sendAnimation.getReplyToMessageId() != null) {
				multiPart.addFieldPart(SendAnimation.REPLYTOMESSAGEID_FIELD,
						new StringContentProvider(sendAnimation.getReplyToMessageId().toString()), null);
			}
			if (sendAnimation.getDisableNotification() != null) {
				multiPart.addFieldPart(SendAnimation.DISABLENOTIFICATION_FIELD,
						new StringContentProvider(sendAnimation.getDisableNotification().toString()), null);
			}
			if (sendAnimation.getDuration() != null) {
				multiPart.addFieldPart(SendAnimation.DURATION_FIELD,
						new StringContentProvider(sendAnimation.getDuration().toString()), null);
			}
			if (sendAnimation.getWidth() != null) {
				multiPart.addFieldPart(SendAnimation.WIDTH_FIELD,
						new StringContentProvider(sendAnimation.getWidth().toString()), null);
			}
			if (sendAnimation.getHeight() != null) {
				multiPart.addFieldPart(SendAnimation.HEIGHT_FIELD,
						new StringContentProvider(sendAnimation.getHeight().toString()), null);
			}
			if (sendAnimation.getThumb() != null) {
				addInputFile(multiPart, sendAnimation.getThumb(), SendAnimation.THUMB_FIELD, false);
				multiPart.addFieldPart(SendAnimation.THUMB_FIELD,
						new StringContentProvider(sendAnimation.getThumb().getAttachName()), null);
			}

			if (sendAnimation.getCaption() != null) {
				multiPart.addFieldPart(SendAnimation.CAPTION_FIELD,
						new StringContentProvider(sendAnimation.getCaption()), null);
				if (sendAnimation.getParseMode() != null) {
					multiPart.addFieldPart(SendAnimation.PARSEMODE_FIELD,
							new StringContentProvider(sendAnimation.getParseMode()), null);
				}
			}
			httppost.content(multiPart);

			return sendAnimation.deserializeResponse(sendHttpPostRequest(httppost));
		} catch (IOException e) {
			throw new TelegramApiException("Unable to edit message media", e);
		}
	}

	// Simplified methods

	@Override
	protected final <T extends Serializable, Method extends BotApiMethod<T>, Callback extends SentCallback<T>> void sendApiMethodAsync(
			Method method, Callback callback) {
		// noinspection Convert2Lambda
		exe.submit(new Runnable() {
			@Override
			public void run() {
				try {
					String responseContent = sendMethodRequest(method);
					try {
						callback.onResult(method, method.deserializeResponse(responseContent));
					} catch (TelegramApiRequestException e) {
						callback.onError(method, e);
					}
				} catch (IOException | TelegramApiValidationException e) {
					callback.onException(method, e);
				}

			}
		});
	}

	@Override
	protected final <T extends Serializable, Method extends BotApiMethod<T>> T sendApiMethod(Method method)
			throws TelegramApiException {
		try {
			String responseContent = sendMethodRequest(method);
			return method.deserializeResponse(responseContent);
		} catch (IOException e) {
			throw new TelegramApiException("Unable to execute " + method.getMethod() + " method", e);
		}
	}

	private <T> Runnable getDownloadFileAsyncJob(T fileIdentifier, DownloadFileCallback<T> callback, String url,
			String tempFileName) {
		// noinspection Convert2Lambda
		return new Runnable() {
			@Override
			public void run() {
				try {
					callback.onResult(fileIdentifier, downloadToTemporaryFile(url, tempFileName));
				} catch (MalformedURLException e) {
					callback.onException(fileIdentifier, new TelegramApiException("Wrong url for file: " + url));
				} catch (IOException e) {
					callback.onException(fileIdentifier,
							new TelegramApiRequestException("Error downloading the file", e));
				}
			}
		};
	}

	private java.io.File downloadToTemporaryFileWrappingExceptions(String url, String tempFileName)
			throws TelegramApiException {
		try {
			return downloadToTemporaryFile(url, tempFileName);
		} catch (MalformedURLException e) {
			throw new TelegramApiException("Wrong url for file: " + url);
		} catch (IOException e) {
			throw new TelegramApiRequestException("Error downloading the file", e);
		}
	}

	private java.io.File downloadToTemporaryFile(String url, String tempFileName) throws IOException {
		java.io.File output = java.io.File.createTempFile(tempFileName, ".tmp");
		FileUtils.copyURLToFile(new URL(url), output);
		return output;
	}

	private <T extends Serializable, Method extends BotApiMethod<T>> String sendMethodRequest(Method method)
			throws TelegramApiValidationException, IOException {
		method.validate();
		String url = getBaseUrl() + method.getMethod();
		Request httppost = configuredHttpPost(url);
		httppost.header("charset", StandardCharsets.UTF_8.name());
		httppost.content(new StringContentProvider(objectMapper.writeValueAsString(method)), "application/json");
		return sendHttpPostRequest(httppost);
	}

	private String sendHttpPostRequest(Request httpPost) throws IOException {

		ContentResponse response;
		try {
			response = httpPost.send();
			return response.getContentAsString();
		} catch (InterruptedException | TimeoutException | ExecutionException e) {
			throw new IOException(e.getMessage());
		}
	}

	private Request configuredHttpPost(String url) {
		return httpclient.POST(url);
	}

	private void addInputData(MultiPartContentProvider builder, InputMedia<?> media, String mediaField,
			boolean addField) throws IOException {
		if (media.isNewMedia()) {
			if (media.getMediaFile() != null) {
				builder.addFilePart(media.getMediaName(), media.getMediaName(),
						new PathContentProvider(Paths.get(media.getMediaFile().getAbsolutePath())), null);
			} else if (media.getNewMediaStream() != null) {
				builder.addFilePart(media.getMediaName(), media.getMediaName(),
						new InputStreamContentProvider(media.getNewMediaStream()), null);
			}
		}

		if (media instanceof InputMediaAudio) {
			InputMediaAudio audio = (InputMediaAudio) media;
			if (audio.getThumb() != null) {
				addInputFile(builder, audio.getThumb(), InputMediaAudio.THUMB_FIELD, false);
			}
		} else if (media instanceof InputMediaDocument) {
			InputMediaDocument document = (InputMediaDocument) media;
			if (document.getThumb() != null) {
				addInputFile(builder, document.getThumb(), InputMediaDocument.THUMB_FIELD, false);
			}
		} else if (media instanceof InputMediaVideo) {
			InputMediaVideo video = (InputMediaVideo) media;
			if (video.getThumb() != null) {
				addInputFile(builder, video.getThumb(), InputMediaVideo.THUMB_FIELD, false);
			}
		}

		if (addField) {
			builder.addFieldPart(mediaField, new StringContentProvider(objectMapper.writeValueAsString(media)), null);
		}
	}

	private void addInputData(MultiPartContentProvider builder, List<InputMedia> media, String mediaField)
			throws IOException {
		for (InputMedia<?> inputMedia : media) {
			addInputData(builder, inputMedia, null, false);
		}

		builder.addFieldPart(mediaField, new StringContentProvider(objectMapper.writeValueAsString(media)), null);
	}

	private void addInputFile(MultiPartContentProvider multiPart, InputFile file, String fileField, boolean addField) {
		if (file.isNew()) {
			try {
				if (file.getNewMediaFile() != null) {
					multiPart.addFilePart(file.getMediaName(), file.getMediaName(),
							new PathContentProvider(Paths.get(file.getNewMediaFile().getAbsolutePath())), null);
				} else if (file.getNewMediaStream() != null) {
					multiPart.addFilePart(file.getMediaName(), file.getMediaName(),
							new InputStreamContentProvider(file.getNewMediaStream()), null);
				}
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}

		}

		if (addField) {
			multiPart.addFieldPart(fileField, new StringContentProvider(file.getAttachName()), null);
		}
	}

	public String getBaseUrl() {
		return ApiConstants.BASE_URL + getBotToken() + "/";
	}

	private void assertParamNotNull(Object param, String paramName) throws TelegramApiException {
		if (param == null) {
			throw new TelegramApiException("Parameter " + paramName + " can not be null");
		}
	}
}
