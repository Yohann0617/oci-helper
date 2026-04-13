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
     * Result DTO returned by {@link #getCurrentRecipients}.
     */
    @lombok.Value
    public static class NotificationSettingResult {
        String domainName;
        boolean testModeEnabled;
        List<String> recipients;
    }

    /**
     * Result DTO returned by {@link #updateRecipients} and {@link #updateTestMode}.
     */
    @lombok.Value
    public static class NotificationUpdateResult {
        List<String> updatedDomains;
        List<String> previousRecipients;
        List<String> newRecipients;
        boolean testModeEnabled;
    }

    /**
     * 校验邮箱
     */
    private static boolean isValidEmail(String email) {
        return StringUtils.isNotBlank(email) && EMAIL_PATTERN.matcher(email.trim()).matches();
    }

    /**
     * Get all active domain URLs for the tenant.
     * According to the Oracle documentation, if there are multiple domains,
     * the notification settings need to be configured for each domain.
     */
    private static List<DomainSummary> getActiveDomains(IdentityClient identityClient, String compartmentId) {
        try {
            ListDomainsResponse response = identityClient.listDomains(
                    ListDomainsRequest.builder().compartmentId(compartmentId).build()
            );
            List<DomainSummary> active = new ArrayList<>();
            for (DomainSummary domain : response.getItems()) {
                if (domain.getLifecycleState() == DomainSummary.LifecycleState.Active) {
                    active.add(domain);
                }
            }
            if (active.isEmpty()) {
                log.warn("No active domain found in compartment: {}", compartmentId);
            }
            return active;
        } catch (Exception e) {
            throw new RuntimeException("Failed to list active domains", e);
        }
    }

    /**
     * Fetch the NotificationSetting using a dedicated client for the given endpoint.
     * A new client is created per call to avoid endpoint-state races when multiple
     * threads share the same {@link OracleInstanceFetcher}.
     */
    private static NotificationSetting fetchNotificationSetting(OracleInstanceFetcher fetcher, String domainUrl) {
        try (IdentityDomainsClient client = fetcher.newIdentityDomainsClient(domainUrl)) {
            return client.getNotificationSetting(
                    GetNotificationSettingRequest.builder()
                            .notificationSettingId(NOTIFICATION_SETTINGS_ID)
                            .build()
            ).getNotificationSetting();
        }
    }

    /**
     * Push the updated NotificationSetting using a dedicated client for the given endpoint.
     */
    private static void pushNotificationSetting(OracleInstanceFetcher fetcher, String domainUrl,
                                                NotificationSetting updated) {
        try (IdentityDomainsClient client = fetcher.newIdentityDomainsClient(domainUrl)) {
            client.putNotificationSetting(PutNotificationSettingRequest.builder()
                    .notificationSettingId(NOTIFICATION_SETTINGS_ID)
                    .notificationSetting(updated)
                    .build());
        }
    }

    /**
     * Get the current notification recipients across all active domains.
     * Reads settings from the first active domain (all domains share the same config).
     *
     * @throws RuntimeException if no active domain is found or the API call fails
     */
    public static NotificationSettingResult getCurrentRecipients(OracleInstanceFetcher fetcher) {
        String tenantId = fetcher.getUser().getOciCfg().getTenantId();
        List<DomainSummary> domains = getActiveDomains(fetcher.getIdentityClient(), tenantId);
        if (domains.isEmpty()) {
            throw new RuntimeException("No active domain found for tenant: " + tenantId);
        }

        DomainSummary domain = domains.get(0);
        NotificationSetting setting = fetchNotificationSetting(fetcher, domain.getUrl());
        List<String> recipients = Optional.ofNullable(setting.getTestRecipients()).orElse(Collections.emptyList());
        return new NotificationSettingResult(
                domain.getDisplayName(),
                Boolean.TRUE.equals(setting.getTestModeEnabled()),
                recipients
        );
    }

    /**
     * Overwrite the recipient list for all active domains and enable test mode.
     * All notification emails will be redirected to the given recipients.
     *
     * @throws IllegalArgumentException if any email is invalid or the list is empty
     * @throws RuntimeException         if no active domain is found or the API call fails
     */
    public static NotificationUpdateResult updateRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
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
            throw new IllegalArgumentException("Invalid emails: " + String.join(", ", invalid));
        }
        if (valid.isEmpty()) {
            throw new IllegalArgumentException("No valid email provided");
        }

        String tenantId = fetcher.getUser().getOciCfg().getTenantId();
        List<DomainSummary> domains = getActiveDomains(fetcher.getIdentityClient(), tenantId);
        if (domains.isEmpty()) {
            throw new RuntimeException("No active domain found for tenant: " + tenantId);
        }

        List<String> updatedDomains = new ArrayList<>();
        List<String> previousRecipients = Collections.emptyList();

        for (DomainSummary domain : domains) {
            String domainUrl = domain.getUrl();
            NotificationSetting old = fetchNotificationSetting(fetcher, domainUrl);
            if (previousRecipients.isEmpty() && old.getTestRecipients() != null) {
                previousRecipients = old.getTestRecipients();
            }
            NotificationSetting updated = NotificationSetting.builder()
                    .copy(old)
                    .testRecipients(valid)
                    .testModeEnabled(true)
                    .schemas(Collections.singletonList(NOTIFICATION_SETTINGS_SCHEMA))
                    .build();
            pushNotificationSetting(fetcher, domainUrl, updated);
            updatedDomains.add(domain.getDisplayName());
            log.info("Updated notification recipients for domain [{}]: {}", domain.getDisplayName(), valid);
        }

        return new NotificationUpdateResult(updatedDomains, previousRecipients, valid, true);
    }

    /**
     * Add recipients to all active domains without removing existing ones.
     * Skips invalid and duplicate emails silently.
     *
     * @return the update result, or {@code null} if there was nothing new to add
     * @throws RuntimeException if no active domain is found or the API call fails
     */
    public static NotificationUpdateResult addRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        NotificationSettingResult current = getCurrentRecipients(fetcher);
        Set<String> merged = new LinkedHashSet<>(current.getRecipients());
        for (String email : emails) {
            String lower = email.trim().toLowerCase();
            if (isValidEmail(lower)) {
                merged.add(lower);
            }
        }
        if (merged.size() == current.getRecipients().size()) {
            // nothing new — return a no-op result
            return new NotificationUpdateResult(
                    Collections.emptyList(),
                    current.getRecipients(),
                    current.getRecipients(),
                    current.isTestModeEnabled()
            );
        }
        return updateRecipients(fetcher, new ArrayList<>(merged));
    }

    /**
     * Remove specific recipients from all active domains.
     * Emails that are not present are silently ignored.
     *
     * @return the update result, or a no-op result when nothing was removed
     * @throws RuntimeException if no active domain is found or the API call fails
     */
    public static NotificationUpdateResult removeRecipients(OracleInstanceFetcher fetcher, List<String> emails) {
        NotificationSettingResult current = getCurrentRecipients(fetcher);
        Set<String> remaining = new LinkedHashSet<>(current.getRecipients());
        for (String email : emails) {
            remaining.remove(email.trim().toLowerCase());
        }
        if (remaining.size() == current.getRecipients().size()) {
            return new NotificationUpdateResult(
                    Collections.emptyList(),
                    current.getRecipients(),
                    current.getRecipients(),
                    current.isTestModeEnabled()
            );
        }
        return updateRecipients(fetcher, new ArrayList<>(remaining));
    }

    /**
     * Toggle test mode on/off for all active domains.
     * When enabled, all notification emails are redirected to the configured test recipients.
     *
     * @throws RuntimeException if no active domain is found or the API call fails
     */
    public static NotificationUpdateResult updateTestMode(OracleInstanceFetcher fetcher, boolean enable) {
        String tenantId = fetcher.getUser().getOciCfg().getTenantId();
        List<DomainSummary> domains = getActiveDomains(fetcher.getIdentityClient(), tenantId);
        if (domains.isEmpty()) {
            throw new RuntimeException("No active domain found for tenant: " + tenantId);
        }

        List<String> updatedDomains = new ArrayList<>();
        List<String> recipients = Collections.emptyList();

        for (DomainSummary domain : domains) {
            String domainUrl = domain.getUrl();
            NotificationSetting old = fetchNotificationSetting(fetcher, domainUrl);
            if (recipients.isEmpty() && old.getTestRecipients() != null) {
                recipients = old.getTestRecipients();
            }
            NotificationSetting updated = NotificationSetting.builder()
                    .copy(old)
                    .testModeEnabled(enable)
                    .schemas(Collections.singletonList(NOTIFICATION_SETTINGS_SCHEMA))
                    .build();
            pushNotificationSetting(fetcher, domainUrl, updated);
            updatedDomains.add(domain.getDisplayName());
            log.info("Updated testMode={} for domain [{}]", enable, domain.getDisplayName());
        }

        return new NotificationUpdateResult(updatedDomains, recipients, recipients, enable);
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

            String domainUrl = getDomain(identityClient, tenantId);
            if (StringUtils.isBlank(domainUrl)) {
                log.warn("No active domain found for tenant: {}", tenantId);
                return false;
            }

            // Use a dedicated client to avoid endpoint-state races
            try (IdentityDomainsClient identityDomainsClient = fetcher.newIdentityDomainsClient(domainUrl)) {
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
            String domainUrl = getDomain(fetcher.getIdentityClient(), tenantId);
            if (StringUtils.isBlank(domainUrl)) {
                return Collections.emptyList();
            }
            // Use a dedicated client to avoid endpoint-state races
            try (IdentityDomainsClient identityDomainsClient = fetcher.newIdentityDomainsClient(domainUrl)) {
                return listPasswordPolicies(identityDomainsClient);
            }
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
     * Get the URL of the first active domain (kept for backward compatibility with password policy methods).
     */
    public static String getDomain(IdentityClient identityClient, String compartmentId) {
        List<DomainSummary> domains = getActiveDomains(identityClient, compartmentId);
        if (domains.isEmpty()) {
            log.error("No active domain found in compartment: {}", compartmentId);
            return "";
        }
        String url = domains.get(0).getUrl();
        log.debug("Found domain [{}] URL: {}", domains.get(0).getDisplayName(), url);
        return url;
    }
}
