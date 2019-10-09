package nachos.userprog;

import nachos.machine.*;
import nachos.threads.*;
import nachos.userprog.*;

import java.io.EOFException;
import java.util.ArrayList;
import java.util.HashMap;
/**
 * Encapsulates the state of a user process that is not contained in its
 * user thread (or threads). This includes its address translation state, a
 * file table, and information about the program being executed.
 *
 * <p>
 * This class is extended by other classes to support additional functionality
 * (such as additional syscalls).
 *
 * @see	nachos.vm.VMProcess
 * @see	nachos.network.NetProcess
 */
public class UserProcess {
    /**
     * Allocate a new process.
     */
    public UserProcess() {
	int numPhysPages = Machine.processor().getNumPhysPages();
	pageTable = new TranslationEntry[numPhysPages];
	for (int i=0; i<numPhysPages; i++)
	    pageTable[i] = new TranslationEntry(i,i, true,false,false,false);
	    
	    
	    JoinLock=new Lock();
		ExitLock = new Lock();
	    
	    descriptors=new OpenFile[16];
	    descriptors[0]=stdin;
		descriptors[1]=stdout;
		
		boolean inStatus=Machine.interrupt().disable();
		counterPIDLock=new Lock();
		counterPIDLock.acquire();
		ProcessID=counterPID++;
		counterPIDLock.release();
		stdin = UserKernel.console.openForReading();
		stdout = UserKernel.console.openForWriting();
		
		Machine.interrupt().restore(inStatus);
		parentProcess=null;
		
		
		ChildPro=new ArrayList<UserProcess>();

    }
    
    /**
     * Allocate and return a new process of the correct class. The class name
     * is specified by the <tt>nachos.conf</tt> key
     * <tt>Kernel.processClassName</tt>.
     *
     * @return	a new process of the correct class.
     */
    public static UserProcess newUserProcess() {
	return (UserProcess)Lib.constructObject(Machine.getProcessClassName());
    }

    /**
     * Execute the specified program with the specified arguments. Attempts to
     * load the program, and then forks a thread to run it.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the program was successfully executed.
     */
    public boolean execute(String name, String[] args) {
	if (!load(name, args))
	    return false;
	
	new UThread(this).setName(name).fork();

	return true;
    }

    /**
     * Save the state of this process in preparation for a context switch.
     * Called by <tt>UThread.saveState()</tt>.
     */
    public void saveState() {
    }

    /**
     * Restore the state of this process after a context switch. Called by
     * <tt>UThread.restoreState()</tt>.
     */
    public void restoreState() {
	Machine.processor().setPageTable(pageTable);
    }

    /**
     * Read a null-terminated string from this process's virtual memory. Read
     * at most <tt>maxLength + 1</tt> bytes from the specified address, search
     * for the null terminator, and convert it to a <tt>java.lang.String</tt>,
     * without including the null terminator. If no null terminator is found,
     * returns <tt>null</tt>.
     *
     * @param	vaddr	the starting virtual address of the null-terminated
     *			string.
     * @param	maxLength	the maximum number of characters in the string,
     *				not including the null terminator.
     * @return	the string read, or <tt>null</tt> if no null terminator was
     *		found.
     */
    public String readVirtualMemoryString(int vaddr, int maxLength) {
	Lib.assertTrue(maxLength >= 0);

	byte[] bytes = new byte[maxLength+1];

	int bytesRead = readVirtualMemory(vaddr, bytes);

	for (int length=0; length<bytesRead; length++) {
	    if (bytes[length] == 0)
		return new String(bytes, 0, length);
	}

	return null;
    }

    /**
     * Transfer data from this process's virtual memory to all of the specified
     * array. Same as <tt>readVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data) {
	return readVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from this process's virtual memory to the specified array.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to read.
     * @param	data	the array where the data will be stored.
     * @param	offset	the first byte to write in the array.
     * @param	length	the number of bytes to transfer from virtual memory to
     *			the array.
     * @return	the number of bytes successfully transferred.
     */
    public int readVirtualMemory(int vaddr, byte[] data, int offset,
				 int length) {
Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	//get the virtual page number, virtual offset, and the physical page number
	int VPN=Machine.processor().pageFromAddress(vaddr);
	//int VPN=vaddr/ pageSize;
    int VOff= Machine.processor().offsetFromAddress(vaddr);
	//int VOff=vaddr %pageSize;
	pageTable[VPN].used=true;
	int phyAddr=pageTable[VPN].ppn*pageSize+VOff;
	if (phyAddr < 0 || phyAddr >= memory.length)
	{
		return 0;
	}
	if (pageTable[VPN].valid==false)
	{
	    return 0;
	}

	int amount = Math.min(length, memory.length-phyAddr);
	System.arraycopy(memory, phyAddr, data, offset, amount);

	return amount;
    }

