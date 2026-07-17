package com.dazo66.qqbot.handler;

import com.alibaba.fastjson.JSONObject;
import com.dazo66.qqbot.BotMessageHandler;
import com.dazo66.qqbot.BotMessageType;
import com.dazo66.qqbot.entity.MediaFile;
import com.dazo66.qqbot.entity.MediaMessage;
import com.dazo66.qqbot.entity.MediaRecord;
import com.dazo66.qqbot.entity.MediaSegment;
import com.dazo66.qqbot.mapper.MediaFileMapper;
import com.dazo66.qqbot.mapper.MediaRecordMapper;
import com.dazo66.qqbot.util.CQCodeUtils;
import com.dazo66.qqbot.util.MediaDownloadUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 媒体保存 Handler —— 从私聊/群聊消息中抽取图片和视频信息，下载到本地并记录入库。
 * <p>
 * convert: 解析消息体（支持 CQ 码字符串和数组两种格式） → 提取媒体 URL → 构建 MediaMessage
 * handle:  遍历 MediaMessage.mediaSegments → 下载文件 → 写 MediaRecord + MediaFile 入库
 *
 * @author Dazo66
 */
@Slf4j
@Component
public class MediaSaveHandler implements BotMessageHandler<MediaMessage> {

    @Autowired
    private MediaRecordMapper mediaRecordMapper;

    @Autowired
    private MediaFileMapper mediaFileMapper;

    @Value("${qqbot.media.save.path:D:/DazoServer/qqbot_media}")
    private String saveBasePath;

    private static final SimpleDateFormat DIR_DATE_FMT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat FILE_TIME_FMT = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");

    @Override
    public boolean accept(BotMessageType type) {
        return "message/group".equals(type.toString())
                || "message/private".equals(type.toString());
    }

    /**
     * 仅解析消息体，提取发送人信息 + 媒体 URL 列表，不进行网络下载。
     * 同时兼容 CQ 码字符串和 JSONArray 两种消息格式。
     */
    @Override
    public MediaMessage convert(JSONObject json) {
        List<MediaSegment> segments = extractMediaSegments(json);
        if (segments.isEmpty()) {
            return null;
        }

        String messageType = json.getString("message_type");
        boolean isGroup = "group".equals(messageType);

        // 发送人名称：优先群名片 > 昵称
        JSONObject senderObj = json.getJSONObject("sender");
        String senderName = "unknown";
        if (senderObj != null) {
            senderName = senderObj.getString("card");
            if (senderName == null || senderName.isEmpty()) {
                senderName = senderObj.getString("nickname");
            }
        }
        String senderId = json.getString("user_id");
        if (senderName == null || senderName.isEmpty()) {
            senderName = senderId;
        }

        Long timeSec = json.getLong("time");
        Date messageTime = (timeSec != null) ? new Date(timeSec * 1000L) : new Date();

        return new MediaMessage()
                .setGroupChat(isGroup)
                .setMessageId(json.getString("message_id"))
                .setGroupId(isGroup ? json.getString("group_id") : null)
                .setSenderId(senderId)
                .setSenderName(senderName)
                .setMessageTime(messageTime)
                .setMediaSegments(segments);
    }

    /**
     * 下载媒体文件并入库。
     */
    @Override
    public void handle(MediaMessage msg) {
        String dateDir = DIR_DATE_FMT.format(msg.getMessageTime());
        String timeName = FILE_TIME_FMT.format(msg.getMessageTime());
        String safeSender = safeFileName(msg.getSenderName());
        String saveDir = saveBasePath + "/" + dateDir;

        // 先创建 MediaRecord 并入库获取 ID
        MediaRecord record = new MediaRecord()
                .setIsGroupChat(msg.isGroupChat())
                .setMessageId(msg.getMessageId())
                .setGroupId(msg.getGroupId())
                .setSenderId(msg.getSenderId())
                .setSenderName(msg.getSenderName())
                .setMessageTime(msg.getMessageTime())
                .setStatus(MediaRecord.STATUS_NORMAL)
                .setCreateTime(new Date());

        mediaRecordMapper.insert(record);

        // 下载每个媒体文件
        int successCount = 0;
        int index = 0;
        for (MediaSegment segment : msg.getMediaSegments()) {
            index++;
            String ext = MediaDownloadUtils.getExtension(
                    segment.getFileName() != null ? segment.getFileName() : ".jpg");
            String fileName = timeName + "_" + safeSender + "_" + index + ext;

            String downloadedPath = MediaDownloadUtils.download(segment.getUrl(), saveDir, fileName);
            if (downloadedPath != null) {
                MediaFile mediaFile = new MediaFile()
                        .setRecordId(record.getId())
                        .setFilePath(downloadedPath)
                        .setFileName(fileName);
                mediaFileMapper.insert(mediaFile);
                successCount++;
            }
        }

        if (successCount == 0) {
            log.warn("消息 {} 的所有媒体文件下载均失败", msg.getMessageId());
        } else {
            log.info("媒体消息入库成功: messageId={}, 文件数={}/{}, sender={}",
                    msg.getMessageId(), successCount, msg.getMediaSegments().size(), msg.getSenderName());
        }
    }

    /**
     * 从消息体中提取 type 为 image/video 的 segment。
     * 同时支持:
     * <ul>
     *   <li>CQ 码字符串格式: "[CQ:image,file=xxx,url=...]"</li>
     *   <li>JSONArray segment 格式: [{"type":"image","data":{...}}]</li>
     * </ul>
     */
    private List<MediaSegment> extractMediaSegments(JSONObject json) {
        List<MediaSegment> result = new ArrayList<>();
        Object messageObj = json.get("raw_message");

        if (messageObj instanceof String) {
            // CQ 码字符串格式
            String cqMessage = (String) messageObj;
            List<Map<String, String>> decoded = CQCodeUtils.decode(cqMessage);
            for (Map<String, String> seg : decoded) {
                String cqType = seg.get("CQ");
                if ("image".equals(cqType) || "video".equals(cqType)) {
                    result.add(new MediaSegment()
                            .setType(cqType)
                            .setUrl(seg.get("url"))
                            .setFileName(seg.get("file")));
                }
            }

        }

        return result;
    }

    /**
     * 清理文件名中的非法字符并截断。
     */
    private String safeFileName(String name) {
        if (name == null || name.isEmpty()) {
            return "unknown";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (cleaned.length() > 30) {
            cleaned = cleaned.substring(0, 30);
        }
        return cleaned;
    }
}
