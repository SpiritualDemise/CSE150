
package nachos.threads;

import nachos.machine.*;
import java.util.LinkedList;

/**
 * A <i>communicator</i> allows threads to synchronously exchange 32-bit
 * messages. Multiple threads can be waiting to <i>speak</i>, and multiple
 * threads can be waiting to <i>listen</i>. But there should never be a time
 * when both a speaker and a listener are waiting, because the two threads can
 * be paired off at this point.
 */
public class Communicator {
	/**
	 * Allocate a new communicator.
	 */
	public Communicator() { // initializing
		MLock = new Lock();
		speaker = new Condition2(MLock);
		listener = new Condition2(MLock);
		speakerCount = 0;
		listenerCount = 0;
	}

	/**
	 * Wait for a thread to listen through this communicator, and then transfer
	 * <i>word</i> to the listener.
	 *
	 * <p>
	 * Does not return until this thread is paired up with a listening thread.
	 * Exactly one listener should receive <i>word</i>.
	 *
	 * @param word the integer to transfer.
	 */
	public void speak(int word) {
		MLock.acquire(); // acquire lock
		speakerCount++; // let listener know that there is a free speaker

		while (listenerCount <= 0) { // wait until a free listener comes
			speaker.sleep();
		}
		listenerCount--; // signify that a listener is no longer free ie it may no longer exist or it is
							// being used up by a speaker

		transferData.add(new Integer(word)); // add the data to be sent to listener

		listener.wake(); // wake a listener so it can grab the data
		speaker.sleep(); // go to sleep so listener can grab data and return before speaker returns
		MLock.release(); // release lock and return ie data transfer complete
	}

	/**
	 * Wait for a thread to speak through this communicator, and then return the
	 * <i>word</i> that thread passed to <tt>speak()</tt>.
	 *
	 * @return the integer transferred.
	 */
	public int listen() {
		MLock.acquire(); // acquire lock
		listenerCount++; // let speaker know that there is a free listener

		while (speakerCount <= 0) { // wait until a free speaker comes
			listener.sleep();
		}
		if (transferData.isEmpty()) { // checks to see if speaker has transfered its data yet if not:
			speaker.wake(); // wakes speaker so that it can transfer data
			speakerCount--; // signify that a speaker is no longer free
			listener.sleep(); // go to sleep until speaker finished transferring data
		} else { // if this else statement occurs, means that speaker has already transferred
					// data, only need to signify that a speaker is no longer free
			speakerCount--;
		}
		int temp = -1; // value used to return data within transferData, default set to -1 to signify
						// non valid output
		if (!transferData.isEmpty()) // make sure that there is data that can be grabbed
			temp = transferData.pollFirst().intValue();
		speaker.wake(); // wake to speaker so that it can return
		MLock.release(); // release lock
		return temp; // return value
	}

	private Condition2 speaker; // contains speakers
	private Condition2 listener; // contains listeners
	private Lock MLock; // lock to keep speaker/listener from returning early
	private int speakerCount; // keeps track of the # of speakers available
	private int listenerCount; // keeps track of the # of listener available
	private LinkedList<Integer> transferData = new LinkedList<Integer>(); // stores the data to be transfered
}