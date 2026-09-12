package com.dazo66.qqbot.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dazo66.qqbot.entity.MediaFile;
import com.dazo66.qqbot.entity.MediaRecord;
import com.dazo66.qqbot.mapper.MediaFileMapper;
import com.dazo66.qqbot.mapper.MediaRecordMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 媒体文件定时清理任务。
 * 每 30 分钟扫描一次，删除超过 5 分钟且未被撤回的消息对应的媒体文件。
 *
 * @author Dazo66
 */
@Slf4j
@Component
public class MediaCleanupTask {

    @Autowired
    private MediaRecordMapper mediaRecordMapper;

    @Autowired
    private MediaFileMapper mediaFileMapper;

    /**
     * 消息发送后保留文件的时间（分钟）
     */
    private static final long KEEP_MINUTES = 5;

    @Scheduled(initialDelay = 1, fixedDelay = 30, timeUnit = TimeUnit.MINUTES)
    public void cleanupExpiredMedia() {
        // 过期时间点：当前时间 - 5 分钟
        Date expireBefore = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(KEEP_MINUTES));

        // 查询所有 NORMAL 状态且消息发送时间早于过期时间点的记录
        List<MediaRecord> expiredRecords = mediaRecordMapper.selectList(
                new LambdaQueryWrapper<MediaRecord>()
                        .eq(MediaRecord::getStatus, MediaRecord.STATUS_NORMAL)
                        .lt(MediaRecord::getMessageTime, expireBefore));

        if (expiredRecords.isEmpty()) {
            log.debug("无过期媒体记录需要清理");
            return;
        }

        int deletedFileCount = 0;
        int updatedRecordCount = 0;

        for (MediaRecord record : expiredRecords) {
            try {
                // 查询关联的媒体文件
                List<MediaFile> files = mediaFileMapper.selectList(
                        new LambdaQueryWrapper<MediaFile>()
                                .eq(MediaFile::getRecordId, record.getId()));

                // 删除物理文件
                for (MediaFile file : files) {
                    File diskFile = new File(file.getFilePath());
                    if (diskFile.exists()) {
                        if (diskFile.delete()) {
                            deletedFileCount++;
                            log.debug("删除文件: {}", file.getFilePath());
                        } else {
                            log.warn("无法删除文件: {}", file.getFilePath());
                        }
                    }
                    // 无论文件是否存在，都删除数据库记录
                    mediaFileMapper.deleteById(file.getId());
                }

                // 标记记录为已删除
                record.setStatus(MediaRecord.STATUS_DELETED);
                record.setUpdateTime(new Date());
                mediaRecordMapper.updateById(record);
                updatedRecordCount++;

            } catch (Exception e) {
                log.error("清理媒体记录 {} 时出错", record.getMessageId(), e);
            }
        }

        log.info("媒体清理完成: 处理记录 {} 条, 删除文件 {} 个", updatedRecordCount, deletedFileCount);
    }
}
