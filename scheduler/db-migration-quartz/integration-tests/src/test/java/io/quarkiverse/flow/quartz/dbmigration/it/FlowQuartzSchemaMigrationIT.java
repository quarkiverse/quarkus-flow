package io.quarkiverse.flow.quartz.dbmigration.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.quartz.JobBuilder.newJob;
import static org.quartz.TriggerBuilder.newTrigger;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.inject.Inject;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.Scheduler;
import org.quartz.SimpleScheduleBuilder;
import org.quartz.Trigger;

import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class FlowQuartzSchemaMigrationIT {

    static final AtomicBoolean FIRED = new AtomicBoolean(false);

    @Inject
    Scheduler scheduler;

    @Test
    @DisplayName("flyway_migrated_qrtz_schema_supports_jdbc_cmt_job_store")
    void flyway_migrated_qrtz_schema_supports_jdbc_cmt_job_store() throws Exception {
        // Quartz validates the QRTZ_* tables against its jdbc-cmt job store implementation at
        // startup and when persisting a job/trigger; scheduling and firing a job below only
        // succeeds if the schema this extension's Flyway migration created is structurally
        // correct for the JobStoreCMT the "flow-quartz" datasource backs.
        FIRED.set(false);

        var job = newJob(PingJob.class)
                .withIdentity("flow-db-migration-quartz-smoke-job")
                .build();
        Trigger trigger = newTrigger()
                .withIdentity("flow-db-migration-quartz-smoke-trigger")
                .startNow()
                .withSchedule(SimpleScheduleBuilder.simpleSchedule().withRepeatCount(0))
                .build();

        scheduler.scheduleJob(job, trigger);

        await().atMost(Duration.ofSeconds(10)).untilTrue(FIRED);
        assertThat(FIRED).isTrue();
    }

    public static class PingJob implements Job {
        @Override
        public void execute(JobExecutionContext context) throws JobExecutionException {
            FIRED.set(true);
        }
    }
}
