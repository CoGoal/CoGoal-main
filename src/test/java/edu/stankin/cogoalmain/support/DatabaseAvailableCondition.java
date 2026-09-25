package edu.stankin.cogoalmain.support;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Runs PostgreSQL integration tests when a database is available: an external one given by
 * {@code IT_DB_URL} (with {@code IT_DB_USERNAME}, {@code IT_DB_PASSWORD}), or Docker for Testcontainers.
 * Otherwise the tests are reported as skipped with the reason, instead of failing.
 */
public class DatabaseAvailableCondition implements ExecutionCondition {

    @Override
    public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
        if (PostgresIntegrationTest.EXTERNAL_URL != null) {
            return ConditionEvaluationResult.enabled("Using the external database " + PostgresIntegrationTest.EXTERNAL_URL);
        }
        if (dockerAvailable()) {
            return ConditionEvaluationResult.enabled("Using PostgreSQL in Testcontainers");
        }
        return ConditionEvaluationResult.disabled(
                "No database: start Docker, or set IT_DB_URL/IT_DB_USERNAME/IT_DB_PASSWORD to an empty PostgreSQL database");
    }

    private static boolean dockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (RuntimeException e) {
            return false;
        }
    }
}
