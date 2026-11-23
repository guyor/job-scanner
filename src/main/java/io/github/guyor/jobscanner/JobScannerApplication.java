package io.github.guyor.jobscanner;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(ScannerProperties.class)
public class JobScannerApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobScannerApplication.class, args);
    }
}