    /**
     * Transfer all data from the specified array to this process's virtual
     * memory.
     * Same as <tt>writeVirtualMemory(vaddr, data, 0, data.length)</tt>.
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data) {
	return writeVirtualMemory(vaddr, data, 0, data.length);
    }

    /**
     * Transfer data from the specified array to this process's virtual memory.
     * This method handles address translation details. This method must
     * <i>not</i> destroy the current process if an error occurs, but instead
     * should return the number of bytes successfully copied (or zero if no
     * data could be copied).
     *
     * @param	vaddr	the first byte of virtual memory to write.
     * @param	data	the array containing the data to transfer.
     * @param	offset	the first byte to transfer from the array.
     * @param	length	the number of bytes to transfer from the array to
     *			virtual memory.
     * @return	the number of bytes successfully transferred.
     */
    public int writeVirtualMemory(int vaddr, byte[] data, int offset,
				  int length) {
Lib.assertTrue(offset >= 0 && length >= 0
				&& offset + length <= data.length);

	Lib.assertTrue(offset >= 0 && length >= 0 && offset+length <= data.length);

	byte[] memory = Machine.processor().getMemory();
	
	int virtPageNum=Machine.processor().pageFromAddress(vaddr);
	int virtOffset=Machine.processor().offsetFromAddress(vaddr);
	int phyAddr=pageTable[virtPageNum].ppn*pageSize+virtOffset;
	//check if the address is valid, the page table entry is valid and not read only. 
	if (phyAddr < 0 || phyAddr >= memory.length || !pageTable[virtPageNum].valid || pageTable[virtPageNum].readOnly )
	{
		return 0;
	}
	//set the entry as used and dirty
	pageTable[virtPageNum].used=true;
	pageTable[virtPageNum].dirty=true;
	

	int amount = Math.min(length, memory.length-phyAddr);
	System.arraycopy(data, offset, memory, phyAddr, amount);

	return amount;
    }

    /**
     * Load the executable with the specified name into this process, and
     * prepare to pass it the specified arguments. Opens the executable, reads
     * its header information, and copies sections and arguments into this
     * process's virtual memory.
     *
     * @param	name	the name of the file containing the executable.
     * @param	args	the arguments to pass to the executable.
     * @return	<tt>true</tt> if the executable was successfully loaded.
     */
    private boolean load(String name, String[] args) {
	Lib.debug(dbgProcess, "UserProcess.load(\"" + name + "\")");
	
	OpenFile executable = ThreadedKernel.fileSystem.open(name, false);
	if (executable == null) {
	    Lib.debug(dbgProcess, "\topen failed");
	    return false;
	}

	try {
	    coff = new Coff(executable);
	}
	catch (EOFException e) {
	    executable.close();
	    Lib.debug(dbgProcess, "\tcoff load failed");
	    return false;
	}

	// make sure the sections are contiguous and start at page 0
	numPages = 0;
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    if (section.getFirstVPN() != numPages) {
		coff.close();
		Lib.debug(dbgProcess, "\tfragmented executable");
		return false;
	    }
	    numPages += section.getLength();
	}

	// make sure the argv array will fit in one page
	byte[][] argv = new byte[args.length][];
	int argsSize = 0;
	for (int i=0; i<args.length; i++) {
	    argv[i] = args[i].getBytes();
	    // 4 bytes for argv[] pointer; then string plus one for null byte
	    argsSize += 4 + argv[i].length + 1;
	}
	if (argsSize > pageSize) {
	    coff.close();
	    Lib.debug(dbgProcess, "\targuments too long");
	    return false;
	}

	// program counterPIDPID initially points at the program entry point
	initialPC = coff.getEntryPoint();	

	// next comes the stack; stack pointer initially points to top of it
	numPages += stackPages;
	initialSP = numPages*pageSize;

	// and finally reserve 1 page for arguments
	numPages++;

	if (!loadSections())
	    return false;

	// store arguments in last page
	int entryOffset = (numPages-1)*pageSize;
	int stringOffset = entryOffset + args.length*4;

