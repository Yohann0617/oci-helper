package com.yohann.ocihelper.enums;

import com.oracle.bmc.core.model.Shape;
import lombok.Getter;

import static com.oracle.bmc.core.model.Shape.BillingType.AlwaysFree;
import static com.oracle.bmc.core.model.Shape.BillingType.LimitedFree;

/**
 * <p>
 * ArchitectureEnum
 * </p >
 *
 * @author yuhui.fan
 * @since 2024/11/7 18:36
 */
@Getter
public enum ArchitectureEnum {

    /**
     * 系统架构
     */
    AMD("AMD", "VM.Standard.E2.1.Micro", AlwaysFree),
    ARM("ARM", "VM.Standard.A1.Flex", LimitedFree),

    ;

    ArchitectureEnum(String type, String shapeDetail, Shape.BillingType billingType) {
        this.type = type;
        this.shapeDetail = shapeDetail;
        this.billingType = billingType;
    }

    private String type;
    private String shapeDetail;
    private Shape.BillingType billingType;

    public static ArchitectureEnum getType(String type) {
        ArchitectureEnum[] values = ArchitectureEnum.values();
        for (ArchitectureEnum value : values) {
            if (value.getType().equals(type)) {
                return value;
            }
        }
        return ARM;
    }
}
