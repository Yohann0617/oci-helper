package com.yohann.ocihelper.utils;

import cn.hutool.json.JSONUtil;
import com.oracle.bmc.identity.IdentityClient;
import com.oracle.bmc.identity.model.DomainSummary;
import com.oracle.bmc.identity.requests.ListDomainsRequest;
import com.oracle.bmc.identity.responses.ListDomainsResponse;
import com.oracle.bmc.identitydomains.IdentityDomainsClient;
import com.oracle.bmc.identitydomains.model.NotificationSetting;
import com.oracle.bmc.identitydomains.model.PasswordPolicies;
import com.oracle.bmc.identitydomains.model.PasswordPolicy;
import com.oracle.bmc.identitydomains.requests.GetNotificationSettingRequest;
import com.oracle.bmc.identitydomains.requests.ListPasswordPoliciesRequest;
import com.oracle.bmc.identitydomains.requests.PutNotificationSettingRequest;
import com.oracle.bmc.identitydomains.requests.PutPasswordPolicyRequest;
import com.oracle.bmc.identitydomains.responses.ListPasswordPoliciesResponse;
import com.oracle.bmc.identitydomains.responses.PutPasswordPolicyResponse;
import com.yohann.ocihelper.config.OracleInstanceFetcher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.util.*;
import java.util.regex.Pattern;

/**
 * @ClassName OciUtils
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-09-19 17:34
 **/
@Slf4j
public class OciUtils {

    private static final String NOTIFICATION_SETTINGS_SCHEMA =
            "urn:ietf:params:scim:schemas:oracle:idcs:NotificationSettings";
    private static final String NOTIFICATION_SETTINGS_ID = "NotificationSettings";
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");

    /**
     * 统一的返回结果封装
     */
    public static class Result extends HashMap<String, Object> {
        public static Result ok(String message) {
            Result r = new Result();
            r.put("success", true);
            r.put("message", message);
            return r;
        }

        public static Result fail(String message) {
            Result r = new Result();
            r.put("success", false);
            r.put("message", message);
            return r;
        }

        public Result data(String key, Object value) {
            this.put(key, value);
            return this;
        }
    }

