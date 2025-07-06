package cn.dataplatform.open.support.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.amqp.RabbitAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Component;

/**
 * 〈一句话功能简述〉<br>
 * 〈发布与订阅〉
 *
 * @author 丁乾文
 * @date 2021/6/17
 * @since 1.0.0
 */
@Slf4j
@Component
public class RabbitConfig {

    public static final String ALARM_QUEUE = "dp-alarm-queue";
    public static final String ALARM_SCENE_QUEUE = "dp-alarm-scene-queue";
    public static final String SOURCE_QUEUE = "dp-data-source-queue";
    public static final String SOURCE_EXCHANGE = "dp-data-source-exchange";


    /**
     * 当配置了rabbitmq配置时启用
     */
    @ConditionalOnProperty("spring.rabbitmq.host")
    @EnableRabbit
    @Component
    @Import(RabbitAutoConfiguration.class)
    public static class RabbitConfiguration {

        public RabbitConfiguration() {
            log.info("load rabbit");
        }


        /**
         * 使用json传输,即使没有实现序列化接口也可以
         *
         * @return MessageConverter
         */
        @Bean
        public MessageConverter messageConverter() {
            return new Jackson2JsonMessageConverter();
        }

    }

    @ConditionalOnMissingBean(RabbitConfiguration.class)
    @Component
    public static class RabbitNoConfigurationWarning {

        /**
         * 未配置Rabbit将导致数据流不能正常发布
         */
        public RabbitNoConfigurationWarning() {
            if (log.isWarnEnabled()) {
                log.warn("If Rabbit is not configured, the rules will not be published normally");
            }
        }

    }

    @Bean
    public Queue alarmQueue() {
        return new Queue(ALARM_QUEUE, true);
    }

    @Bean
    public Queue alarmSceneQueue() {
        return new Queue(ALARM_SCENE_QUEUE, true);
    }


    @Bean
    public FanoutExchange sourceExchange() {
        return new FanoutExchange(SOURCE_EXCHANGE);
    }

    @Bean
    public Queue sourceQueue() {
        return new Queue(SOURCE_QUEUE);
    }

    @Bean
    public Binding sourceBindingExchangeMessage(@Qualifier("sourceQueue") Queue sourceQueue,
                                                @Qualifier("sourceExchange") FanoutExchange sourceExchange) {
        return BindingBuilder.bind(sourceQueue).to(sourceExchange);
    }

}
