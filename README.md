# TestComplete-plugin
The TestComplete Support plug-in lets you run TestComplete tests from Jenkins. The plug-in provides a build step that lets you include TestComplete tests into your Jenkins builds. Also, the plug-in maintains a list of test runs and lets you view test results directly from within Jenkins.

Read more: [https://wiki.jenkins-ci.org/display/JENKINS/TestComplete+Support+Plugin](https://wiki.jenkins-ci.org/display/JENKINS/TestComplete+Support+Plugin)

#Usage
0. Prepare a test computer (node) for automated testing. Make sure the node has everything it needs to run tests successfully:

  * It has TestComplete or TestExecute installed.
  * It has required browsers, test data files, and other helper files and applications.
  * If you have a desktop tested application, make sure the node has that application installed before the TestComplete tests start working. 
To copy files to the node, you can use the CopyToSlavePlugin, or copy the files manually. 
 
0. Add the TestComplete test step to your Jenkins build. 
 
0. Customize the step’s properties:
  * Select the test runner: TestComplete or TestExecute.
  * Specify the TestComplete project suite file and select tests to be run.
  * Important: Select the Run interactive user session check box, if needed (see below), and specify additional parameters for the run. 
 
0. Run the build as you would normally do this. 
 
0. After the build run is completed is over, you will see the TestComplete Test Results link on the build’s page. 
Click the link to view test results. In the subsequent screen, click the link in the Test column to explore detailed test results.
