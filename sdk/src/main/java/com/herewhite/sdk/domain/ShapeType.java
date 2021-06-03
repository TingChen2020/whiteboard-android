package com.herewhite.sdk.domain;

import com.google.gson.annotations.SerializedName;
/**
 * 图形工具。
 */
public enum ShapeType {
    /**
     * （默认）三角形。
     */
    @SerializedName("triangle")
    Triangle,
    /**
     * 菱形。
     */
    @SerializedName("rhombus")
    Rhombus,
    /**
     * 五角星。
     */
    @SerializedName("pentagram")
    Pentagram,
    /**
     * 对话气泡。
     */
    @SerializedName("speechBalloon")
    SpeechBalloon,
}
