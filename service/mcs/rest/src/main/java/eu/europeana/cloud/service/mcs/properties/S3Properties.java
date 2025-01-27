package eu.europeana.cloud.service.mcs.properties;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class S3Properties {
  private String provider;
  private String container;
  private String endpoint;
  private String user;
  private String password;
}
