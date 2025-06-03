package com.yohann.ocihelper.bean.dto;

import lombok.Data;

@Data
public class ConsoleConnectionResultDTO {
    private final String connectionId;
    private final String connectionString;
    private final String vncConnectionString;
    private final SshKeyPairDTO keyPair;
    private final boolean keyGenerated;
}
