import org.junit.*;

public class JobSystemTest {

    private static JobSystem jobSystem = null;

    @BeforeClass
    public static void setUpBeforeClass() {

        jobSystem = new JobSystem(10, 170);
    }

    @Before
    public void beforeTest(){
      jobSystem.cancelAllJobs();

    }

    @After
    public void afterTest(){

    }

    @Test
    public void interruptJob() throws InterruptedException {

        // INTERRUPT A THREAD - TIMEOUT
        final String id = createNewJobExecution(7000);
        Thread.sleep(600);
        Assert.assertEquals("OK", JobSystem.JobState.CANCELLED, jobSystem.getJobState(id));
    }

    @Test
    public void jobComplete() throws InterruptedException {

        // LET A THREAD COMPLETE IT'S JOB
        final String id = createNewJobExecution(30);
        Thread.sleep(100);
        Assert.assertEquals("OK", JobSystem.JobState.DONE, jobSystem.getJobState(id));
    }


    @Test
    public void jobDoesNotExist() throws InterruptedException {

        // LOOKUP FOR A THREAD THREAD THAT DOES NOT EXIST
        final String id = createNewJobExecution(30);
        Thread.sleep(100);
        Assert.assertEquals("OK", JobSystem.JobState.DOES_NOT_EXIST, jobSystem.getJobState(id +1 ));
    }

    @Test
    public void jobIsRunning() {

        // CHECK IF THREAD IS RUNNING
        final String id = createNewJobExecution(70);
        Assert.assertEquals("OK", JobSystem.JobState.RUNNING, jobSystem.getJobState(id));
    }

    @Test
    public void jobScheduled(){

        // REPORT IF THREAD IS SCHEDULED
        final String id = jobSystem.scheduledExecution(new MyRunnable() {

            @Override
            public void clean() {

            }

            @Override
            public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

        }, JobSystem.TimeFrame.ONE_HOUR);
        Assert.assertEquals("OK", JobSystem.JobState.SCHEDULED, jobSystem.getJobState(id));
    }

    @Test
    public void testNumberOfRunningJobs()  {

        // CHECK NUMBER OF RUNNING THREADS
        for(int i = 0 ; i < 10; i ++){
            createNewJobExecution(7000);
        }
        Assert.assertEquals("OK", 10, jobSystem.getNumerOfRunningThreads());
    }

    @Test
    public void testNumberOfScheduledJobs(){

         // CHECK NUMBER OF SCHEDULED THREADS
        for(int i = 0 ; i < 10; i ++) {

            jobSystem.scheduledExecution(new MyRunnable() {

                @Override
                public void clean() {

                }

                @Override
                public void run() {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }


                }
            }, JobSystem.TimeFrame.ONE_HOUR);
        }
        Assert.assertEquals("OK", 10, jobSystem.getNumOfScheduledJobs());
    }

    @Test(expected = IllegalArgumentException.class)
    public void badInputScheduledExecution() {

        // TEST BAD INPUT FOR scheduledExecution METHOD
        jobSystem.scheduledExecution(null, JobSystem.TimeFrame.ONE_HOUR);
    }

    @Test(expected = IllegalArgumentException.class)
    public void badInputStraightExecution() {

        // TEST BAD INPUT FOR execute METHOD
        jobSystem.execute(null);
    }

    @Test
    public void cancelAnExistingJob() {

        // AS NAMED, INIT & CANCEL A JOB, THEN CHECK TO SEE THAT JOB HAS BEEN CHANGED
        final String id =  createNewJobExecution(700);

        if(jobSystem.cancelJob(id)){
            Assert.assertEquals("OK", JobSystem.JobState.CANCELLED, jobSystem.getJobState(id));
        }
        else {
            Assert.assertEquals("OK", JobSystem.JobState.RUNNING, jobSystem.getJobState(id));
        }
    }

    @Test
    public void checkPoolSize() {

        // CHECK THE INITIALIZATION VALUE MATCHES THE POOL CORE SIZE
        Assert.assertEquals("OK", 10, jobSystem.getThreadsThreshold());

        // AFTER CHANGING, CHECK THAT EQUALITY PERSISTS
        jobSystem.setThreadsThreshold(20);
        Assert.assertEquals("OK", 20, jobSystem.getThreadsThreshold());

    }

    @AfterClass
    public static void setUpAfterClass() {
        jobSystem.shutdown();
    }

    private String createNewJobExecution(long milliseconds) {

        return jobSystem.execute(new MyRunnable() {

            @Override
            public void clean() {

            }

            @Override
            public void run() {
                try {
                    Thread.sleep(milliseconds);
                } catch (InterruptedException e) {

                }
            }
        });
    }
}