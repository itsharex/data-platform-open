package cn.dataplatform.open.common.source;

import cn.dataplatform.open.common.enums.DataSourceType;
import cn.hutool.core.io.IoUtil;
import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.ElasticsearchTransport;
import co.elastic.clients.transport.endpoints.BooleanResponse;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.validation.constraints.Min;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.elasticsearch.Version;
import org.elasticsearch.client.*;

import java.io.IOException;
import java.util.Arrays;

/**
 * es数据源
 *
 * @author dqw
 * @since 1.0.0
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
@Data
public class ElasticDataSource extends Source {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    /**
     * 以 ES 7.15.0 为分界线，低于此版本用 RestHighLevelClient，高于等于此版本用新的 ElasticsearchClient
     */
    public static final Version NEW_CLIENT_VERSION_THRESHOLD = Version.fromString("7.15.0");

    /**
     * 连接超时时间（毫秒）建立 TCP 连接的超时时间
     */
    @Min(value = 1000)
    private Integer connectTimeout = 30_000;

    /**
     * 读取超时时间（毫秒）两次数据包之间的最大间隔时间
     */
    @Min(value = 1000)
    private Integer socketTimeout = 60_000;

    /**
     * 连接请求超时时间（毫秒） 从连接池获取连接的超时时间
     */
    @Min(value = 1000)
    private Integer connectionRequestTimeout = 30_000;
    /**
     * 每个路由的最大连接数
     */
    @Min(value = 1)
    private Integer maxConnPerRoute = 10;

    /**
     * 最大连接数
     */
    @Min(value = 1)
    private Integer maxConnTotal = 50;

    /**
     * 客户端，支持以下
     *
     * @see ElasticsearchClient >= NEW_CLIENT_VERSION_THRESHOLD 使用
     * @see RestHighLevelClient < NEW_CLIENT_VERSION_THRESHOLD 使用
     */
    @Setter(AccessLevel.NONE)
    private volatile Object client;

    static {
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        // 配置日期格式
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * 获取客户端连接
     *
     * @return 客户端连接
     */
    public Object getClient() {
        if (this.client == null) {
            synchronized (this) {
                if (this.client == null) {
                    log.info("初始化Elastic数据源:{}", this.url);
                    HttpHost[] httpHosts = Arrays.stream(this.url.split(","))
                            .map(HttpHost::create).toArray(HttpHost[]::new);
                    BasicCredentialsProvider basicCredentialsProvider = new BasicCredentialsProvider();
                    if (StrUtil.isNotBlank(this.username)) {
                        basicCredentialsProvider.setCredentials(AuthScope.ANY, new UsernamePasswordCredentials(this.username, this.password));
                    }
                    RestClientBuilder restClientBuilder = RestClient.builder(httpHosts)
                            .setHttpClientConfigCallback(httpAsyncClientBuilder ->
                                    httpAsyncClientBuilder.setDefaultCredentialsProvider(basicCredentialsProvider)
                            )
                            // 配置超时时间
                            .setRequestConfigCallback(requestConfigBuilder -> requestConfigBuilder
                                    .setConnectTimeout(this.connectTimeout)
                                    .setSocketTimeout(this.socketTimeout)
                                    .setConnectionRequestTimeout(this.connectionRequestTimeout))
                            .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder
                                    .setMaxConnPerRoute(this.maxConnPerRoute)
                                    .setMaxConnTotal(this.maxConnTotal)
                            );
                    // 先构建RestClient用于检查版本
                    RestClient restClient = restClientBuilder.build();
                    try {
                        String versionString = this.getElasticsearchVersion(restClient);
                        log.info("Elasticsearch服务版本号: {}", versionString);
                        Version version = Version.fromString(versionString);
                        // 版本判断：>= 7.17.0 使用 ElasticsearchClient (新版)，否则使用 RestHighLevelClient (旧版)
                        if (version.onOrAfter(NEW_CLIENT_VERSION_THRESHOLD)) {
                            /*
                              ElasticWriteFlowComponent
                              Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
                                 Java 8 date/time type `java.time.LocalDateTime` not supported by default: add Module
                                 "com.fasterxml.jackson.datatype:jackson-datatype-jsr310" to enable handling (through reference chain: java.util.LinkedHashMap["LAST_LOGIN"])
                             */
                            log.info("使用ElasticsearchClient作为Elasticsearch客户端:{}", this.url);
                            JacksonJsonpMapper jsonpMapper = new JacksonJsonpMapper(OBJECT_MAPPER);
                            ElasticsearchTransport transport = new RestClientTransport(restClient, jsonpMapper);
                            this.client = new ElasticsearchClient(transport);
                        } else {
                            log.info("使用RestHighLevelClient作为Elasticsearch客户端:{}", this.url);
                            IoUtil.close(restClient);
                            this.client = new RestHighLevelClient(restClientBuilder);
                        }
                    } catch (Exception e) {
                        IoUtil.close(restClient);
                        throw new RuntimeException("初始化Elasticsearch客户端失败", e);
                    }
                }
            }
        }
        return this.client;
    }

    /**
     * 获取Elasticsearch版本号
     *
     * @param restClient RestClient实例
     * @return 版本号字符串
     * @throws IOException 如果请求失败
     */
    private String getElasticsearchVersion(RestClient restClient) throws IOException {
        Response response = restClient.performRequest(new Request("GET", "/"));
        JsonNode root = OBJECT_MAPPER.readTree(response.getEntity().getContent());
        return root.path("version").path("number").asText();
    }

    /**
     * 类型
     *
     * @return 类型
     */
    @Override
    public DataSourceType type() {
        return DataSourceType.ELASTIC;
    }

    /**
     * 健康检查
     *
     * @return true健康
     */
    @Override
    public Boolean health() throws Exception {
        Object client = this.getClient();
        if (client instanceof ElasticsearchClient elasticsearchClient) {
            // 调用 Elasticsearch 的 ping 方法来检查健康状态
            BooleanResponse pingResponse = elasticsearchClient.ping();
            return pingResponse.value();
        } else if (client instanceof RestHighLevelClient restHighLevelClient) {
            return restHighLevelClient.ping(RequestOptions.DEFAULT);
        }
        return false;
    }


    /**
     * 关闭资源
     */
    @Override
    public void close() {
        if (this.client != null) {
            try {
                log.info("关闭Elasticsearch数据源:{}", this.url);
                if (this.client instanceof ElasticsearchClient elasticsearchClient) {
                    // 关闭底层的 Transport
                    ElasticsearchTransport elasticsearchTransport = elasticsearchClient._transport();
                    elasticsearchTransport.close();
                } else if (this.client instanceof RestHighLevelClient restHighLevelClient) {
                    restHighLevelClient.close();
                }
                log.info("Elasticsearch数据源已关闭:{}", this.url);
            } catch (Exception e) {
                log.error("关闭Elasticsearch客户端时出错", e);
            }
            this.client = null;
        }
    }

}