import java.util.*;
import java.util.concurrent.*;

public class JobSystem {

    private static final int HOUR_TO_MILLI_FACTOR = 3600000;
    private JobExecutorService<String> jobExecutorService;
    private JobSystemStats<String, Future<String>> jobSystemStats;
    private boolean running;
    private Timer timer;
    private Map<String, TimerTask> scheduledJobs;
    private ScheduledExecutorService JobsTerminator;
    private long timeOutMillisec;

    public JobSystem(int threadsThreshold, long timeOutMillisec) {

        if(threadsThreshold <= 0  || timeOutMillisec <= 0)
            throw new IllegalArgumentException();

        this.jobExecutorService = new JobExecutorService<>(threadsThreshold);
        this.jobSystemStats = new JobSystemStats<>();
        this.running = true;
        this.timer = new Timer();
        this.scheduledJobs = new HashMap<>();
        this.JobsTerminator = Executors.newScheduledThreadPool(2);
        this.timeOutMillisec = timeOutMillisec;

        // Background daemon thread, will constantly monitor finished jobs
        // and updates jobSystemStats Object.
        // This can be used in order to post-process jobs, ie:
        // handle any return value of jobs for additional processing / aggregation.
        new Thread(() -> {
            while(running){
                try {
                    final Future<String> f = jobExecutorService.getNextFinishedJob();
                    if(f != null && !f.isCancelled()) {
                        jobSystemStats.removeRunningThread(f.get());
                    }
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    /**
     * This function takes a job, delegates the job to a private execute overloaded func
     * and returns the id of the job (to be spectated by the user)
     *
     * @param job
     * @return the uniqueID allocated for the job.
     */
    public String execute(MyRunnable job){

        if(job == null)
            throw new IllegalArgumentException();

        final String uniqueID = UUID.randomUUID().toString();

        execute(job, uniqueID);

        return uniqueID;
    }

    // private helper function
    private String execute(MyRunnable job, String uniqueID) {

        final Future<String> f = jobExecutorService.execute(uniqueID ,job);

        JobsTerminator.schedule(() -> {
            f.cancel(true);
            jobSystemStats.removeRunningThread(uniqueID);
        }, timeOutMillisec, TimeUnit.MILLISECONDS);

        jobSystemStats.addNewJobRunningJob(uniqueID, f);

        return uniqueID;
    }

    /**
     * @param job a MyRunnable instance to be executed
     * @param time the delay time
     * @return
     */
    public String scheduledExecution(MyRunnable job, TimeFrame time)  {

        if (job == null || time == null)
            throw new IllegalArgumentException();

        final String uniqueID = UUID.randomUUID().toString();

        jobSystemStats.addNewScheduledJob(uniqueID);

        TimerTask timerTask = new TimerTask() {
            @Override
            public void run () {
                jobSystemStats.removeScheduledJob(uniqueID);
                scheduledJobs.remove(uniqueID);
                execute(job, uniqueID);
            }
        };

        scheduledJobs.put(uniqueID, timerTask);

        timer.schedule(timerTask, time.time * HOUR_TO_MILLI_FACTOR);

        return uniqueID;
    }

    private class JobSystemStats <T, V> {

        private Map<T, V> jobMap; // a job map holding <Id,Thread> for State lookup
        private Set<T> scheduledJobs; // a Set containing the all the scheduled jobs
        private Set<T> runningThreads;

        private JobSystemStats(){
            this.runningThreads = new HashSet<>();
            this.jobMap = new HashMap<>();
            this.scheduledJobs = new HashSet<>();
        }

        private boolean addNewJobRunningJob(T uniqueID, V f){
            return this.jobMap.put(uniqueID, f) == null && this.runningThreads.add(uniqueID);
        }

        private boolean addNewScheduledJob(T uniqueId){
            return this.scheduledJobs.add(uniqueId);
        }

        private boolean removeScheduledJob(T uniqueID){
            return this.scheduledJobs.remove(uniqueID);
        }

        private boolean removeRunningThread(T uniqueID){
            return this.runningThreads.remove(uniqueID);
        }

        private int getNumOfScheduledJobs(){
            return this.scheduledJobs.size();
        }

        private int getNumberOfRunningThreads(){
            return this.runningThreads.size();
        }

        Set<T> getScheduledJobs() {
            return scheduledJobs;
        }

        Set<T> getAllJobs() {
            return jobMap.keySet();
        }

        V getJob(T id){
           return this.jobMap.get(id);
        }

        Set<T> getRunningThreads() {
            return runningThreads;
        }
    }

    /*
     * enum class defining constant values,
     * these values represent the possible scheduling options
     */

    public enum TimeFrame {
        ONE_HOUR(1),  //calls constructor with value 1
        TWO_HOURS(2),  //calls constructor with value 2
        SIX_HOURS(6),  //calls constructor with value 6
        TWELVE_HOURS(12); // calls constructor with value 12

        private final long time;

        TimeFrame(int time) {
            this.time = time;
        }
    }

    /*
     * enum class. Defining constant values of job State.
     * Job state is figured by acessing JobStats instance which serves the JobSystem
     * by keep 'introspection' data such as job's state.
     */

    public enum JobState {
        SCHEDULED,
        RUNNING,
        CANCELLED,
        DONE,
        DOES_NOT_EXIST
    }


    /*
    Following methods are part of the "Introspection"
     interface that JobSystem object offers.
     */

    /**
     *
     * @param id
     * @return true if the job was canceled
     * @return false if the job could not be canceled (already canceled, doesn't exist)
     */
    public boolean cancelJob(String id) {

        if(jobSystemStats.getScheduledJobs().contains(id)){
            jobSystemStats.removeScheduledJob(id);
            TimerTask scheduledJobToCancel = scheduledJobs.remove(id);
            if(scheduledJobToCancel != null) {
                return scheduledJobToCancel.cancel();
            }
        }
        else if (jobSystemStats.getJob(id) != null) {
            jobSystemStats.removeRunningThread(id);
            return jobSystemStats.getJob(id).cancel(true);
           }

        return false;
    }


    /**
     *
     * @return true if all jobs (pending / scheduled / running ) jobs were canceled.
     */
    public boolean cancelAllJobs() {

        Collection<String> jobIdsCollection = new HashSet<>(scheduledJobs.keySet());
        jobIdsCollection.addAll(jobSystemStats.getAllJobs());
        for (String id : jobIdsCollection) {
            if (!cancelJob(id)) {
                return false;
            }
        }
        return true;

    }

    /**
     * @param timeOutMillisec sets a new value to job's execution timeout.
     */
    public void setTimeOutMillisec(long timeOutMillisec) {
        this.timeOutMillisec = timeOutMillisec;
    }

    /**
     * @param threadsThreshold sets a new value to max number of allowed concurrent jobs.
     */
    public void setThreadsThreshold(int threadsThreshold) {
        this.jobExecutorService.setThreadsThreshold(threadsThreshold);
    }

    /**
     *
     * @return max number of allowed threads to execute concurrently.
     */
    public long getThreadsThreshold() {
        return jobExecutorService.getPoolThreshold();
    }

    /**
     *
     * @return current TimeOutMilli sec for each Job.
     */
    public long getTimeOutMillisec() {
        return this.timeOutMillisec;
    }

    /**
     *
     * @return number of threads scheduled to be executed later.
     */
    public int getNumOfScheduledJobs() {
        return jobSystemStats.getNumOfScheduledJobs();
    }

    /**
     *
     * @return number of running threads
     */
    public int getNumerOfRunningThreads() {
        return this.jobSystemStats.getNumberOfRunningThreads();
    }


    /**
     * @param id of the job to lookup
     * @return the state of the job;
     */
    public JobState getJobState(String id) {

            // State lookup, Basically should treat these values as enums/constants rather than a String
            // That would be better, and especially important for Users to realize the interface
            if (jobSystemStats.getScheduledJobs().contains(id)) {
                return JobState.SCHEDULED;
            }
            if (jobSystemStats.getJob(id) == null) {
                return JobState.DOES_NOT_EXIST;
            }

            if (jobSystemStats.getJob(id).isCancelled()) {
                return JobState.CANCELLED;
            }
            if (jobSystemStats.getRunningThreads().contains(id)) {
                return JobState.RUNNING;
            }
            return JobState.DONE;
        }

    /**
     * shuts downs the JobSystem service, killing all threads / pools and by that
     * allowing the garbage collector to release the JobSystem object from memory
     */
    public void shutdown() {
        running = false;
        timer.cancel();
        this.JobsTerminator.shutdown();
        this.jobExecutorService.shutdown();
    }
}
