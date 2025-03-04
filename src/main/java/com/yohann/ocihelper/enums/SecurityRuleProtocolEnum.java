package com.yohann.ocihelper.enums;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.enums
 * @className: SecurityRuleProtocolEnum
 * @author: Yohann
 * @date: 2025/3/4 22:13
 */
public enum SecurityRuleProtocolEnum {
    ALL("all", "所有协议"),
    ICMP("1", "ICMP"),
    RDP("6_RDP", "RDP (TCP/3389)"),
    SSH("6_SSH", "SSH (TCP/22)"),
    TCP("6", "TCP"),
    UDP("17", "UDP"),
    THREE_PC("34", "3PC"),
    AN("107", "A/N"),
    AH("51", "AH"),
    ARGUS("13", "ARGUS"),
    ARIS("104", "ARIS"),
    AX_25("93", "AX.25"),
    ANY_ZERO_HOP("114", "任意 0 跃点协议"),
    ANY_DFS("68", "任何分布式文件系统"),
    ANY_HOST_INTERNAL("61", "任何主机内部协议"),
    ANY_LOCAL_NETWORK("63", "任何本地网络"),
    ANY_PRIVATE_ENCRYPTION("99", "任何专用加密方案"),
    BBN_RCC_MON("10", "BBN-RCC-MON"),
    BNA("49", "BNA"),
    BR_SAT_MON("76", "BR-SAT-MON"),
    CBT("7", "CBT"),
    CFTP("62", "CFTP"),
    CHAOS("16", "CHAOS"),
    CPHB("73", "CPHB"),
    CPNX("72", "CPNX"),
    CRTP("126", "CRTP"),
    CRUDP("127", "CRUDP"),
    COMPAQ_PEER("110", "Compaq-Peer"),
    DCCP("33", "DCCP"),
    DCN_MEAS("19", "DCN-MEAS"),
    DDP("37", "DDP"),
    DDX("116", "DDX"),
    DGP("86", "DGP"),
    EGP("8", "EGP"),
    EIGRP("88", "EIGRP"),
    EMCON("14", "EMCON"),
    ENCAP("98", "ENCAP"),
    ESP("50", "ESP"),
    ETHERIP("97", "ETHERIP"),
    FC("133", "FC"),
    FIRE("125", "FIRE"),
    GGP("3", "GGP"),
    GMTP("100", "GMTP"),
    GRE("47", "GRE"),
    HIP("139", "HIP"),
    HMP("20", "HMP"),
    HOPOPT("0", "HOPOPT"),
    IGMP("2", "IGMP"),
    IGRP("9", "IGP"),
    IL("40", "IL"),
    IP_IN_IP("4", "IP-in-IP"),
    IPCOMP("108", "IPComp"),
    IPPC("67", "IPPC"),
    IPV6("41", "IPv6"),
    IPV6_FRAG("44", "IPv6-Frag"),
    IPV6_ICMP("58", "IPv6-ICMP"),
    IPV6_NONXT("59", "IPv6-NoNxt"),
    IPV6_OPTS("60", "IPv6-Opts"),
    IPV6_ROUTE("43", "IPv6-Route"),
    OSPF("89", "OSPF"),
    PGM("113", "PGM"),
    RSVP("46", "RSVP"),
    SCTP("132", "SCTP"),
    UDPLITE("136", "UDPLite");

    private final String code;
    private final String desc;

    SecurityRuleProtocolEnum(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static SecurityRuleProtocolEnum fromCode(String code) {
        for (SecurityRuleProtocolEnum protocol : values()) {
            if (protocol.code.equals(code)) {
                return protocol;
            }
        }
        throw new IllegalArgumentException("Invalid protocol code: " + code);
    }
}

