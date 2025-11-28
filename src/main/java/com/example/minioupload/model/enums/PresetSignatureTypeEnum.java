package com.example.minioupload.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.stream.Stream;

/**
 * 预设签章类型
 */
@Getter
@AllArgsConstructor
public enum PresetSignatureTypeEnum implements DictionaryEnum {

    SUBJECT_SIGNATURE("subjectSignature", "受试者签字"),
    INVESTIGATOR_SIGNATURE("investigatorSignature", "研究者签字"),
    SUBJECT_NAME("subjectName", "受试者姓名"),
    SUBJECT_GENDER("subjectGender", "受试者性别"),
    SUBJECT_ID_CARD("subjectIdCard", "受试者身份证"),
    SUBJECT_PHONE("subjectPhone", "受试者手机号码"),
    SUBJECT_ADDRESS("subjectAddress", "受试者居住地址"),
    SUBJECT_EMERGENCY_PHONE("emergencyPhone", "受试者紧急联系电话"),
    TRIAL_NAME("trialName", "试验名称"),
    PROTOCOL_CODE("schemeNumber", "方案编号"),
    INVESTIGATOR_NAME("piName", "研究者姓名"),
    INVESTIGATOR_CONTACT("piSignPhone", "研究者联系方式"),
    PARTICIPATING_INSTITUTION("siteName", "参与机构"),
    SPONSOR("sponsorName", "申办方"),
    VERSION("version", "知情同意书版本"),

    // 时间相关字段
    CURRENT_YEAR("currentYear", "当前年份"),
    CURRENT_MONTH("currentMonth", "当前月份"),
    CURRENT_DAY("currentDay", "当前日期"),
    CURRENT_HOUR("currentHour", "当前小时"),
    CURRENT_MINUTE("currentMinute", "当前分钟"),
    CURRENT_SECOND("currentSecond", "当前秒数"),
    CURRENT_DATE("currentDate", "当前日期(yyyy-MM-dd)"),
    CURRENT_DATETIME("currentDatetime", "当前时间(yyyy-MM-dd HH:mm:ss)"),
    CURRENT_TIME("currentTime", "当前时间(HH:mm:ss)"),
    CURRENT_HOUR_MINUTE("currentHourMinute", "当前时分(HH:mm)"),

    // 研究者签字时间相关字段
    PI_SIGN_YEAR("piSignYear", "研究者签字年份"),
    PI_SIGN_MONTH("piSignMonth", "研究者签字月份"),
    PI_SIGN_DAY("piSignDay", "研究者签字日期"),
    PI_SIGN_HOUR("piSignHour", "研究者签字小时"),
    PI_SIGN_MINUTE("piSignMinute", "研究者签字分钟"),
    PI_SIGN_SECOND("piSignSecond", "研究者签字秒数"),
    PI_SIGN_DATE("piSignDate", "研究者签字日期(yyyy-MM-dd)"),
    PI_SIGN_DATETIME("piSignDatetime", "研究者签字时间(yyyy-MM-dd HH:mm:ss)"),
    PI_SIGN_TIME("piSignTime", "研究者签字时间(HH:mm:ss)"),
    PI_SIGN_HOUR_MINUTE("piSignHourMinute", "研究者签字时分(HH:mm)"),
    ;
    private final String value;
    private final String name;

    public String getValue() {
        return value;
    }

    public String getName() {
        return name;
    }

    public static PresetSignatureTypeEnum typeOf(String value) {
        return Stream.of(PresetSignatureTypeEnum.values())
                .filter(p -> p.value.equals(value))
                .findAny()
                .orElse(null);
    }
}
