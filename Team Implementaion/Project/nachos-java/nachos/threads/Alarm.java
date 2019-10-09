package nachos.threads;
import java.util.PriorityQueue;
import java.util.*;
import nachos.machine.*;

/**
 * Uses the hardware timer to provide preemption, and to allow threads to sleep
 * until a certain time.
 */

public class Alarm {
    /**
     * Allocate a new Alarm. Set the machine's timer interrupt handler to this
     * alarm's callback.
     *
     * <p><b>Note</b>: Nachos will not function correctly with more than one
     * alarm.
     */
    public Alarm() {
	Machine.timer().setInterruptHandler(new Runnable() {
		public void run() { timerInterrupt(); }
	    });
    }

    /**
     * The timer interrupt handler. This is called by the machine's timer
     * periodically (approximately every 500 clock ticks). Causes the current
     * thread to yield, forcing a context switch if there is another thread
     * that should be run.
     */
    public void timerInterrupt() {
    	//create some variables
    	boolean disableStatus;
    	//KThread pollItem;
    	//disable interrupts
    	disableStatus = Machine.interrupt().setStatus(false);
    	//boolean disableStatus = Machine.interrupt().disable(); 
    	//queue not empty, we proceed, else we do nothing
    	if (ContainerList.size() > 0){
    		//peek into the first element and retrieve the waketime, the compare with current time
    		while (ContainerList.peek().waketime <= Machine.timer().getTime()) {
    			//remove the first element
    			ThreadTime wakeThread = ContainerList.poll();
    			currthread = wakeThread.thread;
    			//after the wait the thread can now move to the ready queue
    			currthread.ready();
    			}
    		}
    	
    	KThread.yield();
    	
    	//enable interrupts
    	Machine.interrupt().restore(disableStatus);
    }

    /**
     * Put the current thread to sleep for at least <i>x</i> ticks,
     * waking it up in the timer interrupt handler. The thread must be
     * woken up (placed in the scheduler ready set) during the first timer
     * interrupt where
     *
     * <p><blockquote>
     * (current time) >= (WaitUntil called time)+(x)
     * </blockquote>
     *
     * @param	x	the minimum number of clock ticks to wait.
     *
     * @see	nachos.machine.Timer#getTime()
     */
    public void waitUntil(long x) {
		// for now, cheat just to get something working (busy waiting is bad)
    	long wakeTime = Machine.timer().getTime() + x;
		//thread adding statement
    	this.currthread = KThread.currentThread();
		this.waketime = wakeTime;
		ThreadTime ContainList = new ThreadTime(waketime, currthread);
	
		boolean disableStatus;
    	//disable interrupts
    	disableStatus = Machine.interrupt().setStatus(false);
		//add to our priority queue called ContainerList
		ContainerList.add(ContainList);	
		Machine.interrupt().restore(disableStatus);
		//go to sleep and go for next process
	   KThread.yield();
    }
    //creating a priority queue to contain the sleeping threads
    //create some global variables so I can used in ThreadTime
    public PriorityQueue <ThreadTime> ContainerList = new PriorityQueue<ThreadTime>(5, new waketimeComparator());
    public KThread currthread;
    public long waketime;
}

//creating a new class with the thread and the time needed to be waken up
class ThreadTime {
		//the current thread
		KThread thread;
		long waketime;

		ThreadTime(long waketime, KThread currthread){
			//the thread
			this.thread = currthread;
			//the wake time
			this.waketime = waketime;
		}
		//need to show the waketime will I pull it out
}
//require a compare to alter the order of the priority queue
class waketimeComparator implements Comparator<ThreadTime>{
	public int compare(ThreadTime s1, ThreadTime s2) {
		//if time coming in is less than or equal to elements in the queue
			if (s1.waketime > s2.waketime) {
				return 1;
			}
			else {
				return -1;
			}
		}
}
