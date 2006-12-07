/*
 * This file is part of binarytranslator.org. The binarytranslator.org
 * project is distributed under the Common Public License (CPL).
 * A copy of the license is included in the distribution, and is also
 * available at http://www.opensource.org/licenses/cpl1.0.php
 *
 * (C) Copyright Ian Rogers, The University of Manchester 2003-2006
 */
package org.binarytranslator.generic.gdbstub;

import org.binarytranslator.generic.os.process.ProcessSpace;
import org.binarytranslator.generic.fault.BadInstructionException;
import java.net.ServerSocket;
import java.net.Socket;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * Interface to GDB
 */
public final class GDBStub {

  /**
   * The socket that connections will arrive on
   */
  private final Socket socket;
  /**
   * The stream to read from the socket
   */
  private final InputStream in;
  /**
   * The stream to read from the socket
   */
  private final OutputStream out;
  /**
   * A buffer used in the reading/writing of data
   */
  private final byte buffer[];
  /**
   * The process being debugged
   */
  private final ProcessSpace ps;
  /**
   * Thread to continue or step, a value of -1 means all threads, 0
   * means any thread.
   */
  private int threadToStep;
  /**
   * Thread to inspect, a value of -1 means all threads, 0 means any
   * thread.
   */
  private int threadToInspect;
  /**
   * An array of breakpoints
   */
  private int breakpoints[];
  /* GDB Stub commands */
  /** ACK - Acknowledged */
  private final static int ACK = '+';
  /** NAK - Not acknowledged, implies retransmit */
  private final static int NAK = '-';
  /** Packet start */
  private final static int START = '$';
  /** Sequence-ID separator - deprecated */
  private final static int SEQUENCE_ID_SEPERATOR = ':';
  /** Checksum start */
  private final static int CHECKSUM_START    = '#';
  /** What signal halted the stub? Default is SIGTRAP */
  private final static int LAST_SIGNAL       = '?';
  /** Set thread */
  private final static int SET_THREAD        = 'H';
  /** Stop debugging */
  private final static int KILL_DEBUG        = 'k';
  /** Get memory values */
  private final static int GET_MEM_VALUE     = 'm';
  /** Set memory values */
  private final static int SET_MEM_VALUE     = 'M';
  /** Get a register value */
  private final static int GET_REG_VALUE     = 'p';
  /** Query */
  private final static int QUERY             = 'q';
  /** A verbose packet */
  private final static int VERBOSE_PACKET    = 'v';
  /** Set memory value to binary value */
  private final static int SET_MEM_VALUE_BIN = 'X';
  /** Remove a breakpoint  */
  private final static int REMOVE_BREAKPOINT = 'z';
  /** Insert a breakpoint */
  private final static int INSERT_BREAKPOINT = 'Z';
  /* Error codes */
  private final static int CANNOT_ACCESS_MEMORY = 1;
  /**
   * Constructor
   */
  public GDBStub(int port, ProcessSpace ps) {
    try {
      ServerSocket connectionSocket = new ServerSocket(port);
      socket = connectionSocket.accept();
      in = socket.getInputStream();
      out = socket.getOutputStream();
      buffer = new byte[256];
      getACK();
    } catch (IOException e) {
      throw new Error("Error opening socket", e);
    }
    breakpoints = new int[0];
    this.ps = ps;
  }

  /**
   * Main run loop
   */
  public void run() {
    try {
      while(socket.isConnected()) {
        int dataEnd = readPacket();
        switch(buffer[1]) {
        case GET_REG_VALUE:     handle_getRegValue(dataEnd); break;
        case GET_MEM_VALUE:     handle_getMemValue(dataEnd); break;
        case INSERT_BREAKPOINT: handle_insertBreakPoint(dataEnd); break;
        case KILL_DEBUG:        System.exit(0);
        case LAST_SIGNAL:       handle_lastSignal(dataEnd); break;
        case QUERY:             handle_query(dataEnd); break;
        case REMOVE_BREAKPOINT: handle_removeBreakPoint(dataEnd); break;
        case SET_MEM_VALUE:     handle_setMemValue(dataEnd); break;
        case SET_MEM_VALUE_BIN: handle_setMemValueBin(dataEnd); break;
        case SET_THREAD:        handle_setThread(dataEnd); break;
        case VERBOSE_PACKET:    handle_verbose(dataEnd); break;
        default: throw new Error("Unknown GDB Stub command " + (char)buffer[1]);
        }
      }
    } catch (IOException e) {
      throw new Error("Error reading/writing to socket", e);
    }
  }

