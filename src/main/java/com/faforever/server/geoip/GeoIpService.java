package com.faforever.server.geoip;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import io.micrometer.core.annotation.Timed;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;

@Slf4j
@Singleton
public class GeoIpService {

    private final @Nullable DatabaseReader databaseReader;

    public GeoIpService(@ConfigProperty(name = "geo-ip.database-path") Path geoIpDatabasePath) {
        DatabaseReader possibleDatabaseReader;
        try {
            if (Files.exists(geoIpDatabasePath)) {
                possibleDatabaseReader = new DatabaseReader.Builder(geoIpDatabasePath.toFile()).withCache(
                        new CHMCache()).build();
            } else {
                LOG.warn("No geoip database exists at {}", geoIpDatabasePath);
                possibleDatabaseReader = null;
            }
        } catch (IOException exception) {
            LOG.warn("Unable to read geoip database", exception);
            possibleDatabaseReader = null;
        }
        databaseReader = possibleDatabaseReader;
    }

    @Timed("faf.country.lookup")
    public String findCountry(String ipAddress) {
        if (databaseReader == null || ipAddress.isBlank()) {
            return "";
        }

        InetAddress inetAddress;
        try {
            inetAddress = InetAddress.ofLiteral(ipAddress);
        } catch (IllegalArgumentException exception) {
            LOG.warn("Invalid ip address used for country lookup: {}", ipAddress);
            return "";
        }

        CountryResponse country;
        try {
            country = databaseReader.country(inetAddress);
        } catch (IOException | GeoIp2Exception e) {
            LOG.warn("Unable to identify country for ip address {}", ipAddress, e);
            return "";
        }

        if (country == null || country.getCountry() == null || country.getCountry().getIsoCode() == null) {
            LOG.warn("Unable to identify country for ip address {}", ipAddress);
            return "";
        }

        String countryCode = country.getCountry().getIsoCode();
        LOG.info("Identified country as {}", countryCode);
        return countryCode;
    }
}