    /**
     * 校验邮箱
     */
    private static boolean isValidEmail(String email) {
        return StringUtils.isNotBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * 获取当前收件人
     */
    public static Result getCurrentRecipients(OracleInstanceFetcher fetcher) {
        try {
            IdentityDomainsClient client = fetcher.getIdentityDomainsClient();
            NotificationSetting setting = client.getNotificationSetting(
                    GetNotificationSettingRequest.builder().notificationSettingId(NOTIFICATION_SETTINGS_ID).build()
            ).getNotificationSetting();
            List<String> recipients = Optional.ofNullable(setting.getTestRecipients()).orElse(Collections.emptyList());
            return Result.ok("Recipients retrieved")
                    .data("recipients", recipients)
                    .data("totalCount", recipients.size());
        } catch (Exception e) {
            log.error("getCurrentRecipients error: {}", e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 更新收件人
     */
    public static Result updateRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        List<String> valid = new ArrayList<>();
        List<String> invalid = new ArrayList<>();
        for (String email : emails) {
            if (isValidEmail(email)) {
                valid.add(email.trim().toLowerCase());
            } else {
                invalid.add(email);
            }
        }
        if (!invalid.isEmpty()) {
            return Result.fail("Invalid emails: " + String.join(", ", invalid));
        }
        if (valid.isEmpty()) {
            return Result.fail("No valid email provided");
        }

        try {
            IdentityDomainsClient client = fetcher.getIdentityDomainsClient();
            NotificationSetting old = client.getNotificationSetting(
                    GetNotificationSettingRequest.builder().notificationSettingId(NOTIFICATION_SETTINGS_ID).build()
            ).getNotificationSetting();

            NotificationSetting updated = NotificationSetting.builder()
                    .copy(old)
                    .testRecipients(valid)
                    .testModeEnabled(true)
                    .schemas(Collections.singletonList(NOTIFICATION_SETTINGS_SCHEMA))
                    .build();

            client.putNotificationSetting(PutNotificationSettingRequest.builder()
                    .notificationSettingId(NOTIFICATION_SETTINGS_ID)
                    .notificationSetting(updated)
                    .build()
            );

            return Result.ok("Recipients updated")
                    .data("previousRecipients", old.getTestRecipients())
                    .data("newRecipients", valid)
                    .data("recipientCount", valid.size());
        } catch (Exception e) {
            log.error("updateRecipients error: {}", e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 添加收件人
     */
    public static Result addRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        Result currentRes = getCurrentRecipients(fetcher);
        if (!(boolean) currentRes.get("success")) {
            return currentRes;
        }

        List<String> current = (List<String>) currentRes.get("recipients");
        Set<String> newSet = new HashSet<>(current);

        List<String> added = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        for (String email : emails) {
            String lower = email.trim().toLowerCase();
            if (!isValidEmail(lower)) {
                continue;
            }
            if (newSet.add(lower)) {
                added.add(lower);
            } else {
                duplicates.add(lower);
            }
        }

        if (added.isEmpty()) {
            return Result.ok("No new recipients added")
                    .data("duplicateEmails", duplicates)
                    .data("currentRecipients", current);
        }

        return updateRecipients(fetcher, new ArrayList<>(newSet))
                .data("addedRecipients", added)
                .data("duplicateEmails", duplicates)
                .data("totalRecipients", newSet.size());
    }

    /**
     * 移除收件人
     */
    public static Result removeRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        Result currentRes = getCurrentRecipients(fetcher);
        if (!(boolean) currentRes.get("success")) {
            return currentRes;
        }

        List<String> current = (List<String>) currentRes.get("recipients");
        Set<String> remaining = new HashSet<>(current);

        List<String> removed = new ArrayList<>();
        for (String email : emails) {
            String lower = email.trim().toLowerCase();
            if (remaining.remove(lower)) {
                removed.add(lower);
            }
        }

        if (removed.isEmpty()) {
            return Result.ok("No recipients removed")
                    .data("currentRecipients", current);
        }

        return updateRecipients(fetcher, new ArrayList<>(remaining))
                .data("removedRecipients", removed)
                .data("remainingRecipients", remaining);
    }

    /**
     * 更新测试模式开关
     */
    public static Result updateTestMode(OracleInstanceFetcher fetcher, boolean enable) {
        try {
            IdentityDomainsClient client = fetcher.getIdentityDomainsClient();
            NotificationSetting old = client.getNotificationSetting(
                    GetNotificationSettingRequest.builder().notificationSettingId(NOTIFICATION_SETTINGS_ID).build()
            ).getNotificationSetting();

            NotificationSetting updated = NotificationSetting.builder()
                    .copy(old)
                    .testModeEnabled(enable)
                    .schemas(Collections.singletonList(NOTIFICATION_SETTINGS_SCHEMA))
                    .build();

            client.putNotificationSetting(PutNotificationSettingRequest.builder()
                    .notificationSettingId(NOTIFICATION_SETTINGS_ID)
                    .notificationSetting(updated)
                    .build()
            );

            return Result.ok("Test mode updated")
                    .data("testMode", enable)
                    .data("recipients", old.getTestRecipients());
        } catch (Exception e) {
            log.error("updateTestMode error: {}", e.getMessage(), e);
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 关闭密码过期
     */
    public static boolean disablePasswordExpirationWithAutoDomain(OracleInstanceFetcher fetcher) {
        return updatePasswordExpiration(fetcher, 0);
    }

    /**
     * 启用密码过期（默认 120 天）
     */
    public static boolean enablePasswordExpirationWithAutoDomain(OracleInstanceFetcher fetcher) {
        return enablePasswordExpirationWithAutoDomain(fetcher, 120);
    }

    /**
     * 启用密码过期（自定义天数）
     */
    public static boolean enablePasswordExpirationWithAutoDomain(OracleInstanceFetcher fetcher, Integer expirationDays) {
        if (expirationDays == null || expirationDays <= 0) {
            log.error("Invalid expirationDays: {}", expirationDays);
            return false;
        }
        return updatePasswordExpiration(fetcher, expirationDays);
    }

    /**
     * 公共方法：更新密码过期策略
     */
    private static boolean updatePasswordExpiration(OracleInstanceFetcher fetcher, int expirationDays) {

        try {
            String tenantId = fetcher.getUser().getOciCfg().getTenantId();
            IdentityClient identityClient = fetcher.getIdentityClient();
            IdentityDomainsClient identityDomainsClient = fetcher.getIdentityDomainsClient();

            // 获取 Domain URL
            String domainUrl = getDomain(identityClient, tenantId);
            if (StringUtils.isBlank(domainUrl)) {
                log.warn("No active domain found for tenant: {}", tenantId);
                return false;
            }
            identityDomainsClient.setEndpoint(domainUrl);

            // 查询当前策略
            List<PasswordPolicy> policies = listPasswordPolicies(identityDomainsClient);
            if (policies.isEmpty()) {
                log.warn("No password policies found for domain: {}", domainUrl);
                return false;
            }

            for (com.oracle.bmc.identitydomains.model.PasswordPolicy policy : policies) {
                log.debug("Current policy: {}", JSONUtil.toJsonStr(policy));

                if (policy.getPasswordStrength() != com.oracle.bmc.identitydomains.model.PasswordPolicy.PasswordStrength.Custom) {
                    log.warn("Skip non-custom policy: {}", policy.getName());
                    continue;
                }

                com.oracle.bmc.identitydomains.model.PasswordPolicy updated = com.oracle.bmc.identitydomains.model.PasswordPolicy.builder()
                        .copy(policy)
                        .passwordExpiresAfter(expirationDays)  // 0 = 不过期
                        .forcePasswordReset(false)
                        .passwordExpireWarning(7)
                        .build();

                PutPasswordPolicyRequest request = PutPasswordPolicyRequest.builder()
                        .passwordPolicyId(policy.getId())
                        .passwordPolicy(updated)
                        .build();

                PutPasswordPolicyResponse response = identityDomainsClient.putPasswordPolicy(request);
                if (response.getPasswordPolicy() != null) {
                    log.info("Updated password policy [{}]: expiresAfter={}",
                            policy.getName(), expirationDays);
                }
            }
            return true;
        } catch (Exception e) {
            log.error("Failed to update password expiration: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取当前密码策略
     */
    public static List<com.oracle.bmc.identitydomains.model.PasswordPolicy> getCurrentPasswordPolicy(OracleInstanceFetcher fetcher) {

        try {
            String tenantId = fetcher.getUser().getOciCfg().getTenantId();
            IdentityClient identityClient = fetcher.getIdentityClient();
            IdentityDomainsClient identityDomainsClient = fetcher.getIdentityDomainsClient();

            String domainUrl = getDomain(identityClient, tenantId);
            if (StringUtils.isBlank(domainUrl)) {
                return Collections.emptyList();
            }
            identityDomainsClient.setEndpoint(domainUrl);

            return listPasswordPolicies(identityDomainsClient);
        } catch (Exception e) {
            log.error("Failed to get password policies: {}", e.getMessage(), e);
            return Collections.emptyList();
        }
    }

    /**
     * 列出密码策略
     */
    private static List<com.oracle.bmc.identitydomains.model.PasswordPolicy> listPasswordPolicies(IdentityDomainsClient domainsClient) {
        ListPasswordPoliciesResponse resp = domainsClient.listPasswordPolicies(
                ListPasswordPoliciesRequest.builder().build()
        );
        PasswordPolicies wrapper = resp.getPasswordPolicies();
        return wrapper != null && wrapper.getResources() != null ? wrapper.getResources() : Collections.emptyList();
    }

    /**
     * 获取 Domain URL
     */
    public static String getDomain(IdentityClient identityClient, String compartmentId) {
        try {
            ListDomainsResponse response = identityClient.listDomains(
                    ListDomainsRequest.builder().compartmentId(compartmentId).build()
            );
            for (DomainSummary domain : response.getItems()) {
                if (domain.getLifecycleState() == DomainSummary.LifecycleState.Active) {
                    log.debug("Found domain [{}] URL: {}", domain.getDisplayName(), domain.getUrl());
                    return domain.getUrl();
                }
            }
            log.error("No active domain found in compartment: {}", compartmentId);
            return "";
        } catch (Exception e) {
            throw new RuntimeException("Failed to get domain", e);
        }
    }
}
