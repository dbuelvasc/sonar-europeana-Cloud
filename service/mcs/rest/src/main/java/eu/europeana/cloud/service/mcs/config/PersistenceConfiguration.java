package eu.europeana.cloud.service.mcs.config;

import eu.europeana.cloud.cassandra.CassandraConnectionProvider;
import eu.europeana.cloud.service.commons.utils.BucketsHandler;
import eu.europeana.cloud.service.mcs.Storage;
import eu.europeana.cloud.service.mcs.persistent.DynamicContentProxy;
import eu.europeana.cloud.service.mcs.persistent.swift.ContentDAO;
import eu.europeana.cloud.service.mcs.persistent.swift.SimpleSwiftConnectionProvider;
import eu.europeana.cloud.service.mcs.persistent.swift.SwiftConnectionProvider;
import eu.europeana.cloud.service.mcs.properties.CassandraProperties;
import eu.europeana.cloud.service.mcs.properties.SwiftProperties;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class PersistenceConfiguration {

  @Autowired
  public PersistenceConfiguration(SwiftProperties swiftProperties) {
    this.swiftProperties = swiftProperties;
  }

  private final SwiftProperties swiftProperties;

  @Bean
  @Qualifier("mcsCassandraConnectionProvider")
  public CassandraConnectionProvider mcsCassandraProvider() {
    return new CassandraConnectionProvider(
        cassandraMCSProperties().getHosts(),
        cassandraMCSProperties().getPort(),
        cassandraMCSProperties().getKeyspace(),
        cassandraMCSProperties().getUser(),
        cassandraMCSProperties().getPassword());
  }

  @Bean
  public SwiftConnectionProvider swiftConnectionProvider() {
    return new SimpleSwiftConnectionProvider(
        swiftProperties.getProvider(),
        swiftProperties.getContainer(),
        swiftProperties.getEndpoint(),
        swiftProperties.getUser(),
        swiftProperties.getPassword());
  }

  @Bean
  public DynamicContentProxy dynamicContentProxy(ContentDAO swiftContentDAO, ContentDAO cassandraContentDAO) {
    Map<Storage, ContentDAO> params = new EnumMap<>(Storage.class);

    params.put(Storage.OBJECT_STORAGE, swiftContentDAO);
    params.put(Storage.DATA_BASE, cassandraContentDAO);

    return new DynamicContentProxy(params);
  }

  @Bean
  public BucketsHandler bucketsHandler() {
    return new BucketsHandler(mcsCassandraProvider().getSession());
  }

  @Bean
  @ConfigurationProperties(prefix = "cassandra.mcs")
  protected CassandraProperties cassandraMCSProperties() {
    return new CassandraProperties();
  }
}