	this.argc = args.length;
	this.argv = entryOffset;
	
	for (int i=0; i<argv.length; i++) {
	    byte[] stringOffsetBytes = Lib.bytesFromInt(stringOffset);
	    Lib.assertTrue(writeVirtualMemory(entryOffset,stringOffsetBytes) == 4);
	    entryOffset += 4;
	    Lib.assertTrue(writeVirtualMemory(stringOffset, argv[i]) ==
		       argv[i].length);
	    stringOffset += argv[i].length;
	    Lib.assertTrue(writeVirtualMemory(stringOffset,new byte[] { 0 }) == 1);
	    stringOffset += 1;
	}

	return true;
    }

    /**
     * Allocates memory for this process, and loads the COFF sections into
     * memory. If this returns successfully, the process will definitely be
     * run (this is the last step in process initialization that can fail).
     *
     * @return	<tt>true</tt> if the sections were successfully loaded.
     */
    protected boolean loadSections() {
	if (numPages > Machine.processor().getNumPhysPages()) {
	    coff.close();
	    Lib.debug(dbgProcess, "\tinsufficient physical memory");
	    return false;
	}

	// load sections
	for (int s=0; s<coff.getNumSections(); s++) {
	    CoffSection section = coff.getSection(s);
	    
	    Lib.debug(dbgProcess, "\tinitializing " + section.getName() + " section (" + section.getLength() + " pages)");

	    	    
	    for (int i=0; i<section.getLength(); i++) {
		int vpn = section.getFirstVPN()+i;
		
		pageTable[vpn].ppn=UserKernel.addPage();//set the physical page number to the first free page in the global page table
		pageTable[vpn].valid=true;// set the page entry to valid
		pageTable[vpn].readOnly=section.isReadOnly();// set the page entry to read only if the section is read only
		section.loadPage(i, pageTable[i].ppn);
	    }
	}
	
	return true;
    }

    /**
     * Release any resources allocated by <tt>loadSections()</tt>.
     */
    protected void unloadSections() {
	    for(int i=0;i<pageTable.length;i++)
    	{
    		if(pageTable[i].valid)//check if the entry is valid
    		{
    			UserKernel.removePage(pageTable[i].ppn);
    		}
	    }
    }   

    /**
     * Initialize the processor's registers in preparation for running the
     * program loaded into this process. Set the PC register to point at the
     * start function, set the stack pointer register to point at the top of
     * the stack, set the A0 and A1 registers to argc and argv, respectively,
     * and initialize all other registers to 0.
     */
    public void initRegisters() {
	Processor processor = Machine.processor();

	// by default, everything's 0
	for (int i=0; i<processor.numUserRegisters; i++)
	    processor.writeRegister(i, 0);

	// initialize PC and SP according
	processor.writeRegister(Processor.regPC, initialPC);
	processor.writeRegister(Processor.regSP, initialSP);

	// initialize the first two argument registers to argc and argv
	processor.writeRegister(Processor.regA0, argc);
	processor.writeRegister(Processor.regA1, argv);
    }

    /**
     * Handle the halt() system call. 
     */
    private int handleHalt() {

	Machine.halt();
	
	Lib.assertNotReached("Machine.halt() did not halt machine!");
	return 0;
    }


    private static final int
        syscallHalt = 0,
	syscallExit = 1,
	syscallExec = 2,
	syscallJoin = 3,
	syscallCreate = 4,
	syscallOpen = 5,
	syscallRead = 6,
	syscallWrite = 7,
	syscallClose = 8,
	syscallUnlink = 9;

