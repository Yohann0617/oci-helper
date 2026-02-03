package com.yohann.ocihelper.enums;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author Yohann
 */

public enum OciRegionsEnum {

    // Asia Pacific
    AP_SYDNEY_1("ap-sydney-1", "澳大利亚东部（悉尼）", "Sydney, Australia", "SYD"),
    AP_MELBOURNE_1("ap-melbourne-1", "澳大利亚东南部（墨尔本）", "Melbourne, Australia", "MEL"),
    AP_HYDERABAD_1("ap-hyderabad-1", "印度南部（海得拉巴）", "Hyderabad, India", "HYD"),
    AP_MUMBAI_1("ap-mumbai-1", "印度西部（孟买）", "Mumbai, India", "BOM"),
    AP_BATAM_1("ap-batam-1", "印度尼西亚北部（巴淡岛）", "Batam, Indonesia", "HSG"),
    AP_OSAKA_1("ap-osaka-1", "日本中部（大阪）", "Osaka, Japan", "KIX"),
    AP_TOKYO_1("ap-tokyo-1", "日本东部（东京）", "Tokyo, Japan", "NRT"),
    AP_SINGAPORE_1("ap-singapore-1", "新加坡", "Singapore", "SIN"),
    AP_SINGAPORE_2("ap-singapore-2", "新加坡西部", "Singapore", "XSP"),
    AP_SEOUL_1("ap-seoul-1", "韩国中部（首尔）", "Seoul, South Korea", "ICN"),
    AP_CHUNCHEON_1("ap-chuncheon-1", "韩国北部（春川）", "Chuncheon, South Korea", "YNY"),
    AP_KULAI_2("ap-kulai-2", "马来西亚西部（古来）", "Kulai, Malaya", "JBP"),

    // Americas
    SA_SAOPAULO_1("sa-saopaulo-1", "巴西东部（圣保罗）", "Sao Paulo, Brazil", "GRU"),
    SA_VINHEDO_1("sa-vinhedo-1", "巴西东南部（维涅杜）", "Vinhedo, Brazil", "VCP"),
    CA_MONTREAL_1("ca-montreal-1", "加拿大东南部（蒙特利尔）", "Montreal, Canada", "YUL"),
    CA_TORONTO_1("ca-toronto-1", "加拿大东南部（多伦多）", "Toronto, Canada", "YYZ"),
    SA_SANTIAGO_1("sa-santiago-1", "智利中部（圣地亚哥）", "Santiago, Chile", "SCL"),
    SA_VALPARAISO_1("sa-valparaiso-1", "智利西部（瓦尔帕莱索）", "Valparaiso, Chile", "VAP"),
    SA_BOGOTA_1("sa-bogota-1", "哥伦比亚中部（波哥大）", "Bogota, Colombia", "BOG"),
    MX_QUERETARO_1("mx-queretaro-1", "墨西哥中部（克雷塔罗）", "Queretaro, Mexico", "QRO"),
    MX_MONTERREY_1("mx-monterrey-1", "墨西哥东北部（蒙特雷）", "Monterrey, Mexico", "MTY"),
    US_ASHBURN_1("us-ashburn-1", "美国东部（阿什本）", "Ashburn, VA", "IAD"),
    US_CHICAGO_1("us-chicago-1", "美国中西部（芝加哥）", "Chicago, IL", "ORD"),
    US_PHOENIX_1("us-phoenix-1", "美国西部（凤凰城）", "Phoenix, AZ", "PHX"),
    US_SANJOSE_1("us-sanjose-1", "美国西部（圣何塞）", "San Jose, CA", "SJC"),

    // Europe
    EU_PARIS_1("eu-paris-1", "法国中部（巴黎）", "Paris, France", "CDG"),
    EU_MARSEILLE_1("eu-marseille-1", "法国南部（马赛）", "Marseille, France", "MRS"),
    EU_FRANKFURT_1("eu-frankfurt-1", "德国中部（法兰克福）", "Frankfurt, Germany", "FRA"),
    EU_MILAN_1("eu-milan-1", "意大利西北部（米兰）", "Milan, Italy", "LIN"),
    EU_TURIN_1("eu-turin-1", "意大利北部（都灵）", "Turin, Italy", "NRQ"),
    EU_AMSTERDAM_1("eu-amsterdam-1", "荷兰西北部（阿姆斯特丹）", "Amsterdam, Netherlands", "AMS"),
    EU_JOVANOVAC_1("eu-jovanovac-1", "塞尔维亚中部（约瓦诺瓦茨）", "Jovanovac, Serbia", "BEG"),
    EU_MADRID_1("eu-madrid-1", "西班牙中部（马德里）", "Madrid, Spain", "MAD"),
    EU_MADRID_3("eu-madrid-3", "西班牙中部（马德里3）", "Madrid, Spain", "ORF"),
    EU_STOCKHOLM_1("eu-stockholm-1", "瑞典中部（斯德哥尔摩）", "Stockholm, Sweden", "ARN"),
    EU_ZURICH_1("eu-zurich-1", "瑞士北部（苏黎世）", "Zurich, Switzerland", "ZRH"),
    UK_LONDON_1("uk-london-1", "英国南部（伦敦）", "London, United Kingdom", "LHR"),
    UK_CARDIFF_1("uk-cardiff-1", "英国西部（纽波特）", "Newport, United Kingdom", "CWL"),

    // Middle East & Africa
    IL_JERUSALEM_1("il-jerusalem-1", "以色列中部（耶路撒冷）", "Jerusalem, Israel", "MTZ"),
    ME_RIYADH_1("me-riyadh-1", "沙特阿拉伯中部（利雅得）", "Riyadh, Saudi Arabia", "RUH"),
    ME_JEDDAH_1("me-jeddah-1", "沙特阿拉伯西部（吉达）", "Jeddah, Saudi Arabia", "JED"),
    AF_JOHANNESBURG_1("af-johannesburg-1", "南非中部（约翰内斯堡）", "Johannesburg, South Africa", "JNB"),
    ME_ABUDHABI_1("me-abudhabi-1", "阿联酋中部（阿布扎比）", "Abu Dhabi, UAE", "AUH"),
    ME_DUBAI_1("me-dubai-1", "阿联酋东部（迪拜）", "Dubai, UAE", "DXB");

    private final String id;
    private final String name;
    private final String location;
    private final String key;

    OciRegionsEnum(String id, String name, String location, String key) {
        this.id = id;
        this.name = name;
        this.location = location;
        this.key = key;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getLocation() {
        return location;
    }

    public String getKey() {
        return key;
    }

    /**
     * 根据 region id 获取 key
     */
    public static Optional<String> getKeyById(String id) {
        return Arrays.stream(values())
                .filter(r -> r.id.equalsIgnoreCase(id))
                .map(OciRegionsEnum::getKey)
                .findFirst();
    }

    /**
     * 根据 region id 获取 name
     */
    public static Optional<String> getNameById(String id) {
        return Arrays.stream(values())
                .filter(r -> r.id.equalsIgnoreCase(id))
                .map(OciRegionsEnum::getName)
                .findFirst();
    }

    public static List<String> getBroadcastAmdRegions() {
        return Arrays.asList(
                AP_SINGAPORE_1,
                EU_MADRID_1,
                EU_MADRID_3,
                AF_JOHANNESBURG_1,
                MX_QUERETARO_1,
                EU_PARIS_1
        ).parallelStream().map(OciRegionsEnum::getId).collect(Collectors.toList());
    }
}
