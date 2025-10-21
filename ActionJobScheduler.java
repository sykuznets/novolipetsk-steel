package com.example.scheduler;

import static com.example.model.JobStatus.ERROR;
import static com.example.model.JobStatus.NEW;

import com.example.converter.GoalAvroConverter;
import com.example.model.ActionJob;
import com.example.repository.ActionJobRepository;
import com.example.service.kafka.KafkaEventProducer;
import java.util.Comparator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ActionJobScheduler {

    private final ActionJobRepository jobRepository;
    private final GoalAvroConverter avroConverter;
    private final KafkaEventProducer kafkaProducer;

    @Value("${app.kafka.topic.goal}")
    private String kafkaTopic;
    @Value("${app.scheduler.jobs.enabled}")
    private boolean schedulerEnabled;

    @Scheduled(
            cron = "${app.scheduler.jobs.cron}",
            zone = "${app.scheduler.jobs.timezone}"
    )
    @SchedulerLock(
            name = "actionJobsLock",
            lockAtLeastFor = "#{@lockSettings.getMinLockDuration()}",
            lockAtMostFor = "#{@lockSettings.getMaxLockDuration()}"
    )
    public void processPendingJobs() {
        if (!schedulerEnabled) {
            log.debug("Scheduler for action jobs is disabled");
            return;
        }

        var pendingJobs = jobRepository.findByStatus(NEW);

        if (pendingJobs.size() > 1) {
            pendingJobs.stream()
                    .sorted(Comparator.comparingLong(ActionJob::getId))
                    .forEach(job -> {
                        String jobKey = job.getId().toString();
                        var avroEvent = avroConverter.convertToAvroModel(job);

                        boolean sentSuccessfully = kafkaProducer.produce(kafkaTopic, jobKey, avroEvent);

                        if (sentSuccessfully) {
                            jobRepository.delete(job);
                            log.info("Deleted ActionJob with ID '{}' after successful Kafka send", jobKey);
                        } else {
                            job.setStatus(ERROR);
                            jobRepository.save(job);
                            log.warn("Failed to send ActionJob with ID '{}' to Kafka, status set to ERROR", jobKey);
                        }
                    });
        }
    }
  
}
