package cn.dataplatform.open.flow.core.component.query;

import cn.dataplatform.open.flow.core.Context;
import cn.dataplatform.open.flow.core.Flow;
import cn.dataplatform.open.flow.core.Transmit;
import cn.dataplatform.open.flow.core.record.BatchPlainRecord;
import cn.dataplatform.open.flow.core.record.PlainRecord;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.io.IoUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.HexValue;
import net.sf.jsqlparser.expression.LongValue;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.conditional.AndExpression;
import net.sf.jsqlparser.expression.operators.relational.GreaterThan;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.select.Limit;
import net.sf.jsqlparser.statement.select.OrderByElement;
import net.sf.jsqlparser.statement.select.PlainSelect;
import net.sf.jsqlparser.statement.select.Select;

import java.sql.*;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/1/4
 * @since 1.0.0
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
public class MySQLQueryFlowComponent extends JDBCQueryFlowComponent {

    public MySQLQueryFlowComponent(Flow flow, String code) {
        super(flow, code);
    }

    /**
     * 执行
     *
     * @param transmit 传输对象
     * @param context  上下文
     */
    @Override
    public void run(Transmit transmit, Context context) {
        super.run(transmit, context);
    }

    /**
     * 处理分页查询
     *
     * @param context    上下文
     * @param connection 数据库连接
     * @param limit      每次查询的数量
     */
    @Override
    public void paginationQuery(Context context, Connection connection, Long limit) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(this.getSelectText());
        // 如果querySql没有指定limit,则按照分页查询
        PlainSelect plainSelect = select.getPlainSelect();
        Limit lt = plainSelect.getLimit();
        if (lt != null) {
            BatchPlainRecord query = this.query(connection, plainSelect.toString());
            this.doNext(query, context);
        } else {
            // 没有limit,需要分页查询 一页一页往后滚动
            int current = 1;
            while (true) {
                lt = new Limit();
                // 从1 到limit 2到limit
                lt.setOffset(new LongValue((current - 1) * limit));
                lt.setRowCount(new LongValue(limit));
                plainSelect.setLimit(lt);
                log.info("开始查询第:{}页", current);
                BatchPlainRecord records = this.query(connection, plainSelect.toString());
                if (records.isEmpty()) {
                    break;
                }
                this.doNext(records, context);
                if (records.size() != limit) {
                    break;
                }
                current++;
            }
        }
    }

    /**
     * 处理流式查询
     *
     * @param context 上下文
     * @param limit   每次查询的数量
     * @throws SQLException SQL异常
     */
    @Override
    public void streamQuery(Context context, Connection connection, Long limit) throws SQLException {
        ResultSet resultSet = null;
        Statement statement = null;
        try {
            statement = connection.createStatement(ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            statement.setQueryTimeout(this.getQueryTimeout());
            // 启动流式查询
            statement.setFetchSize(Integer.MIN_VALUE);
            resultSet = statement.executeQuery(this.getSelectText());
            BatchPlainRecord records = new BatchPlainRecord();
            ResultSetMetaData metaData = resultSet.getMetaData();
            while (resultSet.next()) {
                Map<String, Object> record = new LinkedHashMap<>();
                for (int i = 1; i <= metaData.getColumnCount(); i++) {
                    String columnName = metaData.getColumnName(i);
                    Object value = resultSet.getObject(i);
                    record.put(columnName, value);
                }
                // 添加记录
                records.add(new PlainRecord(record));
                if (records.size() >= limit) {
                    // 后续发送
                    this.doNext(records, context);
                    // 消费后重置,回收内存,继续下一轮
                    records = new BatchPlainRecord();
                }
            }
            if (!records.isEmpty()) {
                // 最后一次发送
                this.doNext(records, context);
            }
        } finally {
            IoUtil.close(statement);
            IoUtil.close(resultSet);
        }
    }

    /**
     * 处理滚动查询
     *
     * @param context    上下文
     * @param connection 数据库连接
     * @param limit      每次查询的数量
     */
    @Override
    public void scrollQuery(Context context, Connection connection, Long limit) throws Exception {
        Select select = (Select) CCJSqlParserUtil.parse(this.getSelectText());
        // 滚动查询
        PlainSelect plainSelect = select.getPlainSelect();
        // 获取有没有order by
        List<OrderByElement> orderByElements = plainSelect.getOrderByElements();
        // 如果为空,则按照id排序
        if (CollUtil.isEmpty(orderByElements)) {
            OrderByElement orderByElement = new OrderByElement();
            orderByElement.setExpression(new net.sf.jsqlparser.schema.Column(this.getScrollColumn()));
            plainSelect.setOrderByElements(CollUtil.newArrayList(orderByElement));
        } else {
            // 判断是否有id排序,否则异常
            boolean hasId = orderByElements.stream().anyMatch(e -> this.getScrollColumn().equalsIgnoreCase(e.getExpression().toString()));
            if (!hasId) {
                throw new IllegalArgumentException("滚动查询,请指定排序字段:" + this.getScrollColumn());
            }
        }
        Limit lt = plainSelect.getLimit();
        if (lt == null) {
            // 如果用户没有设置limit
            Limit newLt = new Limit();
            // 设置每次查询
            newLt.setRowCount(new LongValue(limit));
            plainSelect.setLimit(newLt);
        }
        // 原来的条件存档
        Expression where = plainSelect.getWhere();
        // 滚动查询
        Object lastValue = null;
        while (true) {
            // 拼接滚动查询SQL
            if (lastValue != null) {
                // 增加一个条件例如 and id > 100
                GreaterThan newCondition = new GreaterThan();
                newCondition.setLeftExpression(new net.sf.jsqlparser.schema.Column(this.getScrollColumn()));
                switch (lastValue) {
                    case String lv -> newCondition.setRightExpression(new StringValue(lv));
                    case Date lv -> newCondition.setRightExpression(new StringValue(lv.toString()));
                    case Long lv -> newCondition.setRightExpression(new LongValue(lv));
                    default -> newCondition.setRightExpression(new HexValue(String.valueOf(lastValue)));
                }
                // 将原有的 WHERE 子句和新的条件用 AND 连接起来
                if (where != null) {
                    AndExpression newWhere = new AndExpression(where, newCondition);
                    plainSelect.setWhere(newWhere);
                } else {
                    plainSelect.setWhere(newCondition);
                }
            }
            BatchPlainRecord records = this.query(connection, plainSelect.toString());
            if (records.isEmpty()) {
                log.info("滚动查询结束,未获取到数据");
                break;
            }
            this.doNext(records, context);
            Map<String, Object> map = records.getLast();
            Object object = map.get(this.getScrollColumn());
            if (object == null) {
                log.info("滚动查询结束,未获取到下一个滚动列值");
                break;
            }
            lastValue = object;
            if (records.size() != limit) {
                log.info("滚动查询结束,数据已不足一页");
                break;
            }
        }
    }

}