private int handleExit(int status){
	//set exit reach conditons true
	this.ExitStat = status;
	Normalexit=true;
	//unloading all resources 
	unloadSections();
	//close all descriptors
	for (int i = 2; i < descriptors.length; i++) {
	    if (descriptors[i] != null)
	    	handleClose(i);
	    	//descriptors[i].close();
	}
	//remove all elments in the childlist
	cleanProcess();
	//kill all processes
	if(ProcessID==0){
		Kernel.kernel.terminate();
	}else{
		UThread.finish();
	}
	return status;

}

    private void cleanProcess(){
 	int childrenNum=ChildPro.size();
 	for(int i=0;i<childrenNum;i++){
 		UserProcess child=ChildPro.remove(ProcessID);
 		child.parentProcess=null;
 	}
 }

	private int handleExec(int a0, int a1, int a2){
		//exec(char *name, int argc, char **argv)
		//a0 = filename, 
		//a1 = argc -> number of arguments to pass to the child process
		//a2 = argv -> starting virtual address of the null-terminated string
		String name = readVirtualMemoryString(a0, 256);
		String[] nameSplit = name.split("\\.");
		String[] a1Holder = new String[a1];
		String coffSection = nameSplit[nameSplit.length - 1];

		if( a1 < 0 || name == null || a0 < 0 || !coffSection.toLowerCase().equals("coff"))
		{
			Lib.debug(dbgProcess, "Did not pass Error Check");
			return -1;
		}
		// iterate through "number of arguements to pass to the child process
		for(int i = 0; i < a1; i++)
		{
			byte[] ptr = new byte[256];
			int bytePtr;
			bytePtr = readVirtualMemory(a2 + i*4, ptr);
			//Check Pointer
			if(bytePtr != 4)
			{
				Lib.debug(dbgProcess, "Wrong Pointer...");
				return -1;
			}
			//Checking Arguement
			if(readVirtualMemoryString(Lib.bytesToInt(ptr, 0), 256) == null)
			{
				Lib.debug(dbgProcess, "Wrong Aruement...");
				return -1;
			}
			a1Holder[i] = readVirtualMemoryString(Lib.bytesToInt(ptr, 0), 256);

			
		}

		UserProcess Child = newUserProcess();


		if(Child.execute(name, a1Holder)){
		this.ChildPro.add(Child);
		Child.parentProcess = this;
		return Child.ProcessID;
		}
		else{
			return -1;
		}

		//return Child.ProcessID;
		}

private int handleJoin(int processID,int status){
	//if processID and argument are less than 0,which should not happen return -1
	if(processID < 0||status < 0){
		return -1;
	}
	//create a new Userprocess to hold items in the child arraylish
	UserProcess temp21=null;
	int childrenNum=ChildPro.size();
	//Check the child list for correct child
	for(int i=0;i<childrenNum;i++){
		if(ChildPro.get(i).ProcessID==processID){
			temp21=ChildPro.get(i);
			break;
		}
	}
	// if we didn't find a child or the parenProcess is a null(meaning it is a parent process)
	if(temp21==null||temp21.parentProcess ==null){
		return -1;
	}
	//use previous join in project1
	temp21.thread.join();

	//create a buffer
		byte[] buffer=new byte[4];
		Lib.bytesFromInt(buffer, 0, temp21.ExitStat);
		//do a write in
		int count=writeVirtualMemory(status,buffer);
		//if it which handle exit
		if(count==4 && temp21.Normalexit==true){
			return 1;
		}
		//if not, return a fail
		else{
			
			return 0;
		}
	//}
}

private int handleClose(int a0) {
 	
 	//check to see if it is trying to chose a descriptor that is outside the range of the OpenFile 
 	if(a0 < 0 || a0 > 15) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if the descriptor that is trying to be read is empty
 	if(descriptors[a0] == null) {
 		
 		return -1;
 		
 	}else {
 	
 	//close the descriptor that is being used
 	descriptors[a0].close();
 	
 	//make sure to set it to zero so descriptor can be used
 	descriptors[a0] = null;
 	
 	}
 	
 	return 0;
 	
 }

private int handleCreate(int a0) {
 	
 	//creates a string variable to get the name of the new table being created
 	String newfilename = readVirtualMemoryString(a0,256);
 	
 	//finds an empty file descriptor
 	int freeDescriptor = findDescriptor();
 	
 	//check to see if the the virtual address is empty
 	if(a0 < 0) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if there was a problem with getting the table name
 	if(newfilename == null) {
 		
 		return -1;
 		
 	}
 	
 	//checks to see if there was a problem with getting an available descriptor
 	if(freeDescriptor == -1) {
 		
 		return -1;
 		
 	}else {
 		
 		//create the new file descriptor
 		OpenFile file = ThreadedKernel.fileSystem.open(newfilename, true);
 		
 		//check to see if file returned nothing
 		if(file == null) {
 			
 			return -1;
 			
 		}else {
 			
 			//add the new file descriptor into the available descriptor index
 			descriptors[freeDescriptor] = file;
 			return freeDescriptor;
 			
 		}
 		
 	}
 	
 }

 private int findDescriptor() {
 	
 	/*
 	A small function that loops through
 	each file Descriptor index to find 
 	an index that is null
 	*/
 	
 	for(int i = 0; i < descriptors.length; i++) {
 		
 		if(descriptors[i] == null){
 			
 			return i;
 			
 		}
 		
 	}
 	
 	return -1;
 	
 }

