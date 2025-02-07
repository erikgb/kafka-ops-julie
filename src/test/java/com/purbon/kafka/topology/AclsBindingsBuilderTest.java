package com.purbon.kafka.topology;

import static com.purbon.kafka.topology.Constants.CONNECTOR_ALLOW_TOPIC_CREATE;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;

import com.purbon.kafka.topology.api.adminclient.AclBuilder;
import com.purbon.kafka.topology.model.DynamicUser;
import com.purbon.kafka.topology.model.User;
import com.purbon.kafka.topology.model.users.Connector;
import com.purbon.kafka.topology.model.users.Consumer;
import com.purbon.kafka.topology.model.users.KStream;
import com.purbon.kafka.topology.model.users.Producer;
import com.purbon.kafka.topology.roles.TopologyAclBinding;
import com.purbon.kafka.topology.roles.acls.AclsBindingsBuilder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import org.apache.kafka.common.acl.AccessControlEntry;
import org.apache.kafka.common.acl.AclBinding;
import org.apache.kafka.common.acl.AclOperation;
import org.apache.kafka.common.acl.AclPermissionType;
import org.apache.kafka.common.resource.PatternType;
import org.apache.kafka.common.resource.ResourcePattern;
import org.apache.kafka.common.resource.ResourceType;
import org.junit.Before;
import org.junit.Test;

public class AclsBindingsBuilderTest {

  Configuration config;
  AclsBindingsBuilder builder;

  @Before
  public void before() {
    config = new Configuration();
    builder = new AclsBindingsBuilder(config);
  }

