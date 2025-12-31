package com.yohann.ocihelper.telegram.handler.impl;

import com.yohann.ocihelper.telegram.handler.AbstractCallbackHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.meta.api.methods.botapimethods.BotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedList;

/**
 * Log query callback handler
 * Query the latest 100 log entries and send as a file
 *
 * @author yohann
 */
@Slf4j
@Component
public class LogQueryHandler extends AbstractCallbackHandler {

    private static final String LOG_FILE_PATH = "/var/log/oci-helper.log";
    private static final int MAX_LINES = 300;

    @Override
    public BotApiMethod<?> handle(CallbackQuery callbackQuery, TelegramClient telegramClient) {
        long chatId = callbackQuery.getMessage().getChatId();

        try {
            // Send loading message
            telegramClient.execute(buildEditMessage(
                    callbackQuery,
                    "📋 正在获取日志文件，请稍候..."
            ));

            // Read last 100 lines from log file
            File logFile = new File(LOG_FILE_PATH);

            if (!logFile.exists()) {
                return buildEditMessage(
                        callbackQuery,
                        "❌ 日志文件不存在: " + LOG_FILE_PATH
                );
            }

            // Read last 100 lines as bytes with UTF-8 encoding
            byte[] logContent = readLastLinesAsBytes(logFile);

            if (logContent == null) {
                return buildEditMessage(
                        callbackQuery,
                        "❌ 读取日志文件失败"
                );
            }

            // Send file to user using ByteArrayInputStream to preserve UTF-8 encoding
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String fileName = "oci-helper_" + timestamp + ".log";

            SendDocument sendDocument = SendDocument.builder()
                    .chatId(chatId)
                    .document(new InputFile(new ByteArrayInputStream(logContent), fileName))
                    .caption("📋 最近 " + MAX_LINES + " 条日志记录\n"
                            + "⏰ 生成时间: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .build();

            telegramClient.execute(sendDocument);

                log.info("日志文件发送成功: {}", fileName);

            // Return null since we already sent the document
            return null;

        } catch (TelegramApiException e) {
            log.error("发送日志文件失败", e);
            return buildEditMessage(
                    callbackQuery,
                    "❌ 发送日志文件失败: " + e.getMessage()
            );
        } catch (Exception e) {
            log.error("处理日志查询请求失败", e);
            return buildEditMessage(
                    callbackQuery,
                    "❌ 处理日志查询请求失败: " + e.getMessage()
            );
        }
    }

    /**
     * Read last N lines from log file as bytes with UTF-8 encoding
     *
     * @param logFile source log file
     * @return byte array of last N lines in UTF-8 encoding with BOM
     */
    private byte[] readLastLinesAsBytes(File logFile) {
        try {
            LinkedList<String> lastLines = new LinkedList<>();

            // Read file and keep last 100 lines
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(new FileInputStream(logFile), StandardCharsets.UTF_8))) {

                String line;
                while ((line = reader.readLine()) != null) {
                    lastLines.add(line);
                    if (lastLines.size() > MAX_LINES) {
                        lastLines.removeFirst();
                    }
                }
            }

            // Convert to bytes with UTF-8 BOM encoding
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            // Add UTF-8 BOM (EF BB BF) to help text editors recognize the encoding
            baos.write(0xEF);
            baos.write(0xBB);
            baos.write(0xBF);
            
            try (BufferedWriter writer = new BufferedWriter(
                    new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {

                for (String line : lastLines) {
                    writer.write(line);
                    writer.newLine();
                }
                writer.flush();
            }

            return baos.toByteArray();

        } catch (IOException e) {
            log.error("读取日志文件失败", e);
            return null;
        }
    }

    @Override
    public String getCallbackPattern() {
        return "log_query";
    }
}
