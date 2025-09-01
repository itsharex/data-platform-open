/*
 * ============================================================================
 *
 *                    数海文舟 (DATA PLATFORM) 版权所有 © 2025
 *
 *       本软件受著作权法和国际版权条约保护。
 *       未经明确书面授权，任何单位或个人不得对本软件进行复制、修改、分发、
 *       逆向工程、商业用途等任何形式的非法使用。违者将面临人民币100万元的
 *       法定罚款及可能的法律追责。
 *
 *       举报侵权行为可获得实际罚款金额40%的现金奖励。
 *       法务邮箱：761945125@qq.com
 *
 *       COPYRIGHT (C) 2025 dingqianwen COMPANY. ALL RIGHTS RESERVED.
 *
 * ============================================================================
 */
package cn.dataplatform.open.flow.core;

import cn.dataplatform.open.flow.core.component.FlowComponent;
import cn.dataplatform.open.flow.core.record.*;
import cn.dataplatform.open.flow.core.record.Record;
import lombok.Data;

/**
 * 组件数据传输对象，每次往后传递数据，使用新的Transmit对象，防止数据污染
 *
 * @author dingqianwen
 * @date 2025/1/6
 * @since 1.0.0
 */
@Data
public class Transmit {

    /**
     * 当前传输的组件
     */
    private FlowComponent flowComponent;

    /**
     * 传输的数据
     * <p>
     *
     * @see BatchPlainRecord 批量普通数据
     * @see BatchStreamRecord 批量流数据，当debezium批量模式时，或者接收外部批量流消息，例如Canal的
     * @see PlainRecord 普通单条数据
     * @see StreamRecord 流式单条数据
     * @see EmptyRecord 空数据
     */
    private Record record;

    /**
     * 获取记录数据
     *
     * @return 记录数据
     * @see EmptyRecord
     */
    public Record getRecord() {
        if (this.record != null) {
            return this.record;
        }
        return EmptyRecord.INSTANCE;
    }

}