  /* Packet commands */
  /**
   * Get an acknowledge
   */
  private void getACK() throws IOException {
    int command = in.read();        
    if (command != ACK) {
      throw new IOException("Acknowledge expected but got " + (char)command);
    }
  }
  /**
   * Send an acknowledge
   */
  private void sendACK() throws IOException {
    out.write(ACK);
  }

  /**
   * Read a packet into the buffer and check the checksum
   * @return the last byte in the buffer prior to the checksum
   */
  private int readPacket() throws IOException {
    // Read the packet start
    int index=0;
    buffer[index] = (byte)in.read();
    if(buffer[index] != START) {
      throw new IOException("Expected the start of a packet ($) but got " + (char)buffer[index]);
    }
    // Read the data
    int csum=0;
    do {
      index++;
      buffer[index] = (byte)in.read();
      csum += (int)buffer[index];
    } while(buffer[index] != CHECKSUM_START);
    csum -= CHECKSUM_START;
    csum &= 0xFF;
    // Abort if we got a sequence ID
    if(buffer[3] == SEQUENCE_ID_SEPERATOR) {
      throw new IOException("Found unsupported sequence ID in packet");
    }
    // Read the checksum
    index ++;
    buffer[index] = (byte)in.read();
    index ++;
    buffer[index] = (byte)in.read();
    int checkSum = (hexToInt(buffer[index-1]) << 4) | hexToInt(buffer[index]);
    if(checkSum == csum) {    
      report("Read: " + bufferToString(0, index));
      sendACK();
      return index-3;
    } else {
      throw new IOException("Packet's checksum of " + checkSum + " doesn't match computed checksum of " + csum);
    }
  }

