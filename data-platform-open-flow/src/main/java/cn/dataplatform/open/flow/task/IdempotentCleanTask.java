package cn.dataplatform.open.flow.task;

import cn.dataplatform.open.common.annotation.ScheduledGlobalLock;
import cn.dataplatform.open.flow.store.entity.Idempotent;
import cn.dataplatform.open.flow.store.mapper.IdempotentMapper;
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
 * @date 2025/2/22
 * @since 1.0.0
 */
@Slf4j
@Component
public class IdempotentCleanTask {


    @Resource
    private IdempotentMapper idempotentMapper;

    /**
     * 定期删除过期的幂等数据
     * <p>
     * 30秒执行一次
     */
    @ScheduledGlobalLock
    @Scheduled(cron = "*/30 * * * * ?")
    public void execute() {
        log.info("开始执行定期清理过期的幂等数据");
        // 删除告警日志
        LocalDateTime now = LocalDateTime.now();
        int deleted = this.idempotentMapper.delete(Wrappers.<Idempotent>lambdaUpdate()
                // 过期时间为expireTime字段，小于当前时间的数据需要清理
                .lt(Idempotent::getExpireTime, now));
        log.info("执行完毕,清理数量:" + deleted);
    }

}
