package com.devtools.ui.autoconfigure;

import com.devtools.ui.core.db.DbQueryCaptureStore;
import com.devtools.ui.core.model.DbQueryDescriptor;
import com.devtools.ui.core.policy.DevToolsDataPolicy;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.Instant;
import java.util.Locale;

class DevToolsDataSourceBeanPostProcessor implements BeanPostProcessor {

    private final boolean enabled;
    private final DbQueryCaptureStore store;
    private final DevToolsDataPolicy dataPolicy;

    DevToolsDataSourceBeanPostProcessor(boolean enabled, DbQueryCaptureStore store, DevToolsDataPolicy dataPolicy) {
        this.enabled = enabled;
        this.store = store;
        this.dataPolicy = dataPolicy;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (!enabled) {
            return bean;
        }
        if (!(bean instanceof DataSource dataSource)) {
            return bean;
        }
        if (Proxy.isProxyClass(bean.getClass())) {
            return bean;
        }
        return Proxy.newProxyInstance(
                dataSource.getClass().getClassLoader(),
                new Class<?>[]{DataSource.class},
                new DataSourceInvocationHandler(dataSource, beanName, store, dataPolicy)
        );
    }

    private record DataSourceInvocationHandler(DataSource target, String beanName,
                                               DbQueryCaptureStore store,
                                               DevToolsDataPolicy dataPolicy) implements InvocationHandler {

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            Object result = method.invoke(target, args);
            if ("getConnection".equals(method.getName()) && result instanceof Connection connection) {
                return wrapConnection(connection, beanName, store, dataPolicy);
            }
            return result;
        }
    }

    private static Connection wrapConnection(Connection target, String beanName, DbQueryCaptureStore store, DevToolsDataPolicy dataPolicy) {
        return (Connection) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    Object result = method.invoke(target, args);
                    String methodName = method.getName();

                    if ("prepareStatement".equals(methodName) && args != null && args.length > 0 && args[0] instanceof String sql && result instanceof PreparedStatement preparedStatement) {
                        return wrapPreparedStatement(preparedStatement, sql, beanName, store, dataPolicy);
                    }
                    if ("createStatement".equals(methodName) && result instanceof Statement statement) {
                        return wrapStatement(statement, beanName, store, dataPolicy);
                    }
                    return result;
                }
        );
    }

    private static PreparedStatement wrapPreparedStatement(PreparedStatement target, String sql, String beanName,
                                                           DbQueryCaptureStore store,
                                                           DevToolsDataPolicy dataPolicy) {
        return (PreparedStatement) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    try {
                        Object result = method.invoke(target, args);
                        capturePreparedStatementExecution(method.getName(), sql, beanName, result, store, dataPolicy);
                        return result;
                    } catch (Throwable throwable) {
                        capture(sql, beanName, null, store, dataPolicy);
                        throw throwable;
                    }
                }
        );
    }

    private static Statement wrapStatement(Statement target, String beanName, DbQueryCaptureStore store, DevToolsDataPolicy dataPolicy) {
        return (Statement) Proxy.newProxyInstance(
                target.getClass().getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    String sql = args != null && args.length > 0 && args[0] instanceof String candidate ? candidate : null;
                    try {
                        Object result = method.invoke(target, args);
                        if (sql != null) {
                            captureStatementExecution(method.getName(), sql, beanName, result, store, dataPolicy);
                        }
                        return result;
                    } catch (Throwable throwable) {
                        if (sql != null) {
                            capture(sql, beanName, null, store, dataPolicy);
                        }
                        throw throwable;
                    }
                }
        );
    }

    private static void capturePreparedStatementExecution(String methodName, String sql, String beanName, Object result,
                                                          DbQueryCaptureStore store,
                                                          DevToolsDataPolicy dataPolicy) {
        if ("executeQuery".equals(methodName) || "execute".equals(methodName) || "executeUpdate".equals(methodName) || "executeLargeUpdate".equals(methodName)) {
            Integer rowsAffected = result instanceof Number number ? number.intValue() : null;
            capture(sql, beanName, rowsAffected, store, dataPolicy);
        }
    }

    private static void captureStatementExecution(String methodName, String sql, String beanName, Object result,
                                                  DbQueryCaptureStore store,
                                                  DevToolsDataPolicy dataPolicy) {
        if ("executeQuery".equals(methodName) || "execute".equals(methodName) || "executeUpdate".equals(methodName) || "executeLargeUpdate".equals(methodName)) {
            Integer rowsAffected = result instanceof Number number ? number.intValue() : null;
            capture(sql, beanName, rowsAffected, store, dataPolicy);
        }
    }

    private static void capture(String sql, String beanName, Integer rowsAffected, DbQueryCaptureStore store, DevToolsDataPolicy dataPolicy) {
        store.append(new DbQueryDescriptor(
                Instant.now(),
                dataPolicy.sanitizeSql(sql),
                statementType(sql),
                beanName,
                rowsAffected
        ));
    }

    private static String statementType(String sql) {
        String normalized = sql == null ? "" : sql.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return "statement";
        }
        int firstSpace = normalized.indexOf(' ');
        return firstSpace > 0 ? normalized.substring(0, firstSpace) : normalized;
    }
}
