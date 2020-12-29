package com.purbon.kafka.topology.actions.topics;

import com.purbon.kafka.topology.actions.BaseAction;
import com.purbon.kafka.topology.api.adminclient.TopologyBuilderAdminClient;
import com.purbon.kafka.topology.model.Topic;
import com.purbon.kafka.topology.model.TopicSchemas;
import com.purbon.kafka.topology.schemas.SchemaRegistryManager;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class SyncTopicAction extends BaseAction {

  private static final Logger LOGGER = LogManager.getLogger(SyncTopicAction.class);

  private final Topic topic;
  private final String fullTopicName;
  private final Set<String> listOfTopics;
  private final TopologyBuilderAdminClient adminClient;
  private final SchemaRegistryManager schemaRegistryManager;

  public SyncTopicAction(
      TopologyBuilderAdminClient adminClient,
      SchemaRegistryManager schemaRegistryManager,
      Topic topic,
      String fullTopicName,
      Set<String> listOfTopics) {
    this.topic = topic;
    this.fullTopicName = fullTopicName;
    this.listOfTopics = listOfTopics;
    this.adminClient = adminClient;
    this.schemaRegistryManager = schemaRegistryManager;
  }

  @Override
  public void run() throws IOException {
    syncTopic(topic, fullTopicName, listOfTopics);
  }

  public void syncTopic(Topic topic, String fullTopicName, Set<String> listOfTopics)
      throws IOException {
    LOGGER.debug(String.format("Sync topic %s", fullTopicName));
    if (existTopic(fullTopicName, listOfTopics)) {
      if (topic.partitionsCount() > adminClient.getPartitionCount(fullTopicName)) {
        LOGGER.debug(String.format("Update partition count of topic %s", fullTopicName));
        adminClient.updatePartitionCount(topic, fullTopicName);
      }
      adminClient.updateTopicConfig(topic, fullTopicName);
    } else {
      LOGGER.debug(String.format("Create new topic with name %s", fullTopicName));
      adminClient.createTopic(topic, fullTopicName);
    }

    for (TopicSchemas schema : topic.getSchemas()) {
      schema
          .getKeySchemaFile()
          .ifPresent(
              keySchemaFile ->
                  schemaRegistryManager.register(composeKeySubjectName(topic), keySchemaFile));

      schema
          .getValueSchemaFile()
          .ifPresent(
              valueSchemaFile ->
                  schemaRegistryManager.register(composeValueSubjectName(topic), valueSchemaFile));
    }
  }

  private String composeKeySubjectName(Topic topic) {
    return composeSubjectName(topic, "key");
  }

  private String composeValueSubjectName(Topic topic) {
    return composeSubjectName(topic, "value");
  }

  private String composeSubjectName(Topic topic, String type) {
    switch (topic.getSubjectNameStrategyString()) {
      case "TopicNameStrategy":
        return fullTopicName + "-" + type;
      case "RecordNameStrategy":
        return "record-type";
      case "TopicRecordNameStrategy":
        return fullTopicName + "record-type";
      default:
        return "";
    }
  }

  private boolean existTopic(String topic, Set<String> listOfTopics) {
    return listOfTopics.contains(topic);
  }

  @Override
  protected Map<String, Object> props() {
    Map<String, Object> map = new HashMap<>();
    map.put("Operation", getClass().getName());
    map.put("Topic", fullTopicName);
    String actionName = existTopic(fullTopicName, listOfTopics) ? "update" : "create";
    map.put("Action", actionName);
    return map;
  }
}
