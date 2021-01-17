package com.yogeshpotdar.tradetest;

import com.yogeshpotdar.process.OHLCAnalyzer;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TradeProcessorApplication implements CommandLineRunner {

	public static void main(String[] args) {
		SpringApplication.run(TradeProcessorApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		OHLCAnalyzer.process();
	}
}
