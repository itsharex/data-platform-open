package cn.dataplatform.open.flow.service.core.component;


import cn.dataplatform.open.common.constant.Constant;
import cn.dataplatform.open.common.enums.RedisKey;
import cn.dataplatform.open.common.enums.Status;
import cn.dataplatform.open.common.server.ServerManager;
import cn.dataplatform.open.common.vo.flow.FlowError;
import cn.dataplatform.open.flow.config.ThreadPoolConfig;
import cn.dataplatform.open.flow.service.core.Context;
import cn.dataplatform.open.flow.service.core.Flow;
import cn.dataplatform.open.flow.service.core.Transmit;
import cn.dataplatform.open.flow.service.core.monitor.FlowMonitor;
import cn.hutool.core.exceptions.ExceptionUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.extra.spring.SpringUtil;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.quartz.impl.StdSchedulerFactory;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.slf4j.MDC;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

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
public class JobFlowComponent extends FlowComponent implements Job {

    public static final String JOB_CODE = "jobCode";
    private static final String GROUP_NAME = "default_group";
    private static final String THIS = "this";
    /**
     * context
     */
    private static final String CONTEXT = "context";

    /**
     * 任务名称对应的任务Future
     */
    private static final Map<String, Future<?>> JOB_FUTURE_MAP = new ConcurrentHashMap<>();

    private static final Scheduler SCHEDULER;

