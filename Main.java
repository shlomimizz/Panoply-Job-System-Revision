
public class Main {

    // Main class contains the trivial running context for the JobSystem object.
    // Upon Execution, Prints out the job Id
    // Looking carefully at the output of this Main , we can see the random order of the prints (ie: random job execution order)
    public static void main(String[] args) throws InterruptedException {

        JobSystem jobSystem = new JobSystem(100, 1000);

        for (int i = 0; i < 1000; i++) {

            int finalI = i;
            jobSystem.execute(new MyRunnable() {
                @Override
                public void clean() {
                    System.out.println("Cleaning Job: " + finalI);

                }

                @Override
                public void run() {
                    System.out.println("Running Job: " + finalI);
                }
            });
        }

        jobSystem.scheduledExecution(new MyRunnable(){

            @Override
            public void run() {
                System.out.println("Running Scheduld Execution Job");
            }

            @Override
            public void clean() {
            }

        }, JobSystem.TimeFrame.ONE_HOUR);

        jobSystem.shutdown();
    }
}
