package se.magnus.microservices.composite.product.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import lombok.extern.slf4j.Slf4j;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@Configuration
@Slf4j
public class SchedulerConfig {

	private final Integer threadPoolSize;
	private final Integer taskQueueSize;

	@Autowired
	public SchedulerConfig(
		@Value("${app.threadPoolSize:10}") Integer threadPoolSize,
		@Value("${app.taskQueueSize:100}") Integer taskQueueSize
	) {
		this.threadPoolSize = threadPoolSize;
		this.taskQueueSize = taskQueueSize;
	}

	@Bean
	public Scheduler publishEventScheduler() {
		log.info("Creates a messagingScheduler with connectionPoolSize = {}", threadPoolSize);
		return Schedulers.newBoundedElastic(threadPoolSize, taskQueueSize, "publish-pool");
	}

}
