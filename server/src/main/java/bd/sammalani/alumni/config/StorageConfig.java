package bd.sammalani.alumni.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class StorageConfig {

    private final AppProperties props;

    @Bean
    MinioClient minioClient() {
        AppProperties.Storage s = props.storage();
        return MinioClient.builder()
                .endpoint(s.endpoint())
                .credentials(s.accessKey(), s.secretKey())
                .build();
    }
}
