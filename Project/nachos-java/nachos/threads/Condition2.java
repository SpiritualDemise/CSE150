package nachos.threads;
import java.util.LinkedList;
import nachos.machine.*;

/**
 * An implementation of condition variables that disables interrupt()s for
 * synchronization.
 *
 * <p>
 * You must implement this.
 *
 * @see	nachos.threads.Condition
 */
public class Condition2 {
    /**
     * Allocate a new condition variable.
     *
     * @param	conditionLock	the lock associated with this condition
     *				variable. The current thread must hold this
     *				lock whenever it uses <tt>sleep()</tt>,
     *				<tt>wake()</tt>, or <tt>wakeAll()</tt>.
     */
    public Condition2(Lock conditionLock) {
	this.conditionLock = conditionLock;
    

	
}

    /**
     * Atomically release the associated lock and go to sleep on this condition
     * variable until another thread wakes it using <tt>wake()</tt>. The
     * current thread must hold the associated lock. The thread will
     * automatically reacquire the lock before <tt>sleep()</tt> returns.
     */
    @SuppressWarnings("static-access")
	public void sleep() {
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	
	//updated code
	
	conditionLock.release();
	//disable the interupts
	Machine.interrupt().disable();
	//add the thread to the queue
	queue.add(KThread.currentThread());
	//put the thread to sleep
	KThread.currentThread().sleep();
	//reenable interupts
	Machine.interrupt().enable();
	//reacquire the lock
	conditionLock.acquire();
	
    }

    /**
     * Wake up at most one thread sleeping on this condition variable. The
     * current thread must hold the associated lock.
     */
    public void wake() {
	//check if the current thread has the lock
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
	//disable the interupts
	Machine.interrupt().disable();
	//if there is a thread in the queue, remove it and put it on the ready queue
	if(!queue.isEmpty()) {
	
		KThread thread = queue.pop();
		
		thread.ready();
	}
	//enable the interupts
	Machine.interrupt().enable();
	
    }

    /**
     * Wake up all threads sleeping on this condition variable. The current
     * thread must hold the associated lock.
     */
    public void wakeAll() {
    	//check if the current thread has the lock
	Lib.assertTrue(conditionLock.isHeldByCurrentThread());
		//remove all of the threads in the queue
		while(!queue.isEmpty()) {
		
			queue.pop();
	
		}
    }

    private Lock conditionLock;
    private LinkedList<KThread> queue = new LinkedList<KThread>();
}
