package io.kestra.libs.copilot.services.ai;

import java.io.IOException;

final class TestSchemas {
    static final String FLOW_SCHEMA;
    static final String DASHBOARD_SCHEMA;

    static {
        try {
            FLOW_SCHEMA = new String(TestSchemas.class.getClassLoader().getResourceAsStream("flow-schema.json").readAllBytes());
            DASHBOARD_SCHEMA = new String(TestSchemas.class.getClassLoader().getResourceAsStream("dashboard-schema.json").readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