private int handleOpen(int a0) {
 	
 	//creates a string variable to get the name of the new table being created
 	String newfilename = readVirtualMemoryString(a0,256);
 	
 	//finds an empty file descriptor
 	int freeDescriptor = findDescriptor();
 	
 	//check to see if the the virtual address is empty
 	if(a0 < 0) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if there was a problem with getting the table name
 	if(newfilename == null) {
 		
 		return -1;
 		
 	}
 	
 	//checks to see if there was a problem with getting an available descriptor
 	if(freeDescriptor == -1) {
 		
 		return -1;
 		
 	}else {
 		
 		//open the new file descriptor
 		OpenFile file = ThreadedKernel.fileSystem.open(newfilename, false);
 		
 		//check to see if file returned nothing
 		if(file == null) {
 			
 			return -1;
 			
 		}else {
 			
 			//add the new file descriptor into the available descriptor index
 			descriptors[freeDescriptor] = file;
 			return freeDescriptor;
 			
 		}
 		
 	}
 	
 }

private int handleRead(int a0, int a1, int a2) {
 	
 	//create a byte buffer
 	byte[] buffer = new byte[a2];
 	
 	//check to see if it is trying to chose a descriptor that is outside the range of the OpenFile 
 	if(a0 < 0 || a0 > 15) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if the buffer address is empty
 	if(a2 < 0) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if the descriptor that is trying to be read is empty
 	if(descriptors[a0] == null) {
 		
 		return -1;
 		
 	}
 	
 	//add the descriptor file to a temp
 	OpenFile readFile = descriptors[a0];
 	
 	//get the total amount read
 	int amountRead = readFile.read(buffer, 0, a2);
 	
 	//check to see if there was a problem
 	if(amountRead == -1) {
 		
 		return -1;
 		
 	}
 	
 	//get the total read from virtual memory and return it
 	int totalRead = writeVirtualMemory(a1, buffer, 0, amountRead);
 	
 	return totalRead;

 	
 }

private int handleWrite(int a0, int a1, int a2) {
 	
 	//create a byte buffer
 	byte[] buffer = new byte[a2];
 	
 	//check to see if it is trying to chose a descriptor that is outside the range of the OpenFile 
 	if(a0 < 0 || a0 > 15) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if the buffer address is empty
 	if(a2 < 0) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if the descriptor that is trying to be read is empty
 	if(descriptors[a0] == null) {
 		
 		return -1;
 		
 	}
 	
 	//add the descriptor file to a temp
 	OpenFile readFile = descriptors[a0];
 	
 	//check to see how much is being written
 	int amountWrite = readVirtualMemory(a1, buffer, 0 , a2);
 	
 	//get the total amount written
 	int totalWrite = readFile.write(buffer, 0, amountWrite);
 	
 	//check to see if while written there was an error
 	if(totalWrite == -1) {
 		
 		return -1;
 		
 	}
 	
 	return totalWrite;
 	
 }

