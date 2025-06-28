package cn.dataplatform.open.flow.task;

import cn.dataplatform.open.common.annotation.ScheduledGlobalLock;
import cn.dataplatform.open.flow.store.entity.DebeziumSavePoint;
import cn.dataplatform.open.flow.store.mapper.DebeziumSavePointMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/6/8
 * @since 1.0.0
 */
@Slf4j
@Component
public class DebeziumSavePointCleanTask {

    @Resource
    private DebeziumSavePointMapper debeziumSavePointMapper;

    /**
     * 定期删除过期的数据，expireTime字段小于当前时间的数据需要清理
     * <p>
     * 每小时执行一次
     */
    @ScheduledGlobalLock
    @Scheduled(cron = "0 0 */1 * * ?")
    public void execute() {
        log.info("开始执行定期清理过期的Debezium保存点数据");
        // 删除过期的保存点数据
        LocalDateTime now = LocalDateTime.now();
        int deleted = this.debeziumSavePointMapper.delete(
                // 过期时间为expireTime字段，小于当前时间的数据需要清理
                Wrappers.<DebeziumSavePoint>lambdaUpdate()
                        .lt(DebeziumSavePoint::getExpireTime, now)
        );
        log.info("执行完毕,清理数量: {}", deleted);
    }

}
