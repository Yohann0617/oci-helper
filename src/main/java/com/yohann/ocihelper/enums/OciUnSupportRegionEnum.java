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

    EU_MADRID_3("eu-madrid-3",Realm.OC1,"orf"),
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
