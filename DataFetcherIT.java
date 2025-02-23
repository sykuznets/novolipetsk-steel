package com.example.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import com.example.security.api.UserInfo;
import com.example.integration.data.GenericRepositoryData;
import com.example.integration.util.MockDataUtils;
import com.example.integration.factory.MockSecurityContext;
import com.example.integration.mapper.GenericMapper;
import com.example.types.GenericObject;
import java.util.Collection;
import java.util.List;
import org.intellij.lang.annotations.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Autowired;

class DataFetcherIT extends AbstractFetcherIT implements MockSecurityContext {

    @Autowired
    private GenericRepositoryData repositoryData;
    @Autowired
    private GenericMapper mapper;

    private GenericObject genericObject;

    @BeforeEach
    void setUp() {
        MockDataUtils.initializeData(repositoryData);
        genericObject = mapper.map(repositoryData.getObject());
    }

    @TestFactory
    Collection<DynamicTest> shouldReturnValidObject() {
        @Language("GraphQL")
        var query = """
                query FetchObject {
                    fetchObject(id: "1", timeZoneOffsetInMinutes: 0) {
                        id,
                        source,
                        externalSourceUrl,
                        content,
                        pictureUrl,
                        title,
                        published,
                        viewed
                    }
                }
        """;

        return List.of(dynamicTest(
                "Request object should return a valid result",
                () -> validateObject(query, genericObject)
        ));
    }

    private void validateObject(
            @Language("GraphQL") String query,
            GenericObject expectedObject
    ) {
        proxySecurityContext(userInfo -> {
            final var actualResult = queryExecutor.executeAndExtractJsonPath(
                    query, "data.fetchObject"
            );

            validateObjectFields(actualResult, expectedObject);
        }, UserInfo.builder()
                .profileId(1L)
                .build());
    }

    // Validates fields of the GenericObject against the expected object
    private void validateObjectFields(Object result, GenericObject expectedObject) {
        assertThat(result)
                .extracting(
                        "title",
                        "viewed",
                        "pictureUrl"
                )
                .as("Object fields validation")
                .containsExactly(
                        expectedObject.getTitle(),
                        expectedObject.getViewed(),
                        "https://s3.example.com/" + expectedObject.getPictureUrl()
                );
    }
}
