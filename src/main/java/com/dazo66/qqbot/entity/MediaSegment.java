package com.dazo66.qqbot.entity;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 单个媒体 segment 信息，包含下载 URL 和文件名提示。
 *
 * @author Dazo66
 */
@Accessors(chain = true)
@Data
public class MediaSegment {

    /**
     * "image" 或 "video"
     */
    private String type;

    /**
     * 下载 URL
     */
    private String url;

    /**
     * NapCat 提供的文件名（用于推断扩展名）
     */
    private String fileName;
}
