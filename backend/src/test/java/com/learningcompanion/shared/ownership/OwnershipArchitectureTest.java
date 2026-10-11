package com.learningcompanion.shared.ownership;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.ResolvableType;
import org.springframework.core.type.filter.AssignableTypeFilter;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.ListCrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.QueryByExampleExecutor;

/**
 * Chốt chặn cho mọi chức năng học tập về sau: repository của một {@link OwnedEntity} phải đi qua
 * {@link OwnerScopedRepository} và mọi truy vấn tự khai báo đều phải lọc theo chủ sở hữu.
 */
class OwnershipArchitectureTest {

    @Test
    void everyRepositoryOfLearningDataIsOwnerScoped() throws ClassNotFoundException {
        List<String> violations = new ArrayList<>();
        int owned = 0;
        for (Class<?> repository : repositories()) {
            Class<?> domain = ResolvableType.forClass(repository).as(Repository.class).resolveGeneric(0);
            if (domain == null || !OwnedEntity.class.isAssignableFrom(domain)) {
                continue;
            }
            owned++;
            if (!OwnerScopedRepository.class.isAssignableFrom(repository)) {
                violations.add(repository.getSimpleName() + " phải kế thừa OwnerScopedRepository");
            }
            for (Class<?> unscoped : List.of(CrudRepository.class, ListCrudRepository.class,
                    PagingAndSortingRepository.class, QueryByExampleExecutor.class)) {
                if (unscoped.isAssignableFrom(repository)) {
                    violations.add(repository.getSimpleName() + " không được kế thừa " + unscoped.getSimpleName());
                }
            }
            for (Method method : repository.getDeclaredMethods()) {
                if (!filtersByOwner(method)) {
                    violations.add(repository.getSimpleName() + "." + method.getName() + " không lọc theo ownerId");
                }
            }
        }
        assertThat(owned).as("phải tìm thấy ít nhất repository tài liệu").isPositive();
        assertThat(violations).isEmpty();
    }

    private static boolean filtersByOwner(Method method) {
        if (method.isDefault() || method.isSynthetic()) {
            return false;
        }
        boolean derivedByOwner = method.getName().contains("ByOwnerId") || method.getName().contains("AndOwnerId");
        boolean ownerParameter = false;
        for (Parameter parameter : method.getParameters()) {
            org.springframework.data.repository.query.Param param =
                    parameter.getAnnotation(org.springframework.data.repository.query.Param.class);
            if (param != null && param.value().equals("ownerId")) {
                ownerParameter = true;
            }
        }
        return derivedByOwner && (ownerParameter || method.getAnnotation(
                org.springframework.data.jpa.repository.Query.class) == null);
    }

    private static List<Class<?>> repositories() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false) {
            @Override
            protected boolean isCandidateComponent(AnnotatedBeanDefinition definition) {
                return definition.getMetadata().isInterface();
            }
        };
        scanner.addIncludeFilter(new AssignableTypeFilter(Repository.class));
        List<Class<?>> found = new ArrayList<>();
        for (var definition : scanner.findCandidateComponents("com.learningcompanion")) {
            Class<?> type = Class.forName(definition.getBeanClassName());
            if (type != OwnerScopedRepository.class) {
                found.add(type);
            }
        }
        return found;
    }
}
