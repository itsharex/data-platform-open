package cn.dataplatform.open.support.task;

import cn.dataplatform.open.common.annotation.ScheduledGlobalLock;
import cn.dataplatform.open.support.store.entity.AlarmLog;
import cn.dataplatform.open.support.store.mapper.AlarmLogMapper;
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
public class AlarmLogCleanTask {


    /**
     * 告警日志保留时长,单位:天
     */
    @Value("${dp.alarm.log.retain-day:30}")
    private Integer retainDay;
    @Resource
    private AlarmLogMapper alarmLogMapper;

    /**
     * 定期删除告警日志
     * <p>
     * 每天的 0 点 0 分 0 秒触发定时任务
     */
    @ScheduledGlobalLock
    @Scheduled(cron = "0 0 0 * * ?")
    public void execute() {
        log.info("开始定期清理告警日志数据");
        // 删除告警日志
        DateTime offset = DateUtil.offset(DateUtil.date(), DateField.DAY_OF_YEAR, -this.retainDay);
        int deleted = this.alarmLogMapper.delete(Wrappers.<AlarmLog>lambdaUpdate()
                .lt(AlarmLog::getCreateTime, offset));
        log.info("执行完毕,清理数量:" + deleted);
    }

}
