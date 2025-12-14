package com.example.projectmanagerserver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class ProjectManagerServerApplication

fun main(args: Array<String>) {
    runApplication<ProjectManagerServerApplication>(*args)
}