  @Test
  public void testConsumerAclsBuilder() {

    Consumer consumer = new Consumer("User:foo");

    List<TopologyAclBinding> aclBindings =
        builder.buildLiteralBindingsForConsumers(Collections.singleton(consumer), "bar");
    assertThat(aclBindings.size()).isEqualTo(3);
    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.READ));
    assertThat(aclBindings)
        .contains(
            buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.DESCRIBE));
    assertThat(aclBindings)
        .contains(buildGroupLevelAcl("User:foo", "*", PatternType.LITERAL, AclOperation.READ));
  }

  @Test
  public void testConsumerAclsBuilderWithGroupPrefix() {

    Consumer consumer = new Consumer("User:foo", "foo*");

    List<TopologyAclBinding> aclBindings =
        builder.buildLiteralBindingsForConsumers(Collections.singleton(consumer), "bar");
    assertThat(aclBindings.size()).isEqualTo(3);
    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.READ));
    assertThat(aclBindings)
        .contains(
            buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.DESCRIBE));
    assertThat(aclBindings)
        .contains(buildGroupLevelAcl("User:foo", "foo", PatternType.PREFIXED, AclOperation.READ));
  }

  @Test
  public void testProducerAclsBuilder() {
    Producer producer = new Producer("User:foo");
    List<TopologyAclBinding> aclBindings =
        builder.buildLiteralBindingsForProducers(Collections.singleton(producer), "bar");
    assertThat(aclBindings.size()).isEqualTo(2);
    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.WRITE));
    assertThat(aclBindings)
        .contains(
            buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.DESCRIBE));
  }

  @Test
  public void testStreamsWithTxIdAclsBuilder() {
    HashMap<String, List<String>> topics = new HashMap<>();
    topics.put(KStream.READ_TOPICS, singletonList("bar"));
    topics.put(KStream.WRITE_TOPICS, emptyList());
    KStream producer =
        new KStream("User:foo", topics, emptyList(), Optional.of("app1"), Optional.of(true));
    List<TopologyAclBinding> aclBindings = builder.buildBindingsForKStream(producer, "app1");
    assertThat(aclBindings.size()).isEqualTo(5);

    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.READ));

    assertThat(aclBindings)
        .contains(
            buildTransactionIdLevelAcl(
                producer.getPrincipal(),
                producer.getApplicationId().get(),
                PatternType.PREFIXED,
                AclOperation.DESCRIBE));

    assertThat(aclBindings)
        .contains(
            buildTransactionIdLevelAcl(
                producer.getPrincipal(),
                producer.getApplicationId().get(),
                PatternType.PREFIXED,
                AclOperation.WRITE));
  }

  @Test
  public void testProducerWithTxIdAclsBuilder() {
    Producer producer = new Producer("User:foo", "1234", true);
    List<TopologyAclBinding> aclBindings =
        builder.buildLiteralBindingsForProducers(Collections.singleton(producer), "bar");
    assertThat(aclBindings.size()).isEqualTo(5);

    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.WRITE));
    assertThat(aclBindings)
        .contains(
            buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.DESCRIBE));

    assertThat(aclBindings)
        .contains(
            buildTransactionIdLevelAcl(
                producer.getPrincipal(),
                producer.getTransactionId().get(),
                PatternType.LITERAL,
                AclOperation.DESCRIBE));

    assertThat(aclBindings)
        .contains(
            buildTransactionIdLevelAcl(
                producer.getPrincipal(),
                producer.getTransactionId().get(),
                PatternType.LITERAL,
                AclOperation.WRITE));

    assertThat(aclBindings)
        .contains(buildClusterLevelAcl(producer.getPrincipal(), AclOperation.IDEMPOTENT_WRITE));
  }

  @Test
  public void testProducerWithTxIdPrefixAclsBuilder() {
    Producer producer = new Producer("User:foo", "foo*", true);
    List<TopologyAclBinding> aclBindings =
        builder.buildLiteralBindingsForProducers(Collections.singleton(producer), "bar");
    assertThat(aclBindings.size()).isEqualTo(5);

    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.WRITE));
    assertThat(aclBindings)
        .contains(
            buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.DESCRIBE));

    assertThat(aclBindings)
        .contains(
            buildTransactionIdLevelAcl(
                "User:foo", "foo", PatternType.PREFIXED, AclOperation.DESCRIBE));

    assertThat(aclBindings)
        .contains(
            buildTransactionIdLevelAcl(
                "User:foo", "foo", PatternType.PREFIXED, AclOperation.WRITE));

    assertThat(aclBindings)
        .contains(buildClusterLevelAcl(producer.getPrincipal(), AclOperation.IDEMPOTENT_WRITE));
  }

  @Test
  public void testIdempotenceProducerAclsBuilder() {
    Producer producer = new Producer("User:foo", null, true);
    List<TopologyAclBinding> aclBindings =
        builder.buildLiteralBindingsForProducers(Collections.singleton(producer), "bar");
    assertThat(aclBindings.size()).isEqualTo(3);

    assertThat(aclBindings)
        .contains(buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.WRITE));
    assertThat(aclBindings)
        .contains(
            buildTopicLevelAcl("User:foo", "bar", PatternType.LITERAL, AclOperation.DESCRIBE));

    assertThat(aclBindings)
        .contains(buildClusterLevelAcl(producer.getPrincipal(), AclOperation.IDEMPOTENT_WRITE));
  }

  @Test
  public void testSourceConnectorAcls() {
    Connector connector = new Connector("User:foo");
    HashMap<String, List<String>> topicsMap = new HashMap<>();
    String connectorWriteTopic = "topicA";
    topicsMap.put(DynamicUser.WRITE_TOPICS, singletonList(connectorWriteTopic));
    connector.setTopics(topicsMap);

    List<TopologyAclBinding> bindings = builder.buildBindingsForConnect(connector, "-");

    List<TopologyAclBinding> shouldContainBindings = createConnectorDefaultAclBindings(connector);
    shouldContainBindings.add(buildClusterLevelAcl(connector.getPrincipal(), AclOperation.CREATE));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connectorWriteTopic,
            PatternType.LITERAL,
            AclOperation.WRITE));

    assertThat(bindings).containsExactlyInAnyOrderElementsOf(shouldContainBindings);
  }

  @Test
  public void testSinkConnectorAcls() {
    Connector connector = new Connector("User:foo");
    String connectorReadTopic = "topicA";
    connector.setTopics(singletonMap(DynamicUser.READ_TOPICS, singletonList(connectorReadTopic)));

    List<TopologyAclBinding> bindings = builder.buildBindingsForConnect(connector, "-");

    List<TopologyAclBinding> shouldContainBindings = createConnectorDefaultAclBindings(connector);
    shouldContainBindings.add(buildClusterLevelAcl(connector.getPrincipal(), AclOperation.CREATE));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(), connectorReadTopic, PatternType.LITERAL, AclOperation.READ));

    assertThat(bindings).containsExactlyInAnyOrderElementsOf(shouldContainBindings);
  }

  @Test
  public void testConnectorAclsWithNoClusterCreate() {
    Properties configMap = config.asProperties();
    configMap.put(CONNECTOR_ALLOW_TOPIC_CREATE, false);
    builder = new AclsBindingsBuilder(new Configuration(emptyMap(), configMap));

    Connector connector = new Connector("User:foo");
    HashMap<String, List<String>> topicsMap = new HashMap<>();
    String connectorWriteTopic = "topicA";
    topicsMap.put(DynamicUser.WRITE_TOPICS, singletonList(connectorWriteTopic));
    connector.setTopics(topicsMap);

    List<TopologyAclBinding> bindings = builder.buildBindingsForConnect(connector, "-");

    List<TopologyAclBinding> shouldContainBindings = createConnectorDefaultAclBindings(connector);
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connectorWriteTopic,
            PatternType.LITERAL,
            AclOperation.WRITE));

    assertThat(bindings).containsExactlyInAnyOrderElementsOf(shouldContainBindings);
  }

  private List<TopologyAclBinding> createConnectorDefaultAclBindings(Connector connector) {
    List<TopologyAclBinding> shouldContainBindings = new ArrayList<>();
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connector.statusTopicString(),
            PatternType.LITERAL,
            AclOperation.READ));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connector.offsetTopicString(),
            PatternType.LITERAL,
            AclOperation.READ));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connector.configsTopicString(),
            PatternType.LITERAL,
            AclOperation.READ));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connector.statusTopicString(),
            PatternType.LITERAL,
            AclOperation.WRITE));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connector.offsetTopicString(),
            PatternType.LITERAL,
            AclOperation.WRITE));
    shouldContainBindings.add(
        buildTopicLevelAcl(
            connector.getPrincipal(),
            connector.configsTopicString(),
            PatternType.LITERAL,
            AclOperation.WRITE));
    shouldContainBindings.add(
        buildGroupLevelAcl(
            connector.getPrincipal(),
            connector.groupString(),
            PatternType.LITERAL,
            AclOperation.READ));
    return shouldContainBindings;
  }

  @Test
  public void testStreamsAclsBuilder() {
    Map<String, List<String>> topics = new HashMap<>();
    topics.put(KStream.READ_TOPICS, singletonList("foo"));
    topics.put(KStream.WRITE_TOPICS, singletonList("bar"));
    KStream stream =
        new KStream("User:foo", topics, List.of(new User("observer1"), new User("observer2")));

    List<TopologyAclBinding> bindings = builder.buildBindingsForKStream(stream, "prefix");

    assertThat(bindings.size()).isEqualTo(10);
    assertThat(bindings)
        .contains(
            buildTopicLevelAcl(
                stream.getPrincipal(), "foo", PatternType.LITERAL, AclOperation.READ));
    assertThat(bindings)
        .contains(
            buildTopicLevelAcl(
                stream.getPrincipal(), "bar", PatternType.LITERAL, AclOperation.WRITE));
    assertThat(bindings)
        .contains(
            buildGroupLevelAcl(
                stream.getPrincipal(), "prefix", PatternType.PREFIXED, AclOperation.READ));
    assertThat(bindings)
        .contains(
            buildTopicLevelAcl(
                stream.getPrincipal(), "prefix", PatternType.PREFIXED, AclOperation.ALL));
    for (User observer : stream.getObserverPrincipals()) {
      assertThat(bindings)
          .contains(
              buildGroupLevelAcl(
                  observer.getPrincipal(), "prefix", PatternType.PREFIXED, AclOperation.READ));
      assertThat(bindings)
          .contains(
              buildTopicLevelAcl(
                  observer.getPrincipal(), "prefix", PatternType.PREFIXED, AclOperation.READ));
      assertThat(bindings)
          .contains(
              buildTopicLevelAcl(
                  observer.getPrincipal(), "prefix", PatternType.PREFIXED, AclOperation.DESCRIBE));
    }
  }

  private TopologyAclBinding buildTopicLevelAcl(
      String principal, String topic, PatternType patternType, AclOperation op) {
    return new TopologyAclBinding(
        new AclBuilder(principal)
            .resource(ResourceType.TOPIC, topic, patternType)
            .allow(op)
            .build());
  }

  private TopologyAclBinding buildTransactionIdLevelAcl(
      String principal,
      String transactionId,
      @SuppressWarnings("SameParameterValue") PatternType patternType,
      AclOperation op) {
    return new TopologyAclBinding(
        new AclBuilder(principal)
            .resource(ResourceType.TRANSACTIONAL_ID, transactionId, patternType)
            .allow(op)
            .build());
  }

  private TopologyAclBinding buildClusterLevelAcl(String principal, AclOperation op) {
    ResourcePattern resourcePattern =
        new ResourcePattern(ResourceType.CLUSTER, "kafka-cluster", PatternType.LITERAL);
    AccessControlEntry entry = new AccessControlEntry(principal, "*", op, AclPermissionType.ALLOW);
    return new TopologyAclBinding(new AclBinding(resourcePattern, entry));
  }

  private TopologyAclBinding buildGroupLevelAcl(
      String principal,
      String group,
      PatternType patternType,
      @SuppressWarnings("SameParameterValue") AclOperation op) {
    return new TopologyAclBinding(
        new AclBuilder(principal)
            .resource(ResourceType.GROUP, group, patternType)
            .allow(op)
            .build());
  }
}
