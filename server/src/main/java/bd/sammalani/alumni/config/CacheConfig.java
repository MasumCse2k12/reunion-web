package bd.sammalani.alumni.config;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import bd.sammalani.alumni.admin.AdminDtos;
import bd.sammalani.alumni.admin.AdminSession;
import bd.sammalani.alumni.domain.batch.BatchService;
import bd.sammalani.alumni.domain.event.EventService;
import bd.sammalani.alumni.domain.notice.NoticeDto;
import bd.sammalani.alumni.domain.registration.RegistrationDtos;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;

/**
 * Redis-backed caches, each with the TTL its data deserves rather than one
 * global number.
 * <p>
 * What is cached is what is read constantly and changes slowly: the batch
 * coverage counters (every landing page hit), the reunion and its ticket prices
 * (every registration screen), and an admin's batch scope (every single admin
 * request). What is deliberately <em>not</em> cached is the review queue itself —
 * a coordinator must never be shown a row that someone else decided thirty
 * seconds ago.
 * <p>
 * <strong>Every cache declares its exact value type.</strong> A cache entry is
 * read back as bare bytes with no context, so a {@code List<BatchDto>} stored as
 * plain JSON returns a list of maps and fails on the cast — the classic silent
 * trap of caching a generic type. The alternative, polymorphic default typing,
 * writes Java class names into Redis and instantiates whatever it reads back:
 * that turns write access to Redis into code execution in this JVM. Naming the
 * type per cache costs a line each and needs neither.
 */
@Configuration
public class CacheConfig {

    public static final String BATCH_COVERAGE = "batchCoverage";
    public static final String BATCH_TOTALS = "batchTotals";
    public static final String EVENT_BY_SLUG = "eventBySlug";
    public static final String ADMIN_SCOPE = "adminScope";
    public static final String ADMIN_STATS = "adminStats";
    public static final String NOTICES = "notices";
    public static final String COORDINATORS = "coordinators";

    /** Shares Boot's Jackson defaults, including java.time handling. */
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .prefixCacheNameWith("sammalani:")
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()));

        Map<String, RedisCacheConfiguration> caches = new HashMap<>();
        // Coverage moves whenever someone claims a name, and the landing page is
        // the most-hit route on the site.
        caches.put(BATCH_COVERAGE, typed(base, Duration.ofMinutes(5),
                listOf(BatchService.BatchDto.class)));
        caches.put(BATCH_TOTALS, typed(base, Duration.ofMinutes(5),
                type(BatchService.Totals.class)));
        // Ticket prices change when the committee meets, which is not often.
        caches.put(EVENT_BY_SLUG, typed(base, Duration.ofHours(1),
                type(EventService.EventDto.class)));
        // Read on every admin request; evicted by name the moment a scope is edited.
        caches.put(ADMIN_SCOPE, typed(base, Duration.ofMinutes(15),
                type(AdminSession.class)));
        // A dashboard tile. A minute stale is invisible; recomputing six
        // aggregates on every page load is not.
        caches.put(ADMIN_STATS, typed(base, Duration.ofMinutes(1),
                type(AdminDtos.AdminStatsDto.class)));
        caches.put(NOTICES, typed(base, Duration.ofMinutes(10),
                listOf(NoticeDto.class)));
        caches.put(COORDINATORS, typed(base, Duration.ofMinutes(30),
                listOf(RegistrationDtos.CoordinatorDto.class)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(caches)
                // Entries are only written once the surrounding transaction
                // commits, so a rolled-back decision cannot leave a stale count
                // cached as though it had happened.
                .transactionAware()
                .build();
    }

    private static RedisCacheConfiguration typed(RedisCacheConfiguration base, Duration ttl, JavaType valueType) {
        return base.entryTtl(ttl).serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new JacksonJsonRedisSerializer<>(MAPPER, valueType)));
    }

    private static JavaType type(Class<?> valueType) {
        return MAPPER.constructType(valueType);
    }

    private static JavaType listOf(Class<?> elementType) {
        return MAPPER.getTypeFactory().constructCollectionType(java.util.List.class, elementType);
    }
}
