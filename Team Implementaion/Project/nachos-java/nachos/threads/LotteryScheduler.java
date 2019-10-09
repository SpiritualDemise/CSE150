package nachos.threads;

import nachos.machine.*;

import java.util.ArrayList;
import java.util.Random;
import java.util.LinkedList;

/**
 * A scheduler that chooses threads using a lottery.
 *
 * <p>
 * A lottery scheduler associates a number of tickets with each thread. When a
 * thread needs to be dequeued, a random lottery is held, among all the tickets
 * of all the threads waiting to be dequeued. The thread that holds the winning
 * ticket is chosen.
 *
 * <p>
 * Note that a lottery scheduler must be able to handle a lot of tickets
 * (sometimes billions), so it is not accNTtable to maintain state for every
 * ticket.
 *
 * <p>
 * A lottery scheduler must partially solve the priority inversion problem; in
 * particular, tickets must be transferred through locks, and through joins.
 * Unlike a priority scheduler, these tickets add (as opposed to just taking the
 * maximum).
 */
public class LotteryScheduler extends Scheduler {
	/**
	 * Allocate a new lottery scheduler.
	 */

	public LotteryScheduler() {
	}

	/**
	 * Allocate a new lottery thread queue.
	 *
	 * @param transferPriority <tt>true</tt> if this queue should transfer tickets
	 *                         from waiting threads to the owning thread.
	 * @return a new lottery thread queue.
	 */
	public ThreadQueue newThreadQueue(boolean transferPriority) {
		// implement me
		return new LotteryQueue(transferPriority);
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
	public static final int priorityMaximum = Integer.MAX_VALUE;

	protected ThreadStateLQ getThreadStateLQ(KThread thread) { // same implementation as priority scheduler but made to
																// work with ThreadStateLQ instead of ThreadState
		if (thread.schedulingState == null)
			thread.schedulingState = new ThreadStateLQ(thread);

		return (ThreadStateLQ) thread.schedulingState;
	}

	protected class LotteryQueue extends ThreadQueue {
		LotteryQueue(boolean transferPriority) {
			this.transferPriority = transferPriority;
			current_max = 0;
		}

		public void waitForAccess(KThread thread) { // same implementation as priority scheduler
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadStateLQ(thread).waitForAccess(this);
		}

		public void acquire(KThread thread) { // same implementation as priority scheduler
			Lib.assertTrue(Machine.interrupt().disabled());
			getThreadStateLQ(thread).acquire(this);
		}

		public KThread nextThread() {
			Lib.assertTrue(Machine.interrupt().disabled());
			if (!waitingThreads.isEmpty()) {
				ThreadStateLQ chosenThreadStateLQ = null;
				Random r = new Random();
				int chosenNumber = r.nextInt(current_max + 1); // choose a random number from 0 to
																// current_max(inclusive)
				int amountRemoved = 0; // amount to be removed from min and max of threads past chosen thread
				for (int i = 0; i < waitingThreads.size(); i++) {
					waitingThreads.get(i).lowerMin(amountRemoved); // remove amountRemoved from each threads min to fix
																	// the empty space left by a thread leaving
					waitingThreads.get(i).lowerMax(amountRemoved); // remove amountRemoved from each threads max to fix
																	// the empty space left by a thread leaving
					if (chosenNumber <= waitingThreads.get(i).getMax()
							&& chosenNumber >= waitingThreads.get(i).getMin()) { // checks to see if the chosenNumber is
																					// within a threads range and if it
																					// is that thread becomes
																					// chosenThread
						chosenThreadStateLQ = waitingThreads.get(i);
						amountRemoved = chosenThreadStateLQ.getNumberofTickets(); // amountRemoved is equal to the
																					// number of tickets held by that
																					// thread originally
					}
				}
				if (chosenThreadStateLQ != null) { // makes sure that some thread was chosen
					chosenThreadStateLQ.acquire(this); // have the chosen thread become the queue holder
					chosenThreadStateLQ.setEffective_number_of_tickets(chosenThreadStateLQ.getNumberofTickets()); // reset
																													// number
																													// of
																													// tickets
																													// of
																													// chosen
																													// thread
					waitingThreads.remove(chosenThreadStateLQ); // remove the chosen thread from the queue
				}

				if (transferPriority) {
					this.updateTickets(); // update the tickets held by the queue holder if donations are enabled
				}
				if (chosenThreadStateLQ != null) {
					return chosenThreadStateLQ.thread; // return the chosen thread
				}
			}
			return null; // if no thread could be chosen return null
		}

		public ThreadStateLQ pickNextThread() {
			if (!waitingThreads.isEmpty()) {
				ThreadStateLQ chosenThreadStateLQ = null;
				Random r = new Random();
				int chosenNumber = r.nextInt(current_max + 1); // choose a random number from 0 to
																// current_max(inclusive)
				for (int i = 0; i < waitingThreads.size(); i++) {
					if (chosenNumber <= waitingThreads.get(i).getMax()
							&& chosenNumber >= waitingThreads.get(i).getMin()) { // checks to see if the chosenNumber is
																					// within a threads range and if it
																					// is that thread becomes
																					// chosenThread
						chosenThreadStateLQ = waitingThreads.get(i);
					}
				}
				return chosenThreadStateLQ; // return chosen thread
			}
			return null; // if no thread could be chosen return null
		}

		public void updateTickets() { // goes through queue adding up the tickets held by each thread and then sets
										// the queue holders effective_number_of_tickets to this amount
			if (queueHolder != null) {
				int trueMax = 0;
				for (int i = 0; i < waitingThreads.size(); i++) {
					trueMax += waitingThreads.get(i).getNumberofTickets();
				}
				queueHolder.effective_number_of_tickets += trueMax;
			}
		}

		public void print() {

		}

		public int current_max; // the collective maximum number of tickets currently in the queue
		private ThreadStateLQ queueHolder = null; // queueholder of the queue
		private boolean transferPriority; // determines whether to transfer tickets or not
		ArrayList<ThreadStateLQ> waitingThreads = new ArrayList<ThreadStateLQ>(); // contains the threads waiting for
																					// access
	}

	protected class ThreadStateLQ {
		ThreadStateLQ(KThread thread) {
			this.thread = thread;
			min = 0;
			max = 0;
			setNumberofTickets(priorityDefault); // set number of tickets
			setEffective_number_of_tickets(number_of_tickets); // set effective number of tickets
			QueuesWaitingIn = new LinkedList<LotteryQueue>(); // initialize storage to store which queues this thread is
																// waiting in
		}

		public int getNumberofTickets() { // returns the number of tickets held by this thread
			return number_of_tickets;
		}

		public void setNumberofTickets(int tickets) { // changes the amount of tickets held by this thread
			number_of_tickets = tickets;
		}

		public int getEffective_number_of_tickets() { // returns the number of tickets held by this thread while taking
														// into account donations
			return effective_number_of_tickets;
		}

		public void setEffective_number_of_tickets(int effective_number_of_tickets) { // changed the amount of effective
																						// tickets held by this thread
			this.effective_number_of_tickets = effective_number_of_tickets;
		}

		public int getMin() { // returns the min value of this thread
			return min;
		}

		public void lowerMin(int lower) { // removes the indicated amount from the min value or changes it to 0 if the
											// indicated amount exceeds the current min
			if (min >= lower) {
				min -= lower;
			} else {
				min = 0;
			}
		}

		public int getMax() { // returns the max value of this thread
			return max;
		}

		public void lowerMax(int lower) { // removes the indicated amount from the max vlaue or changes it to 0 if the
											// indicated amount exceeds the current min
			if (max >= lower) {
				max -= lower;
			} else {
				max = 0;
			}
		}

		public void waitForAccess(LotteryQueue waitQueue) {
			min = waitQueue.current_max; // update this threads min value to the current max since this thread would be
											// added to the end of the list
			int chosen_ticket_number = 0; // which number of tickets is used, will choose the higher of the two
			if (number_of_tickets > effective_number_of_tickets) {
				chosen_ticket_number = number_of_tickets;
			} else {
				chosen_ticket_number = effective_number_of_tickets;
			}
			max = min + chosen_ticket_number; // update this threads max with its current min and its tickets
			waitQueue.current_max = max; // update current max to include the new tickets added to the total pool
			waitQueue.waitingThreads.add(this); // add the thread to the queue
			QueuesWaitingIn.add(waitQueue); // include queue into threads list
			if (waitQueue.transferPriority) {
				waitQueue.updateTickets(); // update the tickets of queue holder since a new amount of tickets has been
											// added
			}
		}

		public void acquire(LotteryQueue waitQueue) {
			waitQueue.queueHolder = this; // the thread takes control of the queue
			if (waitQueue.transferPriority) {
				waitQueue.updateTickets(); // update its own ticket count if dontation is enabled
				for (int i = 0; i < QueuesWaitingIn.size(); i++) { // update the ticket count of queue holders from
																	// other queues that this thread is a part of since
																	// its total ticket count went up
					QueuesWaitingIn.get(i).updateTickets();
				}
			}
		}

		protected KThread thread;

		protected int min; // the smallest number that can represent this thread state
		protected int max; // the largest number that can represent this thread state
		protected int number_of_tickets; // current number of tickets held by this thread
		protected int effective_number_of_tickets; // number of tickets with tickets from threads waiting on this thread
													// included
		protected LinkedList<LotteryQueue> QueuesWaitingIn; // queue this thread is waiting in
	}
}
