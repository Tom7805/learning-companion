package com.learningcompanion.shared.config;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.ResourceProvider;
import org.flywaydb.core.api.resource.LoadableResource;
import org.springframework.boot.flyway.autoconfigure.FlywayConfigurationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Bộ khung dự án tạo sẵn tệp migration rỗng cho các story sau. Nếu Flyway áp dụng tệp rỗng,
 * khi story đó điền nội dung thì checksum lệch và ứng dụng không khởi động được.
 * Vì vậy tệp rỗng được coi như chưa tồn tại cho tới khi có nội dung.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayConfigurationCustomizer skipEmptyMigrations() {
        return configuration -> configuration.resourceProvider(
                new NonEmptyClasspathResourceProvider(Arrays.asList(configuration.getLocations())));
    }

    static final class NonEmptyClasspathResourceProvider implements ResourceProvider {

        private final List<LoadableResource> resources;

        NonEmptyClasspathResourceProvider(List<Location> locations) {
            this.resources = scan(locations);
        }

        @Override
        public LoadableResource getResource(String name) {
            return resources.stream()
                    .filter(resource -> resource.getRelativePath().equals(name))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public Collection<LoadableResource> getResources(String prefix, String[] suffixes) {
            return resources.stream()
                    .filter(resource -> resource.getFilename().startsWith(prefix))
                    .filter(resource -> Arrays.stream(suffixes).anyMatch(resource.getFilename()::endsWith))
                    .toList();
        }

        private static List<LoadableResource> scan(List<Location> locations) {
            PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
            List<LoadableResource> found = new ArrayList<>();
            for (Location location : locations) {
                String root = location.getRootPath();
                try {
                    for (Resource resource : resolver.getResources("classpath*:" + root + "/**/*.sql")) {
                        if (resource.isReadable() && resource.contentLength() > 0) {
                            found.add(new SpringLoadableResource(resource, root));
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException("Cannot scan migrations in " + root, e);
                }
            }
            return List.copyOf(found);
        }
    }

    private static final class SpringLoadableResource extends LoadableResource {

        private final Resource resource;
        private final String relativePath;
        private final String absolutePath;

        SpringLoadableResource(Resource resource, String root) throws IOException {
            this.resource = resource;
            String url = resource.getURL().toString();
            int rootIndex = url.lastIndexOf(root + "/");
            this.relativePath = url.substring(rootIndex + root.length() + 1);
            this.absolutePath = root + "/" + relativePath;
        }

        @Override
        public Reader read() {
            try {
                return new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public String getAbsolutePath() {
            return absolutePath;
        }

        @Override
        public String getAbsolutePathOnDisk() {
            try {
                return resource.getFile().getAbsolutePath();
            } catch (IOException e) {
                return absolutePath;
            }
        }

        @Override
        public String getFilename() {
            return resource.getFilename();
        }

        @Override
        public String getRelativePath() {
            return relativePath;
        }
    }
}
