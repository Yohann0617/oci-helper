package com.yohann.ocihelper.service;

import com.yohann.ocihelper.bean.dto.SysUserDTO;
import com.yohann.ocihelper.bean.params.sys.*;
import com.yohann.ocihelper.bean.response.sys.GetGlanceRsp;
import com.yohann.ocihelper.bean.response.sys.GetSysCfgRsp;
import com.yohann.ocihelper.bean.response.sys.LoginRsp;

public interface ISysService {

    void sendMessage(String message);

    LoginRsp login(LoginParams params);

    void updateSysCfg(UpdateSysCfgParams params);

    GetSysCfgRsp getSysCfg();

    boolean getEnableMfa();

    void backup(BackupParams params);

    /**
     * Create backup file and return the file path (for Telegram Bot use)
     * 
     * @param params backup parameters
     * @return backup file path
     */
    String createBackupFile(BackupParams params);

    void recover(RecoverParams params);

    /**
     * Recover from backup file (for Telegram Bot use)
     * 
     * @param backupFilePath backup file path
     * @param password decryption password (can be null for unencrypted backups)
     */
    void recoverFromFile(String backupFilePath, String password);

    GetGlanceRsp glance();

    SysUserDTO getOciUser(String ociCfgId);

    SysUserDTO getOciUser(String ociCfgId, String region, String compartmentId);

        void checkMfaCode(String mfaCode);

    void updateVersion();

    /**
     * Google one-click login
     * 
     * @param params Google login parameters with credential
     * @return login response with token and version info
     */
    LoginRsp googleLogin(GoogleLoginParams params);

    /**
     * Get Google Client ID for OAuth configuration
     * 
     * @return Google Client ID
     */
    String getGoogleClientId();
}
