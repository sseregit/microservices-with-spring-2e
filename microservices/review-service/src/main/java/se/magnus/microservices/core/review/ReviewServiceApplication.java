package se.magnus.microservices.core.review;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

@SpringBootApplication
@ComponentScan({"se.magnus"})
@Slf4j
public class ReviewServiceApplication {

    private final Integer threadPoolSize;
    private final Integer taskQueueSize;

    public ReviewServiceApplication(@Value("${app.threadPoolSize:10}") Integer threadPoolSize,
        @Value("${app.taskQueueSize:100}") Integer taskQueueSize) {
        this.threadPoolSize = threadPoolSize;
        this.taskQueueSize = taskQueueSize;
    }

    public static void main(String[] args) {
        SpringApplication.run(ReviewServiceApplication.class, args);
    }

    @Bean
    public Scheduler jdbcScheduler() {
        log.info("Creates a jdbcScheduler with thread pool size {}", threadPoolSize);
        return Schedulers.newBoundedElastic(threadPoolSize, taskQueueSize, "jdbc-pool");
    }

}
