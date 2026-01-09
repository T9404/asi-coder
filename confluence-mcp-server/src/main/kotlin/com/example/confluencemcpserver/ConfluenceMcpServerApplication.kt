package com.example.confluencemcpserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class ConfluenceMcpServerApplication

fun main(args: Array<String>) {
    runApplication<ConfluenceMcpServerApplication>(*args)
}
