/*
 * Copyright Elasticsearch B.V. and/or licensed to Elasticsearch B.V. under one
 * or more contributor license agreements. Licensed under the Elastic License
 * 2.0 and the Server Side Public License, v 1; you may not use this file except
 * in compliance with, at your election, the Elastic License 2.0 or the Server
 * Side Public License, v 1.
 */

package org.elasticsearch.telemetry.apm.internal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.cluster.service.ClusterService;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.SecureSetting;
import org.elasticsearch.common.settings.SecureString;
import org.elasticsearch.common.settings.Setting;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.core.SuppressForbidden;
import org.elasticsearch.telemetry.apm.internal.tracing.APMTracer;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.elasticsearch.common.settings.Setting.Property.NodeScope;
import static org.elasticsearch.common.settings.Setting.Property.OperatorDynamic;

/**
 * This class is responsible for APM settings, both for Elasticsearch and the APM Java agent.
 * The methods could all be static, however they are not in order to make unit testing easier.
 */
public class APMAgentSettings {

    private static final Logger LOGGER = LogManager.getLogger(APMAgentSettings.class);

    public void addClusterSettingsListeners(
        ClusterService clusterService,
        APMTelemetryProvider apmTelemetryProvider,
        APMMeterService apmMeterService
    ) {
        final ClusterSettings clusterSettings = clusterService.getClusterSettings();
        final APMTracer apmTracer = apmTelemetryProvider.getTracer();

        clusterSettings.addSettingsUpdateConsumer(APM_ENABLED_SETTING, enabled -> {
            apmTracer.setEnabled(enabled);
            this.setAgentSetting("instrument", Boolean.toString(enabled));
        });
        clusterSettings.addSettingsUpdateConsumer(TELEMETRY_METRICS_ENABLED_SETTING, enabled -> {
            apmMeterService.setEnabled(enabled);
            // The agent records data other than spans, e.g. JVM metrics, so we toggle this setting in order to
            // minimise its impact to a running Elasticsearch.
            this.setAgentSetting("recording", Boolean.toString(enabled));
        });
        clusterSettings.addSettingsUpdateConsumer(APM_TRACING_NAMES_INCLUDE_SETTING, apmTracer::setIncludeNames);
        clusterSettings.addSettingsUpdateConsumer(APM_TRACING_NAMES_EXCLUDE_SETTING, apmTracer::setExcludeNames);
        clusterSettings.addSettingsUpdateConsumer(APM_TRACING_SANITIZE_FIELD_NAMES, apmTracer::setLabelFilters);
        clusterSettings.addAffixMapUpdateConsumer(APM_AGENT_SETTINGS, map -> map.forEach(this::setAgentSetting), (x, y) -> {});
    }

    /**
     * Copies APM settings from the provided settings object into the corresponding system properties.
     * @param settings the settings to apply
     */
    public void syncAgentSystemProperties(Settings settings) {
        this.setAgentSetting("recording", Boolean.toString(APM_ENABLED_SETTING.get(settings)));
        // Apply values from the settings in the cluster state
        APM_AGENT_SETTINGS.getAsMap(settings).forEach(this::setAgentSetting);
    }

