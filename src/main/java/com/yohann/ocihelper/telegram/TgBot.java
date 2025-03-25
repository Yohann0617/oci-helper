package com.yohann.ocihelper.telegram;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.service.IOciCreateTaskService;
import com.yohann.ocihelper.service.IOciService;
import com.yohann.ocihelper.service.IOciUserService;
import com.yohann.ocihelper.service.ISysService;
import com.yohann.ocihelper.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.service.impl.OciServiceImpl.TEMP_MAP;
import static java.lang.Math.toIntExact;

/**
 * @ClassName Test
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-25 13:38
 **/
@Slf4j
public class TgBot implements LongPollingSingleThreadUpdateConsumer {

    private final String BOT_TOKEN;
    private final String CHAT_ID;
    private final TelegramClient telegramClient;

    public TgBot(String botToken, String chatId) {
        BOT_TOKEN = botToken;
        CHAT_ID = chatId;
        telegramClient = new OkHttpTelegramClient(BOT_TOKEN);
    }

    @Override
    public void consume(List<Update> updates) {
        LongPollingSingleThreadUpdateConsumer.super.consume(updates);
    }

    @Override
    public void consume(Update update) {
        if (update.hasMessage() && update.getMessage().hasText()) {
            String message_text = update.getMessage().getText();
            long chat_id = update.getMessage().getChatId();
            SendMessage message;

            if (!CHAT_ID.equals(String.valueOf(chat_id))) {
                message = SendMessage
                        .builder()
                        .chatId(chat_id)
                        .text("无权限")
                        .build();
                try {
                    telegramClient.execute(message);
                    ISysService sysService = SpringUtil.getBean(ISysService.class);
                    sysService.sendMessage("用户：" + chat_id + " 操作失败，发送的消息：" + message_text);
                    return;
                } catch (TelegramApiException e) {
                    log.error("TG Bot error", e);
                }
            }

            if (message_text.equals("/start")) {
                start(chat_id);
            }
        }

        if (update.hasCallbackQuery()) {
            // Set variables
            String call_data = update.getCallbackQuery().getData();
            long message_id = update.getCallbackQuery().getMessage().getMessageId();
            long chat_id = update.getCallbackQuery().getMessage().getChatId();

            switch (call_data) {
                case "check_alive":
                    try {
                        telegramClient.execute(EditMessageText.builder()
                                .chatId(chat_id)
                                .messageId(toIntExact(message_id))
                                .text(checkAlive())
                                .replyMarkup(new InlineKeyboardMarkup(getStartInlineKeyboardRowList()))
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("TG Bot error", e);
                    }
                    break;
                case "task_details":
                    try {
                        telegramClient.execute(EditMessageText.builder()
                                .chatId(chat_id)
                                .messageId(toIntExact(message_id))
                                .text(taskDetails())
                                .replyMarkup(new InlineKeyboardMarkup(getStartInlineKeyboardRowList()))
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("TG Bot error", e);
                    }
                    break;
                case "cancel":
                    try {
                        telegramClient.execute(DeleteMessage.builder()
                                .chatId(chat_id)
                                .messageId(toIntExact(message_id))
                                .build());
                    } catch (TelegramApiException e) {
                        log.error("TG Bot error", e);
                    }
                    break;
                default:
                    break;
            }
        }
    }

    private void start(long chatId) {
        try {
            telegramClient.execute(SendMessage
                    .builder()
                    .chatId(chatId)
                    .text("请选择需要执行的操作：")
                    .replyMarkup(InlineKeyboardMarkup
                            .builder()
                            .keyboard(getStartInlineKeyboardRowList())
                            .build())
                    .build());
        } catch (TelegramApiException e) {
            log.error("TG Bot error", e);
        }
    }

    private List<InlineKeyboardRow> getStartInlineKeyboardRowList() {
        return Arrays.asList(
                new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDECE 一键测活")
                                .callbackData("check_alive")
                                .build(),
                        InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDCC3 任务详情")
                                .callbackData("task_details")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text("❌ 关闭")
                                .callbackData("cancel")
                                .build()
                )
        );
    }

    private String checkAlive() {
        ISysService sysService = SpringUtil.getBean(ISysService.class);
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        List<String> ids = userService.listObjs(new LambdaQueryWrapper<OciUser>()
                .isNotNull(OciUser::getId)
                .select(OciUser::getId), String::valueOf);
        if (CollectionUtil.isEmpty(ids)) {
            return "暂无配置";
        }

        List<String> failNames = ids.parallelStream().filter(id -> {
            SysUserDTO ociUser = sysService.getOciUser(id);
            try (OracleInstanceFetcher fetcher = new OracleInstanceFetcher(ociUser)) {
                fetcher.getAvailabilityDomains();
            } catch (Exception e) {
                return true;
            }
            return false;
        }).map(id -> sysService.getOciUser(id).getUsername()).collect(Collectors.toList());
        return String.format("【API测活结果】\n\n有效配置数：%s\n失效配置数：%s\n总配置数：%s\n失效配置：【%s】",
                ids.size() - failNames.size(), failNames.size(), ids.size(), String.join("\n", failNames));
    }

    private String taskDetails() {
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        IOciCreateTaskService createTaskService = SpringUtil.getBean(IOciCreateTaskService.class);

        String message = "【任务详情】\n" +
                "\n" +
                "时间：\t%s\n" +
                "正在执行的开机任务：\n" +
                "%s\n";

        CompletableFuture<String> task = CompletableFuture.supplyAsync(() -> {
            List<OciCreateTask> ociCreateTaskList = createTaskService.list();
            if (ociCreateTaskList.isEmpty()) {
                return "无";
            }
            String template = "[%s] [%s] [%s] [%s核/%sGB/%sGB] [%s台] [%s] [%s次]";
            return ociCreateTaskList.parallelStream().map(x -> {
                OciUser ociUser = userService.getById(x.getUserId());
                Long counts = (Long) TEMP_MAP.get(CommonUtils.CREATE_COUNTS_PREFIX + x.getId());
                return String.format(template, ociUser.getUsername(), ociUser.getOciRegion(), x.getArchitecture(),
                        x.getOcpus().longValue(), x.getMemory().longValue(), x.getDisk(), x.getCreateNumbers(),
                        CommonUtils.getTimeDifference(x.getCreateTime()), counts == null ? "0" : counts);
            }).collect(Collectors.joining("\n"));
        });

        CompletableFuture.allOf(task).join();

        return String.format(message,
                LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                task.join()
        );
    }

}
