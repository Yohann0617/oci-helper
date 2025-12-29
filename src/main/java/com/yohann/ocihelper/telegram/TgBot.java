package com.yohann.ocihelper.telegram;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.RuntimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.entity.OciCreateTask;
import com.yohann.ocihelper.bean.entity.OciKv;
import com.yohann.ocihelper.bean.entity.OciUser;
import com.yohann.ocihelper.bean.response.oci.traffic.FetchInstancesRsp;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import com.yohann.ocihelper.enums.SysCfgEnum;
import com.yohann.ocihelper.enums.SysCfgTypeEnum;
import com.yohann.ocihelper.service.*;
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
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static com.yohann.ocihelper.config.VirtualThreadConfig.VIRTUAL_EXECUTOR;
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
                        .text("❌ 无权限操作此机器人🤖，项目地址：https://github.com/Yohann0617/oci-helper")
                        .build();
                try {
                    telegramClient.execute(message);
//                    ISysService sysService = SpringUtil.getBean(ISysService.class);
//                    sysService.sendMessage("用户：" + chat_id + " 操作失败，发送的消息：" + message_text);
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
                case "version_info":
                    try {
                        getVersionInfo(chat_id, message_id);
                    } catch (TelegramApiException e) {
                        log.error("TG Bot error", e);
                    }
                    break;
                case "traffic_statistics":
                    try {
                        telegramClient.execute(EditMessageText.builder()
                                .chatId(chat_id)
                                .messageId(toIntExact(message_id))
                                .text(getTrafficStatistics())
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
                case "update_sys_version":
                    List<String> command = List.of("/bin/sh", "-c", "echo trigger > /app/oci-helper/update_version_trigger.flag");
                    Process process = RuntimeUtil.exec(command.toArray(new String[0]));

                    int exitCode = 0;
                    try {
                        exitCode = process.waitFor();
                    } catch (InterruptedException e) {
                        log.error("TG Bot error", e);
                    }

                    if (exitCode == 0) {
                        log.info("Start the version update task...");
                        try {
                            telegramClient.execute(DeleteMessage.builder()
                                    .chatId(chat_id)
                                    .messageId(toIntExact(message_id))
                                    .build());
                            telegramClient.execute(SendMessage.builder()
                                    .chatId(chat_id)
                                    .text("\uD83D\uDD04 正在更新 oci-helper 最新版本，请稍后...")
                                    .build());
                        } catch (TelegramApiException e) {
                            log.error("TG Bot error", e);
                        }
                    } else {
                        log.error("version update task exec error,exitCode:{}", exitCode);
                        try {
                            telegramClient.execute(SendMessage.builder()
                                    .chatId(chat_id)
                                    .text("一键更新失败，请手动更新版本~")
                                    .build());
                        } catch (TelegramApiException e) {
                            log.error("TG Bot error", e);
                        }
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
                                .text("\uD83D\uDEE1\uFE0F 版本信息")
                                .callbackData("version_info")
                                .build(),
                        InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDCCA 流量统计")
                                .callbackData("traffic_statistics")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDCE2 通知频道")
                                .url("https://t.me/oci_helper")
                                .build(),
                        InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDD0D 放货查询")
                                .url("https://check.oci-helper.de5.net")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text("\uD83D\uDCBB 开源地址（帮忙点点star⭐）")
                                .url("https://github.com/Yohann0617/oci-helper")
                                .build()
                ),
                new InlineKeyboardRow(
                        InlineKeyboardButton
                                .builder()
                                .text("❌ 关闭窗口")
                                .callbackData("cancel")
                                .build()
                )
        );
    }

    private String getTrafficStatistics() {
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        ITrafficService trafficService = SpringUtil.getBean(ITrafficService.class);
        List<OciUser> ociUserList = userService.list();
        if (CollectionUtil.isEmpty(ociUserList)) {
            return "暂无配置信息";
        }
        return "【流量统计】\n\n" + Optional.ofNullable(userService.list())
                .filter(CollectionUtil::isNotEmpty).orElseGet(Collections::emptyList).parallelStream()
                .map(ociCfg -> {
                    FetchInstancesRsp fetchInstancesRsp;
                    try {
                        fetchInstancesRsp = trafficService.fetchInstances(ociCfg.getId(), ociCfg.getOciRegion());
                    } catch (Exception e) {
                        return "";
                    }
                    return String.format("\uD83D\uDD58 时间：%s\n🔑 配置名：【%s】\n🌏 主区域：【%s】\n\uD83D\uDDA5 实例数量：【%s】 台\n⬇ 本月入站流量总计：%s\n⬆ 本月出站流量总计：%s\n",
                            LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM), ociCfg.getUsername(),
                            ociCfg.getOciRegion(), fetchInstancesRsp.getInstanceCount(),
                            fetchInstancesRsp.getInboundTraffic(), fetchInstancesRsp.getOutboundTraffic()
                    );
                }).filter(StrUtil::isNotBlank).collect(Collectors.joining("\n"));
    }

    private void getVersionInfo(long chatId, long messageId) throws TelegramApiException {
        String content = "【版本信息】\n\n当前版本：%s\n最新版本：%s\n";
        IOciKvService kvService = SpringUtil.getBean(IOciKvService.class);
        String latest = CommonUtils.getLatestVersion();
        String now = kvService.getObj(new LambdaQueryWrapper<OciKv>()
                .eq(OciKv::getCode, SysCfgEnum.SYS_INFO_VERSION.getCode())
                .eq(OciKv::getType, SysCfgTypeEnum.SYS_INFO.getCode())
                .select(OciKv::getValue), String::valueOf);
        String common = String.format(content, now, latest);
        if (!now.equals(latest)) {
            common += String.format("一键脚本：%s\n更新内容：\n%s",
                    "bash <(wget -qO- https://github.com/Yohann0617/oci-helper/releases/latest/download/sh_oci-helper_install.sh)",
                    CommonUtils.getLatestVersionBody());
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(toIntExact(messageId))
                    .text(common)
                    .replyMarkup(new InlineKeyboardMarkup(Arrays.asList(
                            new InlineKeyboardRow(
                                    InlineKeyboardButton
                                            .builder()
                                            .text("\uD83D\uDD04 点击更新至最新版本")
                                            .callbackData("update_sys_version")
                                            .build()
                            ),
                            new InlineKeyboardRow(
                                    InlineKeyboardButton
                                            .builder()
                                            .text("❌ 关闭窗口")
                                            .callbackData("cancel")
                                            .build()
                            )
                    )))
                    .build());
        } else {
            common += "当前已是最新版本，无需更新~";
            telegramClient.execute(EditMessageText.builder()
                    .chatId(chatId)
                    .messageId(toIntExact(messageId))
                    .text(common)
                    .replyMarkup(new InlineKeyboardMarkup(getStartInlineKeyboardRowList()))
                    .build());
        }


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
        return String.format("【API测活结果】\n\n✅ 有效配置数：%s\n❌ 失效配置数：%s\n\uD83D\uDD11 总配置数：%s\n⚠\uFE0F 失效配置：\n%s",
                ids.size() - failNames.size(), failNames.size(), ids.size(), String.join("\n", failNames));
    }

    private String taskDetails() {
        IOciUserService userService = SpringUtil.getBean(IOciUserService.class);
        IOciCreateTaskService createTaskService = SpringUtil.getBean(IOciCreateTaskService.class);

        String message = "【任务详情】\n" +
                "\n" +
                "\uD83D\uDD58 时间：\t%s\n" +
                "\uD83D\uDECE 正在执行的开机任务：\n" +
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
        }, VIRTUAL_EXECUTOR);

        CompletableFuture.allOf(task).join();

        return String.format(message,
                LocalDateTime.now().format(CommonUtils.DATETIME_FMT_NORM),
                task.join()
        );
    }

}
