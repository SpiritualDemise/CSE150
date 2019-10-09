package nachos.threads;

import java.util.LinkedList;

import nachos.ag.BoatGrader;

public class Boat {
	static BoatGrader bg;
	static LinkedList<KThread> adultsOnOahu;
	static LinkedList<KThread> childrenOnOahu;
	static LinkedList<KThread> childrenOnMolokai;
	static LinkedList<KThread> adultsOnMolokai;
	static Lock boat;
	static boolean boatAtOahu;
	static boolean simulationComplete;
	static int childOnOahu;
	static int childOnMolokai;
	static int adultOnOahu;
	static int adultOnMolokai;

	public static void selfTest() {
		BoatGrader b = new BoatGrader();

		System.out.println("\n ***Testing Boats with only 2 children***");
		begin(5, 4, b);

		//System.out.println("\n ***Testing Boats with 2 children, 2 adult***");
		//begin(100, 100, b);

		// System.out.println("\n ***Testing Boats with 3 children, 3 adults***");
		// begin(3, 3, b);
	}

	public static void begin(int adults, int children, BoatGrader b) {
		// Store the externally generated autograder in a class
		// variable to be accessible by children.
		bg = b;

		// Instantiate global variables here
		simulationComplete = false;// true when the final child pilots to
									// Molokai
		childrenOnMolokai = new LinkedList<KThread>();
		adultsOnMolokai = new LinkedList<KThread>();
		childrenOnOahu = new LinkedList<KThread>();
		adultsOnOahu = new LinkedList<KThread>();
		adultOnMolokai = 0;
		childOnMolokai = 0;
		// all threads start on Oahu
		childOnOahu = children;
		adultOnOahu = adults;
		boat = new Lock();
		boatAtOahu = true;
		// Create threads here. See section 3.4 of the Nachos for Java
		// Walkthrough linked from the projects page.
		Runnable ch = new Runnable() {
			public void run() {
				while (!simulationComplete) {
					ChildItinerary();
				}
			}
		};
		for (int i = 1; i <= children; i++) {
			KThread child = new KThread(ch);
			child.setName("Child Thread");
			childrenOnOahu.add(child);
			child.fork();
		}

		Runnable ad = new Runnable() {
			public void run() {
				while (!simulationComplete) {
					AdultItinerary();
				}
			}
		};
		for (int i = 1; i <= adults; i++) {
			KThread adult = new KThread(ad);
			adult.setName("Adult Thread");
			adultsOnOahu.add(adult);
			adult.fork();
		}

		while ((childOnOahu > 0) || (adultOnOahu > 0)) {
			KThread.yield();
		}

	}

	static void AdultItinerary() {
		/*
		 * This is where you should put your solutions. Make calls to the
		 * BoatGrader to show that it is synchronized. For example:
		 * bg.AdultRowToMolokai(); indicates that an adult has rowed the boat
		 * across to Molokai
		 */

		boolean onMolokai = false;
		KThread temp;// temporary thread for list
		// Acquire boat lock
		boat.acquire();
		// check if thread is on Molokai
		if (adultsOnMolokai.contains(KThread.currentThread())) {
			onMolokai = true;
		}
		// if the current thread is on Oahu and the boat is at Oahu
		if (!onMolokai && boatAtOahu) {
			// if there is at most one child
			if (childOnOahu <= 1) {

				if (boat.isHeldByCurrentThread()) {
					// check if the boat is still at Oahu
					if (boatAtOahu) {
						// if there is an adult on Oahu, the adult will row to
						// Molokai
						if (!adultsOnOahu.isEmpty()) {
							temp = adultsOnOahu.removeFirst();
							adultsOnMolokai.addLast(temp);
							bg.AdultRowToMolokai();
							adultOnOahu--;
							adultOnMolokai++;
							System.out.println("Number of threads in child list Molokai: "+childrenOnMolokai.size());
							System.out.println("Number of threads in child list Oahu: "+childrenOnOahu.size());
							System.out.println("On Oahu: "+childOnOahu+"\nOn Molokai: "+childOnMolokai);
							System.out.println();
							System.out.println("Number of threads in adult list Molokai: "+adultsOnMolokai.size());
							System.out.println("Number of threads in adult list Oahu: "+adultsOnOahu.size());
							System.out.println("On Oahu: "+adultOnOahu+"\nOn Molokai: "+adultOnMolokai);
						}
						boatAtOahu=false;
						System.out.println("Boat is at Molokai.");
						boat.release();

					} else {
						// if the boat is no longer at Oahu release the lock
						boat.release();
					}
				}
			} else {
				// if there are 2 or more children on Oahu, do nothing
				boat.release();
				return;
			}
		} else {
			// if the thread is not on Oahu or the boat is not at Oahu, do
			// nothing
			boat.release();
			return;
		}
	}

