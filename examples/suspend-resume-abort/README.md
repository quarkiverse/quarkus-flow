# Suspend/Resume/Cancel Workflow Example with Persistence enabled (Quarkus + Quarkus Flow + Quarkus Flow MVStore)

A minimal example that illustrate suspend, resume and cancel workflow capabilities, plus durability.

* **Quarkus** (hot-reload dev mode)
* **Quarkus Flow** (CNCF Workflow YAML file)
* **Quakus Flow MV store** (Persistence)


The workflow included in the examples consist of a loop that updates an internal counter (originally named `count`) every second, till a certain number is reached. That number can be specified when the workflow is started, by providing an input property called `maxCount`, if not provided the number is 10000 by default. 

The example include a REST service exposing APIs to start a new workflow, suspend, resume and cancel workflow execution. 

The API that creates the workflow returns an identifier. That identifier can be used to call other services to suspend, resume and cancel that workflow execution. 

The example persist workflow execution progress using MVStore DB, which writes information to a local file (location can be changed using `quarkus.flow.persistence.mvstore.db-path` property)
If user stops the JVM and run another one, workflow execution is automatically restored. This means that if any workflow was running before Quarkus was stopped, it will continue running when restarted. If it was suspended, it will remain suspended till restored. And, at any moment, user can cancel the execution (once cancelled, the workflow instance does not exist anymore) 

---

## Quick Start

### Prerequisites

* Java 17+ and Maven

### 1) Run the app

```bash
# from this example directory
mvn clean quarkus:dev
```

Open in your browser:

* **[http://localhost:8080](http://localhost:8080)**

> Quarkus dev mode gives you live reload for Java, resources, and the static UI.

---

### 2) Use the provided APIs

1) Start a workflow 

```
curl --location 'http://localhost:8080/api/flow/start' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data '{}'
```

That will return a workflow identifier, for example 

```
{
    "instanceId": "01KNM0FY887K0CXQYSC0KCGSQ3"
}
```

Take note of the value, since we were using it later. 

You will also see repeated logs in the Quarkus Console 

```
2026-04-07 15:00:53,124 INFO  [org.acme.flow.FlowCustomListener] (pool-9-thread-1) Workflow 01KNM0FY887K0CXQYSC0KCGSQ3 incremented count to 1
2026-04-07 15:00:54,133 INFO  [org.acme.flow.FlowCustomListener] (pool-9-thread-1) Workflow 01KNM0FY887K0CXQYSC0KCGSQ3 incremented count to 2
```

2) Suspend the workflow

```
curl --location 'http://localhost:8080/api/flow/suspend/01KNM0FY887K0CXQYSC0KCGSQ3' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data '{}'
```

The count increment will pause (no new logs in the console)

3) Resume the workflow

``` 
curl --location 'http://localhost:8080/api/flow/resume/01KNM0FY887K0CXQYSC0KCGSQ3' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data '{}'
```

The count increment will resume (new logs will appear in console)

4) Stop Quarkus

Take note of the latest log

``` 
2026-04-07 15:11:26,826 INFO  [org.acme.flow.FlowCustomListener] (pool-9-thread-3) Workflow 01KNM0FY887K0CXQYSC0KCGSQ3 incremented count to 633
2026-04-07 15:11:27,829 INFO  [org.acme.flow.FlowCustomListener] (pool-9-thread-4) Workflow 01KNM0FY887K0CXQYSC0KCGSQ3 incremented count to 634
```

5) Start Quarkus again

You will see that the log indicating the count number starts from the last number before the previous JVM was stopped

```
2026-04-07 15:17:43,923 INFO  [org.acme.flow.FlowCustomListener] (pool-9-thread-2) Workflow 01KNM0FY887K0CXQYSC0KCGSQ3 incremented count to 635
2026-04-07 15:17:44,929 INFO  [org.acme.flow.FlowCustomListener] (pool-9-thread-2) Workflow 01KNM0FY887K0CXQYSC0KCGSQ3 incremented count to 636
```

6) Cancel the workflow

``` 
curl --location 'http://localhost:8080/api/flow/cancel/01KNM0FY887K0CXQYSC0KCGSQ3' \
--header 'Content-Type: application/json' \
--header 'Accept: application/json' \
--data '{}'
```

You will see that the repeating log is not appearing anymore. Any further attempt to suspend or resume that workflow instance will return a 404 code. 

## Learn More

This example is intentionally simple so you can use it as a starting point to try different sequences of resume or cancel or suspend or stop/start.
 
* Quarkus Flow docs:
  [https://docs.quarkiverse.io/quarkus-flow/dev/](https://docs.quarkiverse.io/quarkus-flow/dev/)

Have fun exploring with Quarkus Flow!
