package eu.europeana.cloud.service.dps.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TopologiesTopicsParser {

  private static final Logger LOGGER = LoggerFactory.getLogger(TopologiesTopicsParser.class);

  /**
   * INPUT_VALUE_REGEX is used to parse strings that are passed from configuration files, so there is no possibility of strings
   * being long enough to cause stack overflow
   */
  @SuppressWarnings("java:S5998")
  private static final String INPUT_VALUE_REGEX = "(\\w+:([\\w\\-]*,?)+;?)+";

  public Map<String, List<String>> parse(String topicsList) {
    if (isInputValid(topicsList)) {
      List<String> topologies = extractTopologies(topicsList);
      return extractTopics(topologies);
    } else {
      throw new RuntimeException("Topics list is not valid");
    }
  }

  private boolean isInputValid(String inputTopicsList) {
    return inputTopicsList.matches(INPUT_VALUE_REGEX);
  }

  private List<String> extractTopologies(String inputTopicsList) {
    List<String> topologies = new ArrayList<>();
    try (Scanner scanner = new Scanner(inputTopicsList).useDelimiter(";")) {
      while (scanner.hasNext()) {
        topologies.add(scanner.next());
      }
      return topologies;
    }
  }

  private Map<String, List<String>> extractTopics(List<String> topologies) {
    Map<String, List<String>> resultsMap = new HashMap<>();

    for (String topology : topologies) {
      try (Scanner scanner = new Scanner(topology).useDelimiter(":")) {
        String topologyName = scanner.next();
        resultsMap.put(topologyName, Collections.emptyList());
        while (scanner.hasNext()) {
          resultsMap.put(topologyName, Arrays.asList(scanner.next().split(",")));
        }
      }
    }
    return resultsMap;
  }
}
