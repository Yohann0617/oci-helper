package com.yohann.ocihelper.enums;

import com.oracle.bmc.Realm;
import lombok.Getter;

/**
 * @projectName: oci-helper
 * @package: com.yohann.ocihelper.enums
 * @className: OciUnSupportRegionEnum
 * @author: Yohann
 * @date: 2024/11/30 17:29
 */
@Getter
public enum OciUnSupportRegionEnum {

    AP_KULAI_2("ap-kulai-2",Realm.OC1,"jbp"),
    ;


    OciUnSupportRegionEnum(String regionId, Realm realm, String regionCode) {
        this.regionId = regionId;
        this.realm = realm;
        this.regionCode = regionCode;
    }

    private String regionId;
    private Realm realm;
    private String regionCode;
}
