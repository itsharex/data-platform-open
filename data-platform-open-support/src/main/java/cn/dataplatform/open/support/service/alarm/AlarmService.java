package cn.dataplatform.open.support.service.alarm;

import cn.dataplatform.open.common.body.AlarmMessageBody;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/22
 * @since 1.0.0
 */
public interface AlarmService {

    /**
     * 告警
     *
     * @param alarmMessageBody 告警消息
     */
    void alarm(AlarmMessageBody alarmMessageBody);

    /**
     * 告警
     *
     * @param alarmMessageBody 告警消息
     * @param sceneCode        场景编码
     */
    void alarm(AlarmMessageBody alarmMessageBody, String sceneCode);
}
