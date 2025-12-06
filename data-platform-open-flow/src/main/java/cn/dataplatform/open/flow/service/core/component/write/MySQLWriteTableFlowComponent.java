package cn.dataplatform.open.flow.service.core.component.write;

import cn.dataplatform.open.flow.service.core.Context;
import cn.dataplatform.open.flow.service.core.Flow;
import cn.dataplatform.open.flow.service.core.Transmit;
import cn.dataplatform.open.flow.service.core.annotation.Retryable;
import cn.dataplatform.open.flow.service.core.pack.Column;
import cn.dataplatform.open.flow.service.core.record.Record;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * MySQL写入表组件
 *
 * @author dingqianwen
 * @date 2025/1/5
 * @since 1.0.0
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
@Getter
@Setter
@Retryable
public class MySQLWriteTableFlowComponent extends JDBCWriteTableFlowComponent {

    /**
     * 构造函数
     *
     * @param flow 流
     * @param code 组件编码
     */
    public MySQLWriteTableFlowComponent(Flow flow, String code) {
        super(flow, code);
    }

    /**
     * 写入数据
     *
     * @param transmit 传输对象
     * @param context  上下文
     */
    @Override
    public void run(Transmit transmit, Context context) {
        super.run(transmit, context);
    }


    /**
     * 执行写入
     *
     * @param op            写入类型
     * @param insertColumns 需要插入的列
     * @param connection    连接
     * @param rows          数据
     * @throws SQLException 异常
     */
    @Override
    @SuppressWarnings("all")
    public void doWrite(Record.Op op, List<Column> tableColumns, Connection connection,
                        List<Map<String, Object>> rows) throws SQLException {
        List<String> insertColumns = tableColumns.stream().map(Column::getName).toList();
        // 写入类型
        log.info("执行写入类型:{}", op);
        CustomSQL cs = this.customSQL.get(op);
        if (cs != null) {
            List<String> variables = cs.getVariables();
            // 校验
            for (String variable : variables) {
                // 如果表里都没有这一列,则报错
                if (this.caseInsensitive) {
                    if (insertColumns.stream().noneMatch(col -> col.equalsIgnoreCase(variable))) {
                        throw new IllegalArgumentException("表中不存在列:" + variable);
                    }
                } else {
                    if (!insertColumns.contains(variable)) {
                        throw new IllegalArgumentException("表中不存在列:" + variable);
                    }
                }
            }
            String customSql = cs.getScript();
            log.info("执行处理后的自定义SQL:{}", customSql);
            PreparedStatement customStatement = connection.prepareStatement(customSql);
            for (Map<String, Object> map : rows) {
                for (int i = 0; i < variables.size(); i++) {
                    // 拿到变量的值
                    Object value = map.get(variables.get(i));
                    customStatement.setObject(i + 1, value);
                }
                customStatement.addBatch();
            }
            customStatement.executeBatch();
            return;
        }
        // 拼接sql
        StringBuilder sql = new StringBuilder();
        if (Objects.equals(op, Record.Op.DELETE)) {
            sql.append("delete from ");
            sql.append("`").append(this.schema).append("`").append(".").append(this.table).append(" where ");
            for (int i = 0; i < this.primaryKey.size(); i++) {
                // 添加反引号，修复表名和列名包含关键字的问题报错的问题
                sql.append("`").append(this.primaryKey.get(i)).append("` = ?");
                if (i != this.primaryKey.size() - 1) {
                    sql.append(" and ");
                }
            }
            // 执行删除
            log.info("执行删除SQL:" + sql);
            PreparedStatement deleteStatement = connection.prepareStatement(sql.toString());
            for (Map<String, Object> map : rows) {
                for (int i = 0; i < this.primaryKey.size(); i++) {
                    deleteStatement.setObject(i + 1, map.get(this.primaryKey.get(i)));
                }
                deleteStatement.addBatch();
            }
            deleteStatement.executeBatch();
        } else if (Objects.equals(op, Record.Op.UPDATE)) {
            // 构建UPDATE语句
            sql.append("update ");
            sql.append("`").append(this.schema).append("`").append(".").append(this.table).append(" set ");
            // 添加要更新的列
            List<String> updateColumns;
            if (this.caseInsensitive) {
                // 忽略大小写
                updateColumns = insertColumns.stream()
                        .filter(col -> this.primaryKey.stream().noneMatch(pk -> pk.equalsIgnoreCase(col)))
                        .collect(Collectors.toList());
            } else {
                updateColumns = insertColumns.stream().filter(col -> !this.primaryKey.contains(col))
                        .collect(Collectors.toList());
            }
            for (int i = 0; i < updateColumns.size(); i++) {
                sql.append("`").append(updateColumns.get(i)).append("` = ?");
                if (i != updateColumns.size() - 1) {
                    sql.append(", ");
                }
            }
            // 添加WHERE条件(主键)
            sql.append(" where ");
            for (int i = 0; i < this.primaryKey.size(); i++) {
                sql.append("`").append(this.primaryKey.get(i)).append("` = ?");
                if (i != this.primaryKey.size() - 1) {
                    sql.append(" and ");
                }
            }
            // 执行更新
            log.info("执行更新SQL:" + sql);
            PreparedStatement updateStatement = connection.prepareStatement(sql.toString());
            for (Map<String, Object> map : rows) {
                int paramIndex = 1;
                // 设置更新列的值
                for (String col : updateColumns) {
                    updateStatement.setObject(paramIndex++, map.get(col));
                }
                // 设置WHERE条件(主键)的值
                for (String pk : this.primaryKey) {
                    updateStatement.setObject(paramIndex++, map.get(pk));
                }
                updateStatement.addBatch();
            }
            updateStatement.executeBatch();
        } else {
            // 注意：mysql8遇到 id = 0 时则会自增一条，不会执行更新操作
            // replace into 修复为
            sql.append("insert into ");
            sql.append("`").append(this.schema).append("`").append(".").append(this.table).append(" (");
            for (int i = 0; i < insertColumns.size(); i++) {
                sql.append("`").append(insertColumns.get(i)).append("`");
                if (i != insertColumns.size() - 1) {
                    sql.append(",");
                }
            }
            sql.append(") values (");
            for (int i = 0; i < insertColumns.size(); i++) {
                sql.append("?");
                if (i != insertColumns.size() - 1) {
                    sql.append(",");
                }
            }
            sql.append(") on duplicate key update ");
            // 添加更新列（排除主键）
            List<String> updateColumns = insertColumns.stream()
                    .filter(col -> !this.primaryKey.contains(col))
                    .collect(Collectors.toList());
            for (int i = 0; i < updateColumns.size(); i++) {
                String col = updateColumns.get(i);
                sql.append("`").append(col).append("` = values(`").append(col).append("`)");
                if (i != updateColumns.size() - 1) {
                    sql.append(",");
                }
            }
            log.info("执行插入/更新SQL:" + sql);
            PreparedStatement upsertStatement = connection.prepareStatement(sql.toString());
            for (Map<String, Object> map : rows) {
                for (int i = 0; i < insertColumns.size(); i++) {
                    upsertStatement.setObject(i + 1, map.get(insertColumns.get(i)));
                }
                upsertStatement.addBatch();
            }
            upsertStatement.executeBatch();
        }
    }

}
