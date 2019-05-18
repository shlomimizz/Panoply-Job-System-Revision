import java.util.concurrent.*;

class JobExecutorService<T> {

    private ExecutorService executorService; // the core ThreadPool to handle jobs execution.
    private CompletionService<T> jobTracker; // a wrapper around executorService for tracking jobs in pool

    JobExecutorService(int threshold) {
        this.executorService = Executors.newFixedThreadPool(threshold);
        this.jobTracker = new ExecutorCompletionService<>(executorService);
    }

    /**
     *
     * @return current maximum pool size allowed;
     */
    int getPoolThreshold() {

        return ((ThreadPoolExecutor) executorService).getMaximumPoolSize();
    }

    private static class MyCallableTask<T> implements Callable<T> {

        private T uniqueID;
        private MyRunnable r;

        MyCallableTask(T uniqueID, MyRunnable r){
            this.uniqueID = uniqueID;
            this.r = r;
        }

        @Override
        public T call() {
            r.run();
            r.clean();
            return this.uniqueID;
        }
    }

    /**
     *
     * @param uniqueID of the job
     * @param job the execution code to run
     * @return a Future for inspecting the job's state after submission to ThreadPool.
     */

    Future<T> execute(T uniqueID, MyRunnable job){

        // Try catch block is crucial here, since user might shutdown the service any given time, and, when doing so
        // this code block might try to submit jobs who were sent prior to shutdown command.
        Future <T> f = null;
        try {
           f = jobTracker.submit(new MyCallableTask<>(uniqueID, job));
        } catch (RejectedExecutionException e){
            System.out.println("Submission of job :" + uniqueID + "  has been interrupted\nProbably occurred during user's shutdown during job submission.");
        }

        return f;
    }

    void setThreadsThreshold(int threadsThreshold) {
        ((ThreadPoolExecutor) executorService).setMaximumPoolSize(threadsThreshold);
    }

    /**
     * Spectates the jobTracker object and tries to Poll the next finished jobs, this
     * code blocks for (1) second, unless it has succeeded polling a finished job.
     * @return a job finished executing, or null if none exist during the polling time.
     */
    Future<T> getNextFinishedJob(){
        try {
            return jobTracker.poll(1 ,TimeUnit.SECONDS);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Shuts down and frees all resources occupied by this object.
     */
    void shutdown() {
        this.executorService.shutdown();
    }
}


