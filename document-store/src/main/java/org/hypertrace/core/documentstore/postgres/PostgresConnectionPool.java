package org.hypertrace.core.documentstore.postgres;

import static java.sql.Connection.TRANSACTION_READ_COMMITTED;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.ConnectionFactory;
import org.apache.commons.dbcp2.DriverManagerConnectionFactory;
import org.apache.commons.dbcp2.PoolableConnection;
import org.apache.commons.dbcp2.PoolableConnectionFactory;
import org.apache.commons.dbcp2.PoolingDataSource;
import org.apache.commons.pool2.impl.AbandonedConfig;
import org.apache.commons.pool2.impl.GenericObjectPool;

class PostgresConnectionPool {
  private static final String VALIDATION_QUERY = "SELECT 1";
  private static final Duration VALIDATION_QUERY_TIMEOUT = Duration.ofSeconds(5);

  private final DataSource dataSource;

  PostgresConnectionPool(final PostgresConfig config) {
    this.dataSource = createPooledDataSource(config);
  }

  public Connection getConnection() throws SQLException {
    return dataSource.getConnection();
  }

  private DataSource createPooledDataSource(final PostgresConfig config) {
    final ConnectionFactory connectionFactory =
        new DriverManagerConnectionFactory(
            config.getConnectionString(), config.getUser(), config.getPassword());
    final PoolableConnectionFactory poolableConnectionFactory =
        new PoolableConnectionFactory(connectionFactory, null);
    final GenericObjectPool<PoolableConnection> connectionPool =
        new GenericObjectPool<>(poolableConnectionFactory);

    final PostgresConnectionPoolConfig poolConfig = config.getConnectionPool();
    setPoolProperties(connectionPool, poolConfig);
    setFactoryProperties(poolableConnectionFactory, connectionPool);

    return new PoolingDataSource<>(connectionPool);
  }

  private void setPoolProperties(
      final GenericObjectPool<PoolableConnection> connectionPool,
      final PostgresConnectionPoolConfig poolConfig) {
    final AbandonedConfig abandonedConfig = getAbandonedConfig(poolConfig);
    final int maxConnections = poolConfig.getMaxConnections();
    connectionPool.setMaxTotal(maxConnections);
    // max idle connections are 20% of max connections
    connectionPool.setMaxIdle(getPercentOf(20, maxConnections));
    // min idle connections are 10% of max connections
    connectionPool.setMinIdle(getPercentOf(10, maxConnections));
    connectionPool.setBlockWhenExhausted(true);
    connectionPool.setMaxWaitMillis(poolConfig.getMaxWaitTime().toMillis());
    connectionPool.setAbandonedConfig(abandonedConfig);
  }

  private void setFactoryProperties(
      PoolableConnectionFactory poolableConnectionFactory,
      GenericObjectPool<PoolableConnection> connectionPool) {
    poolableConnectionFactory.setPool(connectionPool);
    poolableConnectionFactory.setValidationQuery(VALIDATION_QUERY);
    poolableConnectionFactory.setValidationQueryTimeout((int) VALIDATION_QUERY_TIMEOUT.toSeconds());
    poolableConnectionFactory.setDefaultReadOnly(false);
    poolableConnectionFactory.setDefaultAutoCommit(false);
    poolableConnectionFactory.setDefaultTransactionIsolation(TRANSACTION_READ_COMMITTED);
    poolableConnectionFactory.setPoolStatements(false);
  }

  private AbandonedConfig getAbandonedConfig(PostgresConnectionPoolConfig poolConfig) {
    final AbandonedConfig abandonedConfig = new AbandonedConfig();
    abandonedConfig.setLogAbandoned(true);
    abandonedConfig.setRemoveAbandonedOnBorrow(true);
    abandonedConfig.setRequireFullStackTrace(true);
    abandonedConfig.setRemoveAbandonedTimeout(poolConfig.getRemoveAbandonedTimeout());
    return abandonedConfig;
  }

  private int getPercentOf(final int percent, final int maxConnections) {
    final int value = (maxConnections * percent) / 100;
    return Math.max(value, 1);
  }
}
