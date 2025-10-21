package kr.co.direa.backoffice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing
public class BackofficeApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(BackofficeApiApplication.class, args);
    }

}
