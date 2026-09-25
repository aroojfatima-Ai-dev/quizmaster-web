package com.quizmaster;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * QuizMaster Web - the browser rebuild of the QuizMaster desktop application.
 *
 * <p>Teachers create classes with a 6-character join code and timed
 * multiple-choice tests; students join with the code, take a test against a
 * countdown timer, and get a full answer review afterwards.
 *
 * <p>Run it with {@code mvn spring-boot:run} (or {@code java -jar
 * target/quizmaster-web.jar}) and open http://localhost:8080.
 */
@SpringBootApplication
public class QuizMasterWebApplication {

    public static void main(String[] args) {
        SpringApplication.run(QuizMasterWebApplication.class, args);
    }
}
