
package nachos.threads;

import nachos.machine.*;
import nachos.threads.PriorityScheduler.ThreadState;

import java.util.TreeSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Comparator;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads based on their priorities.
 *
 * <p>
 * A priority scheduler associates a priority with each thread. The next thread
 * to be dequeued is always a thread with priority no less than any other
 * waiting thread's priority. Like a round-robin scheduler, the thread that is
 * dequeued is, among all the threads of the same (highest) priority, the thread
 * that has been waiting longest.
 *
 * <p>
 * Essentially, a priority scheduler gives access in a round-robin fassion to
 * all the highest-priority threads, and ignores all other threads. This has the
 * potential to starve a thread if there's always a thread waiting with higher
 * priority.
 *
 * <p>
 * A priority scheduler must partially solve the priority inversion problem; in
 * particular, priority must be donated through locks, and through joins.
 */
public class PriorityScheduler extends Scheduler {
	/**
	 * Allocate a new priority scheduler.
	 */
	public PriorityScheduler() {
	}

	/**
	 * Allocate a new priority thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer priority
	 *                         from waiting threads to the owning thread.
	 * @return a new priority thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		return new PriorityQueue(transferPriority);
	}

	public int getPriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getPriority();
	}

	public int getEffectivePriority(KThread thread) {
		Lib.assertTrue(Machine.interrupt().disabled());

		return getThreadState(thread).getEffectivePriority();
	}

	public void setPriority(KThread thread, int priority) {
		Lib.assertTrue(Machine.interrupt().disabled());

		Lib.assertTrue(priority >= priorityMinimum && priority <= priorityMaximum);

		getThreadState(thread).setPriority(priority);
	}

	public boolean increasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMaximum)
			return false;

		setPriority(thread, priority + 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	public boolean decreasePriority() {
		boolean intStatus = Machine.interrupt().disable();

		KThread thread = KThread.currentThread();

		int priority = getPriority(thread);
		if (priority == priorityMinimum)
			return false;

		setPriority(thread, priority - 1);

		Machine.interrupt().restore(intStatus);
		return true;
	}

	/**
	 * The default priority for a new thread. Do not change this value.
	 */
	public static final int priorityDefault = 1;
	/**
	 * The minimum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMinimum = 0;
	/**
	 * The maximum priority that a thread can have. Do not change this value.
	 */
	public static final int priorityMaximum = 7;

	/**
	 * Return the scheduling state of the specified thread.
	 *
	 * @param thread the thread whose scheduling state to return.
	 * @return the scheduling state of the specified thread.
	 */
	protected ThreadState getThreadState(KThread thread) {
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadState(thread);

		return (ThreadState) thread.schedulingState;
	}

	/**
	 * A <tt>ThreadQueue</tt> that sorts threads by priority.
	 */
	protected class PriorityQueue extends ThreadQueue {
		PriorityQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
		}

		public void waitForAccess(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) {
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadState(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me
			if (!waitingThreads.isEmpty()) { // makes sure that there are values in waitingThreads
				ThreadState temp = waitingThreads.first(); // choose the first thread in waitingThreads to be returned
															// because it should be the one with the highest priority or
															// has been there the longest
				temp.acquire(this);
				return temp.thread;
			}
			return null;
		}

		/**
		 * Return the next thread that <tt>nextThread()</tt> would return, without
		 * modifying the state of this queue.
		 *
		 * @return the next thread that <tt>nextThread()</tt> would return.
		 */
		protected ThreadState pickNextThread() {
			// implement me
			if (!waitingThreads.isEmpty()) { // does the same thing as nextThread() without messing with waitingThreads
				return waitingThreads.first();
			}
			return null;
		}

		protected void updateEffectivePriority() {
			Iterator<ThreadState> i = waitingThreads.iterator();
			int maxPriority = 0;
			if(queueHolder.getEffectivePriority() > queueHolder.getPriority()) { // store current largest effectivePriority value
				maxPriority = queueHolder.getEffectivePriority();
			}else {
				maxPriority = queueHolder.getPriority();
			}
			
			while (i.hasNext()) { // iterate through waitingThreads
				if (queueHolder != null && i.next() != null) { // compute as long as queueHolder and i.next() exist
					if (maxPriority < i.next().getPriority()) { // if maxPriority is smaller replace with larger
																// priority value
						maxPriority = i.next().getPriority();
					}
				}
			}
			queueHolder.effectivePriority = maxPriority; // change effectivePriority value of queueHolder to the new
															// maxPriority value, will either be the same value or be
															// larger
		}

		public void print() {
			Lib.assertTrue(Machine.interrupt().disabled());
			// implement me (if you want)
			if (queueHolder != null) {
				System.out.println("current queue holder is thread: " + queueHolder.time);
			}
			Iterator<ThreadState> i = waitingThreads.iterator();
			while (i.hasNext()) {
				ThreadState currentThread = i.next();
				System.out.println("thread " + currentThread.time + "'s current priority is: "
						+ currentThread.getPriority() + " and its effective priority is: "
						+ currentThread.getEffectivePriority() + " out of " + waitingThreads.size());
			}
		}

		/**
		 * <tt>true</tt> if this queue should transfer priority from waiting threads to
		 * the owning thread.
		 */
		public boolean transferPriority;
		private ThreadState queueHolder = null; // current best thread
		protected TreeSet<ThreadState> waitingThreads = new TreeSet<ThreadState>(new comparatorTS()); // threads waiting
		// on queueHolder
	}