    /**
     * Copies a setting to the APM agent's system properties under <code>elastic.apm</code>, either
     * by setting the property if {@code value} has a value, or by deleting the property if it doesn't.
     * @param key the config key to set, without any prefix
     * @param value the value to set, or <code>null</code>
     */
    @SuppressForbidden(reason = "Need to be able to manipulate APM agent-related properties to set them dynamically")
    public void setAgentSetting(String key, String value) {
        final String completeKey = "elastic.apm." + Objects.requireNonNull(key);
        AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
            if (value == null || value.isEmpty()) {
                LOGGER.trace("Clearing system property [{}]", completeKey);
                System.clearProperty(completeKey);
            } else {
                LOGGER.trace("Setting setting property [{}] to [{}]", completeKey, value);
                System.setProperty(completeKey, value);
            }
            return null;
        });
    }

    private static final String APM_SETTING_PREFIX = "tracing.apm.";

    /**
     * Allow-list of APM agent config keys users are permitted to configure.
     * @see <a href="https://www.elastic.co/guide/en/apm/agent/java/current/configuration.html">APM Java Agent Configuration</a>
     */
    private static final Set<String> PERMITTED_AGENT_KEYS = Set.of(
        // Circuit-Breaker:
        "circuit_breaker_enabled",
        "stress_monitoring_interval",
        "stress_monitor_gc_stress_threshold",
        "stress_monitor_gc_relief_threshold",
        "stress_monitor_cpu_duration_threshold",
        "stress_monitor_system_cpu_stress_threshold",
        "stress_monitor_system_cpu_relief_threshold",

        // Core:
        // forbid 'enabled', must remain enabled to dynamically enable tracing / metrics
        // forbid 'recording' / 'instrument', controlled by 'telemetry.metrics.enabled' / 'tracing.apm.enabled'
        "service_name",
        "service_node_name",
        // forbid 'service_version', forced by APMJvmOptions
        "hostname",
        "environment",
        "transaction_sample_rate",
        "transaction_max_spans",
        "long_field_max_length",
        "sanitize_field_names",
        "enable_instrumentations",
        "disable_instrumentations",
        // forbid 'enable_experimental_instrumentations', expected to be always enabled by APMJvmOptions
        "unnest_exceptions",
        "ignore_exceptions",
        "capture_body",
        "capture_headers",
        "global_labels",
        "instrument_ancient_bytecode",
        "context_propagation_only",
        "classes_excluded_from_instrumentation",
        "trace_methods",
        "trace_methods_duration_threshold",
        // forbid 'central_config', may impact usage of config_file, disabled in APMJvmOptions
        // forbid 'config_file', configured by APMJvmOptions
        "breakdown_metrics",
        "plugins_dir",
        "use_elastic_traceparent_header",
        "disable_outgoing_tracecontext_headers",
        "span_min_duration",
        "cloud_provider",
        "enable_public_api_annotation_inheritance",
        "transaction_name_groups",
        "trace_continuation_strategy",
        "baggage_to_attach",

        // Datastore: irrelevant, not whitelisted

        // HTTP:
        "capture_body_content_types",
        "transaction_ignore_urls",
        "transaction_ignore_user_agents",
        "use_path_as_transaction_name",
        // forbid deprecated url_groups

        // Huge Traces:
        "span_compression_enabled",
        "span_compression_exact_match_max_duration",
        "span_compression_same_kind_max_duration",
        "exit_span_min_duration",

        // JAX-RS: irrelevant, not whitelisted

        // JMX:
        "capture_jmx_metrics",

        // Logging:
        "log_level", // allow overriding the default in APMJvmOptions
        // forbid log_file, always set by APMJvmOptions
        "log_ecs_reformatting",
        "log_ecs_reformatting_additional_fields",
        "log_ecs_formatter_allow_list",
        // forbid log_ecs_reformatting_dir, always use logsDir provided in APMJvmOptions
        "log_file_size",
        // forbid log_format_sout, always use file logging
        // forbid log_format_file, expected to be JSON in APMJvmOptions
        "log_sending",

        // Messaging: irrelevant, not whitelisted

        // Metrics:
        "dedot_custom_metrics",
        "custom_metrics_histogram_boundaries",
        "metric_set_limit",
        "agent_reporter_health_metrics",
        "agent_background_overhead_metrics",

        // Profiling:
        "profiling_inferred_spans_enabled",
        "profiling_inferred_spans_logging_enabled",
        "profiling_inferred_spans_sampling_interval",
        "profiling_inferred_spans_min_duration",
        "profiling_inferred_spans_included_classes",
        "profiling_inferred_spans_excluded_classes",
        "profiling_inferred_spans_lib_directory",

        // Reporter:
        // forbid secret_token: use tracing.apm.secret_token instead
        // forbid api_key: use tracing.apm.api_key instead
        "server_url",
        "server_urls",
        "disable_send",
        "server_timeout",
        "verify_server_cert",
        "max_queue_size",
        "include_process_args",
        "api_request_time",
        "api_request_size",
        "metrics_interval",
        "disable_metrics",

        // Serverless:
        "aws_lambda_handler",
        "data_flush_timeout",

        // Stacktraces:
        "application_packages",
        "stack_trace_limit",
        "span_stack_trace_min_duration"
    );

    public static final Setting.AffixSetting<String> APM_AGENT_SETTINGS = Setting.prefixKeySetting(
        APM_SETTING_PREFIX + "agent.",
        (qualifiedKey) -> {
            final String[] parts = qualifiedKey.split("\\.");
            final String key = parts[parts.length - 1];
            return new Setting<>(qualifiedKey, "", (value) -> {
                if (qualifiedKey.equals("_na_") == false && PERMITTED_AGENT_KEYS.contains(key) == false) {
                    throw new IllegalArgumentException("Configuration [" + qualifiedKey + "] is either prohibited or unknown.");
                }
                return value;
            }, Setting.Property.NodeScope, Setting.Property.OperatorDynamic);
        }
    );

    public static final Setting<List<String>> APM_TRACING_NAMES_INCLUDE_SETTING = Setting.stringListSetting(
        APM_SETTING_PREFIX + "names.include",
        OperatorDynamic,
        NodeScope
    );

    public static final Setting<List<String>> APM_TRACING_NAMES_EXCLUDE_SETTING = Setting.stringListSetting(
        APM_SETTING_PREFIX + "names.exclude",
        OperatorDynamic,
        NodeScope
    );

    public static final Setting<List<String>> APM_TRACING_SANITIZE_FIELD_NAMES = Setting.stringListSetting(
        APM_SETTING_PREFIX + "sanitize_field_names",
        List.of(
            "password",
            "passwd",
            "pwd",
            "secret",
            "*key",
            "*token*",
            "*session*",
            "*credit*",
            "*card*",
            "*auth*",
            "*principal*",
            "set-cookie"
        ),
        OperatorDynamic,
        NodeScope
    );

    public static final Setting<Boolean> APM_ENABLED_SETTING = Setting.boolSetting(
        APM_SETTING_PREFIX + "enabled",
        false,
        OperatorDynamic,
        NodeScope
    );

    public static final Setting<Boolean> TELEMETRY_METRICS_ENABLED_SETTING = Setting.boolSetting(
        "telemetry.metrics.enabled",
        false,
        OperatorDynamic,
        NodeScope
    );

    public static final Setting<SecureString> APM_SECRET_TOKEN_SETTING = SecureSetting.secureString(
        APM_SETTING_PREFIX + "secret_token",
        null
    );

    public static final Setting<SecureString> APM_API_KEY_SETTING = SecureSetting.secureString(APM_SETTING_PREFIX + "api_key", null);
}
