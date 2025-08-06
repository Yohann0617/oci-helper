package com.yohann.ocihelper.bean.response.sys;

import lombok.Data;

import java.util.List;

/**
 * <p>
 * GetGlanceRsp
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/12/25 14:11
 */
@Data
public class GetGlanceRsp {

    private String users;
    private String tasks;
    private String regions;
    private String days;
    private String currentVersion;
    private List<MapData> cities;

    @Data
    public static class MapData {
        private Double lat;
        private Double lng;
        private String country;
        private String area;
        private String city;
        private String org;
        private String asn;
        private Integer count;
    }
}
