package cn.dataplatform.open.common.body;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/3/14
 * @since 1.0.0
 */
@Data
public class DataFlowComponentMessageBody implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;


    private String workspaceCode;

    private String flowCode;

    private String componentCode;
    /**
     * 调度的实例
     */
    private List<String> instanceIds;

    private Type type;


    /**
     * 设置单个实例ID
     *
     * @param instanceId 实例ID
     */
    public void setInstanceId(String instanceId) {
        if (this.instanceIds == null) {
            this.instanceIds = new ArrayList<>(1);
        }
        this.instanceIds.add(instanceId);
    }

    public enum Type {
        /**
         * 加载,以及移除
         */
        START, STOP, RESTART
    }


}
