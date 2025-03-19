package com.yohann.ocihelper.bean.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * @ClassName CfDnsRecordDTO
 * @Description:
 * @Author: Yohann_Fan
 * @CreateTime: 2025-03-19 16:32
 **/
@Data
public class CfDnsRecordDTO {

    private String id;
    private String name;
    private String type;
    private String content;
    private Boolean proxiable;
    private Boolean proxied;
    private Integer ttl;
//    private Map<String, Object> settings;
//    private Map<String, Object> meta;
    private String comment;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private LocalDateTime createdOn;
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    private LocalDateTime modifiedOn;
    private List<String> tags;
}
