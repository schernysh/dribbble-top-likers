package org.cloudgarden.dribbbletoplikers.api;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.GsonBuilder;
import feign.*;
import feign.codec.ErrorDecoder;
import feign.gson.GsonDecoder;
import feign.slf4j.Slf4jLogger;
import org.cloudgarden.dribbbletoplikers.model.FollowerEntry;
import org.cloudgarden.dribbbletoplikers.model.Like;
import org.cloudgarden.dribbbletoplikers.model.Shot;
import org.cloudgarden.dribbbletoplikers.model.User;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static feign.Util.RETRY_AFTER;
import static feign.Util.emptyToNull;
import static java.util.Collections.singletonList;

/**
 * Created by schernysh on 7/7/17.
 */
@Headers("Accept: application/json")
public interface DribbbleApi {

    @RequestLine("GET /users/{userId}")
    User getUser(@Param("userId") String userId);

    @RequestLine("GET /users/{userId}/followers?per_page={pageSize}&page={page}")
    List<FollowerEntry> getFollowers(@Param("userId") String userId, @Param("pageSize") int pageSize, @Param("page") int page);

    @RequestLine("GET /users/{userId}/shots?per_page={pageSize}&page={page}")
    List<Shot> getShots(@Param("userId") String userId, @Param("pageSize") int pageSize, @Param("page") int page);

    @RequestLine("GET /shots/{shotId}/likes?per_page={pageSize}&page={page}")
    List<Like> getLikes(@Param("shotId") Long shotId, @Param("pageSize") int pageSize, @Param("page") int page);

    static DribbbleApi connect(String baseUrl, String token) {
        return Feign.builder()
                .options(new Request.Options(10 * 1000, 10 * 60 * 1000))
                .decoder(new GsonDecoder(new GsonBuilder()
                        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                        .create()))
                .logger(new Slf4jLogger())
                .logLevel(Logger.Level.FULL)
                .requestInterceptor(template -> template.header("Authorization", "Bearer " + token))
                .retryer(new Retryer.Default(100, TimeUnit.SECONDS.toMillis(60), 5))
                .errorDecoder(new ErrorDecoder.Default() {
                    @Override
                    public Exception decode(String methodKey, Response response) {
                        // rate limiting handling
                        // set Retry-After header before delegating to default error decoder to trigger Feign retry mechanism
                        final String dateHeader = response.headers().get("Date").iterator().next();
                        final String rateLimitResetHeader = response.headers().get("X-RateLimit-Reset").iterator().next();
                        if (response.status() == 429 && emptyToNull(dateHeader) != null && emptyToNull(rateLimitResetHeader) != null) {
                            final long rateLimitResetEpochSeconds = Long.parseLong(rateLimitResetHeader);
                            final long serverDateTimeEpochSeconds = ZonedDateTime.parse(dateHeader, DateTimeFormatter.RFC_1123_DATE_TIME).toEpochSecond();
                            final long retryAfterSeconds = rateLimitResetEpochSeconds - serverDateTimeEpochSeconds;

                            final Map<String, Collection<String>> headers = new HashMap<>(response.headers());
                            headers.put(RETRY_AFTER, singletonList(Long.toString(retryAfterSeconds)));
                            response = response.toBuilder().headers(headers).build();
                        }
                        return super.decode(methodKey, response);
                    }
                })
                .target(DribbbleApi.class, baseUrl);
    }
}
