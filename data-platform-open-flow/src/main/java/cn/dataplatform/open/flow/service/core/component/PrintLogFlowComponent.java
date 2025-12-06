package cn.dataplatform.open.flow.service.core.component;

import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.flow.service.core.Context;
import cn.dataplatform.open.flow.service.core.Flow;
import cn.dataplatform.open.flow.service.core.Transmit;
import cn.dataplatform.open.flow.service.core.record.BatchRecord;
import cn.dataplatform.open.flow.service.core.record.Record;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.filter.ValueFilter;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/1/14
 * @since 1.0.0
 */
@Getter
@Setter
@Slf4j
public class PrintLogFlowComponent extends FlowComponent {

    /**
     * 记录最大打印行数
     * -1不限制
     */
    private Integer recordMaxPrintLine = 100;

    /**
     * 记录中字段长度限制，超出截断
     * -1不限制
     */
    private Integer recordFieldMaxLength = 1000;

    private volatile ValueFilter valueFilter;

    private ServerManager serverManager;


    public PrintLogFlowComponent(Flow flow, String code) {
        super(flow, code);
        this.serverManager = this.getApplicationContext().getBean(ServerManager.class);
    }

    /**
     * 打印组件
     *
     * @param transmit 传输对象
     * @param context  上下文
     */
    @Override
    public void run(Transmit transmit, Context context) {
        // 打印出当前数据源,上一个组件的名称,当前连接对象地址,记录数以及记录
        FlowComponent flowComponent = transmit.getFlowComponent();
        Record record = transmit.getRecord();
        log.info("""   
                        当前实例:{},
                        启动时间:{},
                        工作空间:{},
                        流程:{}({}),
                        当前组件:{}({}),
                        父组件:{}({}),
                        记录数:{},
                        记录:{},
                        唯一标识:{}""",
                this.serverManager.instanceId(),
                // format
                LocalDateTimeUtil.format(context.getStartTime(), Constant.DATE_TIME_FORMAT),
                this.getWorkspaceCode(),
                this.getFlow().getName(), this.getFlowCode(),
                this.getName(), this.getCode(),
                flowComponent.getName(), flowComponent.getCode(),
                Optional.ofNullable(record).map(Record::size).orElse(0),
                this.recordProcess(record),
                context.getId());
        // 允许继续执行
        this.runNext(() -> {
            log.info("开始传递数据到下一个节点");
            Transmit nextTransmit = new Transmit();
            nextTransmit.setFlowComponent(this);
            nextTransmit.setRecord(record);
            return nextTransmit;
        }, context);
    }

    /**
     * 处理记录数量限制
     *
     * @param record 记录
     * @return 处理后的记录
     */
    private String recordProcess(Record record) {
        if (record == null) {
            return null;
        }
        if (record.isEmpty()) {
            return "记录为空";
        }
        if (this.valueFilter == null) {
            synchronized (this) {
                if (this.valueFilter == null) {
                    this.valueFilter = (object, name, value) -> {
                        if (recordFieldMaxLength == null || recordFieldMaxLength == -1) {
                            // 不限制
                            return value;
                        }
                        // 如果=0，则只查询字段，不打印值
                        if (recordFieldMaxLength == 0) {
                            return null;
                        }
                        if (value instanceof String strValue) {
                            if (strValue.length() > recordFieldMaxLength) {
                                return strValue.substring(0, recordFieldMaxLength) + "..." +
                                        String.format("...[%d个字符被截断]", strValue.length() - recordFieldMaxLength);
                            }
                        }
                        // 后续是否过滤掉byte等
                        // ...
                        return value;
                    };
                }
            }
        }
        if (recordMaxPrintLine == null || recordMaxPrintLine == -1) {
            // 不限制，全量
            return JSON.toJSONString(record, this.valueFilter);
        }
        if (record instanceof BatchRecord batchRecord) {
            // 只有批量记录需要优化
            List<? extends Record> records = batchRecord.getRecords();
            if (batchRecord.size() <= this.recordMaxPrintLine) {
                // 不需要截断
                return JSON.toJSONString(record, this.valueFilter);
            }
            // 数据截断，保留指定行数
            List<? extends List<? extends Record>> lists = CollUtil.split(records, this.recordMaxPrintLine);
            return JSON.toJSONString(lists, this.valueFilter) +
                    String.format("...[%d条记录被截断]", records.size() - this.recordMaxPrintLine);
        } else {
            return JSON.toJSONString(record, this.valueFilter);
        }
    }


}
