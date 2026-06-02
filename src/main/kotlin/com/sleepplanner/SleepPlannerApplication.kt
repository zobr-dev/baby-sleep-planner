package com.sleepplanner

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class SleepPlannerApplication

fun main(args: Array<String>) {
    runApplication<SleepPlannerApplication>(*args)
}
