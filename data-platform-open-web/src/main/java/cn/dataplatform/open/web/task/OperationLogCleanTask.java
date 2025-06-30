package cn.dataplatform.open.web.task;

import cn.dataplatform.open.common.annotation.ScheduledGlobalLock;
import cn.dataplatform.open.web.store.entity.OperationLog;
import cn.dataplatform.open.web.store.mapper.OperationLogMapper;
import cn.hutool.core.date.DateField;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;


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
public class OperationLogCleanTask {


    /**
     * 操作日志保留时长,单位:天
     */
    @Value("${dp.operation.log.retain-day:30}")
    private Integer retainDay;
    @Resource
    private OperationLogMapper operationLogMapper;

    /**
     * 定期清理操作日志数据
     * <p>
     * 每天的 0 点 0 分 0 秒触发定时任务
     */
    @ScheduledGlobalLock
    @Scheduled(cron = "0 0 0 * * ?")
    public void execute() {
        log.info("开始定期清理操作日志数据");
        DateTime offset = DateUtil.offset(DateUtil.date(), DateField.DAY_OF_YEAR, -this.retainDay);
        int deleted = this.operationLogMapper.delete(Wrappers.<OperationLog>lambdaUpdate()
                .lt(OperationLog::getCreateTime, offset));
        log.info("执行完毕,清理数量:" + deleted);
    }

}
