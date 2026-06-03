package io.quarkiverse.flow.runner.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.ws.rs.NotSupportedException;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.serverlessworkflow.api.WorkflowFormat;

@DisplayName("WorkflowFormatUtils Tests")
class WorkflowFormatUtilsTest {

    @Test
    @DisplayName("test_returns_json_for_application_json_accept_header")
    void test_returns_json_for_application_json_accept_header() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_JSON_TYPE));

        // When
        WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Then
        assertThat(format).isEqualTo(WorkflowFormat.JSON);
    }

    @Test
    @DisplayName("test_returns_yaml_for_application_yaml_accept_header")
    void test_returns_yaml_for_application_yaml_accept_header() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.valueOf("application/yaml")));

        // When
        WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Then
        assertThat(format).isEqualTo(WorkflowFormat.YAML);
    }

    @Test
    @DisplayName("test_returns_json_for_wildcard_accept_header")
    void test_returns_json_for_wildcard_accept_header() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.WILDCARD_TYPE));

        // When
        WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Then
        assertThat(format).isEqualTo(WorkflowFormat.JSON);
    }

    @Test
    @DisplayName("test_returns_json_when_json_is_first_in_multiple_accept_headers")
    void test_returns_json_when_json_is_first_in_multiple_accept_headers() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(
                List.of(MediaType.APPLICATION_JSON_TYPE, MediaType.valueOf("application/yaml")));

        // When
        WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Then
        assertThat(format).isEqualTo(WorkflowFormat.JSON);
    }

    @Test
    @DisplayName("test_returns_yaml_when_yaml_is_first_in_multiple_accept_headers")
    void test_returns_yaml_when_yaml_is_first_in_multiple_accept_headers() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(
                List.of(MediaType.valueOf("application/yaml"), MediaType.APPLICATION_JSON_TYPE));

        // When
        WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Then
        assertThat(format).isEqualTo(WorkflowFormat.YAML);
    }

    @Test
    @DisplayName("test_throws_exception_for_unsupported_media_type")
    void test_throws_exception_for_unsupported_media_type() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.APPLICATION_XML_TYPE));

        // When/Then
        assertThatThrownBy(() -> WorkflowFormatUtils.mediaTypeToFormat(headers))
                .isInstanceOf(NotSupportedException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    @DisplayName("test_throws_exception_for_text_plain_media_type")
    void test_throws_exception_for_text_plain_media_type() {
        // Given
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(List.of(MediaType.TEXT_PLAIN_TYPE));

        // When/Then
        assertThatThrownBy(() -> WorkflowFormatUtils.mediaTypeToFormat(headers))
                .isInstanceOf(NotSupportedException.class)
                .hasMessageContaining("Unsupported Media Type");
    }

    @Test
    @DisplayName("test_returns_json_when_wildcard_is_in_accept_list")
    void test_returns_json_when_wildcard_is_in_accept_list() {
        // Given - simulates Accept: text/html, */*
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getAcceptableMediaTypes()).thenReturn(
                List.of(MediaType.TEXT_HTML_TYPE, MediaType.WILDCARD_TYPE));

        // When
        WorkflowFormat format = WorkflowFormatUtils.mediaTypeToFormat(headers);

        // Then - wildcard matches JSON
        assertThat(format).isEqualTo(WorkflowFormat.JSON);
    }

    @Test
    @DisplayName("test_format_to_media_type_returns_application_json_for_json_format")
    void test_format_to_media_type_returns_application_json_for_json_format() {
        // When
        MediaType mediaType = WorkflowFormatUtils.formatToMediaType(WorkflowFormat.JSON);

        // Then
        assertThat(mediaType).isEqualTo(MediaType.APPLICATION_JSON_TYPE);
    }

    @Test
    @DisplayName("test_format_to_media_type_returns_application_yaml_for_yaml_format")
    void test_format_to_media_type_returns_application_yaml_for_yaml_format() {
        // When
        MediaType mediaType = WorkflowFormatUtils.formatToMediaType(WorkflowFormat.YAML);

        // Then
        assertThat(mediaType.toString()).isEqualTo("application/yaml");
    }
}
