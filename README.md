test-stability-plugin
=====================


[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/test-stability-plugin/master)](https://ci.jenkins.io/job/Plugins/job/test-stability-plugin/job/master/)

This plugin adds historical information about the stability of tests -
i.e. the percentage how often they failed.

## Configuration

### Global

Configure the maximal history length (default is 30)

![](docs/images/test-stability-global-config.PNG)

### Per-Job

Enable the *Additional test report features*.

## Pipeline usage

Instead of using this in your Jenkinsfile to collect test results:

```groovy
junit '**/*.xml'
```

you should use this:

```groovy
junit testResults: '**/*.xml', testDataPublishers: [[$class: 'StabilityTestDataPublisher']]
```

## Test health

Example test result:

![](docs/images/test-stability-example1.PNG)