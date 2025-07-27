package cn.dataplatform.open.support.service.alarm.impl;

import cn.dataplatform.open.common.alarm.robot.DingTalkRobot;
import cn.dataplatform.open.common.alarm.robot.LarkRobot;
import cn.dataplatform.open.common.alarm.robot.Robot;
import cn.dataplatform.open.common.alarm.robot.WeComRobot;
import cn.dataplatform.open.common.alarm.robot.content.Content;
import cn.dataplatform.open.common.alarm.robot.content.LarkContent;
import cn.dataplatform.open.common.alarm.robot.content.TextContent;
import cn.dataplatform.open.common.body.AlarmMessageBody;
import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.enums.*;
import cn.dataplatform.open.common.enums.alarm.AlarmLogStatus;
import cn.dataplatform.open.common.enums.alarm.AlarmRobotMode;
import cn.dataplatform.open.common.enums.alarm.AlarmRobotType;
import cn.dataplatform.open.common.util.ParallelStreamUtils;
import cn.dataplatform.open.common.vo.alarm.robot.Receive;
import cn.dataplatform.open.common.vo.alarm.robot.Silent;
import cn.dataplatform.open.support.excepiton.AlarmSilentException;
import cn.dataplatform.open.support.service.alarm.AlarmService;
import cn.dataplatform.open.support.store.entity.AlarmLog;
import cn.dataplatform.open.support.store.entity.AlarmRobot;
import cn.dataplatform.open.support.store.entity.AlarmTemplate;
import cn.dataplatform.open.support.store.mapper.AlarmLogMapper;
import cn.dataplatform.open.support.store.mapper.AlarmRobotMapper;
import cn.dataplatform.open.support.store.mapper.AlarmTemplateMapper;
import cn.dataplatform.open.support.util.FreeMarkerUtils;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.LocalDateTimeUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hankcs.algorithm.AhoCorasickDoubleArrayTrie;
import jakarta.annotation.Resource;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 〈一句话功能简述〉<br>
 * 〈〉
 *
 * @author dingqianwen
 * @date 2025/2/22
 * @since 1.0.0
 */
@Slf4j
@Service
public class AlarmServiceImpl implements AlarmService {

    /**
     * 内置模板参数
     */
    public static final String $_REQUEST_ID = "$requestId";
    public static final String $_SERVER_NAME = "$serverName";
    public static final String $_INSTANCE_ID = "$instanceId";
    public static final String $_ALARM_TIME = "$alarmTime";
    public static final String $_WORKSPACE_CODE = "$workspaceCode";
    public static final String $_SCENE_CODE = "$sceneCode";

    @Resource
    private AlarmRobotMapper alarmRobotMapper;
    @Resource
    private AlarmTemplateMapper alarmTemplateMapper;
    @Resource
    private AlarmLogMapper alarmLogMapper;
    @Resource
    private RedissonClient redissonClient;

    /**
     * 告警
     *
     * @param alarmMessageBody 告警消息
     */
    @Override
    public void alarm(AlarmMessageBody alarmMessageBody) {
        this.alarm(alarmMessageBody, null);
    }

