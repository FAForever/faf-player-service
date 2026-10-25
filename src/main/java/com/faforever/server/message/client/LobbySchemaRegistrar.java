package com.faforever.server.message.client;

import com.faforever.server.message.service.LobbySchemaDetails;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.victools.jsonschema.generator.Option;
import com.github.victools.jsonschema.generator.OptionPreset;
import com.github.victools.jsonschema.generator.SchemaGenerator;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfig;
import com.github.victools.jsonschema.generator.SchemaGeneratorConfigBuilder;
import com.github.victools.jsonschema.generator.SchemaKeyword;
import com.github.victools.jsonschema.generator.SchemaVersion;
import com.github.victools.jsonschema.module.jackson.JacksonOption;
import com.github.victools.jsonschema.module.jackson.JacksonSchemaModule;
import io.quarkus.runtime.Startup;
import io.smallrye.reactive.messaging.rabbitmq.IncomingRabbitMQMetadata;
import io.smallrye.reactive.messaging.rabbitmq.OutgoingRabbitMQMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Emitter;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.Metadata;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.node.ObjectNode;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.stream.Collectors;

@ApplicationScoped
public class LobbySchemaRegistrar {

    private static final JacksonSchemaModule JACKSON_MODULE = new JacksonSchemaModule(
            JacksonOption.FLATTENED_ENUMS_FROM_JSONPROPERTY, JacksonOption.INCLUDE_ONLY_JSONPROPERTY_ANNOTATED_METHODS);
    private static final PropertyNamingStrategies.SnakeCaseStrategy SNAKE_CASE_STRATEGY = new PropertyNamingStrategies.SnakeCaseStrategy();

    private final Emitter<LobbySchemaDetails> schemaDetailsEmitter;

    private final Set<LobbySchemaDetails> lobbySchemaDetails = new HashSet<>();

    public LobbySchemaRegistrar(
            @Channel("lobby-schema-registration") Emitter<LobbySchemaDetails> schemaDetailsEmitter) {
        this.schemaDetailsEmitter = schemaDetailsEmitter;
    }

    @Startup
    void registerSchemasAndSendSchemas() {
        registerSchemasUsingTypeInfo(SocialMessage.Client.class, LobbySchemaDetails.Direction.INBOUND, "social");
        registerSchemasUsingTypeInfo(AdminMessage.Client.class, LobbySchemaDetails.Direction.INBOUND, "admin");
        registerSchemasUsingTypeInfo(ConnectionMessage.Server.class, LobbySchemaDetails.Direction.OUTBOUND,
                                     "connection");
        registerSchemasUsingTypeInfo(SocialMessage.Server.class, LobbySchemaDetails.Direction.OUTBOUND, "social");
        registerSchemasUsingTypeInfo(AdminMessage.Server.class, LobbySchemaDetails.Direction.OUTBOUND, "admin");
        sendSchemaDetails(Metadata.of());
    }

    @Incoming("lobby-schema-request")
    CompletionStage<Void> handleSchemaRequest(Message<Map<String, String>> message) {
        Metadata metadata = message.getMetadata(IncomingRabbitMQMetadata.class).map(
                IncomingRabbitMQMetadata::getReplyTo).map(replyTo -> OutgoingRabbitMQMetadata.builder().withRoutingKey(
                replyTo).withExchange("").build()).map(Metadata::of).orElse(Metadata.of());
        sendSchemaDetails(metadata);
        return message.ack();
    }

    private void registerSchemasUsingTypeInfo(Class<?> baseMessageClass, LobbySchemaDetails.Direction direction,
                                              String route) {
        JsonTypeInfo typeInfo = baseMessageClass.getAnnotation(JsonTypeInfo.class);
        if (typeInfo == null) {
            throw new IllegalArgumentException("No JsonTypeInfo");
        }
        JsonSubTypes jsonSubTypes = baseMessageClass.getAnnotation(JsonSubTypes.class);
        if (jsonSubTypes == null) {
            throw new IllegalArgumentException("No JsonSubTypes");
        }
        Map<? extends Class<?>, String> identifierMap = Arrays.stream(jsonSubTypes.value()).collect(
                Collectors.toMap(JsonSubTypes.Type::value, JsonSubTypes.Type::name));
        String propertyName = typeInfo.property();

        SchemaGeneratorConfigBuilder builder = new SchemaGeneratorConfigBuilder(SchemaVersion.DRAFT_2020_12,
                                                                                OptionPreset.PLAIN_JSON).with(
                JACKSON_MODULE).with(Option.STANDARD_FORMATS, Option.ADDITIONAL_FIXED_TYPES,
                                     Option.FORBIDDEN_ADDITIONAL_PROPERTIES_BY_DEFAULT,
                                     Option.FIELDS_DERIVED_FROM_ARGUMENTFREE_METHODS);


        builder.forFields()
               .withPropertyNameOverrideResolver(
                       fieldScope -> SNAKE_CASE_STRATEGY.nameForField(null, null, fieldScope.getName()))
               .withNullableCheck(
                       fieldScope -> fieldScope.getRawMember().getAnnotatedType().getAnnotation(Nullable.class) != null)
               .withRequiredCheck(fieldScope -> fieldScope.getRawMember()
                                                          .getAnnotatedType()
                                                          .getAnnotation(Nullable.class) == null);

        builder.forTypesInGeneral().withTypeAttributeOverride((objectNode, typeScope, context) -> {
            String command = identifierMap.get(typeScope.getType().getErasedType());
            if (command == null) {
                return;
            }

            objectNode.withObjectProperty(context.getKeyword(SchemaKeyword.TAG_PROPERTIES)).putObject(propertyName).put(
                    context.getKeyword(SchemaKeyword.TAG_CONST), command);
            objectNode.withArrayProperty(context.getKeyword(SchemaKeyword.TAG_REQUIRED)).add(propertyName);
        });

        SchemaGeneratorConfig config = builder.build();
        SchemaGenerator generator = new SchemaGenerator(config);

        identifierMap.forEach((messageClass, command) -> {
            ObjectNode schemaNode = generator.generateSchema(messageClass);
            lobbySchemaDetails.add(new LobbySchemaDetails(command, direction, schemaNode, route));
        });
    }

    private void sendSchemaDetails(Metadata metadata) {
        lobbySchemaDetails.forEach(
                lobbySchemaDetails -> schemaDetailsEmitter.send(Message.of(lobbySchemaDetails, metadata)));
    }
}
