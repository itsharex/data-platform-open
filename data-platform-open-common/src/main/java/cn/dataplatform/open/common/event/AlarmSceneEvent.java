package cn.dataplatform.open.common.event;

import cn.dataplatform.open.common.alarm.scene.Scene;
import cn.dataplatform.open.common.body.AlarmSceneMessageBody;
import org.springframework.context.ApplicationEvent;

import java.io.Serial;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/22
 * @since 1.0.0
 */
public class AlarmSceneEvent extends ApplicationEvent {

    @Serial
    private static final long serialVersionUID = 1628296277627810450L;

    public AlarmSceneEvent(AlarmSceneMessageBody source) {
        super(source);
    }

    /**
     * 创建一个新的告警场景事件
     *
     * @param workspaceCode 工作空间编码
     * @param scene         场景
     */
    public AlarmSceneEvent(String workspaceCode, Scene scene) {
        super(new AlarmSceneMessageBody(scene));
        AlarmSceneMessageBody alarmSceneMessageBody = this.getSource();
        alarmSceneMessageBody.setWorkspaceCode(workspaceCode);
    }

    @Override
    public AlarmSceneMessageBody getSource() {
        return (AlarmSceneMessageBody) super.getSource();
    }

}