  /**
   * Send the command
   */
  private void sendCommand(byte command[]) throws IOException { 
    buffer[0] = START;
    int index = 1;
    int csum = 0;
    if(command != null) {
      for(int i=0; i < command.length; i++, index++) {
        buffer[index] = command[i];
        csum += command[i];
      }
    }
    buffer[index] = CHECKSUM_START;
    index++;
    buffer[index] = intToHex(csum >> 4);
    index++;
    buffer[index] = intToHex(csum);
    out.write(buffer, 0, index+1);
    report("Sent: " + bufferToString(0, index));
    getACK();
  }
  /**
   * Send a reply of 'OK'
   */
  private void replyOK() throws IOException {
    byte command[] = {'O','K'};
    sendCommand(command);
  }
  /**
   * Send a message saying that a sig trap stopped us
   */
  private void sendStoppedByTrap() throws IOException {
    // report that a SIGTRAP halted the debugger
    // byte command[] = {'S','0','5'}; <- a command to just say stopped by SIGTRAP
    byte command[];
    int index;
    if (ps.hasFrameBaseRegister()) {
      // Add base pointer to packet
      command = new byte[39];
      int bpReg = ps.getGDBFrameBaseRegister();
      command[3] = intToHex(bpReg >> 4);
      command[4] = intToHex(bpReg);
      command[5] = ':';
      byte bpVal[] = ps.readRegisterGDB(bpReg);
      command[6] = intToHex(bpVal[0] >> 4);
      command[7] = intToHex(bpVal[0]);
      command[8] = intToHex(bpVal[1] >> 4);
      command[9] = intToHex(bpVal[1]);
      command[10] = intToHex(bpVal[2] >> 4);
      command[11] = intToHex(bpVal[2]);
      command[12] = intToHex(bpVal[3] >> 4);
      command[13] = intToHex(bpVal[3]);
      command[14] = ';';
      index = 15;
    } else {
      command = new byte[27];
      index = 3;
    }
    command[0] = 'T';
    command[1] = '0';
    command[2] = '5'; // stopped by trap
    { // Add stack pointer to packet
      int spReg = ps.getGDBStackPointerRegister();
      command[index] = intToHex(spReg >> 4); index++;
      command[index] = intToHex(spReg); index++;
      command[index] = ':'; index++;
      byte spVal[] = ps.readRegisterGDB(spReg);
      command[index] = intToHex(spVal[0] >> 4); index++;
      command[index] = intToHex(spVal[0]); index++;
      command[index] = intToHex(spVal[1] >> 4); index++;
      command[index] = intToHex(spVal[1]); index++;
      command[index] = intToHex(spVal[2] >> 4); index++;
      command[index] = intToHex(spVal[2]); index++;
      command[index] = intToHex(spVal[3] >> 4); index++;
      command[index] = intToHex(spVal[3]); index++;
      command[index] = ';'; index++;
    }
    { // Add program counter to packet
      int pcReg = ps.getGDBProgramCountRegister();
      command[index] = intToHex(pcReg >> 4); index++;
      command[index] = intToHex(pcReg); index++;
      command[index] = ':'; index++;
      byte pcVal[] = ps.readRegisterGDB(pcReg);
      command[index] = intToHex(pcVal[0] >> 4); index++;
      command[index] = intToHex(pcVal[0]); index++;
      command[index] = intToHex(pcVal[1] >> 4); index++;
      command[index] = intToHex(pcVal[1]); index++;
      command[index] = intToHex(pcVal[2] >> 4); index++;
      command[index] = intToHex(pcVal[2]); index++;
      command[index] = intToHex(pcVal[3] >> 4); index++;
      command[index] = intToHex(pcVal[3]); index++;
      command[index] = ';'; index++;
    }
    sendCommand(command);
  }
  /**
   * Send a reply of 'ENN' indicating an error with error code NN
   */
  private void replyError(int nn) throws IOException {
    byte command[] = {'E', intToHex(nn >> 4), intToHex(nn)};
    sendCommand(command);
  }
  /**
   * A command arrived to set the thread for subsequent operations
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_setThread(int dataEnd) throws IOException {
    if(buffer[2] == 'c') {
      threadToStep = Integer.parseInt(bufferToString(3, dataEnd));
      replyOK();
    } else if(buffer[2] == 'g') {
      threadToInspect = Integer.parseInt(bufferToString(3, dataEnd));
      replyOK();
    } else {
      replyError(0);
    }
  }
  /**
   * A query packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_query(int dataEnd) throws IOException {
    if(buffer[2] == 'C') { // query current thread
      // send reply that current thread is 1
      byte command[] = {'Q','C','0','1'};
      sendCommand(command);
    } else if (doesBufferMatch(2, new byte[] {'O','f','f','s','e','t','s'})) {
      // query relocation offsets. As the binary is loaded where it
      // hoped then we don't specify any relocation offsets.
      byte command[] = {'T','e','x','t','=','0',
                        ';','D','a','t','a','=','0',
                        ';','B','s','s','=','0'};
      sendCommand(command);
    } else if (doesBufferMatch(2, new byte[] {'S','y','m','b','o','l',':',':'})) {
      // GDB is telling us it will handle symbol queries for us - nice :-)
      replyOK();
    } else {
      // unrecognized query
      sendCommand(null);
    }
  }

  /**
   * A last signal packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_lastSignal(int dataEnd) throws IOException {
    sendStoppedByTrap();
  }

  /**
   * A get register value packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_getRegValue(int dataEnd) throws IOException {
    int regNum;
    if (buffer[3] != CHECKSUM_START) {
      regNum = (hexToInt(buffer[2]) << 4) | hexToInt(buffer[3]);
    } else {
      regNum = hexToInt(buffer[2]);
    }
    byte value[] = ps.readRegisterGDB(regNum);
    byte hexValue[] = new byte[value.length * 2];
    for(int i=0; i < value.length; i++) {
      hexValue[i*2]     = intToHex(value[i] >> 4);
      hexValue[(i*2)+1] = intToHex(value[i]);
    }
    sendCommand(hexValue);
  }

  /**
   * A get memory value packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_getMemValue(int dataEnd) throws IOException {
    String data = bufferToString(2,dataEnd);
    int address = Integer.parseInt(data.substring(0,data.indexOf(',')),16);
    int count = Integer.parseInt(data.substring(data.indexOf(',')+1),16);
    try {
      byte value[] = new byte[count*2];
      for(int i=0; i < count; i++) {
        byte byteVal = ps.memoryLoad8(address+i);
        value[i*2] = intToHex(byteVal >> 4);      
        value[(i*2)+1] = intToHex(byteVal);      
      }
      sendCommand(value);
    } catch (NullPointerException e) {
      replyError(CANNOT_ACCESS_MEMORY);
    }
  }

  /**
   * A set memory value packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_setMemValue(int dataEnd) throws IOException {
    int address = readHexValFromBuffer(2);
    int count   = readHexValFromBuffer(indexOf(2,',')+1);
    int start = indexOf(2,':')+1;
    try {
      byte value[] = new byte[2];
      for(int i=0; i < count; i++) {        
        byte byteVal = (byte)((hexToInt(buffer[start+(i*2)]) << 4) | (hexToInt(buffer[start+(i*2)+1])));
        ps.memoryStore8(address+i, byteVal);
      }
      replyOK();
    } catch (NullPointerException e) {
      replyError(CANNOT_ACCESS_MEMORY);
    }
  }
  /**
   * A set memory value packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_setMemValueBin(int dataEnd) throws IOException {
    // Report not supported
    sendCommand(null);
  }

  /**
   * A verbose packet arrived
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_verbose(int dataEnd) throws IOException {
    if (doesBufferMatch(2, new byte[] {'C','o','n','t'})) {
      if (buffer[6] == '?') { // query what verbose resume commands are supported
        byte command[] = {'v','C','o','n','t',
                          ';','s',';','S',';','c',';','C'};
        sendCommand(command);
      }
      else { // a verbose resume packet
        int index = 6;
        while(index < dataEnd) {
          if(buffer[index] != ';') {
            // values for each thread should be ';' separated
            replyError(0);
            break;
          }
          else {
            switch(buffer[index+1]) {
            case 's':
              // the next two optional characters specify the thread
              // to step, we have one thread so we ignore them
              try {
                ps.runOneInstruction();
                index = dataEnd;              
                // report that a SIGTRAP halted the debugger
                sendStoppedByTrap();
              }
              catch (BadInstructionException e) {
                // report that a SIGILL halted the debugger
                byte command[] = {'S','0','4'};
                sendCommand(command);                
              }
              break;
            case 'c':
              // the next two optional characters specify the thread
              // to step, we have one thread so we ignore them
              try {
                boolean hitBreakpoint;
                do {
                  ps.runOneInstruction();
                  hitBreakpoint = false;
                  int pc = ps.getCurrentInstructionAddress();
                  for(int i=0; i < breakpoints.length; i++) {
                    if(pc == breakpoints[i]) {
                      hitBreakpoint = true;
                      break;
                    }
                  }
                } while (!hitBreakpoint);
                index = dataEnd;  
                // report that a SIGTRAP halted the debugger
                sendStoppedByTrap();
              }
              catch (BadInstructionException e) {
                // report that a SIGILL halted the debugger
                byte command[] = {'S','0','4'};
                sendCommand(command);                
              }
              break;
            case 'S':
            case 'C':
            default:
              replyError(0);
              break;
            }
          }
        }
      }
    } else { // unknown verbose packet
      replyError(0);
    }
  }

  /**
   * Insert a break point
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_insertBreakPoint(int dataEnd) throws IOException {
    String data = bufferToString(4,dataEnd);
    int address = Integer.parseInt(data.substring(0,data.indexOf(',')),16);
    int length = Integer.parseInt(data.substring(data.indexOf(',')+1),16);
    switch(buffer[2]) { // determine the breakpoint type
    case '0': // memory break point
      int newbp[] = new int[breakpoints.length+1];
      System.arraycopy(breakpoints, 0, newbp, 0, breakpoints.length);
      newbp[breakpoints.length] = address;
      breakpoints = newbp;
      replyOK();
      break;
    default: // unrecognized breakpoint type
      sendCommand(null);
    }
  }
  /**
   * Remove a break point
   * @param dataEnd the last character in the buffer prior to the
   * checksum
   */
  private void handle_removeBreakPoint(int dataEnd) throws IOException {
    String data = bufferToString(4,dataEnd);
    int address = Integer.parseInt(data.substring(0,data.indexOf(',')),16);
    int length = Integer.parseInt(data.substring(data.indexOf(',')+1),16);
    switch(buffer[2]) { // determine the breakpoint type
    case '0': // memory break point      
      int breakpointToRemove = -1;
      for(int i=0; i < breakpoints.length; i++) {
        if(breakpoints[i] == address) {
          breakpointToRemove = i;
          break;
        }
      }
      if (breakpointToRemove >= 0) {
        int newbp[] = new int[breakpoints.length-1];
        for(int fromIndex = 0, toIndex=0; fromIndex < breakpoints.length; fromIndex++) {
          if(fromIndex != breakpointToRemove) {
            newbp[toIndex] = breakpoints[fromIndex];
            toIndex++;
          }
        }
        breakpoints = newbp;       
        replyOK();        
      }
      else { // breakpoint wasn't found
        sendCommand(null);
      }
      break;
    default: // unrecognized breakpoint type
      sendCommand(null);
    }
  }

