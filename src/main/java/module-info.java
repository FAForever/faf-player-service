import org.jspecify.annotations.NullMarked;

@NullMarked
module com.faforever.server.player {
    requires com.fasterxml.jackson.annotation;
    requires com.github.victools.jsonschema.generator;
    requires com.github.victools.jsonschema.module.jackson;
    requires io.quarkus.core;
    requires io.smallrye.common.annotation;
    requires io.vertx.core;
    requires jakarta.annotation;
    requires jakarta.cdi;
    requires jakarta.inject;
    requires jakarta.interceptor;
    requires jakarta.persistence;
    requires jakarta.transaction;
    requires micrometer.core;
    requires org.eclipse.microprofile.config;
    requires org.hibernate.orm.core;
    requires org.jspecify;
    requires org.slf4j;
    requires quarkus.scheduler.api;
    requires smallrye.reactive.messaging.api;
    requires smallrye.reactive.messaging.rabbitmq;
    requires tools.jackson.databind;
    requires com.fasterxml.classmate;
    requires micrometer.commons;
    requires io.smallrye.config;
    requires com.maxmind.geoip2;
    requires com.maxmind.db;
    requires jakarta.data;
    requires static lombok;
}