private int handleUnlink(int a0) {
 	
 	//get the file name that is going to be unlinked
 	String FileToUnlink = readVirtualMemoryString(a0, 256);
 	
 	//create a temp OpenFile that will be unlinked
 	OpenFile FileUnlink;
 	
 	//int variable to check if there was a problem
 	int index = -1;
 	
 	//check to see if the virtual address a empty
 	if(a0 < 0) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if there was a problem getting the file to unlink
 	if(FileToUnlink == null) {
 		
 		return -1;
 		
 	}
 	
 	/*
 	loop through each descriptor to see if it is null,
 	if it is not null you want to check if it is the file
 	descriptor that we want to unlink with the temp OpenFile
 	variable we created. If yes, then we break out of the loop
 	with the index it was found on.
 	*/
 	
 	for(int i = 0; i < 16; i++) {
 		
 		FileUnlink = descriptors[i];
 		
 		if(FileUnlink != null && FileUnlink.getName().compareTo(FileToUnlink) == 0) {
 			
 			index = i;
 			break;
 			
 		}
 		
 	}
 	
 	//check to see if the index was changed, if not the file could not be found
 	if(index != -1) {
 		
 		return -1;
 		
 	}
 	
 	//check to see if there was a problem removing the file that needs to be unlinked
 	if(!ThreadedKernel.fileSystem.remove(FileToUnlink)) {
 		
 		return -1;
 		
 	}

 	
 	return 0;
 	
 }

    /**
     * Handle a syscall exception. Called by <tt>handleException()</tt>. The
     * <i>syscall</i> argument identifies which syscall the user executed:
     *
     * <table>
     * <tr><td>syscall#</td><td>syscall prototype</td></tr>
     * <tr><td>0</td><td><tt>void halt();</tt></td></tr>
     * <tr><td>1</td><td><tt>void exit(int status);</tt></td></tr>
     * <tr><td>2</td><td><tt>int  exec(char *name, int argc, char **argv);
     * 								</tt></td></tr>
     * <tr><td>3</td><td><tt>int  join(int pid, int *status);</tt></td></tr>
     * <tr><td>4</td><td><tt>int  creat(char *name);</tt></td></tr>
     * <tr><td>5</td><td><tt>int  open(char *name);</tt></td></tr>
     * <tr><td>6</td><td><tt>int  read(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>7</td><td><tt>int  write(int fd, char *buffer, int size);
     *								</tt></td></tr>
     * <tr><td>8</td><td><tt>int  close(int fd);</tt></td></tr>
     * <tr><td>9</td><td><tt>int  unlink(char *name);</tt></td></tr>
     * </table>
     * 
     * @param	syscall	the syscall number.
     * @param	a0	the first syscall argument.
     * @param	a1	the second syscall argument.
     * @param	a2	the third syscall argument.
     * @param	a3	the fourth syscall argument.
     * @return	the value to be returned to the user.
     */
    public int handleSyscall(int syscall, int a0, int a1, int a2, int a3) {
	switch (syscall) {
	case syscallHalt:
	    return handleHalt();
	case syscallExit:
		return handleExit(a0);
	case syscallExec:
		return handleExec(a0,a1,a2);
	case syscallJoin:
		return handleJoin(a0,a1);
	case syscallCreate:
		return handleCreate(a0);
	case syscallOpen:
		return handleOpen(a0);
	case syscallRead:
		return handleRead(a0,a1,a2);
	case syscallWrite:
		return handleWrite(a0,a1,a2);
	case syscallClose:
		return handleClose(a0);
	case syscallUnlink:
		return handleUnlink(a0);

	default:
	    Lib.debug(dbgProcess, "Unknown syscall " + syscall);
	    Lib.assertNotReached("Unknown system call!");
	}
	return 0;
    }

    /**
     * Handle a user exception. Called by
     * <tt>UserKernel.exceptionHandler()</tt>. The
     * <i>cause</i> argument identifies which exception occurred; see the
     * <tt>Processor.exceptionZZZ</tt> constants.
     *
     * @param	cause	the user exception that occurred.
     */
    public void handleException(int cause) {
	Processor processor = Machine.processor();

	switch (cause) {
	case Processor.exceptionSyscall:
	    int result = handleSyscall(processor.readRegister(Processor.regV0),
				       processor.readRegister(Processor.regA0),
				       processor.readRegister(Processor.regA1),
				       processor.readRegister(Processor.regA2),
				       processor.readRegister(Processor.regA3)
				       );
	    processor.writeRegister(Processor.regV0, result);
	    processor.advancePC();
	    break;				       
				       
	default:
	    Lib.debug(dbgProcess, "Unexpected exception: " +
		      Processor.exceptionNames[cause]);
	    Lib.assertNotReached("Unexpected exception");
	}
    }

    /** The program being run by this process. */
    protected Coff coff;

    /** This process's page table. */
    protected TranslationEntry[] pageTable;
    /** The number of contiguous pages occupied by the program. */
    protected int numPages;

    /** The number of pages in the program's stack. */
    protected final int stackPages = 8;
        public OpenFile[] descriptors;
	protected OpenFile stdin;
	protected OpenFile stdout;
    private int initialPC, initialSP;
    private int argc, argv;
	private boolean Normalexit = false;
	public int count;
	private int ProcessID;
private UserProcess parentProcess;
	private ArrayList<UserProcess> ChildPro = new ArrayList<UserProcess>();
	protected HashMap<Integer,Integer> childrenExitStatus;
	protected Lock JoinLock;
	protected Lock ExitLock;
	private Integer status = null;
	public int ExitStat;
	
	protected Lock counterPIDLock = new Lock();
	protected static int counterPID = 0;


    private static final int pageSize = Processor.pageSize;
    private static final char dbgProcess = 'a';
    KThread thread;
}