    /**
     * 告警
     *
     * @param alarmMessageBody 告警消息
     * @param sceneCode        场景编码
     */
    @Override
    public void alarm(AlarmMessageBody alarmMessageBody, String sceneCode) {
        String workspaceCode = alarmMessageBody.getWorkspaceCode();
        String robotCode = alarmMessageBody.getRobotCode();
        AlarmRobot alarmRobot = this.alarmRobotMapper.selectOne(Wrappers.<AlarmRobot>lambdaQuery()
                .eq(AlarmRobot::getWorkspaceCode, workspaceCode)
                .eq(AlarmRobot::getCode, robotCode)
        );
        if (alarmRobot == null) {
            log.info("机器人不存在,告警消息被丢弃");
            return;
        }
        // 是否启用
        if (!Status.ENABLE.name().equals(alarmRobot.getStatus())) {
            log.info("机器人未启用,告警消息被丢弃");
            return;
        }
        String requestId = MDC.get(Constant.REQUEST_ID);
        // 内置参数处理，提前，需要记录日志
        this.builtInParameter(alarmMessageBody, requestId, sceneCode);
        Status recordLogSwitch = Status.valueOf(alarmRobot.getRecordLogSwitch());
        AlarmLog alarmLog = null;
        // 是否需要记录日志
        if (recordLogSwitch.equals(Status.ENABLE)) {
            alarmLog = new AlarmLog();
            alarmLog.setRequestId(requestId);
            alarmLog.setSceneCode(sceneCode);
            alarmLog.setStatus(AlarmLogStatus.SENDING.name());
            alarmLog.setRobotCode(alarmMessageBody.getRobotCode());
            alarmLog.setTemplateCode(alarmMessageBody.getTemplateCode());
            alarmLog.setServerName(alarmMessageBody.getServerName());
            alarmLog.setInstanceId(alarmMessageBody.getInstanceId());
            alarmLog.setWorkspaceCode(alarmMessageBody.getWorkspaceCode());
            alarmLog.setParameter(JSON.toJSONString(alarmMessageBody.getParameter()));
            alarmLog.setCreateTime(alarmMessageBody.getAlarmTime());
            this.alarmLogMapper.insert(alarmLog);
        }
        try {
            this.doAlarm(alarmRobot, alarmMessageBody);
            log.info("发送告警消息成功");
            if (alarmLog != null) {
                alarmLog.setStatus(AlarmLogStatus.SUCCESS.name());
                this.alarmLogMapper.updateById(alarmLog);
            }
        } catch (AlarmSilentException alarmSilentException) {
            if (alarmLog != null) {
                alarmLog.setStatus(AlarmLogStatus.SILENT.name());
                alarmLog.setErrorReason(StrUtil.maxLength(alarmSilentException.getMessage(), 2000));
                this.alarmLogMapper.updateById(alarmLog);
            }
        } catch (Exception e) {
            log.warn("发送告警消息失败", e);
            if (alarmLog != null) {
                alarmLog.setStatus(AlarmLogStatus.FAILED.name());
                alarmLog.setErrorReason(StrUtil.maxLength(e.getMessage(), 2000));
                this.alarmLogMapper.updateById(alarmLog);
            }
        }
    }

    /**
     * 初始化内置请求参数-方便模板配置,以$开头
     *
     * @param alarmMessageBody 告警消息
     * @param requestId        请求ID
     * @param sceneCode        告警场景编码
     */
    private void builtInParameter(AlarmMessageBody alarmMessageBody, String requestId, String sceneCode) {
        Map<String, Object> parameter = alarmMessageBody.getParameter();
        String workspaceCode = alarmMessageBody.getWorkspaceCode();
        if (!parameter.containsKey($_REQUEST_ID)) {
            parameter.put($_REQUEST_ID, requestId);
        }
        if (!parameter.containsKey($_SERVER_NAME)) {
            parameter.put($_SERVER_NAME, alarmMessageBody.getServerName());
        }
        if (!parameter.containsKey($_INSTANCE_ID)) {
            parameter.put($_INSTANCE_ID, alarmMessageBody.getInstanceId());
        }
        if (!parameter.containsKey($_ALARM_TIME)) {
            parameter.put($_ALARM_TIME, LocalDateTimeUtil.format(alarmMessageBody.getAlarmTime(), Constant.DATE_TIME_FORMAT));
        }
        if (!parameter.containsKey($_WORKSPACE_CODE)) {
            parameter.put($_WORKSPACE_CODE, workspaceCode);
        }
        if (!parameter.containsKey($_SCENE_CODE)) {
            parameter.put($_SCENE_CODE, sceneCode);
        }
    }

