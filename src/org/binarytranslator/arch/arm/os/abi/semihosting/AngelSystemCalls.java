package org.binarytranslator.arch.arm.os.abi.semihosting;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.RandomAccessFile;
import java.util.HashMap;
import java.util.Map;

import org.binarytranslator.DBT;
import org.binarytranslator.DBT_Options;
import org.binarytranslator.Main;
import org.binarytranslator.arch.arm.os.process.ARM_ProcessSpace;

/**
 * ARM Ltd. also defined a standard system call interface called Angel.
 * It is usually used in small embedded environments and abstracts a number of basic system functions,
 * among them
 * 
 * <ul>
 *  <li>Character input / output
 *  <li>File access
 *  <li>Timer access
 * </ul>
 * 
 * This class implements the Angel system calls on Pearcolator.
 * 
 * @author Michael Baer
 */
public class AngelSystemCalls {
  
  /** Angel calls requesting user input read it from this stream. */
  private final InputStream consoleInput = System.in;
  
  /** Angels calls that output characters to the console write it to this stream.*/
  private final PrintStream consoleOutput = System.out;
  
  /** The Processspace that we're operating on. */
  private final ARM_ProcessSpace ps;
  
  /** A mapping from file handles to open files. */
  private final Map<Integer, AngelFileStream> files = new HashMap<Integer, AngelFileStream>();
  
  /** The directory in which temporary files are created. Note that the path is expected to end with a path delimiter.*/
  private final static String TEMP_FILE_DIR = "/tmp/";
  
  /** The file handle that is distributed with the next call to {@link #addFile(RandomAccessFile)}. 
   * Valid Angle handles are non-zero values (i.e. >= 1).*/
  private int nextFileHandle = 1;
  
  /** */
  private AngelSystemCall[] sysCalls;
  
  public AngelSystemCalls(ARM_ProcessSpace ps) {
    this.ps = ps;
    sysCalls = new AngelSystemCall[0x32];
    
    sysCalls[0x1] = new Sys_Open();
    sysCalls[0x2] = new Sys_Close();
    sysCalls[0x3] = new Sys_WriteC();
    sysCalls[0x4] = new Sys_Write0();
    sysCalls[0x5] = new Sys_Write();
    sysCalls[0x6] = new Sys_Read();
    sysCalls[0x7] = new Sys_ReadC();
    sysCalls[0x8] = new Sys_IsError();
    sysCalls[0x9] = new Sys_IsTty();
    sysCalls[0xA] = new Sys_Seek();
    sysCalls[0xB] = null; //Angel docs don't say what this call is supposed to be...
    sysCalls[0xC] = new Sys_Flen();
    sysCalls[0xD] = new Sys_TmpNam();
    sysCalls[0xE] = new Sys_Remove();
    sysCalls[0xF] = new Sys_Rename();
    sysCalls[0x10] = new Sys_Clock();
    sysCalls[0x11] = new Sys_Time();
    sysCalls[0x12] = new Sys_System();
    sysCalls[0x13] = new Sys_Errno();
    sysCalls[0x14] = null; //Another undefined call
    sysCalls[0x15] = new Sys_Get_CmdLine();
    sysCalls[0x16] = new Sys_HeapInfo();
    sysCalls[0x18] = new Sys_Exit();
    sysCalls[0x30] = new Sys_Elapsed();
    sysCalls[0x31] = new Sys_TickFreq();
  }
  
  public void doSysCall(int callNum) {
    try {
      
      if (DBT_Options.debugSyscall)
        System.out.println("Executing Angel Syscall: " + callNum);
      
      sysCalls[callNum].execute();
    }
    catch (NullPointerException e) {
      throw new RuntimeException("Not implemented angel call number: " + callNum);
    }
    catch (ArrayIndexOutOfBoundsException e) {
      throw new RuntimeException("Invalid angel call number: " + callNum);
    }
  }
  
  /** Add a file to the open file table and return its handle. */
  private int addFile(AngelFileStream file) {
    int handle = nextFileHandle++;
    files.put(handle, file);
    return handle;
  }
  
  /** Returns the file associated with an open file handle or null, if that file handle does not exist. */
  private AngelFileStream getFile(int handle) {
    return files.get(handle);
  }
  
  private boolean closeFile(int handle) {
    try {
      AngelFileStream file = files.get(handle);
      file.close();
      return true;
    }
    catch (Exception e) {
      return false;
    }
    finally {
      files.remove(handle);
    }
  }
  
