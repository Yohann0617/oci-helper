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
    TCP("6", "TCP"),
    UDP("17", "UDP"),
    THREE_PC("34", "3PC"),
    A_N("107", "A/N"),
    AH("51", "AH"),
    ARGUS("13", "ARGUS"),
    ARIS("104", "ARIS"),
    AX_25("93", "AX.25"),
    ANY_ZERO_HOP_PROTOCOL("114", "任意 0 跃点协议"),
    ANY_DISTRIBUTED_FILE_SYSTEM("68", "任何分布式文件系统"),
    ANY_HOST_INTERNAL_PROTOCOL("61", "任何主机内部协议"),
    ANY_LOCAL_NETWORK("63", "任何本地网络"),
    ANY_PRIVATE_ENCRYPTION_SCHEME("99", "任何专用加密方案"),
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
    I_NLSP("52", "I-NLSP"),
    IATP("117", "IATP"),
    IDPR("35", "IDPR"),
    IDPR_CMTP("38", "IDPR-CMTP"),
    IDRP("45", "IDRP"),
    IFMP("101", "IFMP"),
    IGMP("2", "IGMP"),
    IGP("9", "IGP"),
    IL("40", "IL"),
    IP_IN_IP("4", "IP-in-IP"),
    IPCU("71", "IPCU"),
    IPCOMP("108", "IPComp"),
    IPIP("94", "IPIP"),
    IPLT("129", "IPLT"),
    IPPC("67", "IPPC"),
    IPX_IN_IP("111", "IPX-in-IP"),
    IPV6("41", "IPv6"),
    IPV6_FRAG("44", "IPv6-Frag"),
    IPV6_ICMP("58", "IPv6-ICMP"),
    IPV6_NONXT("59", "IPv6-NoNxt"),
    IPV6_OPTS("60", "IPv6-Opts"),
    IPV6_ROUTE("43", "IPv6-Route"),
    IRTP("28", "IRTP"),
    IS_IS("124", "IS-IS"),
    ISO_IP("80", "ISO-IP"),
    ISO_TP4("29", "ISO-TP4"),
    KRYPTOLAN("65", "KRYPTOLAN"),
    L2TP("115", "L2TP"),
    LARP("91", "LARP"),
    LEAF_1("25", "LEAF-1"),
    LEAF_2("26", "LEAF-2"),
    MANET("138", "Manet"),
    MERIT_INP("32", "MERIT-INP"),
    MFE_NSP("31", "MFE-NSP"),
    MHRP("48", "MHRP"),
    MICP("95", "MICP"),
    MOBILE("55", "MOBILE"),
    MPLS_IN_IP("137", "MPLS-in-IP"),
    MTP("92", "MTP"),
    MUX("18", "MUX"),
    MOBILITY("135", "Mobility"),
    NARP("54", "NARP"),
    NETBLT("30", "NETBLT"),
    NSFNET_IGP("85", "NSFNET-IGP"),
    NVP_II("11", "NVP-II"),
    OSPF("89", "OSPF"),
    PGM("113", "PGM"),
    PIM("103", "PIM"),
    PIPE("131", "PIPE"),
    PNNI("102", "PNNI"),
    PRM("21", "PRM"),
    PTP("123", "PTP"),
    PUP("12", "PUP"),
    PVP("75", "PVP"),
    QNX("106", "QNX"),
    RELIABLE_DATA_PROTOCOL("27", "可靠的数据协议"),
    ROHC("142", "ROHC"),
    RSVP("46", "RSVP"),
    RSVP_E2E_IGNORE("134", "RSVP-E2E-IGNORE"),
    RVD("66", "RVD"),
    SAT_EXPAK("64", "SAT-EXPAK"),
    SAT_MON("69", "SAT-MON"),
    SCC_SP("96", "SCC-SP"),
    SCPS("105", "SCPS"),
    SCTP("132", "SCTP"),
    SDRP("42", "SDRP"),
    SECURE_VMTP("82", "SECURE-VMTP"),
    SKIP("57", "SKIP"),
    SM("122", "SM"),
    SMP("121", "SMP"),
    SNP("109", "SNP"),
    SPS("130", "SPS"),
    SRP("119", "SRP"),
    SSCOPMCE("128", "SSCOPMCE"),
    ST("5", "ST"),
    STP("118", "STP"),
    SUN_ND("77", "SUN-ND"),
    SWIPE("53", "SWIPE"),
    SHIM6("140", "Shim6"),
    SPRITE_RPC("90", "Sprite-RPC"),
    TCF("87", "TCF"),
    TLSP("56", "TLSP"),
    TP_PLUS_PLUS("39", "TP++"),
    TRUNK_1("23", "TRUNK-1"),
    TRUNK_2("24", "TRUNK-2"),
    TTP_IPTM("84", "TTP/IPTM"),
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

