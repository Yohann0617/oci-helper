package com.yohann.ocihelper.bean.response.oci.tenant;

import lombok.Data;

import java.util.List;

/**
 * @ClassName TenantInfoRsp
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-10 14:58
 **/
@Data
public class TenantInfoRsp {

    private String id;
    private String name;
    private String description;
    private String homeRegionKey;
    private String upiIdcsCompatibilityLayerEndpoint;
    private List<String> regions;
    private List<TenantUserInfo> userList;
    private String creatTime;
    private Integer passwordExpiresAfter;
    /** Test-mode recipient emails configured on the Identity Domain notification settings */
    private List<String> notificationRecipients;
    /** Whether domain notification test mode is currently enabled */
    private Boolean notificationTestModeEnabled;
    /** Subscription info */
    private SubscriptionInfo subscriptionInfo;

    @Data
    public static class TenantUserInfo{
           private String id;
           private String name;
           private String email;
           private String lifecycleState;
           private Boolean emailVerified;
           private Boolean isMfaActivated;
           private String timeCreated;
           private String lastSuccessfulLoginTime;
           private String jsonStr;
    }

    @Data
    public static class SubscriptionInfo {
        /** FREE_TIER / PAYG */
        private String planType;
        /** Personal / Corporate / CorporateSubmitted */
        private String accountType;
        /** Promo / Submitted / Error / Upgraded */
        private String upgradeState;
        /** e.g. USD */
        private String currencyCode;
        /** Whether the user has completed payment intent (i.e. upgraded to PAYG) */
        private Boolean isIntentToPay;
        /** Whether corporate conversion is allowed */
        private Boolean isCorporateConversionAllowed;
        /** Subscription start time */
        private String timeStart;
    }
}