	static void ChildItinerary() {
		boolean onMolokai = false;
		KThread temp;// temporary thread for list
		// if the simulation is complete, do nothing
		boat.acquire();
		if (simulationComplete) {
			boat.release();
			return;
		} else {
			
			// check if the thread is on Molokai
			if (childrenOnMolokai.contains(KThread.currentThread())) {
				onMolokai = true;
			}
			// if the thread is on Oahu
			if (!onMolokai) {
				// if the boat is at Oahu and there are at least 2 children on
				// Oahu
				if(childrenOnOahu.isEmpty())
				{
					return;
				}
				if (boatAtOahu && (childOnOahu >= 2)) {
					if (boat.isHeldByCurrentThread()) {
						// check the if the boat is still at Oahu , release the
						// lock if not
						if (!boatAtOahu) {
							boat.release();
						} else {
							bg.ChildRowToMolokai();
							bg.ChildRideToMolokai();
							// move two children from Oahu to Molokai
							if ((childOnOahu!=0)&& (childOnOahu>= 2)) {
								temp = childrenOnOahu.removeFirst();
								childrenOnMolokai.add(temp);
								temp = childrenOnOahu.removeFirst();
								childrenOnMolokai.add(temp);
								childOnOahu--;
								childOnOahu--;
								childOnMolokai++;
								childOnMolokai++;
								System.out.println("Number of threads in child list Molokai: "+childrenOnMolokai.size());
								System.out.println("Number of threads in child list Oahu: "+childrenOnOahu.size());
								System.out.println("On Oahu: "+childOnOahu+"\nOn Molokai: "+childOnMolokai);
								System.out.println();
								System.out.println("Number of threads in adult list Molokai: "+adultsOnMolokai.size());
								System.out.println("Number of threads in adult list Oahu: "+adultsOnOahu.size());
								System.out.println("On Oahu: "+adultOnOahu+"\nOn Molokai: "+adultOnMolokai);
								System.out.println();
								if((childOnOahu==0)&&(adultOnOahu==0))
								{
									simulationComplete=true;
								}

							}
							boatAtOahu = false;
							System.out.println("Boat is at Molokai.");
							boat.release();
						}
					}
				} else {
					// if the thread is on Oahu and there no adults on Oahu
					if (adultsOnOahu.isEmpty()) {
						// if thread has the boat lock and the boat is at Oahu,
						// move the child to Molokai
						if ((boat.isHeldByCurrentThread()) && boatAtOahu) {
							if (!childrenOnOahu.isEmpty()) {
								temp = childrenOnOahu.removeFirst();
								childrenOnMolokai.add(temp);
								bg.ChildRowToMolokai();
								childOnOahu--;
								childOnMolokai++;
								if(childOnOahu==0)
								{
									simulationComplete=true;
								}
								System.out.println("Number of threads in child list Molokai: "+childrenOnMolokai.size());
								System.out.println("Number of threads in child list Oahu: "+childrenOnOahu.size());
								System.out.println("On Oahu: "+childOnOahu+"\nOn Molokai: "+childOnMolokai);
								System.out.println();
								System.out.println("Number of threads in adult list Molokai: "+adultsOnMolokai.size());
								System.out.println("Number of threads in adult list Oahu: "+adultsOnOahu.size());
								System.out.println("On Oahu: "+adultOnOahu+"\nOn Molokai: "+adultOnMolokai);
								System.out.println();

							} else {
								boat.release();
								return;
							}
							boatAtOahu = false;
							System.out.println("Boat is at Molokai.");
							boat.release();
							simulationComplete = true;
						} else {
							// boat is not at Oahu, but thread is.
							boat.release();
						}
					} else {
						// if there are still adults on Oahu, release the lock
						boat.release();
					}
				}
			} else {
				// if the thread is on Molokai and the boat is at Molokai
				if (!boatAtOahu) {
					if(childrenOnMolokai.isEmpty())
					{
						return;
					}
					if (boat.isHeldByCurrentThread()) {
						// if the boat is still at Molokai and there is at least
						// one adult on Molokai
						if (!boatAtOahu) {
							// move one child back to Oahu
							if (childOnMolokai!=0&&!simulationComplete) {
								temp = childrenOnMolokai.remove();
								childrenOnOahu.add(temp);
								bg.ChildRowToOahu();
								childOnMolokai--;
								childOnOahu++;
								System.out.println("Number of threads in child list Molokai: "+childrenOnMolokai.size());
								System.out.println("Number of threads in child list Oahu: "+childrenOnOahu.size());
								System.out.println("Child: On Oahu: "+childOnOahu+"\nOn Molokai: "+childOnMolokai);
								System.out.println();
								System.out.println("Number of threads in adult list Molokai: "+adultsOnMolokai.size());
								System.out.println("Number of threads in adult list Oahu: "+adultsOnOahu.size());
								System.out.println("Adult: On Oahu: "+adultOnOahu+"\nOn Molokai: "+adultOnMolokai);
								System.out.println();

							}
							boatAtOahu = true;
							System.out.println("Boat is at Oahu.");
							boat.release();
						} else {
							boat.release();
						}
					}

				} else {
					boat.release();
				}
			}
		}

	}

	static void SampleItinerary() {
		// Please note that this isn't a valid solution (you can't fit
		// all of them on the boat). Please also note that you may not
		// have a single thread calculate a solution and then just play
		// it back at the autograder -- you will be caught.
		// System.out
		// .println("\n ***Everyone piles on the boat and goes to Molokai***");
		// bg.AdultRowToMolokai();
		// bg.ChildRideToMolokai();
		// bg.AdultRideToMolokai();
		// bg.ChildRideToMolokai();
	}

}
