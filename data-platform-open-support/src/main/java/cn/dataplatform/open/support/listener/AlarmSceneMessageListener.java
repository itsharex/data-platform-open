package cn.dataplatform.open.support.listener;

import cn.dataplatform.open.common.body.AlarmSceneMessageBody;
import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.support.config.RabbitConfig;
import cn.dataplatform.open.support.service.alarm.AlarmSceneService;
import com.alibaba.fastjson2.JSON;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.Message;
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
public class AlarmSceneMessageListener {

    @Resource
    private AlarmSceneService alarmSceneService;

    /**
     * 监听告警场景消息
     *
     * @param messaging 告警消息
     */
    @RabbitListener(queues = {RabbitConfig.ALARM_SCENE_QUEUE})
    public void onMessage(Message<AlarmSceneMessageBody> messaging) {
        String requestId = messaging.getHeaders().get(Constant.REQUEST_ID, String.class);
        MDC.put(Constant.REQUEST_ID, requestId);
        try {
            AlarmSceneMessageBody alarmSceneMessageBody = messaging.getPayload();
            log.info("场景消息:{}", JSON.toJSONString(alarmSceneMessageBody));
            this.alarmSceneService.alarm(alarmSceneMessageBody);
        } finally {
            // 不需要对接异常场景,防止死循环消息
            MDC.clear();
        }
    }

}