  /* Utilities */
  /**
   * Convert the ASCII character in the byte, convert it to its
   * integer value
   */
  private static int hexToInt(byte val) {
    if ((val >= 'a') && (val <= 'f')) {
      return val - 'a' + 10;
    } else if ((val >= 'A') && (val <= 'F')) {
      return val - 'A' + 10;
    } else if ((val >= '0') && (val <= '9')) {
      return val - '0';
    } else{ // found none hex value
      return -1;
    }
  }
  /**
   * Convert the nibble integer into the ASCII character
   */
  private static byte intToHex(int val) {
    val &= 0xF;
    if ((val >= 0) && (val <= 9)) {
      return (byte)(val + '0');
    } else { // ((val >= 10) && (val <= 15))
      return (byte)(val + 'a' - 10);
    }
  }

  /**
   * Convert a range in the buffer into a String
   */
  private String bufferToString(int start, int end) {
    StringBuffer sb = new StringBuffer(end - start + 1);
    for(; start <= end; start++) {
      sb.append((char)buffer[start]);
    }
    return sb.toString();
  }
  /**
   * Read a hexadecimal value from the buffer
   */
  private int readHexValFromBuffer(int start) throws IOException {
    int result = 0;
    for(int i=0; i<8; i++) {
      int hexVal = hexToInt(buffer[start+i]);
      if (hexVal == -1) break;
      result <<= 4;
      result |= hexVal;
    }
    return result;
  }

  /**
   * Does the buffer starting at start match the byte array match
   */
  private boolean doesBufferMatch(int start, byte match[]) {
    for(int i=0; i < match.length; i++) {
      if(buffer[start+i] != match[i]) {
        return false;
      }
    }
    return true;
  }

  /**
   * Return the first index of the specified character
   */
  private int indexOf(int start, char toFind) {
    for(int i=start; i < buffer.length; i++) {
      if(buffer[i] == (byte)toFind) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Debug information
   * @param s string of debug information
   */
  private static void report(String s){
    if (true) {
      System.out.print("GDBStub:");
      System.out.println(s);
    }
  }
}