    static {
        try {
            SCHEDULER = StdSchedulerFactory.getDefaultScheduler();
            SCHEDULER.start();
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    private String cron;

    /**
     * 状态
     */
    private Status status = Status.ENABLE;

    /**
     * 阻塞策略,默认放弃当前的任务,继续之前之前未完成的
     *
     * @see BlockStrategy
     */
    private BlockStrategy blockStrategy = BlockStrategy.ABANDON_CURRENT;

    private final RedissonClient redissonClient;
    private final ThreadPoolTaskExecutor flowJobExecutor;
    private final ServerManager serverManager;
    private FlowMonitor flowMonitor;

    /**
     * 无参构造
     * <p>
     * JobBuilder.newJob(JobFlowComponent.class)
     */
    public JobFlowComponent() {
        this(null, null);
    }

    /**
     * 构造函数
     *
     * @param flow 流程
     * @param code 任务
     */
    public JobFlowComponent(Flow flow, String code) {
        super(flow, code);
        // 获取到执行锁
        this.redissonClient = SpringUtil.getBean(RedissonClient.class);
        this.flowJobExecutor = SpringUtil.getBean(ThreadPoolConfig.FLOW_JOB_EXECUTOR, ThreadPoolTaskExecutor.class);
        this.serverManager = SpringUtil.getBean(ServerManager.class);
        this.flowMonitor = SpringUtil.getBean(FlowMonitor.class);
    }

    /**
     * 启动任务
     */
    @SneakyThrows
    public synchronized void run(Transmit transmit, Context context) {
        log.info("数据流创建定时任务:{}", this.getKey());
        // 创建job
        String key = this.getKey();
        JobDataMap jobDataMap = new JobDataMap();
        jobDataMap.put(THIS, this);
        jobDataMap.put(CONTEXT, context);
        JobDetail jobDetail = JobBuilder.newJob(JobFlowComponent.class)
                .setJobData(jobDataMap)
                .withIdentity(JOB_CODE, key).build();
        // 创建触发器
        Trigger trigger = TriggerBuilder.newTrigger()
                .withIdentity(key, GROUP_NAME)
                .withSchedule(CronScheduleBuilder.cronSchedule(cron))
                .build();
        // 将job和trigger注册到scheduler中
        SCHEDULER.scheduleJob(jobDetail, trigger);
    }

    /**
     * 停止任务
     */
    @Override
    public void stop() {
        try {
            JobKey jobKey = new JobKey(JOB_CODE, this.getKey());
            // 停止任务
            if (SCHEDULER.checkExists(jobKey)) {
                log.info("停止任务:{}", this.getKey());
                SCHEDULER.deleteJob(jobKey);
                log.info("停止任务成功:{}", this.getKey());
            }
            // 停止触发器
            TriggerKey triggerKey = new TriggerKey(this.getKey(), GROUP_NAME);
            if (SCHEDULER.checkExists(triggerKey)) {
                log.info("停止触发器:{}", this.getKey());
                SCHEDULER.unscheduleJob(triggerKey);
                log.info("停止触发器成功:{}", this.getKey());
            }
            // 停止正在运行中的任务线程
            Future<?> future = JOB_FUTURE_MAP.get(this.getKey());
            if (future != null && !future.isDone()) {
                future.cancel(true);
                log.info("停止任务线程成功:{}", this.getKey());
            }
        } catch (SchedulerException e) {
            log.error("停止任务失败", e);
        }
    }

    /**
     * 执行任务
     *
     * @param jobExecutionContext 任务上下文
     */
    @Override
    public void execute(JobExecutionContext jobExecutionContext) {
        String id = UUID.fastUUID().toString(true);
        MDC.put(Constant.REQUEST_ID, id);
        try {
            JobDataMap jobDataMap = jobExecutionContext.getJobDetail().getJobDataMap();
            // 当前实例数据
            JobFlowComponent jobFlowComponent = (JobFlowComponent) jobDataMap.get(THIS);
            if (jobFlowComponent == null) {
                throw new RuntimeException("任务执行失败,任务组件为空");
            }
            MDC.put(Constant.FLOW_CODE, jobFlowComponent.getFlowCode());
            Context context = (Context) jobDataMap.get(CONTEXT);
            // 定时任务,每次执行用一个上下文
            Context c = context.clone();
            // 设置新的上下文ID
            c.setId(id);
            this.doExecute(jobFlowComponent, c);
        } finally {
            MDC.clear();
        }
    }

    /**
     * 执行任务
     *
     * @param jobFlowComponent 任务组件
     */
    @SneakyThrows
    public void doExecute(JobFlowComponent jobFlowComponent, Context context) {
        // 任务启动时,所有实例都会被触发,各自检测各自的任务是否已经在执行即可,后续执行还存在分布式锁
        // 如果任务已存在
        Future<?> future = JOB_FUTURE_MAP.get(jobFlowComponent.getKey());
        boolean acBs = Objects.equals(BlockStrategy.ABANDON_CURRENT, blockStrategy);
        if (future != null && !future.isDone()) {
            // 丢弃后续任务
            if (acBs) {
                log.info("任务:{}已存在,丢弃后续任务", jobFlowComponent.getKey());
                return;
            }
            // 中断之前任务
            if (Objects.equals(BlockStrategy.BEFORE_INTERRUPTION, blockStrategy)) {
                log.info("任务:{}已存在,开始中断之前的任务", jobFlowComponent.getKey());
                // 强制中断
                // target InterruptedException
                boolean cancel = future.cancel(true);
                if (cancel) {
                    log.info("任务:{}中断成功", jobFlowComponent.getKey());
                } else {
                    log.info("任务:{}中断失败", jobFlowComponent.getKey());
                }
            }
        }
        long startMillis = System.currentTimeMillis();
        RLock lock = this.redissonClient.getLock(RedisKey.FLOW_JOB_LOCK.build(jobFlowComponent.getKey()));
        if (!lock.tryLock()) {
            // 丢弃后续任务
            if (acBs) {
                log.info("任务:{}已存在,丢弃后续任务", jobFlowComponent.getKey());
            } else {
                // 如果是中断之前任务,则中断后,正常去抢占锁
                log.info("任务:{}正在执行中", jobFlowComponent.getKey());
            }
            return;
        }
        try {
            future = this.flowJobExecutor.submit(() -> {
                log.info("执行任务:{}", jobFlowComponent.getKey());
                // 使用当前实例的jobFlowComponent
                jobFlowComponent.runNext(() -> {
                    Transmit transmit = new Transmit();
                    transmit.setFlowComponent(jobFlowComponent);
                    return transmit;
                }, context);
            });
            try {
                JOB_FUTURE_MAP.put(jobFlowComponent.getKey(), future);
                // 阻塞此定时任务,获取到结果
                future.get();
            } finally {
                // 任务异常,或者完成
                JOB_FUTURE_MAP.remove(jobFlowComponent.getKey());
            }
        } catch (Exception e) {
            if (e instanceof CancellationException) {
                log.info("数据流任务被取消:" + jobFlowComponent.getKey());
            } else {
                log.error("数据流任务执行失败", e);
                // 获取最底异常
                Throwable rootCause = ExceptionUtil.getRootCause(e);
                // 标记为运行异常，但是不需要中断定时任务，等待下次调度即可
                this.flowMonitor.errorWithAlarm(jobFlowComponent, rootCause, FlowError.ErrorType.RUNNING);
            }
        } finally {
            if (lock.isLocked() && lock.isHeldByCurrentThread()) {
                log.info("释放数据流任务锁:{}", jobFlowComponent.getKey());
                // 控制最快1秒后才能释放锁，规避不合理的竞争
                long endMillis = System.currentTimeMillis();
                long sleepMillis = Math.max(0, 1000 - (endMillis - startMillis));
                if (sleepMillis > 0) {
                    // 休眠剩余的时间
                    ThreadUtil.sleep(sleepMillis);
                }
                try {
                    lock.unlockAsync();
                } catch (Exception t) {
                    log.warn("释放锁失败", t);
                }
            }
        }
    }


    /**
     * 阻塞策略
     */
    public enum BlockStrategy {

        /**
         * 中断之前的任务开始当前任务
         */
        BEFORE_INTERRUPTION,
        /**
         * 放弃当前任务
         */
        ABANDON_CURRENT

    }


}