  private interface AngelFileStream {
    
    boolean isTty();
    int getLength();
    void close();
    
    int read() throws IOException;
    int read(byte[] buffer) throws IOException;
    
    void write(int b) throws IOException;
    void write(byte[] buffer) throws IOException;

    void seek(long pos) throws IOException;
  }
  
  private class ConsoleStream implements AngelFileStream {
    
    private String previousInputLine = null;

    public void close() {
      throw new RuntimeException("Stdin and stdout are not closeable.");
    }

    public int getLength() {
      return 0;
    }

    public boolean isTty() {
      return false;
    }

    public int read() throws IOException {
      return System.in.read();
    }

    public int read(byte[] buffer) throws IOException {
      
      //do we already have unhandled input?
      if (previousInputLine == null) {
        //if not, query a line from the prompt
        
        previousInputLine = new BufferedReader(new InputStreamReader(System.in)).readLine();

        //don't forget the Angel expects us to submit a carriage return etc. too
        previousInputLine += "\n";
      }
        
      if (DBT.VerifyAssertions) DBT._assert(previousInputLine != null);
      
      int bytesToRead = Math.min(previousInputLine.length(), buffer.length);
      
      for (int i = 0; i < bytesToRead; i++) {
        buffer[i] = (byte)previousInputLine.charAt(i);
      }
      
      //if we put the complete line into the buffer, then read a new line the next time
      if (bytesToRead == previousInputLine.length())
        previousInputLine = null;

      return bytesToRead;
    }
    
    public void seek(long pos) throws IOException {
      throw new IOException("The stdin and stdout are not seekable.");
    }

    public void write(int b) throws IOException {
      System.out.write(b);
    }

    public void write(byte[] buffer) throws IOException {
      System.out.write(buffer);      
    }
  }
  
  private class FileStream implements AngelFileStream {
    
    private final RandomAccessFile file;
    
    public FileStream(RandomAccessFile file) {
      this.file = file;
    }

    public void close() {
      try {
        file.close();
      } 
      catch (IOException e) {
        e.printStackTrace();
      }
    }

    public int getLength() {
      try {
        return (int)file.length();
      } 
      catch (IOException e) {
        e.printStackTrace();
        return 0;
      }
    }

    public boolean isTty() {
      return false;
    }

    public int read() throws IOException {
      return file.read();
    }

    public int read(byte[] buffer) throws IOException {
      return file.read(buffer);
    }

    public void seek(long pos) throws IOException {
     file.seek(pos); 
    }

    public void write(int b) throws IOException {
     file.write(b); 
    }

    public void write(byte[] buffer) throws IOException {
      file.write(buffer);
    }
  }
  
  abstract class AngelSystemCall {
    public abstract void execute();
    
    protected final void setReturn(int value) {
      ps.registers.set(0, value);
    }
    
    protected String readString(int ptrBuffer, int length) {
      String s = "";
      
      for (int i = 0; i < length; i++) {
        s += (char)ps.memory.loadUnsigned8(ptrBuffer++);
      }
      
      return s;
    }
    
    protected void writeString(String text, int ptrBuffer) {
      for (int i = 0; i < text.length(); i++) {
        ps.memory.store8(ptrBuffer++, text.charAt(i));
      }
      
      ps.memory.store8(ptrBuffer, 0);
    }
  }
  
  class Sys_Open extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int ptrBuffer = ps.memory.load32(ptrParamBlock);
      int fileMode = ps.memory.load32(ptrParamBlock + 4);
      int length = ps.memory.load32(ptrParamBlock + 8);
      
      String fileName = readString(ptrBuffer, length);
      
      if (DBT_Options.debugSyscallMore)
        System.out.println("Opening file: " + fileName);