	/**
	 * The scheduling state of a thread. This should include the thread's priority,
	 * its effective priority, any objects it owns, and the queue it's waiting for,
	 * if any.
	 *
	 * @see nachos.threads.KThread#schedulingState
	 */
	protected class ThreadState { // need to include list of queues it is waiting in
		/**
		 * Allocate a new <tt>ThreadState</tt> object and associate it with the
		 * specified thread.
		 *
		 * @param thread the thread this state belongs to.
		 */
		public ThreadState(KThread thread) {
			this.thread = thread;
			time = 0; // time defaulted to 0, since it has spent 0 time in a queue
			QueuesWaitingIn = new LinkedList<PriorityQueue>();

			setPriority(priorityDefault);
			effectivePriority = 0; // default effectivePriority to 0 so that it won't impact ordering until it is
									// updated
		}

		/**
		 * Return the priority of the associated thread.
		 *
		 * @return the priority of the associated thread.
		 */
		public int getPriority() {
			return priority;
		}

		/**
		 * Return the effective priority of the associated thread.
		 *
		 * @return the effective priority of the associated thread.
		 */
		public int getEffectivePriority() { // simply return effectivePriority, value computed elsewhere
			// implement me
			return effectivePriority;
		}

		/**
		 * Set the priority of the associated thread to the specified value.
		 *
		 * @param priority the new priority.
		 */
		public void setPriority(int priority) { // no changes needed
			if (this.priority == priority)
				return;

			this.priority = priority;

			// implement me
		}

		/**
		 * Called when <tt>waitForAccess(thread)</tt> (where <tt>thread</tt> is the
		 * associated thread) is invoked on the specified priority queue. The associated
		 * thread is therefore waiting for access to the resource guarded by
		 * <tt>waitQueue</tt>. This method is only called if the associated thread
		 * cannot immediately obtain access.
		 *
		 * @param waitQueue the queue that the associated thread is now waiting on.
		 *
		 * @see nachos.threads.ThreadQueue#waitForAccess
		 */
		public void waitForAccess(PriorityQueue waitQueue) {
			// implement me
			waitQueue.waitingThreads.add(this);
			this.time = Machine.timer().getTime(); // set time value as the time that thread entered queue
			
			if (waitQueue.transferPriority) {
				waitQueue.updateEffectivePriority(); // update effective priority
			}
		}

		/**
		 * Called when the associated thread has acquired access to whatever is guarded
		 * by <tt>waitQueue</tt>. This can occur either as a result of
		 * <tt>acquire(thread)</tt> being invoked on <tt>waitQueue</tt> (where
		 * <tt>thread</tt> is the associated thread), or as a result of
		 * <tt>nextThread()</tt> being invoked on <tt>waitQueue</tt>.
		 *
		 * @see nachos.threads.ThreadQueue#acquire
		 * @see nachos.threads.ThreadQueue#nextThread
		 */
		public void acquire(PriorityQueue waitQueue) {
			// implement me
			waitQueue.queueHolder = this; // become the queue holder
			if (waitQueue.transferPriority) {
				waitQueue.updateEffectivePriority(); // need to update effective priority after becoming the queue
														// holder
			}
		}

		/** The thread with which this object is associated. */
		protected KThread thread;
		/** The priority of the associated thread. */
		protected int priority;
		protected int effectivePriority; // threads effective priority
		protected long time; // time that this thread entered the priority queue
		protected LinkedList<PriorityQueue> QueuesWaitingIn; // the queues that this thread is in
	}

	class comparatorTS implements Comparator<ThreadState> {
		public int compare(ThreadState tTs1, ThreadState tTs2) {

			if (tTs1.thread.equals(tTs2.thread)) {
				return 0;
			} else {
				int priorityUsed1, priorityUsed2;

				if (tTs1.getPriority() > tTs1.getEffectivePriority()) { // chooses the larger of the two priorities to
																		// use for associated thread
					priorityUsed1 = tTs1.getPriority();
				} else {
					priorityUsed1 = tTs1.getEffectivePriority();
				}

				if (tTs2.getPriority() > tTs2.getEffectivePriority()) { // chooses the larger of
																		// the two priorities to
																		// use for the other
																		// thread
					priorityUsed2 = tTs2.getPriority();
				} else {
					priorityUsed2 = tTs2.getEffectivePriority();
				}

				if (priorityUsed1 != priorityUsed2) { // if the priorities are not the same use them for the comparison,
														// otherwise use their times
					if (priorityUsed1 > priorityUsed2) {
						return 1;
					}
				} else {
					if (tTs1.time < tTs2.time) { // The one with the smallest time is the one that was added
													// earlier and thus the one that has been waiting longer
						return 1;
					}
				}
				return -1;
			}
		}
	}
}