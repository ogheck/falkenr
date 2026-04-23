package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.audit.InMemoryAuditLogStore;
import com.devtools.ui.core.config.ConfigInspector;
import com.devtools.ui.core.db.DbQueryCaptureStore;
import com.devtools.ui.core.db.DbQueryCollector;
import com.devtools.ui.core.deps.DependencyGraphCollector;
import com.devtools.ui.core.endpoint.EndpointCollector;
import com.devtools.ui.core.fakes.FakeExternalServiceCollector;
import com.devtools.ui.core.fakes.FakeExternalServiceStore;
import com.devtools.ui.core.flags.FeatureFlagCollector;
import com.devtools.ui.core.flags.FeatureFlagDefinitionLookup;
import com.devtools.ui.core.flags.FeatureFlagOverrideStore;
import com.devtools.ui.core.jobs.JobCollector;
import com.devtools.ui.core.logs.InMemoryLogStore;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import com.devtools.ui.core.requests.RequestCaptureStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping;
import org.springframework.web.client.RestClient;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnClass(RequestMappingInfoHandlerMapping.class)
@ConditionalOnProperty(prefix = "spring.devtools.ui", name = "enabled", havingValue = "true", matchIfMissing = true)
@Conditional(NoProdProfileCondition.class)
@EnableConfigurationProperties(DevToolsUiProperties.class)
public class DevToolsUiAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    EndpointCollector endpointCollector(List<RequestMappingInfoHandlerMapping> handlerMappings) {
        return new EndpointCollector(handlerMappings);
    }

    @Bean
    @ConditionalOnMissingBean
    RequestCaptureStore requestCaptureStore(DevToolsUiProperties properties, ObjectMapper objectMapper) {
        return properties.getHistory().isPersistRequests()
                ? new RequestCaptureStore(
                properties.getRequestLimit(),
                new JsonFileRequestCapturePersistence(
                        java.nio.file.Path.of(properties.getHistory().getRequestsPersistenceFile()),
                        objectMapper,
                        properties.getHistory().getMaxPersistedRequests()
                )
        )
                : new RequestCaptureStore(properties.getRequestLimit());
    }

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock devToolsClock(DevToolsUiProperties properties) {
        return properties.getFeatures().isTime()
                ? new MutableDevToolsClock()
                : Clock.system(ZoneId.systemDefault());
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsDataPolicy devToolsDataPolicy(DevToolsUiProperties properties) {
        return new DefaultDevToolsDataPolicy(properties.getPolicy());
    }

    @Bean
    @ConditionalOnMissingBean
    ConfigInspector configInspector(ConfigurableEnvironment environment, DevToolsDataPolicy dataPolicy) {
        return new ConfigInspector(environment, dataPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    JsonFileConfigSnapshotStore jsonFileConfigSnapshotStore(DevToolsUiProperties properties,
                                                            ObjectMapper objectMapper,
                                                            Clock clock) {
        return new JsonFileConfigSnapshotStore(
                java.nio.file.Path.of(properties.getHistory().getConfigSnapshotsFile()),
                objectMapper,
                clock,
                properties.getHistory().getMaxConfigSnapshots()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    FeatureFlagOverrideStore featureFlagOverrideStore(ConfigurableEnvironment environment) {
        FeatureFlagOverrideStore store = new FeatureFlagOverrideStore();
        if (!environment.getPropertySources().contains(DevToolsFeatureFlagPropertySource.NAME)) {
            environment.getPropertySources().addFirst(new DevToolsFeatureFlagPropertySource(store));
        }
        return store;
    }

    @Bean
    @ConditionalOnMissingBean
    JsonFileFeatureFlagDefinitionStore jsonFileFeatureFlagDefinitionStore(DevToolsUiProperties properties,
                                                                         ObjectMapper objectMapper,
                                                                         Clock clock) {
        return new JsonFileFeatureFlagDefinitionStore(
                java.nio.file.Path.of(properties.getFeatureFlags().getDefinitionsFile()),
                objectMapper,
                clock,
                properties.getFeatureFlags().isPersistDefinitions()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    FeatureFlagCollector featureFlagCollector(ConfigurableEnvironment environment,
                                             FeatureFlagOverrideStore featureFlagOverrideStore,
                                             FeatureFlagDefinitionLookup featureFlagDefinitionLookup) {
        return new FeatureFlagCollector(environment, featureFlagOverrideStore, featureFlagDefinitionLookup);
    }

    @Bean
    @ConditionalOnMissingBean
    FakeExternalServiceStore fakeExternalServiceStore() {
        return new FakeExternalServiceStore();
    }

    @Bean
    @ConditionalOnMissingBean
    FakeExternalServiceCollector fakeExternalServiceCollector(FakeExternalServiceStore store) {
        return new FakeExternalServiceCollector(store);
    }

    @Bean
    @ConditionalOnMissingBean
    JobCollector jobCollector(ListableBeanFactory beanFactory, DevToolsDataPolicy dataPolicy) {
        return new JobCollector(beanFactory, dataPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    DbQueryCaptureStore dbQueryCaptureStore(DevToolsUiProperties properties) {
        return new DbQueryCaptureStore(properties.getDbQueryLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    DbQueryCollector dbQueryCollector(DbQueryCaptureStore store) {
        return new DbQueryCollector(store);
    }

    @Bean
    @ConditionalOnMissingBean
    DependencyGraphCollector dependencyGraphCollector(ConfigurableListableBeanFactory beanFactory) {
        return new DependencyGraphCollector(beanFactory);
    }

    @Bean
    @ConditionalOnClass(DataSource.class)
    DevToolsDataSourceBeanPostProcessor devToolsDataSourceBeanPostProcessor(DevToolsUiProperties properties,
                                                                            DbQueryCaptureStore store,
                                                                            DevToolsDataPolicy dataPolicy) {
        return new DevToolsDataSourceBeanPostProcessor(properties.getFeatures().isDbQueries(), store, dataPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    InMemoryLogStore inMemoryLogStore(DevToolsUiProperties properties) {
        return new InMemoryLogStore(properties.getLogLimit());
    }

    @Bean
    @ConditionalOnMissingBean
    InMemoryAuditLogStore inMemoryAuditLogStore(DevToolsUiProperties properties, Clock clock) {
        return new InMemoryAuditLogStore(properties.getMemory().getAuditLogs(), clock);
    }

    @Bean
    @ConditionalOnMissingBean
    InMemoryApprovalStore inMemoryApprovalStore(Clock clock) {
        return new InMemoryApprovalStore(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsLogbackInitializer devToolsLogbackInitializer(DevToolsUiProperties properties, InMemoryLogStore logStore) {
        return new DevToolsLogbackInitializer(properties.getFeatures().isLogs(), logStore);
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsRequestInterceptor devToolsRequestInterceptor(RequestCaptureStore requestCaptureStore,
                                                          RemoteSessionService remoteSessionService,
                                                          DevToolsUiProperties properties,
                                                          DevToolsDataPolicy dataPolicy) {
        return new DevToolsRequestInterceptor(
                properties.getFeatures().isRequests(),
                requestCaptureStore,
                remoteSessionService,
                properties.getMaxCapturedBodyLength(),
                properties.getRequestSampling(),
                dataPolicy
        );
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsUiWebMvcConfigurer devToolsUiWebMvcConfigurer(DevToolsRequestInterceptor requestInterceptor) {
        return new DevToolsUiWebMvcConfigurer(requestInterceptor);
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsApiController devToolsApiController(EndpointCollector endpointCollector,
                                                RequestCaptureStore requestCaptureStore,
                                                ConfigInspector configInspector,
                                                JsonFileConfigSnapshotStore configSnapshotStore,
                                                JsonFileFeatureFlagDefinitionStore featureFlagDefinitionStore,
                                                InMemoryAuditLogStore auditLogStore,
                                                InMemoryApprovalStore approvalStore,
                                                FeatureFlagCollector featureFlagCollector,
                                                FeatureFlagOverrideStore featureFlagOverrideStore,
                                                DependencyGraphCollector dependencyGraphCollector,
                                                FakeExternalServiceCollector fakeExternalServiceCollector,
                                                FakeExternalServiceStore fakeExternalServiceStore,
                                                RemoteSessionService remoteSessionService,
                                                DevToolsUiProperties properties,
                                                Clock clock,
                                                JobCollector jobCollector,
                                                DbQueryCollector dbQueryCollector,
                                                InMemoryLogStore logStore,
                                                WebhookSimulator webhookSimulator,
                                                ApiTestSimulator apiTestSimulator) {
        return new DevToolsApiController(
                endpointCollector,
                requestCaptureStore,
                configInspector,
                configSnapshotStore,
                featureFlagDefinitionStore,
                auditLogStore,
                approvalStore,
                featureFlagCollector,
                featureFlagOverrideStore,
                dependencyGraphCollector,
                fakeExternalServiceCollector,
                fakeExternalServiceStore,
                remoteSessionService,
                properties,
                clock,
                jobCollector,
                dbQueryCollector,
                logStore,
                webhookSimulator,
                apiTestSimulator
        );
    }

    @Bean
    @ConditionalOnMissingBean
    WebhookSimulator webhookSimulator(ApplicationContext applicationContext) {
        return new WebhookSimulator(applicationContext);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiTestSimulator apiTestSimulator(ApplicationContext applicationContext, DevToolsDataPolicy dataPolicy) {
        return new ApiTestSimulator(applicationContext, dataPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    LocalAgentSessionStore localAgentSessionStore(Clock clock, DevToolsUiProperties properties, DevToolsDataPolicy dataPolicy) {
        return new LocalAgentSessionStore(clock, properties.getRemote(), properties.getSecrets(), dataPolicy);
    }

    @Bean
    @ConditionalOnMissingBean
    HostedRelaySessionStore hostedRelaySessionStore(DevToolsUiProperties properties,
                                                    RestClient.Builder restClientBuilder) {
        return "managed-relay".equalsIgnoreCase(properties.getRemote().getTransportMode())
                ? new HttpHostedRelaySessionStore(properties.getRemote().getRelayApiBaseUrl(), restClientBuilder)
                : new InMemoryHostedRelaySessionStore();
    }

    @Bean
    @ConditionalOnMissingBean
    RelayTokenCodec relayTokenCodec(Clock clock) {
        return new RelayTokenCodec(clock);
    }

    @Bean
    @ConditionalOnMissingBean
    RelayClient relayClient(DevToolsUiProperties properties, RestClient.Builder restClientBuilder) {
        return "managed-relay".equalsIgnoreCase(properties.getRemote().getTransportMode())
                ? new HttpRelayClient(properties.getRemote().getRelayApiBaseUrl(), restClientBuilder)
                : new LocalRelayClient(properties.getRemote());
    }

    @Bean
    @ConditionalOnMissingBean
    TunnelStreamClient tunnelStreamClient(DevToolsUiProperties properties) {
        return "managed-relay".equalsIgnoreCase(properties.getRemote().getTransportMode())
                ? new HttpTunnelStreamClient(properties.getRemote().getRelayApiBaseUrl())
                : new NoOpTunnelStreamClient();
    }

    @Bean
    @ConditionalOnMissingBean
    RemoteSessionService remoteSessionService(LocalAgentSessionStore localAgentSessionStore,
                                              RelayClient relayClient,
                                              RelayTokenCodec relayTokenCodec,
                                              HostedRelaySessionStore hostedRelaySessionStore,
                                              TunnelStreamClient tunnelStreamClient) {
        return new RemoteSessionService(localAgentSessionStore, relayClient, relayTokenCodec, hostedRelaySessionStore, tunnelStreamClient);
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsUiController devToolsUiController() {
        return new DevToolsUiController();
    }

    @Bean
    @ConditionalOnMissingBean
    DevToolsFakeExternalServicesController devToolsFakeExternalServicesController(DevToolsUiProperties properties,
                                                                                 FakeExternalServiceStore store) {
        return new DevToolsFakeExternalServicesController(properties, store);
    }

    @Bean
    FilterRegistrationBean<DevToolsAccessFilter> devToolsAccessFilterRegistration(DevToolsUiProperties properties) {
        FilterRegistrationBean<DevToolsAccessFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DevToolsAccessFilter(properties.getAccess()));
        registration.addUrlPatterns(DevToolsUiConstants.ROOT_PATH, DevToolsUiConstants.ROOT_PATH + "/*");
        registration.setName("springDevToolsUiAccessFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    @Bean
    FilterRegistrationBean<DevToolsRequestWrappingFilter> devToolsRequestWrappingFilterRegistration() {
        FilterRegistrationBean<DevToolsRequestWrappingFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new DevToolsRequestWrappingFilter());
        registration.addUrlPatterns("/*");
        registration.setName("springDevToolsUiRequestWrappingFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }
}
