package cn.dataplatform.open.support.listener;


import cn.dataplatform.open.common.body.AlarmMessageBody;
import cn.dataplatform.open.common.body.AlarmSceneMessageBody;
import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.event.AlarmEvent;
import cn.dataplatform.open.common.event.AlarmSceneEvent;
import cn.dataplatform.open.support.config.RabbitConfig;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/23
 * @since 1.0.0
 */
@Slf4j
@Component
public class EventPublisherListener {

    @Lazy
    @Resource
    private RabbitTemplate rabbitTemplate;


    /**
     * 异常场景事件监听
     *
     * @param alarmSceneEvent 异常场景事件
     */
    @EventListener(classes = AlarmSceneEvent.class)
    public void sceneEvent(AlarmSceneEvent alarmSceneEvent) {
        log.info("发送告警场景消息:" + alarmSceneEvent.getSource());
        AlarmSceneMessageBody alarmSceneMessageBody = alarmSceneEvent.getSource();
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setHeader(Constant.REQUEST_ID, MDC.get(Constant.REQUEST_ID));
        Message message = this.rabbitTemplate.getMessageConverter().toMessage(alarmSceneMessageBody, messageProperties);
        this.rabbitTemplate.convertAndSend(RabbitConfig.ALARM_SCENE_QUEUE, message);
    }

    /**
     * 异常事件监听
     *
     * @param alarmEvent 异常事件
     */
    @EventListener(classes = AlarmEvent.class)
    public void alarmEvent(AlarmEvent alarmEvent) {
        log.info("发送异常消息:" + alarmEvent.getSource());
        AlarmMessageBody alarmMessageBody = alarmEvent.getSource();
        MessageProperties messageProperties = new MessageProperties();
        messageProperties.setHeader(Constant.REQUEST_ID, MDC.get(Constant.REQUEST_ID));
        Message message = this.rabbitTemplate.getMessageConverter().toMessage(alarmMessageBody, messageProperties);
        this.rabbitTemplate.convertAndSend(RabbitConfig.ALARM_QUEUE, message);
    }


}