      try {
        AngelFileStream stream;
        
        //Angel uses a special file called ":tt" to denote stdin / stdout
        if (fileName.equals(":tt")) {
          //we're supposed to open the console
          stream = new ConsoleStream();
        }
        else {
          //we're supposed to open a file
          String openMode = (fileMode >= 4) ? "rw" : "w";
          RandomAccessFile file = new RandomAccessFile(fileName, openMode);
          stream = new FileStream(file);
        }
        
        //return the file's index within this table as a file handle
        setReturn(addFile(stream));
        
      } catch (FileNotFoundException e) {
        
        //return with an error
        setReturn(-1);
      }
    }
  }
    
  class Sys_Close extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int fileHandle = ps.memory.load32(ptrParamBlock);
      
      setReturn(closeFile(fileHandle) ? 0 : -1);
    }
  }
  
  class Sys_WriteC extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrCharToOutput = ps.registers.get(1);
      char output = (char)ps.memory.loadUnsigned8(ptrCharToOutput);
      
      if (output != 0)
        consoleOutput.print(output);
    }
  }
  
  class Sys_Write0 extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrOutput = ps.registers.get(1);
      char output = (char)ps.memory.loadUnsigned8(ptrOutput++);
      
      while (output != 0) {
        
        if (output != 13)
          consoleOutput.print(output);
        
        output = (char)ps.memory.loadUnsigned8(ptrOutput++);
      }
    }
  }
  
  class Sys_Write extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int fileHandle = ps.memory.load32(ptrParamBlock);
      int ptrBuffer  = ps.memory.load32(ptrParamBlock + 4);
      int length     = ps.memory.load32(ptrParamBlock + 8);
      
      try {
        AngelFileStream file = getFile(fileHandle);
        
        //first try to read the whole buffer from memory
        byte[] buf = new byte[length];
        
        for (int i = 0; i < length; i++)
          buf[i] = (byte)ps.memory.loadUnsigned8(ptrBuffer++);
        
        file.write(buf);
        length = 0;
      }
      catch (Exception e) {}
      
      //return the number of chars that have not been written
      setReturn(length);
    }
  }
  
  class Sys_Read extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int fileHandle = ps.memory.load32(ptrParamBlock);
      int ptrBuffer  = ps.memory.load32(ptrParamBlock + 4);
      int length     = ps.memory.load32(ptrParamBlock + 8);
      
      AngelFileStream file = getFile(fileHandle);
      
      //fail with EOF if the handle is invalid. Angel does not provide any facilities to
      //notify about an invalid handle
      if (file == null) {
        setReturn(length);
        return;
      }
      
      byte buf[] = new byte[length];
      try {
        int bytesRead = file.read(buf);
        
        //store the retrieved info into the buffer
        for (int i = 0; i < bytesRead; i++)
          ps.memory.store8(ptrBuffer++, buf[i]);
        
        if (bytesRead == length) {
          setReturn(0);
        }
        else {
          setReturn(bytesRead + 2*length);
        }
        
      } catch (IOException e1) {
        e1.printStackTrace();
        
        //due to us not having a better return code, just return EOF.
        setReturn(length);
        return;
      }
    }
  }
  
  class Sys_ReadC extends AngelSystemCall {

    @Override
    public void execute() {
      try {
        int value = consoleInput.read();
        
        //skip #13, because that's what Angel seems to do.
        while (value == 13)
          value = consoleInput.read();
        
        if (value == -1)
          throw new RuntimeException("Unable to read further characters from console");

        setReturn(value);
      }
      catch (IOException e) {
        throw new RuntimeException("Error while reading character from console.", e);
      }
    }
  }
  
  class Sys_IsError extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int statusWord = ps.memory.load32(ptrParamBlock);
      
      setReturn(statusWord < 0 ? -1 : 0);
    }
  }
  
  class Sys_IsTty extends AngelSystemCall {

    @Override
    public void execute() {
      
      int ptrParamBlock = ps.registers.get(1);
      int fileHandle = ps.memory.load32(ptrParamBlock);
      
      AngelFileStream file = getFile(fileHandle);
      
      if (file != null) {
        setReturn( file.isTty() ? 1 : 0 );
      }
      else {
        setReturn(-1);
      }
    }
  }
  
  class Sys_Seek extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int fileHandle = ps.memory.load32(ptrParamBlock);
      int absolutePosition = ps.memory.load32(ptrParamBlock + 4);
      
      AngelFileStream file = getFile(fileHandle);
      
      try {
        file.seek(absolutePosition);
        setReturn(0);
      } catch (Exception e) { //this path also catches null-pointer exceptions, in case an invalid handle is given
        setReturn(-1);
      }
    }
  }
  
  class Sys_Flen extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int fileHandle = ps.memory.load32(ptrParamBlock);
      
      AngelFileStream file = getFile(fileHandle);
      
      try {
        setReturn(file.getLength());
      }
      catch (Exception e) {  //this path also catches null-pointer exceptions, in case an invalid handle is given
        setReturn(-1);
      }
    }
  }
  
  class Sys_TmpNam extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int ptrBuffer = ps.memory.load32(ptrParamBlock);
      int identifier = ps.memory.load32(ptrParamBlock + 4);
      int bufferLength = ps.memory.load32(ptrParamBlock + 8);
      
      String tmpFile = TEMP_FILE_DIR + "angel_" + identifier + ".tmp";
      
      //do we have enough buffer space to write the tmp file?
      if (bufferLength < tmpFile.length() + 1) {
        setReturn(-1);
        return;
      }
      
      //write the name tmpFile to the buffer
      writeString(tmpFile, ptrBuffer);
      setReturn(0);
    }
  }
  
  class Sys_Remove extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int ptrBuffer = ps.memory.load32(ptrParamBlock);
      int length = ps.memory.load32(ptrParamBlock + 4);
      
      String fileName = readString(ptrBuffer, length);
      File f = new File(fileName);
      
      setReturn(f.delete() ? 0 : -1);
    }
  }
  
  class Sys_Rename extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int ptrOldFile = ps.memory.load32(ptrParamBlock);
      int oldLength = ps.memory.load32(ptrParamBlock + 4);
      
      int ptrNewFile = ps.memory.load32(ptrParamBlock + 8);
      int newLength = ps.memory.load32(ptrParamBlock + 12);
      
      String oldFile = readString(ptrOldFile, oldLength);
      String newFile = readString(ptrNewFile, newLength);
      
      File f = new File(oldFile);
      boolean success = f.renameTo(new File(newFile));
      
      setReturn(success ? 0 : -1);
    }
  }
  
  class Sys_Clock extends AngelSystemCall {
    
    private final long startClock = System.currentTimeMillis();

    @Override
    public void execute() {
      setReturn((int)(System.currentTimeMillis() - startClock) / 10);
    }
  }
  
  class Sys_Time extends AngelSystemCall {

    @Override
    public void execute() {
      setReturn((int)(System.currentTimeMillis() * 1000));
    }
  }
  
  class Sys_System extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int ptrCommand = ps.memory.load32(ptrParamBlock);
      int length = ps.memory.load32(ptrParamBlock + 4);
      
      String command = readString(ptrCommand, length);
      
      throw new RuntimeException("ARM Angel is supposed to execute command on host: " + command);
    }
  }
  
  class Sys_Errno extends AngelSystemCall {

    @Override
    public void execute() {
      setReturn(0);
    }
  }
  
  class Sys_Get_CmdLine extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      int ptrBuffer = ps.memory.load32(ptrParamBlock);
      int length = ps.memory.load32(ptrParamBlock + 4);
      
      String cmdLine = DBT_Options.executableFile;
      
      if (cmdLine.contains(" "))
        cmdLine = '"' + cmdLine + '"';
      
      for(String s : DBT_Options.executableArguments) {
        if (s.contains(" ")) {
          s = '"' + s + '"';
        }
          
        cmdLine += " " + s;
      }
      
      if (length < cmdLine.length() + 1) {
        setReturn(-1);
        return;
      }
      
      writeString(cmdLine, ptrBuffer);
      ps.memory.store32(ptrParamBlock + 4, cmdLine.length() + 1);
      setReturn(0);
    }
  }
  
  class Sys_HeapInfo extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      
      //return that we couldn't calculate any of the requested heap size values
      ps.memory.store32(ptrParamBlock, 0);
      ps.memory.store32(ptrParamBlock + 4, 0);
      ps.memory.store32(ptrParamBlock + 8, 0);
      ps.memory.store32(ptrParamBlock + 12, 0);
    }
  }
  
  class Sys_Elapsed extends AngelSystemCall {

    @Override
    public void execute() {
      int ptrParamBlock = ps.registers.get(1);
      long elapsedTime = System.nanoTime();
      
      ps.memory.store32(ptrParamBlock, (int)elapsedTime);
      ps.memory.store32(ptrParamBlock + 4, (int)(elapsedTime >> 32));
      setReturn(0);
    }
  }
  
  class Sys_TickFreq extends AngelSystemCall {

    @Override
    public void execute() {
      setReturn(1000000000); //Return that ticks are measured in nanoseconds
    }
  }
  
  class Sys_Exit extends AngelSystemCall {

    @Override
    public void execute() {
      ps.finished = true;
      Main.onExit(0);
    }
    
  }
}