    /**
     * 发送告警
     *
     * @param alarmRobot       机器人
     * @param alarmMessageBody 告警消息
     */
    @SneakyThrows
    private void doAlarm(AlarmRobot alarmRobot, AlarmMessageBody alarmMessageBody) {
        String workspaceCode = alarmMessageBody.getWorkspaceCode();
        String robotCode = alarmMessageBody.getRobotCode();
        String templateCode = alarmMessageBody.getTemplateCode();
        Map<String, Object> parameter = alarmMessageBody.getParameter();
        AlarmTemplate alarmTemplate = this.alarmTemplateMapper.selectOne(Wrappers.<AlarmTemplate>lambdaQuery()
                .eq(AlarmTemplate::getWorkspaceCode, workspaceCode)
                .eq(AlarmTemplate::getCode, templateCode)
        );
        if (alarmTemplate == null) {
            throw new RuntimeException("模板不存在");
        }
        // 是否启用
        if (!Status.ENABLE.name().equals(alarmTemplate.getStatus())) {
            throw new RuntimeException("模板未启用");
        }
        String templateContent = alarmTemplate.getTemplateContent();
        // 模板套入参数
        if (StrUtil.isNotBlank(templateContent)) {
            // 使用FreeMarker模板引擎处理模板
            templateContent = FreeMarkerUtils.processTemplate(alarmTemplate.getCode(), templateContent, parameter);
        }
        String type = alarmRobot.getType();
        AlarmRobotType alarmRobotType = AlarmRobotType.valueOf(type);
        Content content;
        Robot robot = switch (alarmRobotType) {
            case LARK -> {
                if (StrUtil.isNotBlank(alarmTemplate.getExternalTemplateCode())) {
                    LarkContent larkContent = new LarkContent();
                    // 外部系统的模板编码,例如飞书的消息卡片 外部
                    larkContent.setTemplateId(alarmTemplate.getExternalTemplateCode());
                    // 外部消息模板参数
                    larkContent.setTemplateParameter(parameter);
                    content = larkContent;
                } else {
                    content = new TextContent(templateContent);
                }
                yield SpringUtil.getBean(LarkRobot.class);
            }
            case DING_TALK -> {
                content = new TextContent(templateContent);
                yield SpringUtil.getBean(DingTalkRobot.class);
            }
            case WE_COM -> {
                content = new TextContent(templateContent);
                yield SpringUtil.getBean(WeComRobot.class);
            }
            default -> throw new RuntimeException("不支持的机器人类型");
        };
        // 告警沉默判断
        List<Silent> silents = JSON.parseArray(alarmRobot.getSilent(), Silent.class);
        {
            // 发送的内容,一大串字符串
            String ct = JSON.toJSONString(content);
            if (CollUtil.isNotEmpty(silents)) {
                // 过滤掉过期的规则
                silents.removeIf(silent -> silent.getExpire() != null && silent.getExpire().isBefore(alarmMessageBody.getAlarmTime()));
                // 存在沉默规则
                if (CollUtil.isNotEmpty(silents)) {
                    // 收集所有的关键词
                    Map<String, String> keyMap = silents.stream()
                            .map(Silent::getKeys).flatMap(Set::stream).collect(Collectors.toMap(k -> k, k -> k));
                    // 使用 Aho - Corasick 算法构建关键词匹配器
                    AhoCorasickDoubleArrayTrie<String> trie = new AhoCorasickDoubleArrayTrie<>();
                    trie.build(keyMap);
                    // 进行匹配
                    Collection<AhoCorasickDoubleArrayTrie.Hit<String>> hits = trie.parseText(ct);
                    if (!hits.isEmpty()) {
                        // 存在匹配的关键词,不发送消息
                        List<String> collect = hits.stream()
                                // 最多打印5个命中的关键词
                                .limit(5).map(m -> m.value).toList();
                        String jsonString = JSON.toJSONString(collect);
                        log.info("告警消息被沉默,告警消息中包含关键词:{}", jsonString);
                        throw new AlarmSilentException(jsonString);
                    }
                }
            }
        }
        List<Receive> receives = JSON.parseArray(alarmRobot.getReceives(), Receive.class);
        // 判断发送模式
        String mode = alarmRobot.getMode();
        if (Objects.equals(mode, AlarmRobotMode.BROADCAST.name())) {
            // 全部发送
            ParallelStreamUtils.forEach(receives, receive -> {
                robot.send(receive.getKey(), content);
            }, false);
        } else if (Objects.equals(mode, AlarmRobotMode.POLLING.name())) {
            // 轮询发送
            RAtomicLong atomicLong = this.redissonClient.getAtomicLong(RedisKey.ALARM_ROBOT_POLLING.build(workspaceCode + robotCode));
            // 当前自增索引
            long index = atomicLong.incrementAndGet();
            // 总机器人数量
            int size = receives.size();
            // 获取当前要发送的机器人
            Receive receive = receives.get((int) (index % size));
            robot.send(receive.getKey(), content);
            // 如果index超出long最大值,重置
            if (index ==
                    // 提前重置
                    Long.MAX_VALUE - 10000) {
                // 告警不需要高精度轮询
                atomicLong.set(0);
            }
        } else if (Objects.equals(mode, AlarmRobotMode.RANDOM.name())) {
            // 随机发送
            Receive receive = receives.get((int) (Math.random() * receives.size()));
            robot.send(receive.getKey(), content);
        } else {
            throw new RuntimeException("不支持的发送模式");
        }
    }

}
